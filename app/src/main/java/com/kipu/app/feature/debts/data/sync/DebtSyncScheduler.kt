package com.kipu.app.feature.debts.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.room.withTransaction
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.logging.SecureLog
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.data.remote.FinancialApiResponse
import com.kipu.app.feature.accounts.data.remote.FinancialInstrumentsApi
import com.kipu.app.feature.accounts.data.remote.PullChangesRequestDto
import com.kipu.app.feature.debts.data.local.DebtDao
import com.kipu.app.feature.debts.data.local.DebtOutboxDao
import com.kipu.app.feature.debts.data.local.DebtOutboxEntity
import com.kipu.app.feature.debts.data.remote.DebtApi
import com.kipu.app.feature.debts.data.remote.DebtApiResponse
import com.kipu.app.feature.debts.data.remote.DebtCommandRemoteResult
import com.kipu.app.feature.movements.data.local.MovementDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

interface DebtSyncScheduler {
    fun schedule(userId: String)
}

object NoOpDebtSyncScheduler : DebtSyncScheduler {
    override fun schedule(userId: String) = Unit
}

@Singleton
class WorkManagerDebtSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : DebtSyncScheduler {
    override fun schedule(userId: String) {
        val request = OneTimeWorkRequestBuilder<SyncDebtChangesWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInputData(workDataOf(SyncDebtChangesWorker.KEY_USER_ID to userId))
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "${SyncDebtChangesWorker.WORK_PREFIX}_$userId",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}

@HiltWorker
class SyncDebtChangesWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val database: KipuDatabase,
    private val debtDao: DebtDao,
    private val outboxDao: DebtOutboxDao,
    private val movementDao: MovementDao,
    private val debtApi: DebtApi,
    private val financialApi: FinancialInstrumentsApi,
    private val changeFeedApplier: DebtChangeFeedApplier,
    private val sessionCoordinator: SessionCoordinator,
    private val reminderReconciler: DebtReminderReconciler,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val activeOwner = sessionCoordinator.currentOwner?.verifiedUserId
        val targetOwner = inputData.getString(KEY_USER_ID) ?: activeOwner
        if (targetOwner.isNullOrBlank() || activeOwner != targetOwner) {
            SecureLog.w(TAG, "Skipping debt sync for an inactive owner")
            return Result.retry()
        }

        var needsRetry = false
        repeat(MAX_COMMANDS_PER_RUN) {
            val now = System.currentTimeMillis()
            val command = outboxDao.claimPending(targetOwner, now, limit = 1).firstOrNull() ?: return@repeat
            if (processCommand(command)) needsRetry = true
        }

