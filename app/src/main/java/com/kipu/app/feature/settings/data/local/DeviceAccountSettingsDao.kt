package com.kipu.app.feature.settings.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceAccountSettingsDao {

    @Query("SELECT * FROM device_account_settings WHERE user_id = :userId")
    suspend fun findSettings(userId: UUID): DeviceAccountSettingsEntity?

    @Query("SELECT * FROM device_account_settings WHERE user_id = :userId")
    fun observeSettings(userId: UUID): Flow<DeviceAccountSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSettings(settings: DeviceAccountSettingsEntity)

    @Query("DELETE FROM device_account_settings WHERE user_id = :userId")
    suspend fun deleteSettings(userId: UUID)
}
