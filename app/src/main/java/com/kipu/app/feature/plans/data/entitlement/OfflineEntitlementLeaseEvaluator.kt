package com.kipu.app.feature.plans.data.entitlement

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheEntity
import com.kipu.app.feature.plans.domain.OfflineEntitlementLeasePolicy
import com.kipu.app.feature.plans.domain.model.OfflineEntitlementLeaseContext
import com.kipu.app.feature.plans.domain.model.OfflineEntitlementLeaseDecision
import com.kipu.app.feature.plans.domain.model.SignedOfflineEntitlementGrant
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

data class OfflineEntitlementClockReading(
    val elapsedRealtimeMillis: Long,
    val bootCount: Int?,
)

fun interface OfflineEntitlementClock {
    fun read(): OfflineEntitlementClockReading
}

/** Re-evaluate capability projections while an app stays open past a lease boundary. */
fun offlineEntitlementRefreshTicker(): Flow<Unit> = channelFlow {
    send(Unit)
    withContext(Dispatchers.Default) {
        while (currentCoroutineContext().isActive) {
            delay(60_000L)
            send(Unit)
        }
    }
}

@Singleton
class AndroidOfflineEntitlementClock @Inject constructor(
    @ApplicationContext private val context: Context,
) : OfflineEntitlementClock {
    override fun read(): OfflineEntitlementClockReading = OfflineEntitlementClockReading(
        elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
        bootCount = runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        }.getOrNull(),
    )
}

fun interface EffectiveEntitlementEvaluator {
    fun evaluate(
        userId: String,
        cache: FeatureAccessCacheEntity?,
    ): OfflineEntitlementLeaseDecision
}

@Singleton
class OfflineEntitlementLeaseEvaluator @Inject constructor(
    private val verifier: OfflineEntitlementGrantVerifier,
    private val installationKeys: InstallationSigningKeyProvider,
    private val clock: OfflineEntitlementClock,
    private val policy: OfflineEntitlementLeasePolicy,
) : EffectiveEntitlementEvaluator {
    override fun evaluate(
        userId: String,
        cache: FeatureAccessCacheEntity?,
    ): OfflineEntitlementLeaseDecision {
        if (cache == null || !cache.effectiveTier.equals("PREMIUM", ignoreCase = true)) {
            return OfflineEntitlementLeaseDecision.PremiumRequired
        }

        val payload = cache.offlineGrantPayload
        val signature = cache.offlineGrantSignature
        val keyId = cache.offlineGrantKeyId
        if (payload == null || signature == null || keyId == null) {
            return revalidation(OfflineEntitlementLeaseDecision.Reason.INVALID_CLAIMS)
        }

        val grant = SignedOfflineEntitlementGrant(payload, signature, keyId)
        val claims = verifier.verify(grant, userId)
            ?: return revalidation(OfflineEntitlementLeaseDecision.Reason.INVALID_CLAIMS)
        val identity = runCatching { installationKeys.getOrCreatePublicIdentity() }.getOrNull()
            ?: return revalidation(OfflineEntitlementLeaseDecision.Reason.INSTALLATION_MISMATCH)
        val reading = runCatching { clock.read() }.getOrNull()
            ?: return revalidation(OfflineEntitlementLeaseDecision.Reason.BOOT_CONTINUITY_LOST)

        return policy.evaluate(
            grant = claims,
            context = OfflineEntitlementLeaseContext(
                currentUserId = userId,
                currentInstallationKeyThumbprint = identity.thumbprint,
                supportedPolicyVersion = cache.policyVersion,
                anchorElapsedRealtimeMillis = cache.offlineAnchorElapsedRealtimeMillis,
                anchorBootCount = cache.offlineAnchorBootCount,
                currentElapsedRealtimeMillis = reading.elapsedRealtimeMillis,
                currentBootCount = reading.bootCount,
            ),
        )
    }

    private fun revalidation(reason: OfflineEntitlementLeaseDecision.Reason) =
        OfflineEntitlementLeaseDecision.RevalidationRequired(reason)
}

/** Safe default for direct test construction; production Hilt binds the signed-grant evaluator. */
object DenyUnverifiedEntitlementEvaluator : EffectiveEntitlementEvaluator {
    override fun evaluate(
        userId: String,
        cache: FeatureAccessCacheEntity?,
    ): OfflineEntitlementLeaseDecision = if (cache?.effectiveTier.equals("PREMIUM", ignoreCase = true)) {
        OfflineEntitlementLeaseDecision.RevalidationRequired(OfflineEntitlementLeaseDecision.Reason.INVALID_CLAIMS)
    } else {
        OfflineEntitlementLeaseDecision.PremiumRequired
    }
}
