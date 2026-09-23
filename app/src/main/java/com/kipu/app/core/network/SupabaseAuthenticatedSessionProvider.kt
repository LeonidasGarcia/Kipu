package com.kipu.app.core.network

import com.kipu.app.core.security.KeystoreEncryptedSessionStorage
import com.kipu.app.core.security.StoredAuthSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.core.session.RemoteSession
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import java.time.Instant
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

        val stored = sessionStorage.load()?.let(StoredAuthSession::parse) ?: return null
        if (!stored.hasValidAccessToken(Instant.now())) return null
        val ownerId = sessionCoordinator.currentOwner?.verifiedUserId ?: return null
        if (ownerId != stored.userId) return null
        val uuid = runCatching { UUID.fromString(ownerId) }.getOrNull() ?: return null
        return AuthenticatedSession(userId = uuid, accessToken = stored.accessToken)
    }

    override suspend fun refreshSession(): AuthenticatedSession? {
        supabaseClient.auth.awaitInitialization()
        val stored = sessionStorage.load()?.let(StoredAuthSession::parse) ?: return null
        val ownerId = sessionCoordinator.currentOwner?.verifiedUserId ?: return null
        if (stored.userId != ownerId) return null
        val refreshed = runCatching { supabaseClient.auth.refreshSession(stored.refreshToken) }.getOrNull()
            ?: return null
        if (refreshed.user?.id != ownerId) return null
        val now = Instant.now()
        val updated = StoredAuthSession.fromUserSession(refreshed, ownerId, now)
        supabaseClient.auth.importSession(updated.toUserSession(), autoRefresh = false)
        sessionStorage.save(StoredAuthSession.encode(updated))
        sessionCoordinator.updateRemoteSession(RemoteSession.Valid(ownerId, now.plusSeconds(updated.expiresIn)))
        return AuthenticatedSession(UUID.fromString(ownerId), updated.accessToken)
    }

    private fun io.github.jan.supabase.auth.user.UserSession.toAuthenticatedSession(): AuthenticatedSession? {
        if (expiresAt <= kotlin.time.Clock.System.now()) return null
        val id = user?.id ?: return null
        if (id != sessionCoordinator.currentOwner?.verifiedUserId) return null
        val userId = runCatching { UUID.fromString(id) }.getOrNull() ?: return null
        return AuthenticatedSession(userId = userId, accessToken = accessToken)
    }
}
