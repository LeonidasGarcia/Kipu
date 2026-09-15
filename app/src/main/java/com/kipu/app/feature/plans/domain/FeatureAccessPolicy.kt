package com.kipu.app.feature.plans.domain

import com.kipu.app.feature.plans.domain.model.*
import javax.inject.Inject

class FeatureAccessPolicy @Inject constructor() {
    fun evaluate(request: FeatureAccessRequest): FeatureAccessDecision {
        if (request.effectiveEntitlement?.verified == true) return FeatureAccessDecision.Allowed(AccessReason.FREE_CAPABILITY)
        val limit = when (request.capability) {
            Capability.FreeCore -> return FeatureAccessDecision.Allowed(AccessReason.FREE_CAPABILITY)
            Capability.PremiumOnly -> return FeatureAccessDecision.Denied(AccessReason.PREMIUM_ENTITLEMENT_REQUIRED)
            Capability.Instruments -> request.freeLimits.instruments
            Capability.CustomCategories -> request.freeLimits.customCategories
            Capability.Debts -> request.freeLimits.debts
            Capability.Goals -> request.freeLimits.goals
            Capability.Budgets -> request.freeLimits.budgets
        }
        return if ((request.currentUsage ?: 0) < limit) FeatureAccessDecision.Allowed(AccessReason.FREE_CAPABILITY)
        else FeatureAccessDecision.Denied(AccessReason.FREE_LIMIT_REACHED)
    }
}
