package com.kipu.app.feature.settings.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(tableName = "user_profiles")
data class UserProfileCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: UUID,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String = "PEN",
    @ColumnInfo(name = "time_zone")
    val timeZone: String? = null,
    @ColumnInfo(name = "month_start")
    val monthStart: Int = 1,
    @ColumnInfo(name = "hide_balances")
    val hideBalances: Boolean = false,
    @ColumnInfo(name = "theme_mode")
    val themeMode: String = "SYSTEM",
    @ColumnInfo(name = "remote_revision")
    val remoteRevision: Long = 1L,
    @ColumnInfo(name = "remote_updated_at")
    val remoteUpdatedAt: Instant? = null,
    @ColumnInfo(name = "sync_state")
    val syncState: String = "SYNCED",
    @ColumnInfo(name = "updated_locally_at")
    val updatedLocallyAt: Instant = Instant.now(),
)
