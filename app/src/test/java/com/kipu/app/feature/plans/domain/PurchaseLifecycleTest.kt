package com.kipu.app.feature.plans.domain

import com.kipu.app.feature.plans.domain.model.PurchaseLifecycle
import com.kipu.app.feature.plans.domain.model.GooglePlayPurchaseState
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PurchaseLifecycleTest {
    private val now = 1_900_000_000_000L
    private val future = now + 86_400_000L

    @Test
    fun `provider states normalize without confusing purchase state and entitlement`() {
        assertEquals(PurchaseLifecycle.ACTIVE, PurchaseLifecycle.fromProviderState("SUBSCRIPTION_STATE_ACTIVE", future, now))
        assertEquals(PurchaseLifecycle.IN_GRACE_PERIOD, PurchaseLifecycle.fromProviderState("SUBSCRIPTION_STATE_IN_GRACE_PERIOD", future, now))
        assertEquals(PurchaseLifecycle.ACCOUNT_HOLD, PurchaseLifecycle.fromProviderState("SUBSCRIPTION_STATE_ON_HOLD", future, now))
        assertEquals(PurchaseLifecycle.CANCELED_ACTIVE, PurchaseLifecycle.fromProviderState("SUBSCRIPTION_STATE_CANCELED", future, now))
        assertEquals(PurchaseLifecycle.EXPIRED, PurchaseLifecycle.fromProviderState("SUBSCRIPTION_STATE_CANCELED", now, now))
        assertEquals(PurchaseLifecycle.REVOKED, PurchaseLifecycle.fromProviderState("PURCHASE_STATE_CANCELLED", null, now, lifetime = true))
        assertEquals(PurchaseLifecycle.PENDING, PurchaseLifecycle.fromProviderState("SUBSCRIPTION_STATE_PENDING", null, now))
    }

    @Test
    fun `only active grace and unexpired cancellation grant subscription access`() {
        assertTrue(PurchaseLifecycle.ACTIVE.grantsAccess(future, now))
        assertTrue(PurchaseLifecycle.IN_GRACE_PERIOD.grantsAccess(future, now))
        assertTrue(PurchaseLifecycle.CANCELED_ACTIVE.grantsAccess(future, now))
        assertFalse(PurchaseLifecycle.CANCELED_ACTIVE.grantsAccess(now, now))
        assertFalse(PurchaseLifecycle.ACCOUNT_HOLD.grantsAccess(future, now))
        assertFalse(PurchaseLifecycle.EXPIRED.grantsAccess(future, now))
        assertFalse(PurchaseLifecycle.REVOKED.grantsAccess(future, now))
        assertFalse(PurchaseLifecycle.PENDING.grantsAccess(future, now))
    }

    @Test
    fun `effective access requires provider verification and Lifetime takes precedence`() {
        val policy = EffectiveEntitlementPolicy()
        val expired = VerifiedBillingPurchase(true, GooglePlayPurchaseState.PURCHASED, PurchaseLifecycle.EXPIRED, now, lifetime = false)
        val revoked = VerifiedBillingPurchase(true, GooglePlayPurchaseState.CANCELLED, PurchaseLifecycle.REVOKED, null, lifetime = false)
        val pending = VerifiedBillingPurchase(true, GooglePlayPurchaseState.PENDING, PurchaseLifecycle.PENDING, future, lifetime = false)
        assertFalse(policy.aggregate(listOf(expired, revoked, pending), now).verified)

        val lifetime = VerifiedBillingPurchase(true, GooglePlayPurchaseState.PURCHASED, PurchaseLifecycle.ACTIVE, null, lifetime = true)
        val entitlement = policy.aggregate(listOf(expired, revoked, lifetime), now)
        assertTrue(entitlement.verified)
        assertEquals(null, entitlement.expiresAtEpochMillis)
        assertFalse(policy.aggregate(listOf(lifetime.copy(providerVerified = false)), now).verified)
    }

    @Test
    fun `provider cancellation retains access only for the paid term and matching Kipu lifecycle`() {
        val policy = EffectiveEntitlementPolicy()
        val canceledActive = VerifiedBillingPurchase(
            true,
            GooglePlayPurchaseState.CANCELLED,
            PurchaseLifecycle.CANCELED_ACTIVE,
            future,
            lifetime = false,
        )
        assertTrue(policy.aggregate(listOf(canceledActive), now).verified)
        assertFalse(policy.aggregate(listOf(canceledActive.copy(expiresAtEpochMillis = now)), now).verified)
        assertFalse(policy.aggregate(listOf(canceledActive.copy(providerPurchaseState = GooglePlayPurchaseState.PURCHASED)), now).verified)
    }
}
