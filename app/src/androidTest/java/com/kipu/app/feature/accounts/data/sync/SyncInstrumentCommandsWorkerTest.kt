package com.kipu.app.feature.accounts.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.accounts.data.local.AccountDao
import com.kipu.app.feature.accounts.data.local.CreditInstallmentEntity
import com.kipu.app.feature.accounts.data.local.CardDao
import com.kipu.app.feature.accounts.data.local.InstrumentSyncDao
import com.kipu.app.feature.accounts.data.local.InstrumentSyncOutboxEntity
import com.kipu.app.feature.accounts.data.remote.CreditCommandRequestDto
import com.kipu.app.feature.accounts.data.remote.CreditCommandResponseDto
import com.kipu.app.feature.accounts.data.remote.CreditCommandErrorDto
import com.kipu.app.feature.accounts.data.remote.CreditTransactionCommandDto
import com.kipu.app.feature.accounts.data.remote.FinancialApiResponse
import com.kipu.app.feature.accounts.data.remote.FinancialInstrumentsApi
import com.kipu.app.feature.movements.data.local.MovementDao
import com.kipu.app.feature.movements.data.local.TransactionEntity
import com.kipu.app.feature.movements.data.local.LocalCommandReceiptEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncInstrumentCommandsWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: KipuDatabase
    private lateinit var accountDao: AccountDao
    private val syncDao: InstrumentSyncDao = mockk(relaxed = true)
    private val cardDao: CardDao = mockk(relaxed = true)
    private val api: FinancialInstrumentsApi = mockk(relaxed = true)
    private val movementDao: MovementDao = mockk(relaxed = true)
    private val sessionCoordinator: SessionCoordinator = mockk(relaxed = true)
    private val workerParams: WorkerParameters = mockk(relaxed = true)
    private val userId = UUID.randomUUID().toString()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java).build()
        accountDao = database.accountDao()
        every { sessionCoordinator.currentOwner } returns null
        every { workerParams.inputData } returns workDataOf(
            SyncInstrumentCommandsWorker.KEY_USER_ID to userId,
        )
    }

    @Test
    fun confirmedPurchaseUsesCanonicalRpcAndAcknowledgesDuplicateIdempotently() = runTest {
        val command = command("CONFIRM_CREDIT_PURCHASE", "CARD_PURCHASE")
        coEvery { syncDao.getPendingCommands(userId, any()) } returnsMany listOf(listOf(command), emptyList())
        coEvery { syncDao.claimCommand(userId, command.operationId, any(), any()) } returns 1
        coEvery { api.registerCreditPurchase(any()) } returns FinancialApiResponse.Success(
            CreditCommandResponseDto(status = "DUPLICATE", transactionId = request(command).transaction.id),
        )

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) {
            api.registerCreditPurchase(match {
                it.idempotencyKey == command.operationId &&
                    it.transaction.id == request(command).transaction.id &&
                    it.transaction.operationKind == "CARD_PURCHASE" &&
                    it.transaction.amountMinor == 10_000L &&
                    it.transaction.installmentCount == 3
            })
        }
        coVerify(exactly = 0) { api.allocateCreditPayment(any()) }
        coVerify(exactly = 1) { syncDao.markSynced(userId, command.operationId, any()) }
        coVerify(exactly = 1) {
            movementDao.updateTransactionSyncStatus(
                userId,
                request(command).transaction.id,
                "SYNCED",
                any(),
            )
        }
    }

    @Test
    fun paymentNetworkFailureKeepsOutboxCommandForRetry() = runTest {
        val command = command("PAY_CREDIT_CARD", "CARD_PAYMENT")
        coEvery { syncDao.getPendingCommands(userId, any()) } returns listOf(command)
        coEvery { syncDao.claimCommand(userId, command.operationId, any(), any()) } returns 1
        coEvery { api.allocateCreditPayment(any()) } returns FinancialApiResponse.NetworkFailure(
            IOException("offline"),
        )

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        coVerify(exactly = 1) {
            api.allocateCreditPayment(match {
                it.idempotencyKey == command.operationId &&
                    it.transaction.operationKind == "CARD_PAYMENT" &&
                    it.transaction.amountMinor == 10_000L
            })
        }
        coVerify(exactly = 0) { api.registerCreditPurchase(any()) }
        coVerify(exactly = 0) { syncDao.delete(userId, command.operationId) }
        coVerify(exactly = 1) {
            syncDao.updateState(
                userId = userId,
                operationId = command.operationId,
                newState = "ERROR",
                nextAttemptAt = any(),
                errorCode = "NETWORK_ERROR",
                nowMicros = any(),
            )
        }
        coVerify(exactly = 0) {
            movementDao.updateTransactionSyncStatus(any(), any(), any(), any())
        }
    }

    @Test
    fun remotelyRejectedPurchaseIsRetainedAsFailedButNoLongerCountsAsDebt() = runTest {
        val command = command("CONFIRM_CREDIT_PURCHASE", "CARD_PURCHASE")
        val request = request(command)
        coEvery { syncDao.getPendingCommands(userId, any()) } returnsMany listOf(listOf(command), emptyList())
        coEvery { syncDao.claimCommand(userId, command.operationId, any(), any()) } returns 1
        coEvery { api.registerCreditPurchase(any()) } returns FinancialApiResponse.Success(
            CreditCommandResponseDto(
                status = "REJECTED",
                error = CreditCommandErrorDto(code = "CREDIT_LIMIT_EXCEEDED", field = "amount_minor"),
            ),
        )
        val transactionId = request.transaction.id
        database.movementDao().insertTransaction(
            TransactionEntity(
                id = transactionId,
                userId = userId,
                type = "EXPENSE",
                amountMinor = request.transaction.amountMinor,
                currencyCode = "PEN",
                categoryId = request.transaction.categoryId,
                occurredAt = 1_000L,
                status = "ACTIVE",
                syncStatus = "PENDING",
                createdAt = 1_000L,
                updatedAt = 1_000L,
                cardId = request.transaction.cardId,
                operationKind = "CARD_PURCHASE",
                installmentCount = 3,
            ),
        )
        database.creditDao().insertInstallments((1..3).map { number ->
            CreditInstallmentEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                transactionId = transactionId,
                installmentNumber = number,
                dueDate = number.toLong(),
                principalMinor = if (number == 3) 3_334L else 3_333L,
                interestMinor = 0L,
                status = "PENDING",
                revision = 1L,
                createdAt = 1_000L,
                updatedAt = 1_000L,
            )
        })
        database.movementDao().insertOrUpdateReceipt(
            LocalCommandReceiptEntity(
                userId = userId,
                idempotencyKey = command.operationId,
                requestHash = command.payloadHash,
                transactionId = transactionId,
                status = "APPLIED",
                createdAt = 1_000L,
                updatedAt = 1_000L,
            ),
        )

        val result = worker(movementDao = database.movementDao()).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        val rejected = database.movementDao().getTransactionById(userId, transactionId)
        assertEquals("FAILED", rejected?.status)
        assertEquals("FAILED_PERMANENT", rejected?.syncStatus)
        assertEquals(emptyList<CreditInstallmentEntity>(), database.creditDao().getInstallmentsForTransaction(userId, transactionId))
        assertEquals(0L, database.creditDao().getLedgerOutstandingPrincipalForCard(userId, request.transaction.cardId!!))
        assertEquals("REJECTED", database.movementDao().getReceipt(userId, command.operationId)?.status)
        coVerify(exactly = 1) { syncDao.updateState(userId, command.operationId, "FAILED_PERMANENT", null, "CREDIT_LIMIT_EXCEEDED", any()) }
    }

    private fun worker(movementDao: MovementDao = this.movementDao) = SyncInstrumentCommandsWorker(
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

    private fun command(commandType: String, operationKind: String): InstrumentSyncOutboxEntity {
        val operationId = UUID.randomUUID().toString()
        val transactionId = UUID.randomUUID().toString()
        val cardId = UUID.randomUUID().toString()
        val request = CreditCommandRequestDto(
            idempotencyKey = operationId,
            requestHash = "canonical-request-hash",
            transaction = CreditTransactionCommandDto(
                id = transactionId,
                type = if (operationKind == "CARD_PAYMENT") "TRANSFER" else "EXPENSE",
                amountMinor = 10_000L,
                currencyCode = "PEN",
                sourceAccountId = if (operationKind == "CARD_PAYMENT") UUID.randomUUID().toString() else null,
                categoryId = if (operationKind == "CARD_PURCHASE") UUID.randomUUID().toString() else null,
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
            createdAt = 1L,
            updatedAt = 1L,
        )
    }

    @After
    fun tearDown() = database.close()

    private fun request(command: InstrumentSyncOutboxEntity): CreditCommandRequestDto =
        Json.decodeFromString(command.payloadJson)
}
