package com.kipu.app.feature.plans.di

import android.content.Context
import androidx.work.WorkManager
import com.kipu.app.BuildConfig
import com.kipu.app.feature.plans.data.OfflineFirstPlanPreferencesRepository
import com.kipu.app.feature.plans.data.billing.BillingRepository
import com.kipu.app.feature.plans.data.entitlement.AndroidKeystoreInstallationSigningKeyProvider
import com.kipu.app.feature.plans.data.entitlement.InstallationSigningKeyProvider
import com.kipu.app.feature.plans.data.entitlement.BuildConfiguredOfflineEntitlementGrantPublicKeyResolver
import com.kipu.app.feature.plans.data.entitlement.OfflineEntitlementGrantPublicKeyResolver
import com.kipu.app.feature.plans.data.entitlement.AndroidOfflineEntitlementGrantBase64UrlDecoder
import com.kipu.app.feature.plans.data.entitlement.OfflineEntitlementGrantBase64UrlDecoder
import com.kipu.app.feature.plans.data.entitlement.OfflineEntitlementLeaseEvaluator
import com.kipu.app.feature.plans.data.entitlement.EffectiveEntitlementEvaluator
import com.kipu.app.feature.plans.data.entitlement.AndroidOfflineEntitlementClock
import com.kipu.app.feature.plans.data.entitlement.OfflineEntitlementClock
import com.kipu.app.feature.plans.data.remote.PlanSelectionApi
import com.kipu.app.feature.plans.data.remote.VerifyPurchaseApi
import com.kipu.app.feature.plans.domain.*
import com.kipu.app.feature.plans.purchase.BillingPurchaseRepository
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
    @Binds abstract fun billingPurchaseRepository(value: BillingRepository): BillingPurchaseRepository
    @Binds abstract fun installationSigningKeyProvider(value: AndroidKeystoreInstallationSigningKeyProvider): InstallationSigningKeyProvider
    @Binds abstract fun offlineEntitlementGrantPublicKeyResolver(value: BuildConfiguredOfflineEntitlementGrantPublicKeyResolver): OfflineEntitlementGrantPublicKeyResolver
    @Binds abstract fun offlineEntitlementGrantBase64UrlDecoder(value: AndroidOfflineEntitlementGrantBase64UrlDecoder): OfflineEntitlementGrantBase64UrlDecoder
    @Binds abstract fun offlineEntitlementLeaseEvaluator(value: OfflineEntitlementLeaseEvaluator): EffectiveEntitlementEvaluator
    @Binds abstract fun offlineEntitlementClock(value: AndroidOfflineEntitlementClock): OfflineEntitlementClock
    companion object {
        @Provides fun operationIds(): OperationIdProvider = OperationIdProvider(UUID::randomUUID)
        @Provides fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
        @Provides fun api(client: HttpClient): PlanSelectionApi = PlanSelectionApi(client, BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1")
        @Provides fun verifyPurchaseApi(client: HttpClient): VerifyPurchaseApi = VerifyPurchaseApi(client, BuildConfig.SUPABASE_URL)
    }
}
