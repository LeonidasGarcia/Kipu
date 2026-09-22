package com.kipu.app.feature.movements.data.sync

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
import com.kipu.app.feature.movements.data.local.MovementDao
import com.kipu.app.feature.movements.data.local.MovementOutboxEntity
import com.kipu.app.feature.movements.data.remote.MovementApi
import com.kipu.app.feature.movements.data.remote.MovementApiResponse
import com.kipu.app.feature.movements.data.remote.RegisterTransactionRequestDto
import com.kipu.app.feature.movements.domain.model.MovementSyncStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MovementSyncScheduler @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    fun scheduleSync(userId: String) {
        val workRequest = OneTimeWorkRequestBuilder<SyncMovementsWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(workDataOf(SyncMovementsWorker.KEY_USER_ID to userId))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "${SyncMovementsWorker.WORK_PREFIX}_$userId",
            ExistingWorkPolicy.KEEP,
            workRequest,
        )
    }

    fun cancelSync(userId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(
            "${SyncMovementsWorker.WORK_PREFIX}_$userId"
        )
    }
}

@HiltWorker
class SyncMovementsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val movementDao: MovementDao,
    private val api: MovementApi,
    private val sessionCoordinator: SessionCoordinator,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_USER_ID = "key_user_id"
        const val WORK_PREFIX = "movement-sync"
        private val json = Json { ignoreUnknownKeys = true }
    }

    override suspend fun doWork(): Result {
        val currentVerifiedOwner = sessionCoordinator.currentOwner?.verifiedUserId
        val targetUserId = inputData.getString(KEY_USER_ID) ?: currentVerifiedOwner

        if (targetUserId.isNullOrEmpty()) {
            SecureLog.w("SyncMovementsWorker", "No active verified owner found, deferring sync")
            return Result.retry()
        }

        if (currentVerifiedOwner != null && targetUserId != currentVerifiedOwner) {
            SecureLog.w("SyncMovementsWorker", "Target owner differs from verified active owner")
            return Result.retry()
        }

        val now = System.currentTimeMillis()
        val pendingCommands = movementDao.claimPendingOutbox(targetUserId, now, limit = 10)

        if (pendingCommands.isEmpty()) {
            return Result.success()
        }

        var anyFailed = false

        for (cmd in pendingCommands) {
            val success = processCommand(cmd, targetUserId, now)
            if (!success) {
                anyFailed = true
            }
        }

        return if (anyFailed) Result.retry() else Result.success()
    }

    private suspend fun processCommand(cmd: MovementOutboxEntity, userId: String, now: Long): Boolean {
        val leaseUntil = now + 60_000L // 1 minute lease
        movementDao.setOutboxLease(userId, cmd.id, "IN_FLIGHT", leaseUntil)

        return try {
            val requestDto = json.decodeFromString<RegisterTransactionRequestDto>(cmd.payload)
            when (val response = api.registerTransaction(requestDto)) {
                is MovementApiResponse.Success -> {
                    when (response.data.status) {
                        "APPLIED", "DUPLICATE" -> {
                            movementDao.updateOutboxResult(userId, cmd.id, "SYNCED", null, null)
                            movementDao.updateTransactionSyncStatus(userId, cmd.aggregateId, MovementSyncStatus.SYNCED.name)
                            true
                        }
                        "CONFLICT" -> {
                            movementDao.updateOutboxResult(userId, cmd.id, "CONFLICT", null, "CONFLICT")
                            movementDao.updateTransactionSyncStatus(userId, cmd.aggregateId, MovementSyncStatus.CONFLICT.name)
                            true
                        }
                        "REJECTED" -> {
                            movementDao.updateOutboxResult(userId, cmd.id, "FAILED_PERMANENT", null, response.data.error?.code ?: "REJECTED")
                            movementDao.updateTransactionSyncStatus(userId, cmd.aggregateId, MovementSyncStatus.FAILED_PERMANENT.name)
                            true
                        }
                        else -> {
                            movementDao.updateOutboxResult(userId, cmd.id, "RETRY", now + 10_000L, "UNKNOWN_STATUS")
                            false
                        }
                    }
                }
                is MovementApiResponse.Error -> {
                    val isPermanent = response.statusCode in 400..499 && response.statusCode != 408 && response.statusCode != 429
                    if (isPermanent) {
                        movementDao.updateOutboxResult(userId, cmd.id, "FAILED_PERMANENT", null, "HTTP_${response.statusCode}")
                        movementDao.updateTransactionSyncStatus(userId, cmd.aggregateId, MovementSyncStatus.FAILED_PERMANENT.name)
                        true
                    } else {
                        val nextBackoff = now + (5_000L * (1L shl (cmd.attemptCount.coerceAtMost(5))))
                        movementDao.updateOutboxResult(userId, cmd.id, "RETRY", nextBackoff, "HTTP_${response.statusCode}")
                        false
                    }
                }
                is MovementApiResponse.NetworkFailure -> {
                    val nextBackoff = now + (5_000L * (1L shl (cmd.attemptCount.coerceAtMost(5))))
                    movementDao.updateOutboxResult(userId, cmd.id, "RETRY", nextBackoff, "NETWORK_ERROR")
                    false
                }
            }
        } catch (e: Exception) {
            SecureLog.e("SyncMovementsWorker", "Error processing outbox command: ${e.javaClass.simpleName}")
            val nextBackoff = now + (5_000L * (1L shl (cmd.attemptCount.coerceAtMost(5))))
            movementDao.updateOutboxResult(userId, cmd.id, "RETRY", nextBackoff, "PARSE_ERROR")
            false
        }
    }
}
