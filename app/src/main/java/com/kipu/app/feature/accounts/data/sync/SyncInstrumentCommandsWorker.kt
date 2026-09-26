package com.kipu.app.feature.accounts.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kipu.app.core.logging.SecureLog
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.data.local.InstrumentSyncDao
import com.kipu.app.feature.accounts.data.local.InstrumentSyncOutboxEntity
import com.kipu.app.feature.accounts.data.local.CardDao
import com.kipu.app.feature.accounts.data.remote.CreateAccountRequestDto
import com.kipu.app.feature.accounts.data.remote.CreditCommandRequestDto
import com.kipu.app.feature.accounts.data.remote.UpdatePersonalTeaRequestDto
import com.kipu.app.feature.accounts.data.remote.DeleteUnusedCardRequestDto
import com.kipu.app.feature.accounts.data.remote.FinancialApiResponse
import com.kipu.app.feature.accounts.data.remote.FinancialInstrumentsApi
import com.kipu.app.feature.accounts.data.remote.RecordOpeningAdjustmentRequestDto
import com.kipu.app.feature.accounts.data.remote.RegisterCardRequestDto
import com.kipu.app.feature.accounts.data.remote.SetArchivedRequestDto
import com.kipu.app.feature.accounts.data.remote.UpdateAppearanceRequestDto
import com.kipu.app.feature.movements.data.local.MovementDao
import com.kipu.app.feature.movements.domain.model.MovementSyncStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstrumentSyncScheduler @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    fun scheduleSync(userId: String) {
        val workRequest = OneTimeWorkRequestBuilder<SyncInstrumentCommandsWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(workDataOf(SyncInstrumentCommandsWorker.KEY_USER_ID to userId))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "${SyncInstrumentCommandsWorker.WORK_PREFIX}_$userId",
            ExistingWorkPolicy.KEEP,
            workRequest,
        )
    }

    fun cancelSync(userId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(
            "${SyncInstrumentCommandsWorker.WORK_PREFIX}_$userId"
        )
    }
}

