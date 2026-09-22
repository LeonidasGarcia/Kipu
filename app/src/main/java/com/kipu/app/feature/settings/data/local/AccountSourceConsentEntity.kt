package com.kipu.app.feature.settings.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "account_source_consent",
    primaryKeys = ["user_id", "source"],
)
data class AccountSourceConsentEntity(
    @ColumnInfo(name = "user_id")
    val userId: UUID,
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "consent_state")
    val consentState: String,
    @ColumnInfo(name = "capability_state")
    val capabilityState: String,
    @ColumnInfo(name = "explanation_version")
    val explanationVersion: Int = 1,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now(),
)
