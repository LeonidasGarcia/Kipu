package com.kipu.app.feature.plans.domain.model

sealed interface BillingVerificationResult {
    val effectivePremium: Boolean

    data class Verifying(val productId: String) : BillingVerificationResult {
        override val effectivePremium: Boolean = false
    }

    data class Verified(
        val productId: String,
        val providerPurchaseState: GooglePlayPurchaseState,
        val lifecycle: PurchaseLifecycle,
        val expiresAtEpochMillis: Long?,
        override val effectivePremium: Boolean,
        val acknowledgementPending: Boolean,
        val effectiveExpiresAtEpochMillis: Long? = expiresAtEpochMillis,
    ) : BillingVerificationResult

    data class PaymentPending(val productId: String) : BillingVerificationResult {
        override val effectivePremium: Boolean = false
    }

    data class Retryable(val code: String) : BillingVerificationResult {
        override val effectivePremium: Boolean = false
    }

    data class Rejected(val code: String) : BillingVerificationResult {
        override val effectivePremium: Boolean = false
    }

    data object UserCancelled : BillingVerificationResult {
        override val effectivePremium: Boolean = false
    }

    data object Unavailable : BillingVerificationResult {
        override val effectivePremium: Boolean = false
    }
}
