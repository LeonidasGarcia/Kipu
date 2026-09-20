package com.kipu.app.feature.plans.domain.model

enum class AccessReason { FREE_CAPABILITY, FREE_LIMIT_REACHED, PREMIUM_ENTITLEMENT_REQUIRED }
sealed interface FeatureAccessDecision {
    data class Allowed(val reason: AccessReason) : FeatureAccessDecision
    data class Denied(val reason: AccessReason) : FeatureAccessDecision
}
