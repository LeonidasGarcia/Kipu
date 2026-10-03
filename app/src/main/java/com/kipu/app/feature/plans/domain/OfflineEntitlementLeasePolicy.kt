package com.kipu.app.feature.plans.domain

import com.kipu.app.feature.plans.domain.model.OfflineEntitlementGrantClaims
import com.kipu.app.feature.plans.domain.model.OfflineEntitlementLeaseContext
import com.kipu.app.feature.plans.domain.model.OfflineEntitlementLeaseDecision
import java.util.UUID
import javax.inject.Inject

/** Pure evaluation of a previously signature-verified offline entitlement grant. */
class OfflineEntitlementLeasePolicy @Inject constructor() {
    companion object {
        const val POLICY_VERSION = 1
        const val GRANT_VERSION = 1
        const val OFFLINE_CONCESSION_MILLIS = 72L * 60L * 60L * 1_000L
    }

    fun evaluate(
        grant: OfflineEntitlementGrantClaims?,
        context: OfflineEntitlementLeaseContext,
    ): OfflineEntitlementLeaseDecision {
        if (grant == null) return OfflineEntitlementLeaseDecision.PremiumRequired

        if (grant.userId != context.currentUserId) {
            return revalidation(OfflineEntitlementLeaseDecision.Reason.OWNER_MISMATCH)
        }
        if (context.currentInstallationKeyThumbprint.isNullOrBlank() ||
            grant.installationKeyThumbprint != context.currentInstallationKeyThumbprint
        ) {
            return revalidation(OfflineEntitlementLeaseDecision.Reason.INSTALLATION_MISMATCH)
        }
        if (grant.policyVersion != context.supportedPolicyVersion ||
            context.supportedPolicyVersion != POLICY_VERSION
        ) {
            return revalidation(OfflineEntitlementLeaseDecision.Reason.POLICY_MISMATCH)
        }

        val validClaimShape = grant.version == GRANT_VERSION &&
            grant.keyId.isNotBlank() &&
            runCatching { UUID.fromString(grant.grantId) }.isSuccess &&
            grant.tier == "PREMIUM" &&
            grant.serverVerifiedAtMillis > 0L &&
            (grant.entitlementEndsAtMillis == null || grant.entitlementEndsAtMillis > grant.serverVerifiedAtMillis)
        if (!validClaimShape) {
            return revalidation(OfflineEntitlementLeaseDecision.Reason.INVALID_CLAIMS)
        }

        val anchorElapsed = context.anchorElapsedRealtimeMillis
            ?: return revalidation(OfflineEntitlementLeaseDecision.Reason.BOOT_CONTINUITY_LOST)
        val anchorBoot = context.anchorBootCount
            ?: return revalidation(OfflineEntitlementLeaseDecision.Reason.BOOT_CONTINUITY_LOST)
        val currentBoot = context.currentBootCount
            ?: return revalidation(OfflineEntitlementLeaseDecision.Reason.BOOT_CONTINUITY_LOST)
        if (anchorBoot != currentBoot) {
            return revalidation(OfflineEntitlementLeaseDecision.Reason.BOOT_CONTINUITY_LOST)
        }
        if (context.currentElapsedRealtimeMillis < anchorElapsed) {
            return revalidation(OfflineEntitlementLeaseDecision.Reason.MONOTONIC_TIME_REGRESSED)
        }

        val concessionEnd = runCatching {
            Math.addExact(grant.serverVerifiedAtMillis, OFFLINE_CONCESSION_MILLIS)
        }.getOrElse {
            return revalidation(OfflineEntitlementLeaseDecision.Reason.INVALID_CLAIMS)
        }
        val contractualNotAfter = minOf(
            concessionEnd,
            grant.entitlementEndsAtMillis ?: Long.MAX_VALUE,
        )
        if (grant.notAfterMillis != contractualNotAfter) {
            return revalidation(OfflineEntitlementLeaseDecision.Reason.INVALID_CLAIMS)
        }

        val elapsedDelta = context.currentElapsedRealtimeMillis - anchorElapsed
        val trustedNow = runCatching {
            Math.addExact(grant.serverVerifiedAtMillis, elapsedDelta)
        }.getOrElse {
            return revalidation(OfflineEntitlementLeaseDecision.Reason.INVALID_CLAIMS)
        }
        if (trustedNow >= contractualNotAfter) {
            return revalidation(OfflineEntitlementLeaseDecision.Reason.LEASE_EXPIRED)
        }

        return OfflineEntitlementLeaseDecision.Allowed(trustedNow)
    }

    private fun revalidation(reason: OfflineEntitlementLeaseDecision.Reason) =
        OfflineEntitlementLeaseDecision.RevalidationRequired(reason)
}
