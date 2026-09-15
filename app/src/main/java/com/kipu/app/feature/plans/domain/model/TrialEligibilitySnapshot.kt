package com.kipu.app.feature.plans.domain.model

import java.time.Instant

enum class TrialEligibilityStatus { ELIGIBLE, INELIGIBLE, UNKNOWN }
enum class TrialEligibilitySource { VERIFIED_ACCOUNT_HISTORY, UNAVAILABLE }

data class TrialEligibilitySnapshot(
    val status: TrialEligibilityStatus,
    val source: TrialEligibilitySource,
    val verifiedAt: Instant?,
    val validUntil: Instant?,
) {
    fun isEligible(at: Instant = Instant.now()): Boolean = status == TrialEligibilityStatus.ELIGIBLE && validUntil?.isAfter(at) == true
    companion object {
        val UNKNOWN = unknown()
        fun unknown() = TrialEligibilitySnapshot(TrialEligibilityStatus.UNKNOWN, TrialEligibilitySource.UNAVAILABLE, null, null)
        fun eligible(verifiedAt: Instant, validUntil: Instant) = TrialEligibilitySnapshot(TrialEligibilityStatus.ELIGIBLE, TrialEligibilitySource.VERIFIED_ACCOUNT_HISTORY, verifiedAt, validUntil)
        fun ineligible(verifiedAt: Instant) = TrialEligibilitySnapshot(TrialEligibilityStatus.INELIGIBLE, TrialEligibilitySource.VERIFIED_ACCOUNT_HISTORY, verifiedAt, null)
    }
}
