package com.kipu.app.feature.plans.presentation

import com.kipu.app.feature.plans.domain.model.*

enum class TrialOfferState { TRIAL_AVAILABLE, TRIAL_UNAVAILABLE, SUBJECT_TO_VERIFICATION }
enum class PlanSelectionError { CONFIRMATION_FAILED }
sealed interface PlanSelectionEvent { data object Confirmed : PlanSelectionEvent }

data class PlanSelectionUiState(
    val selectedOption: CommercialOption = CommercialOption.FREE,
    val eligibility: TrialEligibilitySnapshot = TrialEligibilitySnapshot.UNKNOWN,
    val isLoadingEligibility: Boolean = true,
    val isConfirming: Boolean = false,
    val errorMessage: String? = null,
    val error: PlanSelectionError? = null,
) {
    val isSubmitting get() = isConfirming
    val trialOfferState get() = when (eligibility.status) {
        TrialEligibilityStatus.ELIGIBLE -> TrialOfferState.TRIAL_AVAILABLE
        TrialEligibilityStatus.INELIGIBLE -> TrialOfferState.TRIAL_UNAVAILABLE
        TrialEligibilityStatus.UNKNOWN -> TrialOfferState.SUBJECT_TO_VERIFICATION
    }
}
