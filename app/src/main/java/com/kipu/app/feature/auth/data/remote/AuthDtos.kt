package com.kipu.app.feature.auth.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequestDto(
    val email: String,
    val password: String,
    val captchaToken: String,
)

@Serializable
data class RegisterResponseDto(
    val status: String,
    val confirmationRequired: Boolean = true,
)

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String,
    val captchaToken: String,
)

@Serializable
data class SessionEnvelopeDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tokenType: String = "bearer",
    val userId: String,
)

@Serializable
data class ProblemDto(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    @SerialName("retry_after_seconds")
    val retryAfterSeconds: Int? = null,
)
