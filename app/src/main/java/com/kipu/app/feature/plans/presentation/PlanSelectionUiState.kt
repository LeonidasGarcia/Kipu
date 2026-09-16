package com.kipu.app.feature.plans.presentation

import com.kipu.app.feature.plans.domain.model.*

enum class PlanSelectionError { CONFIRMATION_FAILED }
sealed interface PlanSelectionEvent { data object Confirmed : PlanSelectionEvent }

data class PlanSelectionUiState(
    val selectedOption: CommercialOption = CommercialOption.ANNUAL,
    val eligibility: TrialEligibilitySnapshot = TrialEligibilitySnapshot.UNKNOWN,
    val isLoadingEligibility: Boolean = true,
    val isConfirming: Boolean = false,
    val errorMessage: String? = null,
    val error: PlanSelectionError? = null,
) {
    val isSubmitting get() = isConfirming
}
