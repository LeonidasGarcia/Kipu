package com.kipu.app.feature.categories.data.sync

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

interface CategorySyncScheduler {
    fun scheduleSync(userId: String)
    fun cancelSync(userId: String)
}

@Singleton
class WorkManagerCategorySyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : CategorySyncScheduler {
    companion object {
        const val WORK_PREFIX = "sync_categories_work"
    }

    override fun scheduleSync(userId: String) {
        val workRequest = OneTimeWorkRequestBuilder<SyncCategoryCommandsWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(workDataOf(SyncCategoryCommandsWorker.KEY_USER_ID to userId))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "${WORK_PREFIX}_$userId",
            ExistingWorkPolicy.KEEP,
            workRequest,
        )
    }

    override fun cancelSync(userId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("${WORK_PREFIX}_$userId")
    }
}
