package com.kipu.app.feature.plans.domain

import com.kipu.app.feature.plans.domain.model.EffectiveEntitlement
import com.kipu.app.feature.plans.domain.model.GooglePlayPurchaseState
import com.kipu.app.feature.plans.domain.model.PurchaseLifecycle

/** A row returned by the authenticated server verifier; local Play callbacks are not accepted here. */
data class VerifiedBillingPurchase(
    val providerVerified: Boolean,
    val providerPurchaseState: GooglePlayPurchaseState,
    val lifecycle: PurchaseLifecycle,
    val expiresAtEpochMillis: Long?,
    val lifetime: Boolean,
)

class EffectiveEntitlementPolicy {
    fun aggregate(purchases: List<VerifiedBillingPurchase>, nowEpochMillis: Long): EffectiveEntitlement {
        val active = purchases.filter { purchase ->
            purchase.providerVerified && purchase.lifecycle.grantsAccess(
                purchase.expiresAtEpochMillis,
                nowEpochMillis,
                purchase.lifetime,
            ) && when (purchase.providerPurchaseState) {
                GooglePlayPurchaseState.PURCHASED -> purchase.lifecycle in setOf(
                    PurchaseLifecycle.ACTIVE,
                    PurchaseLifecycle.IN_GRACE_PERIOD,
                )
                GooglePlayPurchaseState.CANCELLED -> purchase.lifecycle == PurchaseLifecycle.CANCELED_ACTIVE
                GooglePlayPurchaseState.PENDING -> false
            }
        }
        if (active.any { it.lifetime }) return EffectiveEntitlement(verified = true, expiresAtEpochMillis = null)
        val expiry = active.mapNotNull { it.expiresAtEpochMillis }.maxOrNull()
        return if (expiry != null) EffectiveEntitlement(verified = true, expiresAtEpochMillis = expiry)
        else EffectiveEntitlement(verified = false, expiresAtEpochMillis = null)
    }
}
