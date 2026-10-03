package com.kipu.app.feature.movements.di

import com.kipu.app.feature.movements.data.OfflineFirstMovementRepository
import com.kipu.app.feature.movements.domain.ExpenseConsumptionRepository
import com.kipu.app.feature.movements.domain.MovementMaintenanceRepository
import com.kipu.app.feature.movements.domain.MovementRepository
import com.kipu.app.feature.movements.domain.MovementRevisionPlanner
import com.kipu.app.feature.movements.domain.MovementRevisionRequestHasher
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

    @Binds
    @Singleton
    abstract fun bindMovementMaintenanceRepository(
        impl: OfflineFirstMovementRepository
    ): MovementMaintenanceRepository

    @Binds
    @Singleton
    abstract fun bindExpenseConsumptionRepository(
        impl: OfflineFirstMovementRepository
    ): ExpenseConsumptionRepository

    @Binds
    @Singleton
    abstract fun bindMovementHistoryQueryRepository(
        impl: OfflineFirstMovementRepository
    ): com.kipu.app.feature.movements.domain.MovementHistoryQueryRepository

    @Binds
    @Singleton
    abstract fun bindMovementHistoryAccessPolicy(
        impl: com.kipu.app.feature.movements.domain.DefaultMovementHistoryAccessPolicy
    ): com.kipu.app.feature.movements.domain.MovementHistoryAccessPolicy

    @Binds
    @Singleton
    abstract fun bindMovementEntitlementProvider(
        impl: com.kipu.app.feature.movements.data.PlansMovementEntitlementProvider
    ): com.kipu.app.feature.movements.domain.MovementEntitlementProvider
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

    @Provides
    @Singleton
    fun provideMovementRevisionRequestHasher(): MovementRevisionRequestHasher =
        MovementRevisionRequestHasher()

    @Provides
    @Singleton
    fun provideMovementRevisionPlanner(): MovementRevisionPlanner =
        MovementRevisionPlanner()
}
