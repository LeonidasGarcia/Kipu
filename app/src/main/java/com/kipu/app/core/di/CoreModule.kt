package com.kipu.app.core.di

import android.content.Context
import androidx.room.Room
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.plans.data.local.PlanPreferencesDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

import androidx.room.withTransaction

@Module @InstallIn(SingletonComponent::class)
object CoreModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): KipuDatabase =
        Room.databaseBuilder(context, KipuDatabase::class.java, "kipu.db")
            .addCallback(com.kipu.app.core.database.MovementSchemaCallback())
            .addMigrations(
                com.kipu.app.core.database.MIGRATION_1_2,
                com.kipu.app.core.database.MIGRATION_2_3,
                com.kipu.app.core.database.MIGRATION_3_4,
                com.kipu.app.core.database.MIGRATION_4_5,
                com.kipu.app.core.database.MIGRATION_5_6,
                com.kipu.app.core.database.MIGRATION_6_7,
                com.kipu.app.core.database.MIGRATION_7_8,
                com.kipu.app.core.database.MIGRATION_8_9,
                com.kipu.app.core.database.MIGRATION_9_10,
                com.kipu.app.core.database.MIGRATION_10_11,
                com.kipu.app.core.database.MIGRATION_11_12,
                com.kipu.app.core.database.MIGRATION_12_13,
                com.kipu.app.core.database.MIGRATION_13_14,
                com.kipu.app.core.database.MIGRATION_14_15,
                com.kipu.app.core.database.MIGRATION_15_16,
                com.kipu.app.core.database.MIGRATION_16_17,
                com.kipu.app.core.database.MIGRATION_17_18,
                com.kipu.app.core.database.MIGRATION_18_19,
                com.kipu.app.core.database.MIGRATION_19_20,
            )
            .build()

    @Provides @Singleton fun transactionRunner(database: KipuDatabase): com.kipu.app.core.database.DatabaseTransactionRunner =
        object : com.kipu.app.core.database.DatabaseTransactionRunner {
            override suspend operator fun <R> invoke(block: suspend () -> R): R =
                database.withTransaction { block() }
        }

    @Provides fun dao(database: KipuDatabase): PlanPreferencesDao = database.planPreferencesDao()
    @Provides fun planQuotaSelectionDao(database: KipuDatabase): com.kipu.app.feature.plans.data.local.PlanQuotaSelectionDao = database.planQuotaSelectionDao()
    @Provides fun featureAccessCacheDao(database: KipuDatabase): com.kipu.app.feature.plans.data.local.FeatureAccessCacheDao = database.featureAccessCacheDao()
    @Provides fun profileDao(database: KipuDatabase): com.kipu.app.feature.settings.data.local.ProfilePreferencesDao = database.profilePreferencesDao()
    @Provides fun permissionConsentDao(database: KipuDatabase): com.kipu.app.feature.settings.data.local.PermissionConsentDao = database.permissionConsentDao()
    @Provides fun deviceAccountSettingsDao(database: KipuDatabase): com.kipu.app.feature.settings.data.local.DeviceAccountSettingsDao = database.deviceAccountSettingsDao()
    @Provides fun accountDao(database: KipuDatabase): com.kipu.app.feature.accounts.data.local.AccountDao = database.accountDao()
    @Provides fun cardDao(database: KipuDatabase): com.kipu.app.feature.accounts.data.local.CardDao = database.cardDao()
    @Provides fun financialMovementDao(database: KipuDatabase): com.kipu.app.feature.accounts.data.local.FinancialMovementDao = database.financialMovementDao()
    @Provides fun instrumentSyncDao(database: KipuDatabase): com.kipu.app.feature.accounts.data.local.InstrumentSyncDao = database.instrumentSyncDao()
    @Provides fun categoryDao(database: KipuDatabase): com.kipu.app.feature.categories.data.local.CategoryDao = database.categoryDao()
    @Provides fun merchantCatalogDao(database: KipuDatabase): com.kipu.app.feature.categories.data.local.MerchantCatalogDao = database.merchantCatalogDao()
    @Provides fun merchantRulesDao(database: KipuDatabase): com.kipu.app.feature.categories.data.local.MerchantRulesDao = database.merchantRulesDao()
    @Provides fun movementDao(database: KipuDatabase): com.kipu.app.feature.movements.data.local.MovementDao = database.movementDao()
    @Provides fun debtDao(database: KipuDatabase): com.kipu.app.feature.debts.data.local.DebtDao = database.debtDao()
    @Provides fun debtOutboxDao(database: KipuDatabase): com.kipu.app.feature.debts.data.local.DebtOutboxDao = database.debtOutboxDao()
    @Provides fun creditDao(database: KipuDatabase): com.kipu.app.feature.accounts.data.local.CreditDao = database.creditDao()
    @Provides fun appNotificationDao(database: KipuDatabase): com.kipu.app.feature.notifications.data.local.AppNotificationDao = database.appNotificationDao()
    @Provides fun notificationSyncOutboxDao(database: KipuDatabase): com.kipu.app.feature.notifications.data.local.NotificationSyncOutboxDao = database.notificationSyncOutboxDao()
    @Provides fun clock(): Clock = Clock.systemUTC()
}
