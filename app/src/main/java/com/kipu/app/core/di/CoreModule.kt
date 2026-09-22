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

@Module @InstallIn(SingletonComponent::class)
object CoreModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): KipuDatabase =
        Room.databaseBuilder(context, KipuDatabase::class.java, "kipu.db")
            .addMigrations(
                com.kipu.app.core.database.MIGRATION_1_2,
                com.kipu.app.core.database.MIGRATION_2_3,
                com.kipu.app.core.database.MIGRATION_3_4,
            )
            .build()

    @Provides fun dao(database: KipuDatabase): PlanPreferencesDao = database.planPreferencesDao()
    @Provides fun profileDao(database: KipuDatabase): com.kipu.app.feature.settings.data.local.ProfilePreferencesDao = database.profilePreferencesDao()
    @Provides fun permissionConsentDao(database: KipuDatabase): com.kipu.app.feature.settings.data.local.PermissionConsentDao = database.permissionConsentDao()
    @Provides fun deviceAccountSettingsDao(database: KipuDatabase): com.kipu.app.feature.settings.data.local.DeviceAccountSettingsDao = database.deviceAccountSettingsDao()
    @Provides fun accountDao(database: KipuDatabase): com.kipu.app.feature.accounts.data.local.AccountDao = database.accountDao()
    @Provides fun cardDao(database: KipuDatabase): com.kipu.app.feature.accounts.data.local.CardDao = database.cardDao()
    @Provides fun financialMovementDao(database: KipuDatabase): com.kipu.app.feature.accounts.data.local.FinancialMovementDao = database.financialMovementDao()
    @Provides fun instrumentSyncDao(database: KipuDatabase): com.kipu.app.feature.accounts.data.local.InstrumentSyncDao = database.instrumentSyncDao()
    @Provides fun movementDao(database: KipuDatabase): com.kipu.app.feature.movements.data.local.MovementDao = database.movementDao()
    @Provides fun clock(): Clock = Clock.systemUTC()
}
