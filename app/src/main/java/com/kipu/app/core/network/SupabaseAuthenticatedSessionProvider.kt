package com.kipu.app.core.network

import com.kipu.app.core.security.KeystoreEncryptedSessionStorage
import com.kipu.app.core.session.SessionCoordinator
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseAuthenticatedSessionProvider @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val sessionStorage: KeystoreEncryptedSessionStorage,
    private val sessionCoordinator: SessionCoordinator,
) : AuthenticatedSessionProvider {
    override suspend fun currentSession(): AuthenticatedSession? {
        val supabaseSession = runCatching {
            supabaseClient.auth.awaitInitialization()
            supabaseClient.auth.currentSessionOrNull()?.toAuthenticatedSession()
        }.getOrNull()

        if (supabaseSession != null) return supabaseSession

        val sessionJson = sessionStorage.load() ?: return null
        val token = runCatching {
            val regex = "\"access_token\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            regex.find(sessionJson)?.groupValues?.get(1)
        }.getOrNull() ?: return null

        val ownerId = sessionCoordinator.currentOwner?.verifiedUserId ?: runCatching {
            val regex = "\"user_id\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            regex.find(sessionJson)?.groupValues?.get(1)
        }.getOrNull() ?: return null

        val uuid = runCatching { UUID.fromString(ownerId) }.getOrNull() ?: return null
        return AuthenticatedSession(userId = uuid, accessToken = token)
    }

    override suspend fun refreshSession(): AuthenticatedSession? {
        supabaseClient.auth.awaitInitialization()
        if (supabaseClient.auth.currentSessionOrNull() != null) {
            supabaseClient.auth.refreshCurrentSession()
            val refreshed = supabaseClient.auth.currentSessionOrNull()?.toAuthenticatedSession()
            if (refreshed != null) return refreshed
        }

        return currentSession()
    }

    private fun io.github.jan.supabase.auth.user.UserSession.toAuthenticatedSession(): AuthenticatedSession? {
        val id = user?.id ?: return null
        val userId = runCatching { UUID.fromString(id) }.getOrNull() ?: return null
        return AuthenticatedSession(userId = userId, accessToken = accessToken)
    }
}
