package com.kipu.app.feature.movements.domain

import com.kipu.app.feature.movements.domain.model.MovementHistoryAccessDecision
import com.kipu.app.feature.movements.domain.model.MovementHistoryQuery
import javax.inject.Inject

data class MovementEntitlementEvidence(
    val verified: Boolean,
    val verifiedServerTimeMillis: Long,
    val entitlementExpiresAtMillis: Long?,
    val isLifetime: Boolean = false,
    val monotonicContinuityValid: Boolean = false,
    val trustedNowMillis: Long? = null,
    val revalidationRequired: Boolean = false,
)

interface MovementEntitlementProvider {
    suspend fun getEffectiveEntitlement(userId: String): MovementEntitlementEvidence?
}

interface MovementHistoryAccessPolicy {
    fun evaluate(
        userId: String?,
        query: MovementHistoryQuery,
        evidence: MovementEntitlementEvidence?,
        requiresAdvancedAccess: Boolean = query.requiresAdvancedAccess,
    ): MovementHistoryAccessDecision
}

class DefaultMovementHistoryAccessPolicy @Inject constructor() : MovementHistoryAccessPolicy {
    companion object {
        const val OFFLINE_CONCESSION_HOURS = 72L
        const val OFFLINE_CONCESSION_MILLIS = OFFLINE_CONCESSION_HOURS * 60L * 60L * 1000L
    }

    override fun evaluate(
        userId: String?,
        query: MovementHistoryQuery,
        evidence: MovementEntitlementEvidence?,
        requiresAdvancedAccess: Boolean,
    ): MovementHistoryAccessDecision {
        if (userId.isNullOrBlank()) {
            return MovementHistoryAccessDecision.NotAuthorized
        }

        // Basic query is available to everyone for their entire local history.
        if (!requiresAdvancedAccess) {
            return MovementHistoryAccessDecision.Allowed
        }

        // Advanced criteria requires verified entitlement and bounded offline concession
        if (evidence == null || !evidence.verified) {
            if (evidence?.revalidationRequired == true || evidence?.monotonicContinuityValid == false && evidence.verified) {
                return MovementHistoryAccessDecision.RevalidationRequired(query.toBasicFallback())
            }
            return MovementHistoryAccessDecision.PremiumRequired(query.toBasicFallback())
        }

        // Continuity check: reboot without verifiable monotonic continuity requires revalidation
        if (!evidence.monotonicContinuityValid) {
            return MovementHistoryAccessDecision.RevalidationRequired(query.toBasicFallback())
        }

        // The plans evaluator derives trustedNow exclusively from the signed server timestamp
        // plus same-boot elapsedRealtime. The movement layer applies the published notAfter cap.
        val concessionLimit = Math.addExact(evidence.verifiedServerTimeMillis, OFFLINE_CONCESSION_MILLIS)
        val notAfter = if (evidence.isLifetime || evidence.entitlementExpiresAtMillis == null) {
            concessionLimit
        } else {
            minOf(concessionLimit, evidence.entitlementExpiresAtMillis)
        }

        // Strictly: trustedNow < notAfter. Exact match or greater requires revalidation.
        val trustedNowMillis = evidence.trustedNowMillis
            ?: return MovementHistoryAccessDecision.RevalidationRequired(query.toBasicFallback())
        if (trustedNowMillis >= notAfter) {
            return MovementHistoryAccessDecision.RevalidationRequired(query.toBasicFallback())
        }

        return MovementHistoryAccessDecision.Allowed
    }
}