        if (!pullChanges(targetOwner)) needsRetry = true
        reminderReconciler.reconcile(targetOwner)
        return if (needsRetry) Result.retry() else Result.success()
    }

    /** Returns true only for a transient failure that WorkManager should retry. */
    private suspend fun processCommand(command: DebtOutboxEntity): Boolean {
        val payload = runCatching { Json.parseToJsonElement(command.payload).jsonObject }.getOrNull()
        if (payload == null) {
            finishCommand(command, "FAILED_PERMANENT", "INVALID_LOCAL_PAYLOAD", null)
            return false
        }
        return when (val response = debtApi.execute(command.commandType, payload)) {
            is DebtApiResponse.Success -> processRemoteResult(command, response.data)
            is DebtApiResponse.Error -> {
                val retryable = response.statusCode == 401 || response.statusCode == 408 || response.statusCode == 429 || response.statusCode >= 500
                finishCommand(
                    command,
                    if (retryable) "RETRY" else "FAILED_PERMANENT",
                    "HTTP_${response.statusCode}",
                    null,
                )
                retryable
            }
            is DebtApiResponse.NetworkFailure -> {
                finishCommand(command, "RETRY", response.exception.javaClass.simpleName, null)
                true
            }
        }
    }

    private suspend fun processRemoteResult(
        command: DebtOutboxEntity,
        response: DebtCommandRemoteResult,
    ): Boolean {
        if (response.debtId != null && response.debtId != command.debtId) {
            finishCommand(command, "FAILED_PERMANENT", "REMOTE_DEBT_ID_MISMATCH", null)
            return false
        }
        return when (response.status.uppercase()) {
            "APPLIED", "DUPLICATE" -> {
                finishCommand(command, "SYNCED", null, response.revision)
                false
            }
            "CONFLICT" -> {
                finishCommand(command, "CONFLICT", "STALE_REVISION", response.currentRevision)
                false
            }
            "REJECTED" -> {
                finishCommand(command, "FAILED_PERMANENT", response.error ?: "REJECTED", null)
                false
            }
            else -> {
                finishCommand(command, "RETRY", "UNKNOWN_REMOTE_STATUS", null)
                true
            }
        }
    }

    private suspend fun finishCommand(
        command: DebtOutboxEntity,
        state: String,
        errorCode: String?,
        remoteRevision: Long?,
    ) {
        val now = System.currentTimeMillis()
        val attempt = command.attemptCount + 1
        val nextAttemptAt = if (state == "RETRY") now + retryDelay(attempt) else null
        database.withTransaction {
            val completed = outboxDao.completeClaimed(command, state, nextAttemptAt, errorCode, now)
            if (!completed) return@withTransaction
            val debtSyncState = when (state) {
                "SYNCED" -> "SYNCED"
                "CONFLICT" -> "CONFLICT"
                "FAILED_PERMANENT" -> "FAILED"
                else -> "PENDING"
            }
            if (remoteRevision != null) {
                debtDao.updateRemoteRevision(command.userId, command.debtId, remoteRevision, debtSyncState)
            } else {
                debtDao.updateSyncState(command.userId, command.debtId, debtSyncState)
            }
            val receiptStatus = when (state) {
                "SYNCED" -> "SYNCED"
                "CONFLICT" -> "CONFLICT"
                "FAILED_PERMANENT" -> "REJECTED"
                else -> "PENDING"
            }
            movementDao.updateCommandReceiptStatus(command.userId, command.operationId, receiptStatus, now)
        }
    }

    private suspend fun pullChanges(userId: String): Boolean {
        var checkpoint = debtDao.getSyncCheckpoint(userId)?.sequence ?: 0L
        repeat(MAX_PAGES_PER_RUN) {
            when (val response = financialApi.pullChanges(PullChangesRequestDto(afterSequence = checkpoint))) {
                is FinancialApiResponse.Success -> {
                    val page = response.data
                    when (val applied = changeFeedApplier.applyPage(userId, page)) {
                        is DebtChangeApplyResult.Applied -> checkpoint = applied.nextSequence
                        is DebtChangeApplyResult.Conflict -> {
                            SecureLog.w(TAG, "Debt change feed stopped on a stale revision")
                            return true
                        }
                        is DebtChangeApplyResult.Rejected -> {
                            SecureLog.w(TAG, "Debt change page rejected: ${applied.code}")
                            return false
                        }
                    }
                    if (page.changes.isEmpty() || !page.hasMore) return true
                }
                is FinancialApiResponse.Error -> return response.statusCode in 400..499 && response.statusCode != 401
                is FinancialApiResponse.NetworkFailure -> return false
            }
        }
        return false
    }

    private fun retryDelay(attempt: Int): Long =
        (BASE_RETRY_DELAY_MS * (1L shl (attempt.coerceIn(0, 10) - 1))).coerceAtMost(MAX_RETRY_DELAY_MS)

    companion object {
        const val KEY_USER_ID = "debt_sync_user_id"
        const val WORK_PREFIX = "debt-sync"
        private const val TAG = "SyncDebtChangesWorker"
        private const val MAX_COMMANDS_PER_RUN = 10
        private const val MAX_PAGES_PER_RUN = 10
        private const val BASE_RETRY_DELAY_MS = 30_000L
        private const val MAX_RETRY_DELAY_MS = 3_600_000L
    }
}
