package com.kipu.app.feature.settings.di

import com.kipu.app.feature.settings.data.OfflineFirstProfilePreferencesRepository
import com.kipu.app.feature.settings.domain.ProfilePreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {

    @Binds
    @Singleton
    abstract fun bindProfilePreferencesRepository(
        impl: OfflineFirstProfilePreferencesRepository,
    ): ProfilePreferencesRepository

    @Binds
    @Singleton
    abstract fun bindProfileSyncScheduler(
        impl: com.kipu.app.feature.settings.data.sync.WorkManagerProfileSyncScheduler,
    ): com.kipu.app.feature.settings.data.sync.ProfileSyncScheduler

    @Binds
    @Singleton
    abstract fun bindPermissionSourceGateway(
        impl: com.kipu.app.feature.settings.data.AndroidPermissionSourceGateway,
    ): com.kipu.app.feature.settings.domain.PermissionSourceGateway

    @Binds
    @Singleton
    abstract fun bindLocalAuthenticatorGateway(
        impl: com.kipu.app.core.security.AndroidXBiometricGateway,
    ): com.kipu.app.core.security.LocalAuthenticatorGateway
}
