package com.kipu.app.feature.accounts.di

import com.kipu.app.feature.accounts.data.OfflineFirstFinancialInstrumentsRepository
import com.kipu.app.feature.accounts.domain.FinancialInstrumentsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AccountsModule {

    @Binds
    @Singleton
    abstract fun bindFinancialInstrumentsRepository(
        impl: OfflineFirstFinancialInstrumentsRepository,
    ): FinancialInstrumentsRepository
}
