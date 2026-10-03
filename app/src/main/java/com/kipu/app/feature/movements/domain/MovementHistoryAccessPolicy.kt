package com.kipu.app.feature.movements.domain

import com.kipu.app.feature.movements.domain.model.MovementHistoryAccessDecision
import com.kipu.app.feature.movements.domain.model.MovementHistoryQuery
import javax.inject.Inject

data class MovementEntitlementEvidence(
    val verified: Boolean,
    val verifiedServerTimeMillis: Long,
    val entitlementExpiresAtMillis: Long?,
    val isLifetime: Boolean = false,
    val monotonicContinuityValid: Boolean = true,
)

interface MovementEntitlementProvider {
    suspend fun getEffectiveEntitlement(userId: String): MovementEntitlementEvidence?
}

interface MovementHistoryAccessPolicy {
    fun evaluate(
        userId: String?,
        query: MovementHistoryQuery,
        evidence: MovementEntitlementEvidence?,
        trustedNowMillis: Long,
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
        trustedNowMillis: Long,
    ): MovementHistoryAccessDecision {
        if (userId.isNullOrBlank()) {
            return MovementHistoryAccessDecision.NotAuthorized
        }

        // Basic query is available to everyone for their entire local history.
        if (!query.requiresAdvancedAccess) {
            return MovementHistoryAccessDecision.Allowed
        }

        // Advanced criteria requires verified entitlement and bounded offline concession
        if (evidence == null || !evidence.verified) {
            return MovementHistoryAccessDecision.PremiumRequired(query.toBasicFallback())
        }

        // Continuity check: reboot without verifiable monotonic continuity requires revalidation
        if (!evidence.monotonicContinuityValid) {
            return MovementHistoryAccessDecision.RevalidationRequired(query.toBasicFallback())
        }

        // notAfter = min(verifiedServerTime + 72 hours, knownEntitlementEnd)
        val concessionLimit = Math.addExact(evidence.verifiedServerTimeMillis, OFFLINE_CONCESSION_MILLIS)
        val notAfter = if (evidence.isLifetime || evidence.entitlementExpiresAtMillis == null) {
            concessionLimit
        } else {
            minOf(concessionLimit, evidence.entitlementExpiresAtMillis)
        }

        // Strictly: trustedNow < notAfter. Exact match or greater requires revalidation.
        if (trustedNowMillis >= notAfter) {
            return MovementHistoryAccessDecision.RevalidationRequired(query.toBasicFallback())
        }

        return MovementHistoryAccessDecision.Allowed
    }
}
