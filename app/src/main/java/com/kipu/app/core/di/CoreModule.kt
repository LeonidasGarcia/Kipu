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
    @Provides @Singleton fun database(@ApplicationContext context: Context): KipuDatabase = Room.databaseBuilder(context, KipuDatabase::class.java, "kipu.db").build()
    @Provides fun dao(database: KipuDatabase): PlanPreferencesDao = database.planPreferencesDao()
    @Provides fun clock(): Clock = Clock.systemUTC()
}
