package com.kipu.app.feature.plans.domain.model

enum class BillingProductKind { SUBSCRIPTION, ONE_TIME }

data class BillingProduct(
    val id: String,
    val storeProductId: String,
    val basePlanId: String?,
    val displayName: String,
    val option: CommercialOption,
    val kind: BillingProductKind,
) {
    companion object {
        fun fromCatalog(
            id: String,
            storeProductId: String,
            basePlanId: String?,
            displayName: String,
            planType: String,
        ): BillingProduct? {
            val option = when (planType) {
                "PRO_MONTHLY" -> CommercialOption.MONTHLY
                "PRO_ANNUAL" -> CommercialOption.ANNUAL
                "PRO_LIFETIME" -> CommercialOption.LIFETIME
                else -> return null
            }
            val kind = if (option == CommercialOption.LIFETIME) BillingProductKind.ONE_TIME else BillingProductKind.SUBSCRIPTION
            if (id.isBlank() || storeProductId.isBlank() || displayName.isBlank()) return null
            if (kind == BillingProductKind.SUBSCRIPTION && basePlanId.isNullOrBlank()) return null
            if (kind == BillingProductKind.ONE_TIME && basePlanId != null) return null
            return BillingProduct(id, storeProductId, basePlanId, displayName, option, kind)
        }
    }
}

class EphemeralPurchaseToken internal constructor(private val value: String) {
    init { require(value.isNotBlank()) }
    internal fun forVerification(): String = value
    override fun toString(): String = "[REDACTED_PURCHASE_TOKEN]"
}

data class BillingPurchaseRequest(
    val productId: String,
    val purchaseToken: EphemeralPurchaseToken,
) {
    init { require(productId.isNotBlank()) }
    override fun toString(): String = "BillingPurchaseRequest(productId=$productId, purchaseToken=[REDACTED])"
}

enum class StorePurchaseState { PURCHASED, PENDING, CANCELLED, FAILED }
enum class GooglePlayPurchaseState { PURCHASED, PENDING, CANCELLED }

data class StorePurchaseUpdate(
    val productId: String,
    val purchaseToken: EphemeralPurchaseToken?,
    val state: StorePurchaseState,
) {
    val requiresBackendVerification: Boolean get() = state == StorePurchaseState.PURCHASED && purchaseToken != null
    override fun toString(): String = "StorePurchaseUpdate(productId=$productId, state=$state, purchaseToken=[REDACTED])"
}

data class LocalizedBillingOffer(
    val product: BillingProduct,
    val formattedPrice: String,
    val currencyCode: String,
    val offerToken: String?,
    val trialPeriodDays: Int?,
    val renewalPeriod: String?,
) {
    init {
        require(formattedPrice.isNotBlank())
        require(currencyCode.matches(Regex("^[A-Z]{3}$")))
        require(product.kind != BillingProductKind.SUBSCRIPTION || !offerToken.isNullOrBlank())
        require(product.option != CommercialOption.LIFETIME || trialPeriodDays == null)
    }
}
