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
import com.kipu.app.feature.accounts.data.remote.CreateAccountRequestDto
import com.kipu.app.feature.accounts.data.remote.DeleteUnusedCardRequestDto
import com.kipu.app.feature.accounts.data.remote.FinancialApiResponse
import com.kipu.app.feature.accounts.data.remote.FinancialInstrumentsApi
import com.kipu.app.feature.accounts.data.remote.RecordOpeningAdjustmentRequestDto
import com.kipu.app.feature.accounts.data.remote.RegisterCardRequestDto
import com.kipu.app.feature.accounts.data.remote.SetArchivedRequestDto
import com.kipu.app.feature.accounts.data.remote.UpdateAppearanceRequestDto
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
    private val api: FinancialInstrumentsApi,
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
}
