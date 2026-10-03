package com.kipu.app.feature.accounts

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.data.local.AccountDao
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.accounts.data.local.CardDao
import com.kipu.app.feature.accounts.data.local.CardEntity
import com.kipu.app.feature.accounts.data.local.CreditInstallmentEntity
import com.kipu.app.feature.accounts.data.local.InstrumentSyncDao
import com.kipu.app.feature.accounts.data.local.InstrumentSyncOutboxEntity
import com.kipu.app.feature.accounts.data.remote.CreditCommandErrorDto
import com.kipu.app.feature.accounts.data.remote.CreditCommandRequestDto
import com.kipu.app.feature.accounts.data.remote.CreditCommandResponseDto
import com.kipu.app.feature.accounts.data.remote.CreditTransactionCommandDto
import com.kipu.app.feature.accounts.data.remote.FinancialApiResponse
import com.kipu.app.feature.accounts.data.remote.FinancialInstrumentsApi
import com.kipu.app.feature.accounts.data.sync.SyncInstrumentCommandsWorker
import com.kipu.app.feature.movements.data.local.LedgerEntryEntity
import com.kipu.app.feature.movements.data.local.LocalCommandReceiptEntity
import com.kipu.app.feature.movements.data.local.MovementDao
import com.kipu.app.feature.movements.data.local.TransactionEntity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FinancialRejectionCompensationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: KipuDatabase
    private lateinit var accountDao: AccountDao
    private lateinit var movementDao: MovementDao
    private val syncDao: InstrumentSyncDao = mockk(relaxed = true)
    private val cardDao: CardDao = mockk(relaxed = true)
    private val api: FinancialInstrumentsApi = mockk(relaxed = true)
    private val sessionCoordinator: SessionCoordinator = mockk(relaxed = true)
    private val workerParams: WorkerParameters = mockk(relaxed = true)
    private val userId = UUID.randomUUID().toString()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java).build()
        accountDao = database.accountDao()
        movementDao = database.movementDao()
        every { sessionCoordinator.currentOwner } returns null
        every { workerParams.inputData } returns workDataOf(
            SyncInstrumentCommandsWorker.KEY_USER_ID to userId,
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun creditPurchasePermanentRejectionAppliesVoidedAndCompensatoryLedgerWithoutPhysicalDelete() = runTest {
        val cardId = UUID.randomUUID().toString()
        val liabilityAccountId = UUID.randomUUID().toString()
        val transactionId = UUID.randomUUID().toString()
        val operationId = UUID.randomUUID().toString()
        val originalLedgerId = UUID.randomUUID().toString()

        // 1. Seed accounts & card
        accountDao.insert(
            AccountEntity(
                id = liabilityAccountId,
                userId = userId,
                creationOperationId = UUID.randomUUID().toString(),
                alias = "Tarjeta Visa Pasivo",
                type = "CREDIT_LIABILITY",
                currency = "PEN",
                presetId = null,
                color = null,
                icon = null,
                initialBalanceMinorUnits = 0L,
                openedAt = 1000L,
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )
        database.cardDao().insert(
            CardEntity(
                id = cardId,
                userId = userId,
                creationOperationId = UUID.randomUUID().toString(),
                accountId = liabilityAccountId,
                alias = "Visa Signature",
                type = "CREDIT",
                currency = "PEN",
                network = "VISA",
                issuer = "BCP",
                lastFourDigits = "1234",
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )

        // 2. Seed confirmed transaction with 3 installments and ledger entry
        movementDao.insertTransaction(
            TransactionEntity(
                id = transactionId,
                userId = userId,
                type = "EXPENSE",
                amountMinor = 15_000L,
                currencyCode = "PEN",
                sourceAccountId = liabilityAccountId,
                destinationAccountId = null,
                categoryId = "food",
                merchantId = null,
                cardId = cardId,
                operationKind = "CARD_PURCHASE",
                installmentCount = 3,
                occurredAt = 1000L,
                status = "ACTIVE",
                syncStatus = "IN_FLIGHT",
                revision = 1L,
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )
        movementDao.insertLedgerEntries(
            listOf(
                LedgerEntryEntity(
                    id = originalLedgerId,
                    userId = userId,
                    transactionId = transactionId,
                    accountId = liabilityAccountId,
                    role = "SOURCE",
                    signedAmountMinor = 15_000L,
                    currencyCode = "PEN",
                    createdAt = 1000L,
                )
            )
        )
        database.creditDao().insertInstallments((1..3).map { number ->
            CreditInstallmentEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                transactionId = transactionId,
                installmentNumber = number,
                dueDate = number.toLong(),
                principalMinor = 5_000L,
                interestMinor = 0L,
                status = "PENDING",
                revision = 1L,
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        })
        movementDao.insertOrUpdateReceipt(
            LocalCommandReceiptEntity(
                userId = userId,
                idempotencyKey = operationId,
                requestHash = "hash-purchase",
                transactionId = transactionId,
                status = "APPLIED",
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )

        // 3. Prepare outbox command and mock remote rejection
        val outboxCommand = createOutboxCommand(
            operationId = operationId,
            commandType = "CONFIRM_CREDIT_PURCHASE",
            operationKind = "CARD_PURCHASE",
            transactionId = transactionId,
            cardId = cardId,
            amountMinor = 15_000L,
        )
        coEvery { syncDao.getPendingCommands(userId, any()) } returnsMany listOf(listOf(outboxCommand), emptyList())
        coEvery { syncDao.claimCommand(userId, operationId, any(), any()) } returns 1
        coEvery { api.registerCreditPurchase(any()) } returns FinancialApiResponse.Success(
            CreditCommandResponseDto(
                status = "REJECTED",
                error = CreditCommandErrorDto(code = "CREDIT_LIMIT_EXCEEDED", field = "amount_minor"),
            )
        )

        // 4. Run worker
        val worker = SyncInstrumentCommandsWorker(
            appContext = context,
            workerParams = workerParams,
            syncDao = syncDao,
            cardDao = cardDao,
            accountDao = accountDao,
            database = database,
            api = api,
            movementDao = movementDao,
            sessionCoordinator = sessionCoordinator,
        )
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        // 5. Verify ZERO physical DELETE and state preservation
        val transaction = movementDao.getTransactionById(userId, transactionId)
        assertNotNull(transaction)
        assertEquals("VOIDED", transaction?.status)
        assertEquals("FAILED_PERMANENT", transaction?.syncStatus)
        assertEquals(2L, transaction?.revision)

        // All 3 installments preserved, status set to VOIDED
        val installments = database.creditDao().getInstallmentsForTransaction(userId, transactionId)
        assertEquals(3, installments.size)
        assertTrue(installments.all { it.status == "VOIDED" })

        // Ledger entries preserved: 1 original + 1 compensatory reversal = 2 rows
        val ledgerRows = movementDao.getLedgerEntriesForTransaction(userId, transactionId)
        assertEquals(2, ledgerRows.size)
        assertEquals(1, ledgerRows.count { it.id == originalLedgerId })
        // Net liability is 0
        assertEquals(0L, ledgerRows.sumOf { it.signedAmountMinor })
        assertEquals(0L, database.creditDao().getLedgerOutstandingPrincipalForCard(userId, cardId))

        // Receipts preserved
        assertEquals("REJECTED", movementDao.getReceipt(userId, operationId)?.status)
    }

    @Test
    fun cardPaymentPermanentRejectionAppliesVoidedAndCompensatesBothAccountsWithoutPhysicalDelete() = runTest {
        val cardId = UUID.randomUUID().toString()
        val liquidAccountId = UUID.randomUUID().toString()
        val liabilityAccountId = UUID.randomUUID().toString()
        val transactionId = UUID.randomUUID().toString()
        val operationId = UUID.randomUUID().toString()
        val liquidLedgerId = UUID.randomUUID().toString()
        val liabilityLedgerId = UUID.randomUUID().toString()

        // 1. Seed liquid and liability accounts
        accountDao.insert(
            AccountEntity(
                id = liquidAccountId,
                userId = userId,
                creationOperationId = UUID.randomUUID().toString(),
                alias = "Cuenta Corriente BCP",
                type = "BANK",
                currency = "PEN",
                presetId = null,
                color = null,
                icon = null,
                initialBalanceMinorUnits = 50_000L,
                openedAt = 1000L,
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )
        accountDao.insert(
            AccountEntity(
                id = liabilityAccountId,
                userId = userId,
                creationOperationId = UUID.randomUUID().toString(),
                alias = "Tarjeta Pasivo",
                type = "CREDIT_LIABILITY",
                currency = "PEN",
                presetId = null,
                color = null,
                icon = null,
                initialBalanceMinorUnits = 0L,
                openedAt = 1000L,
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )
        database.cardDao().insert(
            CardEntity(
                id = cardId,
                userId = userId,
                creationOperationId = UUID.randomUUID().toString(),
                accountId = liabilityAccountId,
                alias = "Visa Signature",
                type = "CREDIT",
                currency = "PEN",
                network = "VISA",
                issuer = "BCP",
                lastFourDigits = "1234",
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )

        // 2. Seed confirmed payment: -10000 on liquid account, -10000 on liability account
        movementDao.insertTransaction(
            TransactionEntity(
                id = transactionId,
                userId = userId,
                type = "TRANSFER",
                amountMinor = 10_000L,
                currencyCode = "PEN",
                sourceAccountId = liquidAccountId,
                destinationAccountId = liabilityAccountId,
                categoryId = null,
                merchantId = null,
                cardId = cardId,
                operationKind = "CARD_PAYMENT",
                installmentCount = 1,
                occurredAt = 1000L,
                status = "ACTIVE",
                syncStatus = "IN_FLIGHT",
                revision = 1L,
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )
        movementDao.insertLedgerEntries(
            listOf(
                LedgerEntryEntity(
                    id = liquidLedgerId,
                    userId = userId,
                    transactionId = transactionId,
                    accountId = liquidAccountId,
                    role = "SOURCE",
                    signedAmountMinor = -10_000L,
                    currencyCode = "PEN",
                    createdAt = 1000L,
                ),
                LedgerEntryEntity(
                    id = liabilityLedgerId,
                    userId = userId,
                    transactionId = transactionId,
                    accountId = liabilityAccountId,
                    role = "DESTINATION",
                    signedAmountMinor = -10_000L,
                    currencyCode = "PEN",
                    createdAt = 1000L,
                )
            )
        )
        movementDao.insertOrUpdateReceipt(
            LocalCommandReceiptEntity(
                userId = userId,
                idempotencyKey = operationId,
                requestHash = "hash-payment",
                transactionId = transactionId,
                status = "APPLIED",
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )

        // 3. Prepare outbox command and mock remote permanent rejection
        val outboxCommand = createOutboxCommand(
            operationId = operationId,
            commandType = "ALLOCATE_CREDIT_PAYMENT",
            operationKind = "CARD_PAYMENT",
            transactionId = transactionId,
            cardId = cardId,
            amountMinor = 10_000L,
            sourceAccountId = liquidAccountId,
        )
        coEvery { syncDao.getPendingCommands(userId, any()) } returnsMany listOf(listOf(outboxCommand), emptyList())
        coEvery { syncDao.claimCommand(userId, operationId, any(), any()) } returns 1
        coEvery { api.allocateCreditPayment(any()) } returns FinancialApiResponse.Success(
            CreditCommandResponseDto(
                status = "REJECTED",
                error = CreditCommandErrorDto(code = "PAYMENT_REJECTED"),
            )
        )

        // 4. Run worker
        val worker = SyncInstrumentCommandsWorker(
            appContext = context,
            workerParams = workerParams,
            syncDao = syncDao,
            cardDao = cardDao,
            accountDao = accountDao,
            database = database,
            api = api,
            movementDao = movementDao,
            sessionCoordinator = sessionCoordinator,
        )
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        // 5. Verify NO physical DELETE and full compensation
        val transaction = movementDao.getTransactionById(userId, transactionId)
        assertNotNull(transaction)
        assertEquals("VOIDED", transaction?.status)
        assertEquals("FAILED_PERMANENT", transaction?.syncStatus)

        // Ledger entries: 2 originals + 2 reversals = 4 rows
        val ledgerRows = movementDao.getLedgerEntriesForTransaction(userId, transactionId)
        assertEquals(4, ledgerRows.size)

        // Net effect of payment transaction across both accounts is 0
        assertEquals(0L, ledgerRows.filter { it.accountId == liquidAccountId }.sumOf { it.signedAmountMinor })
        assertEquals(0L, ledgerRows.filter { it.accountId == liabilityAccountId }.sumOf { it.signedAmountMinor })
    }

    private fun createOutboxCommand(
        operationId: String,
        commandType: String,
        operationKind: String,
        transactionId: String,
        cardId: String,
        amountMinor: Long,
        sourceAccountId: String? = null,
    ): InstrumentSyncOutboxEntity {
        val request = CreditCommandRequestDto(
            idempotencyKey = operationId,
            requestHash = "canonical-request-hash",
            transaction = CreditTransactionCommandDto(
                id = transactionId,
                type = if (operationKind == "CARD_PAYMENT") "TRANSFER" else "EXPENSE",
                amountMinor = amountMinor,
                currencyCode = "PEN",
                sourceAccountId = sourceAccountId,
                categoryId = if (operationKind == "CARD_PURCHASE") "food" else null,
                occurredAt = "2026-09-24T12:00:00Z",
                cardId = cardId,
                operationKind = operationKind,
                installmentCount = if (operationKind == "CARD_PURCHASE") 3 else 1,
            ),
        )
        return InstrumentSyncOutboxEntity(
            operationId = operationId,
            userId = userId,
            commandType = commandType,
            aggregateType = "CARD",
            aggregateId = cardId,
            payloadJson = Json.encodeToString(request),
            payloadHash = request.requestHash,
            createdAt = 1000L,
            updatedAt = 1000L,
        )
    }
}
