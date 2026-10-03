package com.kipu.app.feature.movements.data

import com.kipu.app.feature.movements.domain.MovementEntitlementEvidence
import com.kipu.app.feature.movements.domain.MovementEntitlementProvider
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheDao
import com.kipu.app.feature.plans.data.entitlement.DenyUnverifiedEntitlementEvaluator
import com.kipu.app.feature.plans.data.entitlement.EffectiveEntitlementEvaluator
import com.kipu.app.feature.plans.domain.model.OfflineEntitlementLeaseDecision
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlansMovementEntitlementProvider @Inject constructor(
    private val featureAccessCacheDao: FeatureAccessCacheDao,
    private val entitlementEvaluator: EffectiveEntitlementEvaluator = DenyUnverifiedEntitlementEvaluator,
) : MovementEntitlementProvider {
    override suspend fun getEffectiveEntitlement(userId: String): MovementEntitlementEvidence? {
        val parsedUuid = runCatching { UUID.fromString(userId) }.getOrNull() ?: return null
        val entity = featureAccessCacheDao.get(parsedUuid) ?: return null
        val isPremium = entity.effectiveTier.equals("PREMIUM", ignoreCase = true)
        val decision = entitlementEvaluator.evaluate(userId, entity)
        val allowed = decision as? OfflineEntitlementLeaseDecision.Allowed
        val requiresRevalidation = decision is OfflineEntitlementLeaseDecision.RevalidationRequired
        val verifiedServerTimeMillis = entity.verifiedAt?.toEpochMilli() ?: 0L
        val entitlementExpiresAtMillis = entity.entitlementExpiresAt?.toEpochMilli()

        return MovementEntitlementEvidence(
            verified = allowed != null,
            verifiedServerTimeMillis = verifiedServerTimeMillis,
            entitlementExpiresAtMillis = entitlementExpiresAtMillis,
            isLifetime = isPremium && entitlementExpiresAtMillis == null,
            monotonicContinuityValid = allowed != null,
            trustedNowMillis = allowed?.trustedNowMillis,
            revalidationRequired = requiresRevalidation,
        )
    }
}
