package com.kipu.app.feature.plans.presentation

import com.kipu.app.feature.plans.domain.model.BillingVerificationResult
import com.kipu.app.feature.plans.domain.model.GooglePlayPurchaseState
import com.kipu.app.feature.plans.domain.model.LocalizedBillingOffer
import com.kipu.app.feature.plans.domain.model.PurchaseLifecycle

enum class PlanPurchaseStatus {
    LOADING,
    READY,
    LAUNCHING,
    VERIFYING,
    VERIFIED,
    PENDING,
    RETRYABLE,
    ERROR,
    UNAVAILABLE,
}

data class PlanPurchaseUiState(
    val offers: List<LocalizedBillingOffer> = emptyList(),
    val selectedProductId: String? = null,
    val status: PlanPurchaseStatus = PlanPurchaseStatus.LOADING,
    val effectivePremium: Boolean = false,
    val providerPurchaseState: GooglePlayPurchaseState? = null,
    val lifecycle: PurchaseLifecycle? = null,
    val expiresAtEpochMillis: Long? = null,
    val acknowledgementPending: Boolean = false,
    val errorCode: String? = null,
) {
        val selectedOffer: LocalizedBillingOffer? get() = offers.firstOrNull { it.product.storeProductId == selectedProductId }
    val isLoading: Boolean get() = status == PlanPurchaseStatus.LOADING

    companion object {
        fun forResult(previous: PlanPurchaseUiState, result: BillingVerificationResult): PlanPurchaseUiState = when (result) {
            is BillingVerificationResult.Verifying -> previous.copy(selectedProductId = result.productId, status = PlanPurchaseStatus.VERIFYING, errorCode = null)
            is BillingVerificationResult.Verified -> previous.copy(
                selectedProductId = result.productId,
                status = PlanPurchaseStatus.VERIFIED,
                effectivePremium = result.effectivePremium,
                providerPurchaseState = result.providerPurchaseState,
                lifecycle = result.lifecycle,
                expiresAtEpochMillis = result.expiresAtEpochMillis,
                acknowledgementPending = result.acknowledgementPending,
                errorCode = null,
            )
            is BillingVerificationResult.PaymentPending -> previous.copy(
                status = PlanPurchaseStatus.PENDING,
                providerPurchaseState = GooglePlayPurchaseState.PENDING,
                errorCode = null,
            )
            is BillingVerificationResult.Retryable -> previous.copy(
                status = PlanPurchaseStatus.RETRYABLE,
                errorCode = result.code,
            )
            is BillingVerificationResult.Rejected -> previous.copy(
                status = PlanPurchaseStatus.ERROR,
                errorCode = result.code,
            )
            BillingVerificationResult.UserCancelled -> previous.copy(status = if (previous.effectivePremium) PlanPurchaseStatus.VERIFIED else PlanPurchaseStatus.READY)
            BillingVerificationResult.Unavailable -> previous.copy(status = PlanPurchaseStatus.UNAVAILABLE, errorCode = "UNAVAILABLE")
        }
    }
}
