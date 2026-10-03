package com.kipu.app.feature.plans.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import java.time.Instant
import java.util.UUID
import com.kipu.app.feature.plans.domain.model.OfflineEntitlementGrantClaims
import com.kipu.app.feature.plans.domain.model.SignedOfflineEntitlementGrant

@Entity(tableName = "feature_access_cache")
data class FeatureAccessCacheEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: UUID,
    @ColumnInfo(name = "policy_version") val policyVersion: Int,
    @ColumnInfo(name = "effective_tier") val effectiveTier: String = "FREE",
    @ColumnInfo(name = "entitlement_expires_at") val entitlementExpiresAt: Instant? = null,
    @ColumnInfo(name = "verified_at") val verifiedAt: Instant? = null,
    val source: String = "LOCAL_DEFAULT",
    @ColumnInfo(name = "offline_grant_payload") val offlineGrantPayload: String? = null,
    @ColumnInfo(name = "offline_grant_signature") val offlineGrantSignature: String? = null,
    @ColumnInfo(name = "offline_grant_key_id") val offlineGrantKeyId: String? = null,
    @ColumnInfo(name = "offline_anchor_elapsed_ms") val offlineAnchorElapsedRealtimeMillis: Long? = null,
    @ColumnInfo(name = "offline_anchor_boot_count") val offlineAnchorBootCount: Int? = null,
) {
    companion object {
        fun fromVerifiedGrant(
            userId: UUID,
            claims: OfflineEntitlementGrantClaims,
            grant: SignedOfflineEntitlementGrant,
            anchorElapsedRealtimeMillis: Long,
            anchorBootCount: Int,
        ): FeatureAccessCacheEntity = FeatureAccessCacheEntity(
            userId = userId,
            policyVersion = claims.policyVersion,
            effectiveTier = "PREMIUM",
            entitlementExpiresAt = claims.entitlementEndsAtMillis?.let(Instant::ofEpochMilli),
            verifiedAt = Instant.ofEpochMilli(claims.serverVerifiedAtMillis),
            source = "SIGNED_SERVER_GRANT",
            offlineGrantPayload = grant.payload,
            offlineGrantSignature = grant.signature,
            offlineGrantKeyId = grant.keyId,
            offlineAnchorElapsedRealtimeMillis = anchorElapsedRealtimeMillis,
            offlineAnchorBootCount = anchorBootCount,
        )

        fun verifiedFree(userId: UUID): FeatureAccessCacheEntity = FeatureAccessCacheEntity(
            userId = userId,
            policyVersion = 1,
            effectiveTier = "FREE",
            entitlementExpiresAt = null,
            verifiedAt = null,
            source = "VERIFIED_SERVER",
        )
    }
}
