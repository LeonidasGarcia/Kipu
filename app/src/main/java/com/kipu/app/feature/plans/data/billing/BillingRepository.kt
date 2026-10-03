package com.kipu.app.feature.plans.data.billing

import android.app.Activity
import com.kipu.app.core.network.AuthenticatedSession
import com.kipu.app.core.network.AuthenticatedSessionProvider
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheDao
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheEntity
import com.kipu.app.feature.plans.data.entitlement.EffectiveEntitlementEvaluator
import com.kipu.app.feature.plans.data.entitlement.InstallationSigningKeyProvider
import com.kipu.app.feature.plans.data.entitlement.OfflineEntitlementClock
import com.kipu.app.feature.plans.data.entitlement.OfflineEntitlementGrantVerifier
import com.kipu.app.feature.plans.data.remote.VerifyPurchaseApi
import com.kipu.app.feature.plans.domain.model.OfflineEntitlementLeaseDecision
import com.kipu.app.feature.plans.domain.model.BillingVerificationResult
import com.kipu.app.feature.plans.domain.model.BillingPurchaseRequest
import com.kipu.app.feature.plans.domain.model.LocalizedBillingOffer
import com.kipu.app.feature.plans.domain.model.StorePurchaseState
import com.kipu.app.feature.plans.purchase.BillingPurchaseRepository
import java.security.MessageDigest
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
    private val installationKeys: InstallationSigningKeyProvider,
    private val offlineClock: OfflineEntitlementClock,
    private val grantVerifier: OfflineEntitlementGrantVerifier,
    private val entitlementEvaluator: EffectiveEntitlementEvaluator,
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

    override suspend fun restoreAndVerifyAccess(): BillingVerificationResult = try {
        kotlinx.coroutines.withTimeout(30_000L) {
            val restored = gateway.recoverPurchaseUpdates()
            if (restored.isEmpty()) {
                BillingVerificationResult.Retryable("NO_RECOVERABLE_PURCHASE")
            } else {
                val results = mutableListOf<BillingVerificationResult>()
                for (update in restored) {
                    val result = processPurchaseUpdate(update)
                    if (result is BillingVerificationResult.Verified && result.effectivePremium) return@withTimeout result
                    results += result
                }
                results.firstOrNull { it is BillingVerificationResult.Retryable || it is BillingVerificationResult.Rejected }
                    ?: results.first()
            }
        }
    } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
        BillingVerificationResult.Retryable("VERIFICATION_TIMEOUT")
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        BillingVerificationResult.Retryable("VERIFICATION_UNAVAILABLE")
    }

    private suspend fun processPurchaseUpdate(update: com.kipu.app.feature.plans.domain.model.StorePurchaseUpdate): BillingVerificationResult {
        return when (update.state) {
            StorePurchaseState.PENDING -> BillingVerificationResult.PaymentPending(update.productId)
            StorePurchaseState.CANCELLED -> BillingVerificationResult.UserCancelled
            StorePurchaseState.FAILED -> BillingVerificationResult.Unavailable
            StorePurchaseState.PURCHASED -> {
                val token = update.purchaseToken ?: return BillingVerificationResult.Retryable("MISSING_PURCHASE_TOKEN")
                val session = sessions.currentSession() ?: return BillingVerificationResult.Retryable("UNAUTHENTICATED")
                val installation = runCatching { installationKeys.getOrCreatePublicIdentity() }.getOrNull()
                val anchor = runCatching { offlineClock.read() }.getOrNull()
                val result = api.verify(
                    session,
                    BillingPurchaseRequest(update.productId, token),
                    installationPublicKey = installation?.publicKeyBase64,
                )
                if (result !is BillingVerificationResult.Verified) return result
                val current = sessions.currentSession()
                if (current?.userId != session.userId) return BillingVerificationResult.Rejected("AUTH_SESSION_CHANGED")

                if (!result.effectivePremium) {
                    return runCatching {
                        accessCache.putVerified(FeatureAccessCacheEntity.verifiedFree(session.userId))
                    }.fold(
                        onSuccess = { result },
                        onFailure = { BillingVerificationResult.Retryable("ACCESS_CACHE_UNAVAILABLE") },
                    )
                }

                val signedGrant = result.offlineEntitlementGrant
                    ?: return BillingVerificationResult.Retryable("OFFLINE_GRANT_UNAVAILABLE")
                val verifiedClaims = grantVerifier.verify(signedGrant, session.userId.toString())
                    ?: return BillingVerificationResult.Retryable("OFFLINE_GRANT_INVALID")
                if (installation == null || anchor?.bootCount == null) {
                    return BillingVerificationResult.Retryable("OFFLINE_CLOCK_UNAVAILABLE")
                }
                val currentAnchor = runCatching { offlineClock.read() }.getOrNull()
                    ?: return BillingVerificationResult.Retryable("OFFLINE_CLOCK_UNAVAILABLE")
                if (currentAnchor.bootCount != anchor.bootCount ||
                    currentAnchor.elapsedRealtimeMillis < anchor.elapsedRealtimeMillis
                ) {
                    return BillingVerificationResult.Retryable("OFFLINE_CONTINUITY_LOST")
                }
                val cacheEntry = FeatureAccessCacheEntity.fromVerifiedGrant(
                    userId = session.userId,
                    claims = verifiedClaims,
                    grant = signedGrant,
                    anchorElapsedRealtimeMillis = anchor.elapsedRealtimeMillis,
                    anchorBootCount = anchor.bootCount,
                )
                if (entitlementEvaluator.evaluate(session.userId.toString(), cacheEntry) !is OfflineEntitlementLeaseDecision.Allowed) {
                    return BillingVerificationResult.Retryable("OFFLINE_GRANT_INVALID")
                }
                runCatching {
                    accessCache.putVerified(cacheEntry)
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
