package com.kipu.app.feature.movements.di

import com.kipu.app.feature.movements.data.OfflineFirstMovementRepository
import com.kipu.app.feature.movements.domain.MovementRepository
import com.kipu.app.feature.movements.domain.RegisterTransactionValidator
import com.kipu.app.feature.movements.domain.TransactionRequestHasher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MovementsBindingModule {
    @Binds
    @Singleton
    abstract fun bindMovementRepository(
        impl: OfflineFirstMovementRepository
    ): MovementRepository
}

@Module
@InstallIn(SingletonComponent::class)
object MovementsModule {
    @Provides
    @Singleton
    fun provideRegisterTransactionValidator(): RegisterTransactionValidator =
        RegisterTransactionValidator()

    @Provides
    @Singleton
    fun provideTransactionRequestHasher(): TransactionRequestHasher =
        TransactionRequestHasher()
}
