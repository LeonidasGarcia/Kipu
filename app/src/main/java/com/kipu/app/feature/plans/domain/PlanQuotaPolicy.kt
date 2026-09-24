package com.kipu.app.feature.plans.domain

import com.kipu.app.feature.plans.domain.model.FreePlanLimits
import com.kipu.app.feature.plans.domain.model.QuotaGroup
import com.kipu.app.feature.plans.domain.model.QuotaGroupState
import javax.inject.Inject

class PlanQuotaPolicy @Inject constructor() {
    fun evaluate(
        group: QuotaGroup,
        activeResourceIds: Collection<String>,
        selectedResourceIds: Collection<String>,
        limits: FreePlanLimits = FreePlanLimits(),
        premiumVerified: Boolean = false,
    ): QuotaGroupState {
        val limit = when (group) {
            QuotaGroup.INSTRUMENTS -> limits.instruments
            QuotaGroup.CUSTOM_CATEGORIES -> limits.customCategories
            QuotaGroup.DEBTS -> limits.debts
            QuotaGroup.GOALS -> limits.goals
            QuotaGroup.BUDGETS -> limits.budgets
        }
        return QuotaGroupState(
            group = group,
            limit = limit,
            activeResourceIds = activeResourceIds.toSet(),
            selectedResourceIds = selectedResourceIds.toSortedSet(),
            isPremiumVerified = premiumVerified,
        )
    }
}
