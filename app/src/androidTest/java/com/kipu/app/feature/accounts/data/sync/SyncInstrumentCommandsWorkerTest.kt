package com.kipu.app.feature.accounts.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.data.local.CardDao
import com.kipu.app.feature.accounts.data.local.InstrumentSyncDao
import com.kipu.app.feature.accounts.data.local.InstrumentSyncOutboxEntity
import com.kipu.app.feature.accounts.data.remote.CreditCommandRequestDto
import com.kipu.app.feature.accounts.data.remote.CreditCommandResponseDto
import com.kipu.app.feature.accounts.data.remote.CreditTransactionCommandDto
import com.kipu.app.feature.accounts.data.remote.FinancialApiResponse
import com.kipu.app.feature.accounts.data.remote.FinancialInstrumentsApi
import com.kipu.app.feature.movements.data.local.MovementDao
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncInstrumentCommandsWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val syncDao: InstrumentSyncDao = mockk(relaxed = true)
    private val cardDao: CardDao = mockk(relaxed = true)
    private val api: FinancialInstrumentsApi = mockk(relaxed = true)
    private val movementDao: MovementDao = mockk(relaxed = true)
    private val sessionCoordinator: SessionCoordinator = mockk(relaxed = true)
    private val workerParams: WorkerParameters = mockk(relaxed = true)
    private val userId = UUID.randomUUID().toString()

    @Before
    fun setUp() {
        every { sessionCoordinator.currentOwner } returns null
        every { workerParams.inputData } returns workDataOf(
            SyncInstrumentCommandsWorker.KEY_USER_ID to userId,
        )
    }

    @Test
    fun confirmedPurchaseUsesCanonicalRpcAndAcknowledgesDuplicateIdempotently() = runTest {
        val command = command("CONFIRM_CREDIT_PURCHASE", "CARD_PURCHASE")
        coEvery { syncDao.getPendingCommands(userId, any()) } returns listOf(command)
        coEvery { api.registerCreditPurchase(any()) } returns FinancialApiResponse.Success(
            CreditCommandResponseDto(status = "DUPLICATE", transactionId = command.aggregateId),
        )

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) {
            api.registerCreditPurchase(match {
                it.idempotencyKey == command.operationId &&
                    it.transaction.id == command.aggregateId &&
                    it.transaction.operationKind == "CARD_PURCHASE" &&
                    it.transaction.amountMinor == 10_000L &&
                    it.transaction.installmentCount == 3
            })
        }
        coVerify(exactly = 0) { api.allocateCreditPayment(any()) }
        coVerify(exactly = 1) { syncDao.delete(userId, command.operationId) }
        coVerify(exactly = 1) {
            movementDao.updateTransactionSyncStatus(
                userId,
                command.aggregateId,
                "SYNCED",
                any(),
            )
        }
    }

    @Test
    fun paymentNetworkFailureKeepsOutboxCommandForRetry() = runTest {
        val command = command("PAY_CREDIT_CARD", "CARD_PAYMENT")
        coEvery { syncDao.getPendingCommands(userId, any()) } returns listOf(command)
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

    private fun worker() = SyncInstrumentCommandsWorker(
        appContext = context,
        workerParams = workerParams,
        syncDao = syncDao,
        cardDao = cardDao,
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
                sourceAccountId = UUID.randomUUID().toString(),
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
            aggregateType = "TRANSACTION",
            aggregateId = transactionId,
            payloadJson = Json.encodeToString(request),
            payloadHash = request.requestHash,
            createdAt = 1L,
            updatedAt = 1L,
        )
    }
}
