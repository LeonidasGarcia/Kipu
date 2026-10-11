package com.kipu.app.feature.plans.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.kipu.app.core.network.AuthenticatedSessionProvider
import com.kipu.app.feature.plans.data.local.PlanPreferencesDao
import com.kipu.app.feature.plans.data.remote.ApiResult
import com.kipu.app.feature.plans.data.remote.PlanSelectionApi
import com.kipu.app.feature.plans.data.remote.toDomain
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

@HiltWorker
class SyncPlanSelectionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: PlanPreferencesDao,
    private val api: PlanSelectionApi,
    private val sessions: AuthenticatedSessionProvider,
    private val clock: Clock,
) : CoroutineWorker(context, params) {
    private fun parseRemoteInstant(value: String): Instant = OffsetDateTime.parse(value).toInstant()

    override suspend fun doWork(): Result {
        val startedAt = clock.instant()
        dao.recoverExpiredLeases(startedAt)
        val requestedUserId = inputData.getString(PlanSyncScheduler.USER_ID)?.let(UUID::fromString)
        val session = sessions.currentSession()
        val userId = requestedUserId ?: session?.userId ?: return Result.success()

        while (true) {
            val now = clock.instant()
            val operation = dao.acquireNextPending(
                userId,
                now,
                now.plus(Duration.ofMinutes(2)),
            ) ?: return if (dao.retryablePendingCount(userId) > 0) Result.retry() else Result.success()

            if (session == null || session.userId != operation.userId) {
                dao.waitForAuth(operation.operationId, now)
                continue
            }

            when (val response = api.select(session.accessToken, operation)) {
                is ApiResult.Success -> {
                    val preference = response.value.currentPreference
                    dao.reconcile(
                        operation.operationId,
                        response.value.result.toDomain(),
                        response.value.acceptedRevision.toLong(),
                        preference.selection.toDomain(),
                        parseRemoteInstant(preference.selectedAt),
                        parseRemoteInstant(preference.updatedAt),
                    )
                }
                is ApiResult.Failure -> when {
                    response.code == "UNAUTHENTICATED" -> {
                        dao.waitForAuth(operation.operationId, now)
                    }
                    !response.retryable -> {
                        dao.terminal(operation.operationId, response.code, now)
                    }
                    else -> {
                        dao.retry(
                            operation.operationId,
                            response.code,
                            now.plusSeconds(response.retryAfterSeconds ?: 30),
                            now,
                        )
                        return Result.retry()
                    }
                }
            }
        }
    }

    companion object {
        fun factory(
            dao: PlanPreferencesDao,
            api: PlanSelectionApi,
            sessions: AuthenticatedSessionProvider,
            clock: Clock,
        ) = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? = if (workerClassName == SyncPlanSelectionWorker::class.java.name) {
                SyncPlanSelectionWorker(appContext, workerParameters, dao, api, sessions, clock)
            } else {
                null
            }
        }
    }
}
