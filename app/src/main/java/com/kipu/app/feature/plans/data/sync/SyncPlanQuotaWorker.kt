package com.kipu.app.feature.plans.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kipu.app.core.logging.SecureLog
import com.kipu.app.core.network.AuthenticatedSessionProvider
import com.kipu.app.feature.plans.data.local.PlanQuotaSelectionDao
import com.kipu.app.feature.plans.data.local.PlanQuotaSelectionItemEntity
import com.kipu.app.feature.plans.data.remote.ApiResult
import com.kipu.app.feature.plans.data.remote.PlanSelectionApi
import com.kipu.app.feature.plans.data.remote.QuotaSelectionItemDto
import com.kipu.app.feature.plans.data.remote.QuotaSelectionRequestDto
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.nio.charset.StandardCharsets
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
        val requestedUserId = inputData.getString(KEY_USER_ID)
            ?.let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() }
            ?: return deferForAuthentication()

        val initialSession = sessions.currentSession()
        if (initialSession == null || initialSession.userId != requestedUserId) {
            return deferForAuthentication()
        }

        val userId = requestedUserId.toString()
        val snapshots = dao.getAllSelectionSnapshots(userId)
        for (snapshot in snapshots) {
            val activeSession = sessions.currentSession()
            if (activeSession == null || activeSession.userId != requestedUserId) {
                return deferForAuthentication()
            }

            val items = snapshot.items
                .map { it.toRequestItem() }
                .sortedWith(ITEM_ORDER)
            val request = QuotaSelectionRequestDto(
                contractVersion = 1,
                operationId = operationId(
                    userId = userId,
                    featureKey = snapshot.selection.featureKey,
                    revision = snapshot.selection.revision,
                    items = items,
                ),
                featureKey = snapshot.selection.featureKey,
                selectionRevision = snapshot.selection.revision.toString(),
                items = items,
            )

            when (val result = api.selectQuota(activeSession.accessToken, request)) {
                is ApiResult.Success -> when (result.value.result) {
                    "APPLIED", "DUPLICATE" -> {
                        SecureLog.i(TAG, "Quota selection acknowledged")
                    }
                    "STALE", "CONFLICT" -> {
                        val acceptedRevision = result.value.acceptedRevision
                            ?.toLongOrNull()
                            ?.takeIf { it >= 0L }
                        val currentItems = result.value.currentItems
                        if (acceptedRevision == null || currentItems == null) {
                            SecureLog.w(TAG, "Quota selection response could not be reconciled")
                            return Result.retry()
                        }

                        val latest = dao.getSelectionSnapshot(userId, snapshot.selection.featureKey)
                        val serverMatchesLatest = latest?.items?.map { it.toRequestItem() }
                            ?.let { canonicalItems(it) == canonicalItems(currentItems) }
                            ?: false
                        val rebased = dao.rebaseSelection(
                            userId = userId,
                            featureKey = snapshot.selection.featureKey,
                            acceptedRevision = acceptedRevision,
                            now = System.currentTimeMillis(),
                        )
                        if (rebased == null) {
                            SecureLog.w(TAG, "Quota selection rebase was unavailable")
                        } else if (serverMatchesLatest) {
                            SecureLog.i(TAG, "Matching quota selection rebased to the accepted revision")
                        } else {
                            SecureLog.i(TAG, "Local quota selection preserved and rebased")
                        }
                        // WorkManager applies its retry backoff; do not immediately resend a stale revision.
                        return Result.retry()
                    }
                    else -> {
                        SecureLog.w(TAG, "Quota selection response contained an invalid result")
                        return terminalFailure("UNKNOWN")
                    }
                }
                is ApiResult.Failure -> when {
                    result.code == "UNAUTHENTICATED" -> return deferForAuthentication()
                    result.code in TERMINAL_API_CODES -> {
                        return terminalFailure(result.code)
                    }
                    result.retryable -> {
                        SecureLog.w(TAG, "Transient quota selection sync failure; retrying")
                        return Result.retry()
                    }
                    else -> {
                        return terminalFailure("UNKNOWN")
                    }
                }
            }
        }
        return Result.success()
    }

    private fun deferForAuthentication(): Result {
        SecureLog.w(TAG, "Quota selection sync deferred until the matching session is available")
        return Result.retry()
    }

    private fun terminalFailure(apiCode: String): Result {
        val safeCode = apiCode.takeIf { it in TERMINAL_API_CODES } ?: "UNKNOWN"
        SecureLog.e(TAG, "Terminal quota selection sync failure: $safeCode")
        return Result.failure(workDataOf(KEY_ERROR_CODE to safeCode))
    }

    companion object {
        const val KEY_USER_ID = "key_user_id"
        const val KEY_ERROR_CODE = "error_code"
        private const val TAG = "SyncPlanQuotaWorker"
        private val TERMINAL_API_CODES = setOf("INVALID_REQUEST", "UNSUPPORTED_VERSION", "FORBIDDEN")
        private val ITEM_ORDER = compareBy<QuotaSelectionItemDto>({ it.resourceId }, { it.resourceType })

        private fun operationId(
            userId: String,
            featureKey: String,
            revision: Long,
            items: List<QuotaSelectionItemDto>,
        ): String {
            val canonicalPayload = buildString {
                append("kipu-quota-selection-v1\n")
                appendField(userId)
                appendField(featureKey)
                appendField(revision.toString())
                items.sortedWith(ITEM_ORDER).forEach { item ->
                    appendField(item.resourceId)
                    appendField(item.resourceType)
                }
            }
            return UUID.nameUUIDFromBytes(canonicalPayload.toByteArray(StandardCharsets.UTF_8)).toString()
        }

        private fun StringBuilder.appendField(value: String) {
            append(value.length)
            append(':')
            append(value)
            append('\n')
        }

        private fun canonicalItems(items: List<QuotaSelectionItemDto>): List<QuotaSelectionItemDto> =
            items.sortedWith(ITEM_ORDER)

        private fun PlanQuotaSelectionItemEntity.toRequestItem() = QuotaSelectionItemDto(
            resourceId = resourceId,
            resourceType = resourceType,
        )

        fun factory(
            dao: PlanQuotaSelectionDao,
            api: PlanSelectionApi,
            sessions: AuthenticatedSessionProvider,
        ) = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? = if (workerClassName == SyncPlanQuotaWorker::class.java.name) {
                SyncPlanQuotaWorker(appContext, workerParameters, dao, api, sessions)
            } else {
                null
            }
        }
    }
}
