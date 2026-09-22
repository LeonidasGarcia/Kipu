package com.kipu.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kipu.app.feature.accounts.data.local.AccountDao
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.accounts.data.local.CardDao
import com.kipu.app.feature.accounts.data.local.CardEntity
import com.kipu.app.feature.accounts.data.local.FinancialMovementDao
import com.kipu.app.feature.accounts.data.local.FinancialMovementEntity
import com.kipu.app.feature.accounts.data.local.InstrumentSyncDao
import com.kipu.app.feature.accounts.data.local.InstrumentSyncOutboxEntity
import com.kipu.app.feature.categories.data.local.CategoryConflictEntity
import com.kipu.app.feature.categories.data.local.CategoryDao
import com.kipu.app.feature.categories.data.local.CategoryEntity
import com.kipu.app.feature.categories.data.local.CategoryPresentationEntity
import com.kipu.app.feature.categories.data.local.CategorySyncOutboxEntity
import com.kipu.app.feature.categories.data.local.MerchantCatalogDao
import com.kipu.app.feature.categories.data.local.MerchantCatalogEntity
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
        AccountEntity::class,
        CardEntity::class,
        FinancialMovementEntity::class,
        InstrumentSyncOutboxEntity::class,
        CategoryEntity::class,
        CategoryPresentationEntity::class,
        MerchantCatalogEntity::class,
        CategoryConflictEntity::class,
        CategorySyncOutboxEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class KipuDatabase : RoomDatabase() {
    abstract fun planPreferencesDao(): PlanPreferencesDao
    abstract fun profilePreferencesDao(): com.kipu.app.feature.settings.data.local.ProfilePreferencesDao
    abstract fun permissionConsentDao(): com.kipu.app.feature.settings.data.local.PermissionConsentDao
    abstract fun deviceAccountSettingsDao(): com.kipu.app.feature.settings.data.local.DeviceAccountSettingsDao
    abstract fun accountDao(): AccountDao
    abstract fun cardDao(): CardDao
    abstract fun financialMovementDao(): FinancialMovementDao
    abstract fun instrumentSyncDao(): InstrumentSyncDao
    abstract fun categoryDao(): CategoryDao
    abstract fun merchantCatalogDao(): MerchantCatalogDao
}
