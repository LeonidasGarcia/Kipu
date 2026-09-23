package com.kipu.app.feature.plans.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kipu.app.core.logging.SecureLog
import com.kipu.app.core.network.AuthenticatedSessionProvider
import com.kipu.app.feature.plans.data.local.PlanQuotaSelectionDao
import com.kipu.app.feature.plans.data.remote.ApiResult
import com.kipu.app.feature.plans.data.remote.PlanSelectionApi
import com.kipu.app.feature.plans.data.remote.QuotaSelectionItemDto
import com.kipu.app.feature.plans.data.remote.QuotaSelectionRequestDto
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.UUID

@HiltWorker
class SyncPlanQuotaWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: PlanQuotaSelectionDao,
    private val api: PlanSelectionApi,
    private val sessions: AuthenticatedSessionProvider,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val session = sessions.currentSession() ?: return Result.success()
        val userId = inputData.getString(KEY_USER_ID) ?: session.userId.toString()

        val selections = dao.getAllSelections(userId)
        for (selection in selections) {
            val items = dao.getSelectedItems(userId, selection.featureKey)
            val request = QuotaSelectionRequestDto(
                contractVersion = 1,
                operationId = UUID.randomUUID().toString(),
                featureKey = selection.featureKey,
                selectionRevision = selection.revision.toString(),
                items = items.map {
                    QuotaSelectionItemDto(
                        resourceId = it.resourceId,
                        resourceType = it.resourceType,
                    )
                },
            )

            when (val result = api.selectQuota(session.accessToken, request)) {
                is ApiResult.Success -> {
                    SecureLog.i(TAG, "Quota selection synced for ${selection.featureKey}: ${result.value.result}")
                }
                is ApiResult.Failure -> {
                    if (result.retryable) {
                        return Result.retry()
                    }
                }
            }
        }
        return Result.success()
    }

    companion object {
        const val KEY_USER_ID = "key_user_id"
        private const val TAG = "SyncPlanQuotaWorker"
    }
}
