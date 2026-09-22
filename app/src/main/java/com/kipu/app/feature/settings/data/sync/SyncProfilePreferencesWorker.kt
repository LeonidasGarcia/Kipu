package com.kipu.app.feature.settings.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kipu.app.core.logging.SecureLog
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.settings.domain.ProfilePreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.UUID

@HiltWorker
class SyncProfilePreferencesWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: ProfilePreferencesRepository,
    private val sessionCoordinator: SessionCoordinator,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_USER_ID = "key_user_id"
        const val WORK_NAME = "sync_profile_preferences"
    }

    override suspend fun doWork(): Result {
        val currentVerifiedOwner = sessionCoordinator.currentOwner?.verifiedUserId
        if (currentVerifiedOwner.isNullOrEmpty()) {
            SecureLog.w("SyncProfileWorker", "No active verified owner found, deferring sync")
            return Result.retry()
        }

        val userIdString = inputData.getString(KEY_USER_ID)
        val targetUserId = if (userIdString != null) {
            if (userIdString != currentVerifiedOwner) {
                SecureLog.w("SyncProfileWorker", "Outbox owner ($userIdString) differs from active verified owner ($currentVerifiedOwner), deferring sync")
                return Result.retry()
            }
            runCatching { UUID.fromString(userIdString) }.getOrNull()
        } else {
            runCatching { UUID.fromString(currentVerifiedOwner) }.getOrNull()
        }

        if (targetUserId == null) {
            SecureLog.w("SyncProfileWorker", "Invalid user ID for sync")
            return Result.failure()
        }

        return try {
            val syncResult = repository.syncPendingPreferences(targetUserId)
            if (syncResult.isSuccess) {
                Result.success()
            } else {
                val exception = syncResult.exceptionOrNull()
                if (exception is IllegalStateException && exception.message?.contains("conflict", ignoreCase = true) == true) {
                    Result.failure()
                } else {
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            SecureLog.e("SyncProfileWorker", "Work failed", e)
            Result.retry()
        }
    }
}
