package com.kipu.app.core.network

import java.util.UUID

data class AuthenticatedSession(
    val userId: UUID,
    val accessToken: String,
)

/** Boundary supplied by HU-01; plan code must not initiate authentication. */
interface AuthenticatedSessionProvider {
    suspend fun currentSession(): AuthenticatedSession?

    suspend fun refreshSession(): AuthenticatedSession?
}
