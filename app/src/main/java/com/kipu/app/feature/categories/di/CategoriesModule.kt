package com.kipu.app.feature.categories.di

import com.kipu.app.feature.categories.data.OfflineFirstCategoriesRepository
import com.kipu.app.feature.categories.domain.CategoriesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CategoriesModule {

    @Binds
    @Singleton
    abstract fun bindCategoriesRepository(
        impl: OfflineFirstCategoriesRepository,
    ): CategoriesRepository

    @Binds
    @Singleton
    abstract fun bindCategorySyncScheduler(
        impl: com.kipu.app.feature.categories.data.sync.WorkManagerCategorySyncScheduler,
    ): com.kipu.app.feature.categories.data.sync.CategorySyncScheduler
}
