package com.kipu.app.feature.notifications.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface NotificationSyncScheduler {
    fun scheduleSync(userId: String)
}

@Singleton
class WorkManagerNotificationSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : NotificationSyncScheduler {
    override fun scheduleSync(userId: String) {
        if (userId.isBlank()) return
        val request = OneTimeWorkRequestBuilder<SyncNotificationsWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInputData(workDataOf(SyncNotificationsWorker.KEY_USER_ID to userId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "${SyncNotificationsWorker.WORK_NAME}_$userId",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