@HiltWorker
class SyncInstrumentCommandsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncDao: InstrumentSyncDao,
    private val cardDao: CardDao,
    private val api: FinancialInstrumentsApi,
    private val movementDao: MovementDao,
    private val sessionCoordinator: SessionCoordinator,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_USER_ID = "key_user_id"
        const val WORK_PREFIX = "instrument-sync"
        private val json = Json { ignoreUnknownKeys = true }
    }

    override suspend fun doWork(): Result {
        val currentVerifiedOwner = sessionCoordinator.currentOwner?.verifiedUserId
        val targetUserId = inputData.getString(KEY_USER_ID) ?: currentVerifiedOwner

        if (targetUserId.isNullOrEmpty()) {
            SecureLog.w("SyncInstrumentWorker", "No active verified owner found, deferring sync")
            return Result.retry()
        }

        if (currentVerifiedOwner != null && targetUserId != currentVerifiedOwner) {
            SecureLog.w("SyncInstrumentWorker", "Target owner differs from verified active owner")
            return Result.retry()
        }

        val nowMicros = System.currentTimeMillis() * 1000L
        val pendingCommands = syncDao.getPendingCommands(targetUserId, nowMicros)

        if (pendingCommands.isEmpty()) {
            return Result.success()
        }

        var anyFailed = false

        for (cmd in pendingCommands) {
            val success = processCommand(cmd, nowMicros)
            if (!success) {
                anyFailed = true
                break // Maintain causal predecessor order
            }
        }

        return if (anyFailed) Result.retry() else Result.success()
    }

    private suspend fun processCommand(cmd: InstrumentSyncOutboxEntity, nowMicros: Long): Boolean {
        syncDao.updateState(
            userId = cmd.userId,
            operationId = cmd.operationId,
            newState = "IN_FLIGHT",
            nextAttemptAt = null,
            errorCode = null,
            nowMicros = nowMicros,
        )

        if (cmd.commandType == "CONFIRM_CREDIT_PURCHASE" || cmd.commandType == "PAY_CREDIT_CARD") {
            return processCanonicalCreditCommand(cmd, nowMicros)
        }
        if (cmd.commandType == "UPDATE_CARD_PERSONAL_TEA") {
            return processPersonalTeaCommand(cmd, nowMicros)
        }

        val result: FinancialApiResponse<Any> = try {
            when (cmd.commandType) {
                "CREATE_ACCOUNT" -> {
                    val dto = json.decodeFromString<CreateAccountRequestDto>(cmd.payloadJson)
                    api.createAccount(dto)
                }
                "REGISTER_CARD" -> {
                    val dto = json.decodeFromString<RegisterCardRequestDto>(cmd.payloadJson)
                    api.registerCard(dto)
                }
                "UPDATE_APPEARANCE" -> {
                    val dto = json.decodeFromString<UpdateAppearanceRequestDto>(cmd.payloadJson)
                    api.updateAppearance(dto)
                }
                "SET_ARCHIVED" -> {
                    val dto = json.decodeFromString<SetArchivedRequestDto>(cmd.payloadJson)
                    api.setArchived(dto)
                }
                "DELETE_UNUSED_CARD" -> {
                    val dto = json.decodeFromString<DeleteUnusedCardRequestDto>(cmd.payloadJson)
                    api.deleteUnusedCard(dto)
                }
                "RECORD_OPENING_ADJUSTMENT" -> {
                    val dto = json.decodeFromString<RecordOpeningAdjustmentRequestDto>(cmd.payloadJson)
                    api.recordOpeningAdjustment(dto)
                }
                else -> {
                    SecureLog.w("SyncInstrumentWorker", "Unknown command type: ${cmd.commandType}")
                    FinancialApiResponse.Error(400, "Unknown command type")
                }
            }
        } catch (e: Exception) {
            FinancialApiResponse.NetworkFailure(e)
        }

        return when (result) {
            is FinancialApiResponse.Success -> {
                syncDao.delete(cmd.userId, cmd.operationId)
                true
            }
            is FinancialApiResponse.Error -> {
                if (result.statusCode in 400..499 && result.statusCode != 429) {
                    val errorState = if (result.statusCode == 409) "CONFLICT" else "ERROR"
                    syncDao.updateState(
                        userId = cmd.userId,
                        operationId = cmd.operationId,
                        newState = errorState,
                        nextAttemptAt = null,
                        errorCode = result.message,
                        nowMicros = System.currentTimeMillis() * 1000L,
                    )
                } else {
                    // Retryable error
                    val backoffSeconds = (1L shl kotlin.math.min(cmd.attemptCount + 1, 6)) * 5L
                    val nextAttempt = (System.currentTimeMillis() + backoffSeconds * 1000L) * 1000L
                    syncDao.updateState(
                        userId = cmd.userId,
                        operationId = cmd.operationId,
                        newState = "ERROR",
                        nextAttemptAt = nextAttempt,
                        errorCode = result.message,
                        nowMicros = System.currentTimeMillis() * 1000L,
                    )
                }
                false
            }
            is FinancialApiResponse.NetworkFailure -> {
                val backoffSeconds = (1L shl kotlin.math.min(cmd.attemptCount + 1, 6)) * 5L
                val nextAttempt = (System.currentTimeMillis() + backoffSeconds * 1000L) * 1000L
                syncDao.updateState(
                    userId = cmd.userId,
                    operationId = cmd.operationId,
                    newState = "PENDING",
                    nextAttemptAt = nextAttempt,
                    errorCode = result.exception.message,
                    nowMicros = System.currentTimeMillis() * 1000L,
                )
                false
            }
        }
    }

    private suspend fun processCanonicalCreditCommand(
        cmd: InstrumentSyncOutboxEntity,
        nowMicros: Long,
    ): Boolean {
        val response = try {
            val request = json.decodeFromString<CreditCommandRequestDto>(cmd.payloadJson)
            when (cmd.commandType) {
                "CONFIRM_CREDIT_PURCHASE" -> api.registerCreditPurchase(request)
                "PAY_CREDIT_CARD" -> api.allocateCreditPayment(request)
                else -> error("Unsupported canonical credit command")
            }
        } catch (e: Exception) {
            SecureLog.e(
                "SyncInstrumentWorker",
                "Error preparing credit command: " + e.javaClass.simpleName,
            )
            updateCreditCommandState(cmd, "ERROR", nowMicros + 10_000_000L, "INVALID_PAYLOAD")
            return false
        }

        return when (response) {
            is FinancialApiResponse.Success -> when (response.data.status) {
                "APPLIED", "DUPLICATE" -> {
                    syncDao.delete(cmd.userId, cmd.operationId)
                    movementDao.updateTransactionSyncStatus(
                        userId = cmd.userId,
                        transactionId = cmd.aggregateId,
                        syncStatus = MovementSyncStatus.SYNCED.name,
                        updatedAt = System.currentTimeMillis(),
                    )
                    true
                }
                "CONFLICT" -> {
                    updateCreditCommandState(cmd, "CONFLICT", null, response.data.error?.code ?: "CONFLICT")
                    movementDao.updateTransactionSyncStatus(
                        cmd.userId, cmd.aggregateId, MovementSyncStatus.CONFLICT.name, System.currentTimeMillis(),
                    )
                    true
                }
                "REJECTED" -> {
                    updateCreditCommandState(
                        cmd,
                        "FAILED_PERMANENT",
                        null,
                        response.data.error?.code ?: "REJECTED",
                    )
                    movementDao.updateTransactionSyncStatus(
                        cmd.userId, cmd.aggregateId, MovementSyncStatus.FAILED_PERMANENT.name, System.currentTimeMillis(),
                    )
                    true
                }
                else -> {
                    updateCreditCommandState(cmd, "ERROR", nowMicros + 10_000_000L, "UNKNOWN_STATUS")
                    false
                }
            }
            is FinancialApiResponse.Error -> {
                if (response.statusCode in 400..499 && response.statusCode != 408 && response.statusCode != 429) {
                    val state = if (response.statusCode == 409) "CONFLICT" else "FAILED_PERMANENT"
                    updateCreditCommandState(cmd, state, null, "HTTP_" + response.statusCode)
                    val syncStatus = if (state == "CONFLICT") {
                        MovementSyncStatus.CONFLICT.name
                    } else {
                        MovementSyncStatus.FAILED_PERMANENT.name
                    }
                    movementDao.updateTransactionSyncStatus(
                        cmd.userId, cmd.aggregateId, syncStatus, System.currentTimeMillis(),
                    )
                    true
                } else {
                    val backoffSeconds = (1L shl kotlin.math.min(cmd.attemptCount + 1, 6)) * 5L
                    updateCreditCommandState(cmd, "ERROR", nowMicros + backoffSeconds * 1_000_000L, "HTTP_" + response.statusCode)
                    false
                }
            }
            is FinancialApiResponse.NetworkFailure -> {
                val backoffSeconds = (1L shl kotlin.math.min(cmd.attemptCount + 1, 6)) * 5L
                updateCreditCommandState(
                    cmd,
                    "ERROR",
                    nowMicros + backoffSeconds * 1_000_000L,
                    "NETWORK_ERROR",
                )
                false
            }
        }
    }

    private suspend fun processPersonalTeaCommand(
        cmd: InstrumentSyncOutboxEntity,
        nowMicros: Long,
    ): Boolean {
        val request = try {
            json.decodeFromString<UpdatePersonalTeaRequestDto>(cmd.payloadJson)
        } catch (e: Exception) {
            updateCreditCommandState(cmd, "FAILED_PERMANENT", null, "INVALID_PAYLOAD")
            return true
        }
        val response = api.updatePersonalTea(request)
        return when (response) {
            is FinancialApiResponse.Success -> when (response.data.status) {
                "APPLIED", "DUPLICATE" -> {
                    response.data.revision?.let {
                        // Local card metadata remains owner-scoped; update revision after server acceptance.
                        cardDao.updateRemoteRevision(cmd.userId, cmd.aggregateId, it, System.currentTimeMillis() * 1_000L)
                    }
                    syncDao.delete(cmd.userId, cmd.operationId)
                    true
                }
                "CONFLICT", "REJECTED" -> {
                    updateCreditCommandState(cmd, response.data.status, null, response.data.error?.code ?: response.data.status)
                    true
                }
                else -> {
                    updateCreditCommandState(cmd, "ERROR", nowMicros + 10_000_000L, "UNKNOWN_STATUS")
                    false
                }
            }
            is FinancialApiResponse.Error -> if (response.statusCode in 400..499 && response.statusCode != 408 && response.statusCode != 429) {
                updateCreditCommandState(cmd, if (response.statusCode == 409) "CONFLICT" else "FAILED_PERMANENT", null, "HTTP_${response.statusCode}")
                true
            } else {
                updateCreditCommandState(cmd, "ERROR", nowMicros + 30_000_000L, "HTTP_${response.statusCode}")
                false
            }
            is FinancialApiResponse.NetworkFailure -> {
                updateCreditCommandState(cmd, "ERROR", nowMicros + 30_000_000L, "NETWORK_ERROR")
                false
            }
        }
    }

    private suspend fun updateCreditCommandState(
        cmd: InstrumentSyncOutboxEntity,
        state: String,
        nextAttemptAt: Long?,
        errorCode: String?,
    ) {
        syncDao.updateState(
            userId = cmd.userId,
            operationId = cmd.operationId,
            newState = state,
            nextAttemptAt = nextAttemptAt,
            errorCode = errorCode,
            nowMicros = System.currentTimeMillis() * 1_000L,
        )
    }
}
