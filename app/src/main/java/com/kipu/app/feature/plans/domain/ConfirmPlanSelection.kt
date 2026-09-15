package com.kipu.app.feature.plans.domain

import com.kipu.app.feature.plans.domain.model.*
import java.util.UUID
import javax.inject.Inject

class ConfirmPlanSelection @Inject constructor(
    private val repository: PlanPreferencesRepository,
    private val operationIds: OperationIdProvider,
) {
    suspend operator fun invoke(option: CommercialOption, eligibility: TrialEligibilitySnapshot): PlanSelection {
        val selection = map(option, eligibility)
        repository.confirmSelection(selection, operationIds.create())
        return selection
    }
    companion object {
        fun map(option: CommercialOption, eligibility: TrialEligibilitySnapshot): PlanSelection = when (option) {
            CommercialOption.FREE -> PlanSelection.FREE
            CommercialOption.MONTHLY, CommercialOption.ANNUAL -> if (eligibility.isEligible()) PlanSelection.TRIAL_INTENT else PlanSelection.PREMIUM_INTENT
            CommercialOption.LIFETIME -> PlanSelection.PREMIUM_INTENT
        }
    }
}
