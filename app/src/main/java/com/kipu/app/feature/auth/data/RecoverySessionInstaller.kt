package com.kipu.app.feature.auth.data

import com.kipu.app.core.security.KeystoreEncryptedSessionStorage
import com.kipu.app.core.session.SessionCoordinator
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.parseSessionFromUrl
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecoverySessionInstaller @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val sessionStorage: KeystoreEncryptedSessionStorage,
    private val sessionCoordinator: SessionCoordinator,
    private val sessionGate: AuthSessionGate,
) {
    private var ready = false

    suspend fun install(callbackUrl: String): Result<Unit> = sessionGate.exclusive { installExclusive(callbackUrl) }

    private suspend fun installExclusive(callbackUrl: String): Result<Unit> {
        ready = false
        return runCatching {
            val uri = URI(callbackUrl)
            require(uri.scheme == "https" && uri.host == "kipu.app" && uri.path == "/auth/recovery")
            val code = uri.rawQuery.orEmpty().split("&")
                .firstOrNull { it.startsWith("code=") }?.substringAfter("code=")
            val session = if (code.isNullOrBlank()) {
                supabaseClient.auth.parseSessionFromUrl(callbackUrl).also {
                    require(it.type == "recovery")
                }
            } else {
                supabaseClient.auth.exchangeCodeForSession(code, saveSession = false)
            }
            val verifiedUser = supabaseClient.auth.retrieveUser(session.accessToken)
            sessionCoordinator.clearActiveOwner(explicit = true)
            sessionStorage.delete()
            supabaseClient.auth.importSession(session.copy(user = verifiedUser), autoRefresh = false)
            ready = true
        }
    }

    fun isReady(): Boolean = ready

    fun clear() {
        ready = false
    }
}
