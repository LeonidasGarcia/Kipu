package com.kipu.app.core.security

import io.github.jan.supabase.auth.user.UserSession
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class StoredAuthSession(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("token_type") val tokenType: String,
    @SerialName("user_id") val userId: String,
    @SerialName("expires_at") val expiresAtEpochSeconds: Long? = null,
) {
    fun hasValidAccessToken(now: Instant): Boolean =
        expiresAtEpochSeconds?.let { now.epochSecond < it - 30L } == true

    fun toUserSession(): UserSession = UserSession(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresIn = expiresIn,
        tokenType = tokenType,
        user = null,
        expiresAt = kotlin.time.Instant.fromEpochSeconds(requireNotNull(expiresAtEpochSeconds)),
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(raw: String): StoredAuthSession? = runCatching {
            json.decodeFromString<StoredAuthSession>(raw)
        }.getOrNull()?.takeIf {
            it.accessToken.isNotBlank() && it.refreshToken.isNotBlank() &&
                runCatching { UUID.fromString(it.userId) }.isSuccess
        }

        fun encode(session: StoredAuthSession): String = json.encodeToString(session)

        fun fromUserSession(session: UserSession, userId: String, now: Instant): StoredAuthSession =
            StoredAuthSession(
                accessToken = session.accessToken,
                refreshToken = session.refreshToken,
                expiresIn = session.expiresIn,
                tokenType = session.tokenType,
                userId = userId,
                expiresAtEpochSeconds = now.plusSeconds(session.expiresIn).epochSecond,
            )
    }
}
