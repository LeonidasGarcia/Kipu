package com.kipu.app.feature.plans.domain.model

enum class PurchaseLifecycle {
    ACTIVE,
    IN_GRACE_PERIOD,
    ACCOUNT_HOLD,
    CANCELED_ACTIVE,
    EXPIRED,
    REVOKED,
    PAUSED,
    PENDING;

    fun grantsAccess(expiresAtEpochMillis: Long?, nowEpochMillis: Long, lifetime: Boolean = false): Boolean = when (this) {
        ACTIVE -> if (lifetime) expiresAtEpochMillis == null else expiresAtEpochMillis?.let { it > nowEpochMillis } == true
        IN_GRACE_PERIOD, CANCELED_ACTIVE -> !lifetime && expiresAtEpochMillis?.let { it > nowEpochMillis } == true
        ACCOUNT_HOLD, EXPIRED, REVOKED, PAUSED, PENDING -> false
    }

    companion object {
        fun fromProviderState(providerState: String, expiresAtEpochMillis: Long?, nowEpochMillis: Long, lifetime: Boolean = false): PurchaseLifecycle {
            val normalized = providerState.uppercase()
            if (lifetime) {
                return when (normalized) {
                    "PURCHASED", "PURCHASE_STATE_PURCHASED" -> ACTIVE
                    "CANCELLED", "PURCHASE_STATE_CANCELLED", "REVOKED" -> REVOKED
                    "PENDING", "PURCHASE_STATE_PENDING" -> PENDING
                    else -> EXPIRED
                }
            }
            return when (normalized) {
                "SUBSCRIPTION_STATE_ACTIVE", "ACTIVE" -> if (expiresAtEpochMillis != null && expiresAtEpochMillis <= nowEpochMillis) EXPIRED else ACTIVE
                "SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "IN_GRACE_PERIOD" -> IN_GRACE_PERIOD
                "SUBSCRIPTION_STATE_ON_HOLD", "ACCOUNT_HOLD", "ON_HOLD" -> ACCOUNT_HOLD
                "SUBSCRIPTION_STATE_CANCELED", "CANCELED", "CANCELED_ACTIVE" -> if (expiresAtEpochMillis?.let { it > nowEpochMillis } == true) CANCELED_ACTIVE else EXPIRED
                "SUBSCRIPTION_STATE_EXPIRED", "EXPIRED" -> EXPIRED
                "SUBSCRIPTION_STATE_PAUSED", "PAUSED" -> PAUSED
                "SUBSCRIPTION_STATE_PENDING", "PENDING" -> PENDING
                "PURCHASE_STATE_CANCELLED", "REVOKED" -> REVOKED
                else -> EXPIRED
            }
        }
    }
}
