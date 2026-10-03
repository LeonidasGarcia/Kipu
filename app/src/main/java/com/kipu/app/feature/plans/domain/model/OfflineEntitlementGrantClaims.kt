package com.kipu.app.feature.plans.domain.model

data class SignedOfflineEntitlementGrant(
    val payload: String,
    val signature: String,
    val keyId: String,
)

/** Parsed claims from the exact payload bytes of a server-signed offline grant. */
data class OfflineEntitlementGrantClaims(
    val version: Int,
    val keyId: String,
    val grantId: String,
    val userId: String,
    val installationKeyThumbprint: String,
    val policyVersion: Int,
    val tier: String,
    val serverVerifiedAtMillis: Long,
    val entitlementEndsAtMillis: Long?,
    val notAfterMillis: Long,
)
