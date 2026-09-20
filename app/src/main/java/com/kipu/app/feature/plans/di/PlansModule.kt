package com.kipu.app.feature.plans.di

import android.content.Context
import androidx.work.WorkManager
import com.kipu.app.BuildConfig
import com.kipu.app.feature.plans.data.OfflineFirstPlanPreferencesRepository
import com.kipu.app.feature.plans.data.remote.PlanSelectionApi
import com.kipu.app.feature.plans.domain.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import java.util.UUID

@Module @InstallIn(SingletonComponent::class)
abstract class PlansModule {
    @Binds abstract fun repository(value: OfflineFirstPlanPreferencesRepository): PlanPreferencesRepository
    companion object {
        @Provides fun operationIds(): OperationIdProvider = OperationIdProvider(UUID::randomUUID)
        @Provides fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
        @Provides fun api(client: HttpClient): PlanSelectionApi = PlanSelectionApi(client, BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1")
    }
}
