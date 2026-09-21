package com.kipu.app.feature.settings.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "device_account_settings")
data class DeviceAccountSettingsEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: UUID,
    @ColumnInfo(name = "local_unlock_enabled")
    val localUnlockEnabled: Boolean = false,
    @ColumnInfo(name = "unlock_authenticators")
    val unlockAuthenticators: Int = 0,
)
