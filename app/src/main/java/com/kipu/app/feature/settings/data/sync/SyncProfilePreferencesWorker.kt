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
        val userIdString = inputData.getString(KEY_USER_ID)
        val userId: UUID? = if (userIdString != null) {
            runCatching { UUID.fromString(userIdString) }.getOrNull()
        } else {
            sessionCoordinator.currentOwner?.verifiedUserId?.let {
                runCatching { UUID.fromString(it) }.getOrNull()
            }
        }

        if (userId == null) {
            SecureLog.w("SyncProfileWorker", "No active user ID found for sync")
            return Result.success()
        }

        return try {
            val syncResult = repository.syncPendingPreferences(userId)
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
