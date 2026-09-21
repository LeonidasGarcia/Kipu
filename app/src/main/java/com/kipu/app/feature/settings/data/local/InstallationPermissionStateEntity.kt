package com.kipu.app.feature.settings.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "installation_permission_state")
data class InstallationPermissionStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "device_authorization")
    val deviceAuthorization: String,
    @ColumnInfo(name = "last_checked_at")
    val lastCheckedAt: Instant = Instant.now(),
)
