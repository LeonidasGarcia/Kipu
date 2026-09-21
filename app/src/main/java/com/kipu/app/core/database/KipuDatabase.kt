package com.kipu.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheEntity
import com.kipu.app.feature.plans.data.local.PlanPreferencesDao
import com.kipu.app.feature.plans.data.local.PlanPreferencesEntity
import com.kipu.app.feature.plans.data.local.PlanSelectionSyncStateEntity
import com.kipu.app.feature.plans.data.local.SyncOutboxEntity
import com.kipu.app.feature.settings.data.local.AccountSourceConsentEntity
import com.kipu.app.feature.settings.data.local.DeviceAccountSettingsEntity
import com.kipu.app.feature.settings.data.local.InstallationPermissionStateEntity
import com.kipu.app.feature.settings.data.local.ProfilePreferenceOutboxEntity
import com.kipu.app.feature.settings.data.local.UserProfileCacheEntity

@Database(
    entities = [
        PlanPreferencesEntity::class,
        PlanSelectionSyncStateEntity::class,
        SyncOutboxEntity::class,
        FeatureAccessCacheEntity::class,
        UserProfileCacheEntity::class,
        ProfilePreferenceOutboxEntity::class,
        DeviceAccountSettingsEntity::class,
        InstallationPermissionStateEntity::class,
        AccountSourceConsentEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class KipuDatabase : RoomDatabase() {
    abstract fun planPreferencesDao(): PlanPreferencesDao
    abstract fun profilePreferencesDao(): com.kipu.app.feature.settings.data.local.ProfilePreferencesDao
    abstract fun permissionConsentDao(): com.kipu.app.feature.settings.data.local.PermissionConsentDao
    abstract fun deviceAccountSettingsDao(): com.kipu.app.feature.settings.data.local.DeviceAccountSettingsDao
}
