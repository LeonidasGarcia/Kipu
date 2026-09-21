package com.kipu.app.navigation

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DeepLinkResult {
    data object ConfirmEmail : DeepLinkResult
    data class ResetPassword(val token: String) : DeepLinkResult
    data object InvalidOrConsumed : DeepLinkResult
}

@Singleton
class AuthDeepLinkHandler @Inject constructor() {

    private val consumedTokens = mutableSetOf<String>()

    /**
     * Parses and validates deep links according to auth-session-contract.
     * Accepted paths: /auth/confirm and /auth/recovery.
     * Replays and unexpected paths are rejected.
     */
    fun handleDeepLink(uri: Uri?): DeepLinkResult {
        if (uri == null) return DeepLinkResult.InvalidOrConsumed
        return handleDeepLink(uri.toString())
    }

    fun handleDeepLink(uriString: String?): DeepLinkResult {
        if (uriString.isNullOrBlank()) return DeepLinkResult.InvalidOrConsumed
        return try {
            val javaUri = java.net.URI(uriString)
            val path = javaUri.path?.trimEnd('/') ?: return DeepLinkResult.InvalidOrConsumed
            val query = javaUri.query.orEmpty()
            val queryParams = query.split("&").mapNotNull {
                val parts = it.split("=")
                if (parts.size == 2) parts[0] to parts[1] else null
            }.toMap()
            val token = queryParams["token"] ?: javaUri.fragment

            when (path) {
                "/auth/confirm" -> {
                    if (token.isNullOrEmpty() || consumedTokens.contains(token)) {
                        DeepLinkResult.InvalidOrConsumed
                    } else {
                        consumedTokens.add(token)
                        DeepLinkResult.ConfirmEmail
                    }
                }
                "/auth/recovery" -> {
                    if (token.isNullOrEmpty() || consumedTokens.contains(token)) {
                        DeepLinkResult.InvalidOrConsumed
                    } else {
                        consumedTokens.add(token)
                        DeepLinkResult.ResetPassword(token)
                    }
                }
                else -> DeepLinkResult.InvalidOrConsumed
            }
        } catch (_: Exception) {
            DeepLinkResult.InvalidOrConsumed
        }
    }
}
