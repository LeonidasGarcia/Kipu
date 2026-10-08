package com.kipu.app.feature.categories.domain.model

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.plans.domain.model.EffectiveEntitlement

@JvmInline
value class CaptureConfidence(val score: Double) {
    init {
        require(score.isFinite() && score in 0.0..1.0) {
            "Capture confidence must be a finite score between 0 and 1"
        }
    }
}

/** Stable source evidence required before a bank signal can enter alias evaluation. */
data class CaptureProvenance(
    val sourcePackage: String,
    val sourceEventId: String,
    val capturedAtEpochMillis: Long,
) {
    init {
        require(sourcePackage.isNotBlank()) { "Capture source package is required" }
        require(sourceEventId.isNotBlank()) { "Capture source event ID is required" }
    }
}

/** A candidate is issued only after checking current Premium, consent, and source provenance. */
class CaptureCandidate private constructor(
    val ownerId: UserId,
    val sourceText: SourceMerchantText,
    val provenance: CaptureProvenance,
    val confidence: CaptureConfidence,
) {
    companion object {
        fun authorize(
            ownerId: UserId,
            sourceText: SourceMerchantText,
            provenance: CaptureProvenance,
            confidence: CaptureConfidence,
            entitlement: EffectiveEntitlement,
            consentGranted: Boolean,
            nowEpochMillis: Long,
        ): Result<CaptureCandidate> = runCatching {
            require(sourceText.value.isNotBlank()) { "Capture source text is empty" }
            require(entitlement.verified) { "A verified Premium entitlement is required" }
            require(entitlement.expiresAtEpochMillis?.let { it > nowEpochMillis } != false) {
                "The Premium entitlement has expired"
            }
            require(consentGranted) { "Current capture consent is required" }
            require(provenance.capturedAtEpochMillis <= nowEpochMillis) { "Capture timestamp is in the future" }
            CaptureCandidate(ownerId, sourceText, provenance, confidence)
        }
    }
}
