package com.kipu.app.feature.movements.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.accounts.data.local.FinancialMovementEntity
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.RegisterTransactionCommand
import com.kipu.app.feature.movements.domain.model.RegisterTransactionResult
import com.kipu.app.feature.movements.data.remote.RegisterTransactionRequestDto
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
