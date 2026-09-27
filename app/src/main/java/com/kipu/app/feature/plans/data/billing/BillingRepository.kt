package com.kipu.app.feature.plans.data.billing

import android.app.Activity
import com.kipu.app.core.network.AuthenticatedSession
import com.kipu.app.core.network.AuthenticatedSessionProvider
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheDao
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheEntity
import com.kipu.app.feature.plans.data.remote.VerifyPurchaseApi
import com.kipu.app.feature.plans.domain.model.BillingVerificationResult
import com.kipu.app.feature.plans.domain.model.BillingPurchaseRequest
import com.kipu.app.feature.plans.domain.model.EffectiveEntitlement
import com.kipu.app.feature.plans.domain.model.LocalizedBillingOffer
import com.kipu.app.feature.plans.domain.model.StorePurchaseState
import com.kipu.app.feature.plans.purchase.BillingPurchaseRepository
import java.security.MessageDigest
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform

@Singleton
class BillingRepository @Inject constructor(
    private val gateway: PlayBillingGateway,
    private val api: VerifyPurchaseApi,
    private val sessions: AuthenticatedSessionProvider,
    private val accessCache: FeatureAccessCacheDao,
) : BillingPurchaseRepository {
    override val purchaseResults: Flow<BillingVerificationResult> = gateway.purchaseUpdates.transform { update ->
        if (update.state == StorePurchaseState.PURCHASED) emit(BillingVerificationResult.Verifying(update.productId))
        emit(processPurchaseUpdate(update))
    }

    override suspend fun loadOffers(): List<LocalizedBillingOffer> {
        val session = sessions.currentSession() ?: return emptyList()
        val catalog = api.loadProducts(session)
        val offers = gateway.queryOffers(catalog)
        // Restore Play purchases after the result collector is active; restored and new purchases
        // must use the same server verification and acknowledgement path.
        runCatching { gateway.requeryPurchases() }
        return offers
    }

    override suspend fun startPurchase(activity: Activity, offer: LocalizedBillingOffer) {
        val session = sessions.currentSession() ?: throw IllegalStateException("A signed-in account is required")
        gateway.launchPurchase(activity, offer, obfuscatedAccountId(session))
    }

    override suspend fun refreshPurchases() {
        gateway.requeryPurchases()
    }

    private suspend fun processPurchaseUpdate(update: com.kipu.app.feature.plans.domain.model.StorePurchaseUpdate): BillingVerificationResult {
        return when (update.state) {
            StorePurchaseState.PENDING -> BillingVerificationResult.PaymentPending(update.productId)
            StorePurchaseState.CANCELLED -> BillingVerificationResult.UserCancelled
            StorePurchaseState.FAILED -> BillingVerificationResult.Unavailable
            StorePurchaseState.PURCHASED -> {
                val token = update.purchaseToken ?: return BillingVerificationResult.Retryable("MISSING_PURCHASE_TOKEN")
                val session = sessions.currentSession() ?: return BillingVerificationResult.Retryable("UNAUTHENTICATED")
                val result = api.verify(session, BillingPurchaseRequest(update.productId, token))
                if (result !is BillingVerificationResult.Verified) return result
                val current = sessions.currentSession()
                if (current?.userId != session.userId) return BillingVerificationResult.Rejected("AUTH_SESSION_CHANGED")
                runCatching {
                    accessCache.putVerified(
                        FeatureAccessCacheEntity.fromVerifiedServer(
                            userId = session.userId,
                            entitlement = EffectiveEntitlement(
                                verified = result.effectivePremium,
                                expiresAtEpochMillis = result.effectiveExpiresAtEpochMillis,
                            ),
                            verifiedAt = Instant.now(),
                        ),
                    )
                }.fold(
                    onSuccess = { result },
                    onFailure = { BillingVerificationResult.Retryable("ACCESS_CACHE_UNAVAILABLE") },
                )
            }
        }
    }

    private fun obfuscatedAccountId(session: AuthenticatedSession): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(("com.kipu.app:" + session.userId.toString()).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }
}
