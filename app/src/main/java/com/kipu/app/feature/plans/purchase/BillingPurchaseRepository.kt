package com.kipu.app.feature.plans.purchase

import android.app.Activity
import com.kipu.app.feature.plans.domain.model.BillingVerificationResult
import com.kipu.app.feature.plans.domain.model.LocalizedBillingOffer
import kotlinx.coroutines.flow.Flow

/** Application boundary for the platform purchase UI and its server-verified result stream. */
interface BillingPurchaseRepository {
    val purchaseResults: Flow<BillingVerificationResult>
    suspend fun loadOffers(): List<LocalizedBillingOffer>
    suspend fun startPurchase(activity: Activity, offer: LocalizedBillingOffer)
    suspend fun refreshPurchases()
    /** Explicit recovery completes even when Play has no purchase; no catalog is required. */
    suspend fun restoreAndVerifyAccess(): BillingVerificationResult =
        BillingVerificationResult.Retryable("RESTORE_UNAVAILABLE")
}
