package com.kipu.app.feature.plans.data.billing

import android.content.Context
import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.kipu.app.feature.plans.domain.model.BillingProduct
import com.kipu.app.feature.plans.domain.model.BillingProductKind
import com.kipu.app.feature.plans.domain.model.EphemeralPurchaseToken
import com.kipu.app.feature.plans.domain.model.LocalizedBillingOffer
import com.kipu.app.feature.plans.domain.model.StorePurchaseState
import com.kipu.app.feature.plans.domain.model.StorePurchaseUpdate
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.time.Period

@Singleton
class PlayBillingGateway @Inject constructor(@ApplicationContext context: Context) {
    private val updates = MutableSharedFlow<StorePurchaseUpdate>(extraBufferCapacity = 32)
    val purchaseUpdates = updates.asSharedFlow()
    private val connectionMutex = Mutex()
    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener { billingResult, purchases -> onPurchasesUpdated(billingResult, purchases) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    suspend fun queryOffers(products: List<BillingProduct>): List<LocalizedBillingOffer> {
        if (products.isEmpty()) return emptyList()
        ensureConnected()
        val details = products.groupBy { it.kind }.flatMap { (kind, productGroup) ->
            queryProductDetails(productGroup.map { it.storeProductId }, kind)
        }.associateBy { it.productId }
        return products.flatMap { product ->
            val productDetails = details[product.storeProductId] ?: return@flatMap emptyList()
            mapLocalizedOffers(product, productDetails)
        }
    }

    suspend fun launchPurchase(activity: Activity, offer: LocalizedBillingOffer, obfuscatedAccountId: String) {
        ensureConnected()
        val details = queryProductDetails(listOf(offer.product.storeProductId), offer.product.kind)
            .firstOrNull() ?: throw BillingUnavailableException()
        val currentOfferToken = findOfferToken(details, offer) ?: throw BillingUnavailableException()
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply { currentOfferToken?.let(::setOfferToken) }
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .setObfuscatedAccountId(obfuscatedAccountId)
            .build()
        val result = client.launchBillingFlow(activity, flowParams)
        if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            updates.tryEmit(StorePurchaseUpdate(offer.product.storeProductId, null, StorePurchaseState.CANCELLED))
        } else if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            updates.tryEmit(StorePurchaseUpdate(offer.product.storeProductId, null, StorePurchaseState.FAILED))
        }
    }

    suspend fun requeryPurchases() {
        ensureConnected()
        listOf(BillingClient.ProductType.SUBS, BillingClient.ProductType.INAPP).forEach { type ->
            queryPurchases(type).forEach(::publishPurchase)
        }
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach(::publishPurchase)
            BillingClient.BillingResponseCode.USER_CANCELED -> updates.tryEmit(
                StorePurchaseUpdate("", null, StorePurchaseState.CANCELLED),
            )
            else -> updates.tryEmit(StorePurchaseUpdate("", null, StorePurchaseState.FAILED))
        }
    }

    private fun publishPurchase(purchase: Purchase) {
        val state = when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> StorePurchaseState.PURCHASED
            Purchase.PurchaseState.PENDING -> StorePurchaseState.PENDING
            else -> return
        }
        purchase.products.forEach { productId ->
            updates.tryEmit(
                StorePurchaseUpdate(
                    productId = productId,
                    purchaseToken = EphemeralPurchaseToken(purchase.purchaseToken),
                    state = state,
                ),
            )
        }
    }

    private suspend fun queryProductDetails(productIds: List<String>, kind: BillingProductKind): List<ProductDetails> {
        if (productIds.isEmpty()) return emptyList()
        val type = if (kind == BillingProductKind.SUBSCRIPTION) BillingClient.ProductType.SUBS else BillingClient.ProductType.INAPP
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productIds.distinct().map { productId ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(type)
                    .build()
            })
            .build()
        return suspendCancellableCoroutine { continuation ->
            client.queryProductDetailsAsync(params) { result, queryResult ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    continuation.resume(queryResult.productDetailsList)
                } else {
                    continuation.resumeWithException(BillingUnavailableException())
                }
            }
        }
    }

    private suspend fun queryPurchases(type: String): List<Purchase> {
        val params = QueryPurchasesParams.newBuilder().setProductType(type).build()
        return suspendCancellableCoroutine { continuation ->
            client.queryPurchasesAsync(params) { result, queryResult ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    continuation.resume(queryResult)
                } else {
                    continuation.resumeWithException(BillingUnavailableException())
                }
            }
        }
    }

    private suspend fun ensureConnected() {
        if (client.isReady) return
        connectionMutex.withLock {
            if (client.isReady) return
            suspendCancellableCoroutine { continuation ->
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                            continuation.resume(Unit)
                        } else {
                            continuation.resumeWithException(BillingUnavailableException())
                        }
                    }
                    override fun onBillingServiceDisconnected() = Unit
                })
            }
        }
    }

    private fun mapLocalizedOffers(product: BillingProduct, details: ProductDetails): List<LocalizedBillingOffer> {
        if (product.kind == BillingProductKind.ONE_TIME) {
            val offers = details.oneTimePurchaseOfferDetailsList ?: listOfNotNull(details.oneTimePurchaseOfferDetails)
            return offers.firstOrNull()?.let { offer ->
                listOf(LocalizedBillingOffer(product, offer.formattedPrice, offer.priceCurrencyCode, offer.offerToken, null, null))
            }.orEmpty()
        }
        val eligible = details.subscriptionOfferDetails.orEmpty().filter { it.basePlanId == product.basePlanId }
        val selected = eligible.firstOrNull { subscriptionOffer ->
            subscriptionOffer.pricingPhases.pricingPhaseList.firstOrNull()?.let {
                it.priceAmountMicros == 0L && parsePeriodDays(it.billingPeriod) != null
            } == true
        } ?: eligible.firstOrNull { it.offerId == null } ?: eligible.firstOrNull()
        val phases = selected?.pricingPhases?.pricingPhaseList.orEmpty()
        val finalPhase = phases.lastOrNull() ?: return emptyList()
        val trialDays = phases.firstOrNull()?.takeIf { it.priceAmountMicros == 0L }?.billingPeriod?.let(::parsePeriodDays)
        return selected?.let {
            listOf(LocalizedBillingOffer(product, finalPhase.formattedPrice, finalPhase.priceCurrencyCode, it.offerToken, trialDays, finalPhase.billingPeriod))
        }.orEmpty()
    }

    private fun findOfferToken(details: ProductDetails, offer: LocalizedBillingOffer): String? =
        if (offer.product.kind == BillingProductKind.ONE_TIME) {
            val offers = details.oneTimePurchaseOfferDetailsList ?: listOfNotNull(details.oneTimePurchaseOfferDetails)
            offers.firstOrNull { it.offerToken == offer.offerToken }?.offerToken
        } else {
            details.subscriptionOfferDetails.orEmpty()
                .firstOrNull { it.basePlanId == offer.product.basePlanId && it.offerToken == offer.offerToken }
                ?.offerToken
        }

    private fun parsePeriodDays(value: String): Int? = runCatching {
        val period = Period.parse(value)
        period.days + period.months * 30 + period.years * 365
    }.getOrNull()?.takeIf { it > 0 }
}

class BillingUnavailableException : IllegalStateException("Google Play Billing is unavailable")
