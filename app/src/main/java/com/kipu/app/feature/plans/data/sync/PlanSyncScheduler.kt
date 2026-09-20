package com.kipu.app.feature.plans.data.sync

import androidx.work.*
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class PlanSyncScheduler @Inject constructor(private val workManager: WorkManager) {
    fun enqueue(userId: UUID) = workManager.enqueueUniqueWork(uniqueWorkName(userId), ExistingWorkPolicy.KEEP, oneShotRequest(userId))
    fun ensurePeriodic() = workManager.enqueueUniquePeriodicWork("plan-selection-reconcile", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<SyncPlanSelectionWorker>(15, TimeUnit.MINUTES).setConstraints(network()).build())
    companion object {
        const val USER_ID = "user_id"
        fun uniqueWorkName(userId: UUID) = "plan-selection-sync-$userId"
        fun oneShotRequest(userId: UUID) = OneTimeWorkRequestBuilder<SyncPlanSelectionWorker>().setInputData(workDataOf(USER_ID to userId.toString())).setConstraints(network()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        private fun network() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    }
}
