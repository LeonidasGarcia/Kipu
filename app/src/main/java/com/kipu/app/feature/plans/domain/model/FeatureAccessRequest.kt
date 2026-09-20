package com.kipu.app.feature.plans.domain.model

data class FeatureAccessRequest(val capability: Capability, val currentUsage: Int?, val freeLimits: FreePlanLimits, val effectiveEntitlement: EffectiveEntitlement?)
