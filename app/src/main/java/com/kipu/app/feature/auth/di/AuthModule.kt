package com.kipu.app.feature.auth.di

import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.core.session.SessionCoordinatorImpl
import com.kipu.app.feature.auth.data.SupabaseAuthRepository
import com.kipu.app.feature.auth.domain.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindSessionCoordinator(
        impl: SessionCoordinatorImpl,
    ): SessionCoordinator

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: SupabaseAuthRepository,
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindPendingChangesRepository(
        impl: com.kipu.app.core.session.PendingChangesRepositoryImpl,
    ): com.kipu.app.core.session.PendingChangesRepository

    @dagger.multibindings.Multibinds
    abstract fun bindPendingChangesSources(): Set<com.kipu.app.core.session.PendingChangesSource>
}
