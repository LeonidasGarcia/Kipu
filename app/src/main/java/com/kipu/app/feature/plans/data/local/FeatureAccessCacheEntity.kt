package com.kipu.app.feature.plans.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import java.time.Instant
import java.util.UUID
import com.kipu.app.feature.plans.domain.model.EffectiveEntitlement

@Entity(tableName = "feature_access_cache")
data class FeatureAccessCacheEntity(@PrimaryKey @ColumnInfo(name = "user_id") val userId: UUID, @ColumnInfo(name = "policy_version") val policyVersion: Int, @ColumnInfo(name = "effective_tier") val effectiveTier: String = "FREE", @ColumnInfo(name = "entitlement_expires_at") val entitlementExpiresAt: Instant? = null, @ColumnInfo(name = "verified_at") val verifiedAt: Instant? = null, val source: String = "LOCAL_DEFAULT") {
    companion object {
        fun fromVerifiedServer(userId: UUID, entitlement: EffectiveEntitlement, verifiedAt: Instant): FeatureAccessCacheEntity =
            FeatureAccessCacheEntity(
                userId = userId,
                policyVersion = 1,
                effectiveTier = if (entitlement.verified) "PREMIUM" else "FREE",
                entitlementExpiresAt = entitlement.expiresAtEpochMillis?.let(Instant::ofEpochMilli),
                verifiedAt = verifiedAt,
                source = "VERIFIED_SERVER",
            )
    }
}
