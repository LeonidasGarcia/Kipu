package com.kipu.app.feature.plans.data.sync

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

@Singleton
class PlanQuotaSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun scheduleSync(userId: String) {
        val workRequest = OneTimeWorkRequestBuilder<SyncPlanQuotaWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(workDataOf(SyncPlanQuotaWorker.KEY_USER_ID to userId))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "${WORK_PREFIX}_$userId",
            ExistingWorkPolicy.REPLACE,
            workRequest,
        )
    }

    companion object {
        private const val WORK_PREFIX = "sync_plan_quota_work"
    }
}
