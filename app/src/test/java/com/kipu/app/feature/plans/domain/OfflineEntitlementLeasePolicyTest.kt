package com.kipu.app.feature.plans.domain

import com.kipu.app.feature.plans.domain.model.OfflineEntitlementGrantClaims
import com.kipu.app.feature.plans.domain.model.OfflineEntitlementLeaseContext
import com.kipu.app.feature.plans.domain.model.OfflineEntitlementLeaseDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineEntitlementLeasePolicyTest {
    private val policy = OfflineEntitlementLeasePolicy()
    private val userId = "7d100000-0000-4000-8000-000000000001"
    private val thumbprint = "installation-thumbprint"
    private val verifiedAt = 1_000_000L
    private val seventyTwoHours = 72L * 60L * 60L * 1_000L

    @Test
    fun validGrantUsesServerTimePlusMonotonicElapsedDelta() {
        val grant = grant()
        val context = context(
            anchorElapsed = 10_000L,
            nowElapsed = 12_500L,
        )

        assertEquals(
            OfflineEntitlementLeaseDecision.Allowed(verifiedAt + 2_500L),
            policy.evaluate(grant, context),
        )
    }

    @Test
    fun exactSeventyTwoHourBoundaryRequiresRevalidation() {
        val grant = grant()

        assertTrue(
            policy.evaluate(
                grant,
                context(anchorElapsed = 5L, nowElapsed = 5L + seventyTwoHours),
            ) is OfflineEntitlementLeaseDecision.RevalidationRequired,
        )
    }

    @Test
    fun earlierCommercialEndBoundsLease() {
        val commercialEnd = verifiedAt + 12L * 60L * 60L * 1_000L
        val grant = grant(
            entitlementEndsAtMillis = commercialEnd,
            notAfterMillis = commercialEnd,
        )

        assertTrue(
            policy.evaluate(
                grant,
                context(anchorElapsed = 2_000L, nowElapsed = 2_000L + commercialEnd - verifiedAt),
            ) is OfflineEntitlementLeaseDecision.RevalidationRequired,
        )
    }

    @Test
    fun lifetimeIsStillCappedAtSeventyTwoHours() {
        val grant = grant(entitlementEndsAtMillis = null, notAfterMillis = verifiedAt + seventyTwoHours)

        assertEquals(
            OfflineEntitlementLeaseDecision.Allowed(verifiedAt + seventyTwoHours - 1L),
            policy.evaluate(
                grant,
                context(anchorElapsed = 1_000L, nowElapsed = 1_000L + seventyTwoHours - 1L),
            ),
        )
        assertTrue(
            policy.evaluate(
                grant,
                context(anchorElapsed = 1_000L, nowElapsed = 1_000L + seventyTwoHours),
            ) is OfflineEntitlementLeaseDecision.RevalidationRequired,
        )
    }

    @Test
    fun rebootOrUnavailableBootCounterRequiresRevalidation() {
        val grant = grant()

        assertTrue(
            policy.evaluate(grant, context(anchorBootCount = 4, currentBootCount = 5)) is
                OfflineEntitlementLeaseDecision.RevalidationRequired,
        )
        assertTrue(
            policy.evaluate(grant, context(currentBootCount = null)) is
                OfflineEntitlementLeaseDecision.RevalidationRequired,
        )
    }

    @Test
    fun elapsedRealtimeRegressionRequiresRevalidation() {
        val decision = policy.evaluate(
            grant(),
            context(anchorElapsed = 5_000L, nowElapsed = 4_999L),
        )

        assertTrue(decision is OfflineEntitlementLeaseDecision.RevalidationRequired)
    }

    @Test
    fun mismatchedOwnerInstallationOrPolicyRequiresRevalidation() {
        val grant = grant()

        assertTrue(
            policy.evaluate(grant, context(currentUserId = "another-user")) is
                OfflineEntitlementLeaseDecision.RevalidationRequired,
        )
        assertTrue(
            policy.evaluate(grant, context(currentThumbprint = "another-install")) is
                OfflineEntitlementLeaseDecision.RevalidationRequired,
        )
        assertTrue(
            policy.evaluate(grant, context(supportedPolicyVersion = 2)) is
                OfflineEntitlementLeaseDecision.RevalidationRequired,
        )
    }

    @Test
    fun aMissingGrantMeansPremiumRequiredRatherThanInventingEvidence() {
        assertEquals(
            OfflineEntitlementLeaseDecision.PremiumRequired,
            policy.evaluate(null, context()),
        )
    }

    @Test
    fun aSignedGrantCannotChooseALaterThanContractualNotAfter() {
        val grant = grant(notAfterMillis = verifiedAt + seventyTwoHours + 1L)

        assertTrue(
            policy.evaluate(grant, context()) is OfflineEntitlementLeaseDecision.RevalidationRequired,
        )
    }

    private fun grant(
        entitlementEndsAtMillis: Long? = verifiedAt + 7L * 24L * 60L * 60L * 1_000L,
        notAfterMillis: Long = minOf(verifiedAt + seventyTwoHours, entitlementEndsAtMillis ?: Long.MAX_VALUE),
    ) = OfflineEntitlementGrantClaims(
        version = 1,
        keyId = "test-key-1",
        grantId = "d45373b2-9b32-4e19-a847-f585e59c50ba",
        userId = userId,
        installationKeyThumbprint = thumbprint,
        policyVersion = 1,
        tier = "PREMIUM",
        serverVerifiedAtMillis = verifiedAt,
        entitlementEndsAtMillis = entitlementEndsAtMillis,
        notAfterMillis = notAfterMillis,
    )

    private fun context(
        currentUserId: String = userId,
        currentThumbprint: String? = thumbprint,
        supportedPolicyVersion: Int = 1,
        anchorElapsed: Long = 100L,
        nowElapsed: Long = 200L,
        anchorBootCount: Int? = 4,
        currentBootCount: Int? = 4,
    ) = OfflineEntitlementLeaseContext(
        currentUserId = currentUserId,
        currentInstallationKeyThumbprint = currentThumbprint,
        supportedPolicyVersion = supportedPolicyVersion,
        anchorElapsedRealtimeMillis = anchorElapsed,
        anchorBootCount = anchorBootCount,
        currentElapsedRealtimeMillis = nowElapsed,
        currentBootCount = currentBootCount,
    )
}
