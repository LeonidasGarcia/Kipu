package com.kipu.app.feature.movements.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.accounts.data.local.CardEntity
import com.kipu.app.feature.accounts.data.local.CreditInstallmentEntity
import com.kipu.app.feature.accounts.data.local.FinancialMovementEntity
import com.kipu.app.feature.categories.data.local.CategoryEntity
import com.kipu.app.feature.categories.data.local.MerchantCatalogEntity
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.RegisterTransactionCommand
import com.kipu.app.feature.movements.domain.model.RegisterTransactionResult
import com.kipu.app.feature.movements.domain.model.Transaction
import com.kipu.app.feature.movements.data.remote.RegisterTransactionRequestDto
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MovementLocalDataSourceTest {
    private lateinit var database: KipuDatabase
    private lateinit var dao: MovementDao
    private lateinit var source: MovementLocalDataSource
    private val userId = UUID.randomUUID().toString()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.movementDao()
        source = MovementLocalDataSource(database, dao, BalanceProjectionStore(dao))
        runBlocking {
            database.categoryDao().insertCategory(
                CategoryEntity("category-id", null, null, "SYSTEM", createdAt = 1L, updatedAt = 1L)
            )
        }
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun firstMovementAndRetryLeaveProjectionEqualToLedger() = runTest {
        seedAccount("source")
        val command = command(MovementType.EXPENSE, "source", amount = 1_250L)

        val first = source.commitTransactionAtomic(command, "same-hash")
        val retry = source.commitTransactionAtomic(command, "same-hash")

        assertTrue(first is RegisterTransactionResult.Success)
        assertFalse((first as RegisterTransactionResult.Success).isDuplicate)
        assertTrue(retry is RegisterTransactionResult.Success)
        assertTrue((retry as RegisterTransactionResult.Success).isDuplicate)
        assertEquals(1, dao.getLedgerEntriesForTransaction(userId, first.transaction.id).size)
        assertEquals(-1_250L, dao.calculateLedgerSumForAccount(userId, "source"))
        assertEquals(-1_250L, dao.getBalanceProjection(userId, "source")?.balanceMinor)
    }

    @Test
    fun provisionalMerchantSurvivesLocalCommitAndOutbox() = runTest {
        seedAccount("source")
        val result = source.commitTransactionAtomic(
            command(MovementType.EXPENSE, "source", amount = 800L)
                .copy(merchantProvisionalText = "Bodega del barrio"),
            "merchant-hash",
        ) as RegisterTransactionResult.Success

        val stored = dao.getTransactionById(userId, result.transaction.id)
        assertEquals("Bodega del barrio", stored?.merchantProvisionalText)
        assertEquals(null, stored?.merchantId)
        val outbox = dao.claimPendingOutbox(userId, System.currentTimeMillis()).single()
        val payload = Json.decodeFromString<RegisterTransactionRequestDto>(outbox.payload)
        assertEquals("Bodega del barrio", payload.transaction.merchantProvisionalText)
        assertEquals(null, payload.transaction.merchantId)
    }

    @Test
    fun legacyOpeningAndManualExpenseShareOneBalance() = runTest {
        seedAccount("source")
        val movementDao = database.financialMovementDao()
        movementDao.insert(
            FinancialMovementEntity(
                id = "opening",
                operationId = "opening-operation",
                operationSequence = 0,
                userId = userId,
                kind = "OPENING",
                amountMinorUnits = 10_000L,
                currency = "PEN",
                accountId = "source",
                openingAccountId = "source",
                effectiveAt = 1_000_000L,
                createdAt = 1_000_000L,
            )
        )

        assertTrue(source.commitTransactionAtomic(
            command(MovementType.EXPENSE, "source", amount = 2_000L), "expense-hash"
        ) is RegisterTransactionResult.Success)

        assertEquals(8_000L, movementDao.getAccountBalance(userId, "source"))
        assertEquals(8_000L, dao.calculateLedgerSumForAccount(userId, "source"))
        assertEquals(8_000L, dao.getBalanceProjection(userId, "source")?.balanceMinor)
        assertEquals("OPENING", dao.getTransactionById(userId, "legacy:opening")?.legacyKind)
    }

    @Test
    fun transferUpdatesBothAccountsExactlyOnce() = runTest {
        seedAccount("source")
        seedAccount("destination")
        val command = command(MovementType.TRANSFER, "source", "destination", 4_200L)

        val result = source.commitTransactionAtomic(command, "transfer-hash")

        assertTrue(result is RegisterTransactionResult.Success)
        assertEquals(-4_200L, dao.calculateLedgerSumForAccount(userId, "source"))
        assertEquals(4_200L, dao.calculateLedgerSumForAccount(userId, "destination"))
        assertEquals(-4_200L, dao.getBalanceProjection(userId, "source")?.balanceMinor)
        assertEquals(4_200L, dao.getBalanceProjection(userId, "destination")?.balanceMinor)
    }

    @Test
    fun creditPurchasePersistsAndMapsFromObservedRoomFlowWithLiabilityLedgerOnly() = runTest {
        val cardId = UUID.randomUUID().toString()
        val liabilityAccountId = UUID.randomUUID().toString()
        val transactionId = UUID.randomUUID().toString()
        database.accountDao().insert(
            AccountEntity(
                id = liabilityAccountId,
                userId = userId,
                creationOperationId = UUID.randomUUID().toString(),
                alias = "Internal card liability",
                type = "CREDIT_LIABILITY",
                currency = "PEN",
                presetId = null,
                color = null,
                icon = null,
                initialBalanceMinorUnits = 0L,
                openedAt = 1L,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        database.cardDao().insert(
            CardEntity(
                id = cardId,
                userId = userId,
                creationOperationId = UUID.randomUUID().toString(),
                accountId = liabilityAccountId,
                alias = "BCP Visa",
                type = "CREDIT",
                currency = "PEN",
                network = "VISA",
                issuer = "BCP",
                lastFourDigits = "7548",
                creditLimitMinorUnits = 100_000L,
                billingDay = 15,
                dueDay = 5,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        val initialEmission = CompletableDeferred<Unit>()
        val mappedPurchase = CompletableDeferred<Transaction>()
        val observer = launch(Dispatchers.IO) {
            dao.observeTransactions(userId).collect { rows ->
                val domainRows = rows.map(TransactionEntity::toDomain)
                if (domainRows.isEmpty()) initialEmission.complete(Unit)
                else domainRows.singleOrNull()?.let(mappedPurchase::complete)
            }
        }

        try {
            // Room executes on real IO threads, outside runTest's virtual clock.
            withContext(Dispatchers.IO) { withTimeout(5_000L) { initialEmission.await() } }
            dao.insertTransaction(
                TransactionEntity(
                    id = transactionId,
                    userId = userId,
                    type = "EXPENSE",
                    amountMinor = 12_500L,
                    currencyCode = "PEN",
                    categoryId = "category-id",
                    occurredAt = 10_000L,
                    status = "ACTIVE",
                    syncStatus = "PENDING",
                    createdAt = 10_000L,
                    updatedAt = 10_000L,
                    cardId = cardId,
                    operationKind = "CARD_PURCHASE",
                    installmentCount = 1,
                ),
            )
            val mapped = withContext(Dispatchers.IO) { withTimeout(5_000L) { mappedPurchase.await() } }
            dao.insertLedgerEntries(
                listOf(
                    LedgerEntryEntity(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        transactionId = transactionId,
                        accountId = liabilityAccountId,
                        role = "LIABILITY",
                        signedAmountMinor = -12_500L,
                        currencyCode = "PEN",
                        createdAt = 10_000L,
                    ),
                ),
            )
            database.creditDao().insertInstallment(
                CreditInstallmentEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    transactionId = transactionId,
                    installmentNumber = 1,
                    dueDate = 20_000L,
                    principalMinor = 12_500L,
                    interestMinor = 0L,
                    status = "PENDING",
                    revision = 1L,
                    createdAt = 10_000L,
                    updatedAt = 10_000L,
                ),
            )

            assertEquals(cardId, mapped.cardId)
            assertEquals("CARD_PURCHASE", mapped.operationKind)
            assertEquals(1, mapped.installmentCount)
            assertNull(mapped.sourceAccountId)
            val entries = dao.getLedgerEntriesForTransaction(userId, transactionId)
            assertEquals(1, entries.size)
            assertEquals("LIABILITY", entries.single().role)
            assertEquals(-12_500L, entries.single().signedAmountMinor)
            assertEquals(liabilityAccountId, entries.single().accountId)
            assertEquals(12_500L, database.creditDao().getLedgerOutstandingPrincipalForCard(userId, cardId))
        } finally {
            observer.cancelAndJoin()
        }
    }

    @Test
    fun rejectsForeignOrWrongCurrencyAccountWithoutPosting() = runTest {
        seedAccount("owned")
        seedAccount("foreign", owner = UUID.randomUUID().toString())

        val foreignResult = source.commitTransactionAtomic(
            command(MovementType.INCOME, "foreign", amount = 100L), "foreign-hash"
        )
        val currencyResult = source.commitTransactionAtomic(
            command(MovementType.INCOME, "owned", amount = 100L).copy(currency = "USD"),
            "currency-hash",
        )

        assertTrue(foreignResult is RegisterTransactionResult.ValidationError)
        assertTrue(currencyResult is RegisterTransactionResult.ValidationError)
        assertEquals(null, dao.getBalanceProjection(userId, "owned"))
        assertEquals(null, dao.calculateLedgerSumForAccount(userId, "owned"))
    }

    @Test
    fun rejectsForeignInactiveCategoryAndUnknownMerchant() = runTest {
        seedAccount("source")
        database.categoryDao().insertCategory(
            CategoryEntity("foreign-category", UUID.randomUUID().toString(), null, "CUSTOM", createdAt = 1L, updatedAt = 1L)
        )
        database.categoryDao().insertCategory(
            CategoryEntity("inactive-category", null, null, "SYSTEM", isActive = false, createdAt = 1L, updatedAt = 1L)
        )
        database.categoryDao().insertCategory(
            CategoryEntity("child-category", null, "inactive-category", "SYSTEM", createdAt = 1L, updatedAt = 1L)
        )
        val base = command(MovementType.EXPENSE, "source", amount = 100L)
        assertTrue(source.commitTransactionAtomic(base.copy(categoryId = "foreign-category"), "foreign-cat") is RegisterTransactionResult.ValidationError)
        assertTrue(source.commitTransactionAtomic(base.copy(categoryId = "inactive-category"), "inactive-cat") is RegisterTransactionResult.ValidationError)
        assertTrue(source.commitTransactionAtomic(base.copy(categoryId = "child-category"), "inactive-parent") is RegisterTransactionResult.ValidationError)
        assertTrue(source.commitTransactionAtomic(base.copy(merchantId = "unknown"), "unknown-merchant") is RegisterTransactionResult.ValidationError)
        assertEquals(null, dao.calculateLedgerSumForAccount(userId, "source"))
    }

    @Test
    fun archivedAccountCannotPost() = runTest {
        seedAccount("source")
        database.accountDao().setArchived(userId, "source", true, 2L)
        val result = source.commitTransactionAtomic(
            command(MovementType.EXPENSE, "source", amount = 100L), "archived-account"
        )
        assertTrue(result is RegisterTransactionResult.ValidationError)
        assertEquals(null, dao.calculateLedgerSumForAccount(userId, "source"))
    }

    @Test
    fun acceptsActiveCatalogMerchant() = runTest {
        seedAccount("source")
        database.merchantCatalogDao().insertMerchants(listOf(
            MerchantCatalogEntity("merchant-id", "Tambo", "tambo", lastSyncedAt = 1L)
        ))
        val result = source.commitTransactionAtomic(
            command(MovementType.EXPENSE, "source", amount = 100L).copy(merchantId = "merchant-id"),
            "catalog-merchant",
        )
        assertTrue(result is RegisterTransactionResult.Success)
    }

    @Test
    fun freePlanSelectionLocksOnlyUnselectedCustomRootsWithoutArchivingThem() = runTest {
        seedAccount("source")
        val roots = (1..6).map { index ->
            val id = "custom-root-$index"
            database.categoryDao().insertCategory(
                CategoryEntity(id, userId, null, "CUSTOM", isActive = true, createdAt = index.toLong(), updatedAt = index.toLong())
            )
            id
        }
        database.planQuotaSelectionDao().replaceSelection(
            userId = userId,
            featureKey = "CUSTOM_CATEGORIES",
            resourceType = "CATEGORY_ROOT",
            resourceIds = roots.take(5),
            now = 100L,
        )

        val selected = source.commitTransactionAtomic(
            command(MovementType.EXPENSE, "source", amount = 100L).copy(categoryId = roots[0]),
            "selected-category",
        )
        val locked = source.commitTransactionAtomic(
            command(MovementType.EXPENSE, "source", amount = 100L).copy(categoryId = roots[5]),
            "locked-category",
        )
        val freeCore = source.commitTransactionAtomic(
            command(MovementType.EXPENSE, "source", amount = 100L),
            "system-category-after-category-overflow",
        )

        assertTrue(selected is RegisterTransactionResult.Success)
        assertTrue(locked is RegisterTransactionResult.ValidationError)
        assertTrue(freeCore is RegisterTransactionResult.Success)
        assertEquals(1L, database.categoryDao().getCategoryById(roots[5])?.isActive?.let { if (it) 1L else 0L })
    }

    @Test
    fun freePlanSelectionLocksOnlyUnselectedAccountsWithoutArchivingOrChangingTheirBalances() = runTest {
        val accounts = (1..5).map { index -> "account-$index".also { seedAccount(it) } }
        database.planQuotaSelectionDao().replaceSelection(
            userId = userId,
            featureKey = "INSTRUMENTS",
            resourceType = "INSTRUMENT",
            resourceIds = accounts.take(4),
            now = 100L,
        )

        val selected = source.commitTransactionAtomic(
            command(MovementType.EXPENSE, accounts.first(), amount = 100L), "selected-account",
        )
        val locked = source.commitTransactionAtomic(
            command(MovementType.EXPENSE, accounts.last(), amount = 100L), "locked-account",
        )

        assertTrue(selected is RegisterTransactionResult.Success)
        assertTrue(locked is RegisterTransactionResult.ValidationError)
        assertFalse(database.accountDao().getById(userId, accounts.last())!!.isArchived)
        assertEquals(null, dao.calculateLedgerSumForAccount(userId, accounts.last()))
    }

    @Test
    fun orphanedReceiptDoesNotPostASecondTransaction() = runTest {
        seedAccount("source")
        val command = command(MovementType.INCOME, "source", amount = 100L)
        dao.insertOrUpdateReceipt(
            LocalCommandReceiptEntity(
                userId = userId,
                idempotencyKey = command.idempotencyKey,
                requestHash = "same-hash",
                transactionId = UUID.randomUUID().toString(),
                status = "APPLIED",
            )
        )

        val result = source.commitTransactionAtomic(command, "same-hash")

        assertTrue(result is RegisterTransactionResult.Failure)
        assertEquals(null, dao.calculateLedgerSumForAccount(userId, "source"))
    }

    private suspend fun seedAccount(id: String, owner: String = userId) {
        database.accountDao().insert(
            AccountEntity(
                id = id,
                userId = owner,
                creationOperationId = UUID.randomUUID().toString(),
                alias = id,
                type = "SAVINGS",
                currency = "PEN",
                presetId = null,
                color = null,
                icon = null,
                initialBalanceMinorUnits = 0L,
                openedAt = 1L,
                createdAt = 1L,
                updatedAt = 1L,
            )
        )
    }

    private fun command(
        type: MovementType,
        source: String,
        destination: String? = null,
        amount: Long,
    ) = RegisterTransactionCommand(
        idempotencyKey = UUID.randomUUID().toString(),
        userId = userId,
        type = type,
        amountMinor = amount,
        currency = "PEN",
        sourceAccountId = source,
        destinationAccountId = destination,
        categoryId = if (type == MovementType.EXPENSE) "category-id" else null,
    )
}
