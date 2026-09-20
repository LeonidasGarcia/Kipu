package com.kipu.app.core.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseAuthenticatedSessionProvider @Inject constructor(
    private val supabaseClient: SupabaseClient,
) : AuthenticatedSessionProvider {
    override suspend fun currentSession(): AuthenticatedSession? {
        supabaseClient.auth.awaitInitialization()
        return supabaseClient.auth.currentSessionOrNull()?.toAuthenticatedSession()
    }

    override suspend fun refreshSession(): AuthenticatedSession? {
        supabaseClient.auth.awaitInitialization()
        if (supabaseClient.auth.currentSessionOrNull() == null) return null

        supabaseClient.auth.refreshCurrentSession()
        return supabaseClient.auth.currentSessionOrNull()?.toAuthenticatedSession()
    }

    private fun io.github.jan.supabase.auth.user.UserSession.toAuthenticatedSession(): AuthenticatedSession? {
        val id = user?.id ?: return null
        val userId = runCatching { UUID.fromString(id) }.getOrNull() ?: return null
        return AuthenticatedSession(userId = userId, accessToken = accessToken)
    }
}
