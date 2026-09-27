package com.kipu.app.feature.notifications.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.notifications.domain.NotificationsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.CancellationException

@HiltWorker
class SyncNotificationsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: NotificationsRepository,
    private val sessionCoordinator: SessionCoordinator,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val verifiedOwner = sessionCoordinator.currentOwner?.verifiedUserId
            ?: return Result.retry()
        val requestedOwner = inputData.getString(KEY_USER_ID)
        if (requestedOwner != null && requestedOwner != verifiedOwner) {
            return Result.retry()
        }

        return try {
            val syncResult = repository.syncPending(verifiedOwner)
            if (syncResult.isSuccess) Result.success() else Result.retry()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_USER_ID = "notification_sync_user_id"
        const val WORK_NAME = "sync_notifications"
    }
}
