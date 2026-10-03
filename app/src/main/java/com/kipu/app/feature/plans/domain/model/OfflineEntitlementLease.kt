package com.kipu.app.feature.plans.domain.model

data class OfflineEntitlementLeaseContext(
    val currentUserId: String,
    val currentInstallationKeyThumbprint: String?,
    val supportedPolicyVersion: Int,
    val anchorElapsedRealtimeMillis: Long?,
    val anchorBootCount: Int?,
    val currentElapsedRealtimeMillis: Long,
    val currentBootCount: Int?,
)

sealed interface OfflineEntitlementLeaseDecision {
    data class Allowed(val trustedNowMillis: Long) : OfflineEntitlementLeaseDecision
    data object PremiumRequired : OfflineEntitlementLeaseDecision
    data class RevalidationRequired(val reason: Reason) : OfflineEntitlementLeaseDecision

    enum class Reason {
        INVALID_CLAIMS,
        OWNER_MISMATCH,
        INSTALLATION_MISMATCH,
        POLICY_MISMATCH,
        BOOT_CONTINUITY_LOST,
        MONOTONIC_TIME_REGRESSED,
        LEASE_EXPIRED,
    }
}
