package com.kipu.app.feature.plans.domain

import com.kipu.app.feature.plans.domain.model.*
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface PlanPreferencesRepository {
    val currentPreference: Flow<PlanSelection?> get() = flowOf(null)
    val eligibility: Flow<TrialEligibilitySnapshot> get() = flowOf(TrialEligibilitySnapshot.UNKNOWN)
    suspend fun getTrialEligibility(): TrialEligibilitySnapshot = TrialEligibilitySnapshot.UNKNOWN
    suspend fun refreshEligibility() = Unit
    suspend fun confirmSelection(selection: PlanSelection, operationId: UUID)
}

fun interface OperationIdProvider { fun create(): UUID }
