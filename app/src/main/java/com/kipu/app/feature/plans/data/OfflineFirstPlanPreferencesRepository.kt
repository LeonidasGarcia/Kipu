package com.kipu.app.feature.plans.data

import com.kipu.app.core.network.AuthenticatedSessionProvider
import com.kipu.app.feature.plans.data.local.PlanPreferencesDao
import com.kipu.app.feature.plans.data.remote.*
import com.kipu.app.feature.plans.data.sync.PlanSyncScheduler
import com.kipu.app.feature.plans.domain.*
import com.kipu.app.feature.plans.domain.model.*
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.*

class OfflineFirstPlanPreferencesRepository @Inject constructor(private val dao: PlanPreferencesDao, private val sessions: AuthenticatedSessionProvider, private val api: PlanSelectionApi, private val scheduler: PlanSyncScheduler, private val clock: Clock) : PlanPreferencesRepository {
    private val eligibilityState = MutableStateFlow(TrialEligibilitySnapshot.UNKNOWN)
    override val eligibility: StateFlow<TrialEligibilitySnapshot> = eligibilityState
    override val currentPreference: Flow<PlanSelection?> = flow { val s = sessions.currentSession(); if (s == null) emit(null) else emitAll(dao.observePreference(s.userId).map { it?.selection }) }
    override suspend fun getTrialEligibility(): TrialEligibilitySnapshot { refreshEligibility(); return eligibilityState.value }
    override suspend fun refreshEligibility() {
        val session = sessions.currentSession() ?: return
        eligibilityState.value = when (val result = api.eligibility(session.accessToken)) { is ApiResult.Success -> result.value.toDomain(); is ApiResult.Failure -> TrialEligibilitySnapshot.UNKNOWN }
    }
    override suspend fun confirmSelection(selection: PlanSelection, operationId: UUID) {
        val session = requireNotNull(sessions.currentSession())
        dao.confirmSelection(session.userId, selection, clock.instant(), operationId)
        scheduler.enqueue(session.userId)
    }
}
