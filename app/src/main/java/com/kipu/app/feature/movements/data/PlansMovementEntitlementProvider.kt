package com.kipu.app.feature.movements.data

import com.kipu.app.feature.movements.domain.MovementEntitlementEvidence
import com.kipu.app.feature.movements.domain.MovementEntitlementProvider
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheDao
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlansMovementEntitlementProvider @Inject constructor(
    private val featureAccessCacheDao: FeatureAccessCacheDao,
) : MovementEntitlementProvider {
    override suspend fun getEffectiveEntitlement(userId: String): MovementEntitlementEvidence? {
        val parsedUuid = runCatching { UUID.fromString(userId) }.getOrNull() ?: return null
        val entity = featureAccessCacheDao.get(parsedUuid) ?: return null
        val isVerifiedServer = entity.source == "VERIFIED_SERVER"
        val isPremium = entity.effectiveTier.equals("PREMIUM", ignoreCase = true)
        val verified = isVerifiedServer && isPremium
        val verifiedServerTimeMillis = entity.verifiedAt?.toEpochMilli() ?: 0L
        val entitlementExpiresAtMillis = entity.entitlementExpiresAt?.toEpochMilli()

        return MovementEntitlementEvidence(
            verified = verified,
            verifiedServerTimeMillis = verifiedServerTimeMillis,
            entitlementExpiresAtMillis = entitlementExpiresAtMillis,
            isLifetime = isPremium && entitlementExpiresAtMillis == null,
            monotonicContinuityValid = true,
        )
    }
}
