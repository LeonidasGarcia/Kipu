package com.kipu.app.core.network

import com.kipu.app.BuildConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
abstract class SupabaseNetworkModule {
    @Binds
    abstract fun bindAuthenticatedSessionProvider(
        provider: SupabaseAuthenticatedSessionProvider,
    ): AuthenticatedSessionProvider

    companion object {
        private val json = Json {
            ignoreUnknownKeys = false
            explicitNulls = false
        }

        @Provides
        @Singleton
        fun provideSupabaseClient(): SupabaseClient = createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        ) {
            install(Auth)
            install(Postgrest)
        }

        @Provides
        @Singleton
        fun provideHttpClient(): HttpClient = HttpClient(Android) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(json)
            }
            defaultRequest {
                url(BuildConfig.SUPABASE_URL.trimEnd('/') + "/")
                header(HttpHeaders.Accept, "application/json")
                header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            }
        }
    }
}
