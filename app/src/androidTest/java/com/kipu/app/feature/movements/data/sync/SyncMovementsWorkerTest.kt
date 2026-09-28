package com.kipu.app.feature.movements.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.data.local.AccountDao
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.accounts.data.local.CardEntity
import com.kipu.app.feature.accounts.data.local.CreditInstallmentEntity
import com.kipu.app.feature.accounts.data.remote.FinancialApiResponse
import com.kipu.app.feature.accounts.data.remote.FinancialInstrumentsApi
import com.kipu.app.feature.accounts.data.remote.PullChangesResponseDto
import com.kipu.app.feature.accounts.data.remote.SyncChangeItemDto
import com.kipu.app.feature.movements.data.remote.MovementApi
import com.kipu.app.feature.movements.data.local.MovementDao
import com.kipu.app.feature.movements.data.local.LedgerEntryEntity
import com.kipu.app.feature.movements.data.local.TransactionEntity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncMovementsWorkerTest {
    private val userId = UUID.randomUUID().toString()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: KipuDatabase
    private lateinit var movementDao: MovementDao
    private lateinit var accountDao: AccountDao
    private val movementApi: MovementApi = mockk(relaxed = true)
    private val financialApi: FinancialInstrumentsApi = mockk()
    private val session: SessionCoordinator = mockk(relaxed = true)
    private val params: WorkerParameters = mockk(relaxed = true)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java).build()
        movementDao = database.movementDao()
        accountDao = database.accountDao()
        every { params.inputData } returns workDataOf(SyncMovementsWorker.KEY_USER_ID to userId)
        every { session.currentOwner } returns LocalOwner(userId)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun pullsAccountThenTransactionAndBuildsOneLedgerProjection() = runTest {
        val accountId = UUID.randomUUID().toString()
        val transactionId = UUID.randomUUID().toString()
        val entryId = UUID.randomUUID().toString()
        val accountChange = SyncChangeItemDto(
            sequence = 1, entityType = "ACCOUNT", entityId = accountId, revision = 1,
            operation = "UPSERT", payload = Json.parseToJsonElement(
                """{"id":"$accountId","alias":"Efectivo","type":"CASH","currency":"PEN","initial_balance_minor_units":1000,"opened_at":"2026-09-23T10:00:00Z"}"""
            ),
        )
        val transactionChange = SyncChangeItemDto(
            sequence = 2, entityType = "TRANSACTION", entityId = transactionId, revision = 1,
            operation = "UPSERT", payload = Json.parseToJsonElement(
                """{"id":"$transactionId","type":"EXPENSE","amount_minor":50,"currency_code":"PEN","source_account_id":"$accountId","category_id":null,"merchant_id":null,"merchant_provisional_text":"Bodega","occurred_at":"2026-09-23T11:00:00Z","status":"ACTIVE","ledger_entries":[{"id":"$entryId","account_id":"$accountId","role":"SOURCE","signed_amount_minor":-50,"currency_code":"PEN","created_at":"2026-09-23T11:00:00Z"}]}"""
            ),
        )
        coEvery { financialApi.pullChanges(any()) } returnsMany listOf(
            FinancialApiResponse.Success(PullChangesResponseDto(listOf(accountChange, transactionChange), 2, true)),
            FinancialApiResponse.Success(PullChangesResponseDto(emptyList(), 2, false)),
        )
        val worker = worker()

        assertTrue(worker.doWork().javaClass.simpleName.contains("Success"))
        assertEquals("Efectivo", accountDao.getById(userId, accountId)?.alias)
        assertEquals("Bodega", movementDao.getTransactionById(userId, transactionId)?.merchantProvisionalText)
        assertEquals(950L, movementDao.calculateLedgerSumForAccount(userId, accountId))
        assertEquals(950L, movementDao.getBalanceProjection(userId, accountId)?.balanceMinor)
        assertEquals(2L, movementDao.getSyncCheckpoint(userId)?.sequence)
    }

    @Test
    fun pullsCardPurchaseMetadataAndInstallmentsWithLiabilityLedger() = runTest {
        val cardId = UUID.randomUUID().toString()
        val liabilityAccountId = UUID.randomUUID().toString()
        val transactionId = UUID.randomUUID().toString()
        val installmentId = UUID.randomUUID().toString()
        val cardChange = SyncChangeItemDto(
            sequence = 1, entityType = "CARD", entityId = cardId, revision = 1,
            operation = "UPSERT", payload = Json.parseToJsonElement(
                """{"id":"$cardId","account_id":"$liabilityAccountId","alias":"BCP Visa","type":"CREDIT","currency":"PEN","network":"VISA","issuer":"BCP","last_four_digits":"7548","credit_limit_minor_units":100000,"billing_day":15,"due_day":5,"is_archived":false}""",
            ),
        )
        val purchaseChange = SyncChangeItemDto(
            sequence = 2, entityType = "TRANSACTION", entityId = transactionId, revision = 1,
            operation = "UPSERT", payload = Json.parseToJsonElement(
                """{"id":"$transactionId","type":"EXPENSE","operation_kind":"CARD_PURCHASE","card_id":"$cardId","installment_count":1,"amount_minor":12500,"currency_code":"PEN","category_id":"food","occurred_at":"2026-09-23T11:00:00Z","status":"ACTIVE","ledger_entries":[{"id":"liability-$transactionId","account_id":"$liabilityAccountId","role":"LIABILITY","signed_amount_minor":-12500,"currency_code":"PEN"}],"installments":[{"id":"$installmentId","installment_number":1,"due_date":"2026-10-05","principal_minor":12500,"interest_minor":0}]}""",
            ),
        )
        coEvery { financialApi.pullChanges(any()) } returnsMany listOf(
            FinancialApiResponse.Success(PullChangesResponseDto(listOf(cardChange, purchaseChange), 2, true)),
            FinancialApiResponse.Success(PullChangesResponseDto(emptyList(), 2, false)),
        )

        val result = worker().doWork()

        assertTrue(result.javaClass.simpleName.contains("Success"))
        assertEquals(cardId, database.cardDao().getById(userId, cardId)?.id)
        assertEquals("CREDIT_LIABILITY", accountDao.getById(userId, liabilityAccountId)?.type)
        val stored = movementDao.getTransactionById(userId, transactionId)
        assertEquals(cardId, stored?.cardId)
        assertEquals("CARD_PURCHASE", stored?.operationKind)
        assertEquals(1, stored?.installmentCount)
        assertEquals(12_500L, database.creditDao().getLedgerOutstandingPrincipalForCard(userId, cardId))
        assertEquals(-12_500L, movementDao.calculateLedgerSumForAccount(userId, liabilityAccountId))
        assertEquals("LIABILITY", movementDao.getLedgerEntriesForTransaction(userId, transactionId).single().role)
        assertEquals(2L, movementDao.getSyncCheckpoint(userId)?.sequence)
    }

    @Test
    fun freshDeviceAppliesArchivedCardSnapshotBeforeArchiveEvent() = runTest {
        val cardId = UUID.randomUUID().toString()
        val liabilityAccountId = UUID.randomUUID().toString()
        val creationChange = SyncChangeItemDto(
            sequence = 1, entityType = "CARD", entityId = cardId, revision = 2,
            operation = "UPSERT", payload = Json.parseToJsonElement(
                """{"id":"$cardId","account_id":"$liabilityAccountId","alias":"BCP Visa","type":"CREDIT","currency":"PEN","network":"VISA","issuer":"BCP","last_four_digits":"7548","credit_limit_minor_units":100000,"billing_day":15,"due_day":5,"is_archived":true}""",
            ),
        )
        val archiveChange = SyncChangeItemDto(
            sequence = 2, entityType = "CARD", entityId = cardId, revision = 2,
            operation = "ARCHIVE", payload = creationChange.payload,
        )
        coEvery { financialApi.pullChanges(any()) } returns FinancialApiResponse.Success(
            PullChangesResponseDto(listOf(creationChange, archiveChange), 2, false),
        )

        val result = worker().doWork()

        assertTrue(result.javaClass.simpleName.contains("Success"))
        assertEquals(true, database.cardDao().getById(userId, cardId)?.isArchived)
        assertEquals("CREDIT_LIABILITY", accountDao.getById(userId, liabilityAccountId)?.type)
        assertEquals(2L, movementDao.getSyncCheckpoint(userId)?.sequence)
    }

    @Test
    fun pullsCardPaymentAsOneCashEntryAndAppliesInstallmentAllocation() = runTest {
        val cardId = UUID.randomUUID().toString()
        val liabilityAccountId = UUID.randomUUID().toString()
        val accountId = UUID.randomUUID().toString()
        val purchaseId = UUID.randomUUID().toString()
        val installmentId = UUID.randomUUID().toString()
        val paymentId = UUID.randomUUID().toString()
        database.cardDao().insert(
            CardEntity(
                id = cardId,
                userId = userId,
                creationOperationId = UUID.randomUUID().toString(),
                accountId = liabilityAccountId,
                alias = "Visa principal",
                type = "CREDIT",
                currency = "PEN",
                network = "VISA",
                issuer = "Banco",
                lastFourDigits = "4242",
                creditLimitMinorUnits = 100_000L,
                billingDay = 10,
                dueDay = 20,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        accountDao.insert(
            AccountEntity(
                id = accountId,
                userId = userId,
                creationOperationId = UUID.randomUUID().toString(),
                alias = "Cuenta principal",
                type = "SAVINGS",
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
        accountDao.insert(
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
        movementDao.insertTransaction(
            TransactionEntity(
                id = purchaseId,
                userId = userId,
                type = "EXPENSE",
                amountMinor = 10_000L,
                currencyCode = "PEN",
                categoryId = "food",
                occurredAt = 1_000L,
                status = "ACTIVE",
                syncStatus = "SYNCED",
                createdAt = 1_000L,
                updatedAt = 1_000L,
                cardId = cardId,
                operationKind = "CARD_PURCHASE",
                installmentCount = 1,
            ),
        )
        movementDao.insertLedgerEntries(
            listOf(
                LedgerEntryEntity(
                    id = "purchase-liability-$purchaseId",
                    userId = userId,
                    transactionId = purchaseId,
                    accountId = liabilityAccountId,
                    role = "LIABILITY",
                    signedAmountMinor = -10_000L,
                    currencyCode = "PEN",
                    createdAt = 1_000L,
                ),
            ),
        )
        database.creditDao().insertInstallment(
            CreditInstallmentEntity(
                id = installmentId,
                userId = userId,
                transactionId = purchaseId,
                installmentNumber = 1,
                dueDate = LocalDate.parse("2026-10-20").toEpochDay(),
                principalMinor = 10_000L,
                interestMinor = 0L,
                status = "PENDING",
                revision = 1L,
                createdAt = 1_000L,
                updatedAt = 1_000L,
            ),
        )
        val paymentChange = SyncChangeItemDto(
            sequence = 1, entityType = "TRANSACTION", entityId = paymentId, revision = 1,
            operation = "UPSERT", payload = Json.parseToJsonElement(
                """{"id":"$paymentId","type":"TRANSFER","operation_kind":"CARD_PAYMENT","card_id":"$cardId","amount_minor":4000,"currency_code":"PEN","source_account_id":"$accountId","occurred_at":"2026-09-23T12:00:00Z","status":"ACTIVE","ledger_entries":[{"id":"cash-$paymentId","account_id":"$accountId","role":"SOURCE","signed_amount_minor":-4000,"currency_code":"PEN"},{"id":"liability-$paymentId","account_id":"$liabilityAccountId","role":"LIABILITY","signed_amount_minor":4000,"currency_code":"PEN"}],"allocations":[{"installment_id":"$installmentId","amount_minor":4000}]}""",
            ),
        )
        coEvery { financialApi.pullChanges(any()) } returns FinancialApiResponse.Success(
            PullChangesResponseDto(listOf(paymentChange), 1, false),
        )

        val result = worker().doWork()

        assertTrue(result.javaClass.simpleName.contains("Success"))
        assertEquals(2, movementDao.getLedgerEntriesForTransaction(userId, paymentId).size)
        assertEquals(-4_000L, movementDao.getLedgerEntriesForTransaction(userId, paymentId).single { it.role == "SOURCE" }.signedAmountMinor)
        assertEquals(4_000L, movementDao.getLedgerEntriesForTransaction(userId, paymentId).single { it.role == "LIABILITY" }.signedAmountMinor)
        assertEquals(-6_000L, movementDao.calculateLedgerSumForAccount(userId, liabilityAccountId))
        assertEquals(4_000L, database.creditDao().getAllocationsForPayment(userId, paymentId).single().allocatedMinor)
        assertEquals(6_000L, database.creditDao().getLedgerOutstandingPrincipalForCard(userId, cardId))
        assertEquals(1L, movementDao.getSyncCheckpoint(userId)?.sequence)
    }

    @Test
    fun unknownChangeRollsBackWholePageAndDoesNotAdvanceCursor() = runTest {
        val accountId = UUID.randomUUID().toString()
        val accountChange = SyncChangeItemDto(
            sequence = 1, entityType = "ACCOUNT", entityId = accountId, revision = 1,
            operation = "UPSERT", payload = Json.parseToJsonElement(
                """{"id":"$accountId","alias":"Efectivo","type":"CASH","currency":"PEN","initial_balance_minor_units":0,"opened_at":"2026-09-23T10:00:00Z"}""",
            ),
        )
        val unsupported = SyncChangeItemDto(
            sequence = 2, entityType = "FUTURE_ENTITY", entityId = "entity", revision = 1,
            operation = "UPSERT", payload = Json.parseToJsonElement("{}"),
        )
        coEvery { financialApi.pullChanges(any()) } returns FinancialApiResponse.Success(
            PullChangesResponseDto(listOf(accountChange, unsupported), 2, false),
        )

        val result = worker().doWork()

        assertTrue(result.javaClass.simpleName.contains("Retry"))
        assertEquals(null, accountDao.getById(userId, accountId))
        assertEquals(null, movementDao.getSyncCheckpoint(userId))
    }

    private fun worker() = SyncMovementsWorker(
        appContext = context,
        workerParams = params,
        movementDao = movementDao,
        api = movementApi,
        financialApi = financialApi,
        accountDao = accountDao,
        cardDao = database.cardDao(),
        database = database,
        sessionCoordinator = session,
    )
}
