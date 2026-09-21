package com.kipu.app.feature.settings.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface ProfileSyncScheduler {
    fun scheduleSync(userId: UUID)
}

@Singleton
class WorkManagerProfileSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ProfileSyncScheduler {

    override fun scheduleSync(userId: UUID) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncProfilePreferencesWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(SyncProfilePreferencesWorker.KEY_USER_ID to userId.toString()))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "${SyncProfilePreferencesWorker.WORK_NAME}_$userId",
            ExistingWorkPolicy.REPLACE,
            syncRequest,
        )
    }
}
