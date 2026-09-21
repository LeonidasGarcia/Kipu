package com.kipu.app.feature.auth.domain.model

import java.time.Instant

/**
 * Domain representation of authentication credentials.
 * Passwords must never be persisted or logged.
 */
data class AuthCredentials(
    val email: String,
    val password: String,
    val captchaToken: String = "dummy-dev-captcha",
)

/**
 * Outcome of an authentication command.
 */
sealed interface AuthResult {
    data class Success(val userId: String) : AuthResult
    data class ConfirmationRequired(val email: String) : AuthResult
    data object AccountAlreadyExists : AuthResult
}

/**
 * Domain-level safe error representations complying with neutral error rules (FR-004).
 */
sealed interface AuthError {
    data object InvalidCredentials : AuthError
    data class InvalidPolicy(val reason: String) : AuthError
    data object NetworkRequired : AuthError
    data class RateLimited(val retryAfterSeconds: Int) : AuthError
    data object AccountNotConfirmed : AuthError
    data class Unknown(val message: String) : AuthError
}

/**
 * State tracking progressive rate limit cooldown for authentication operations (FR-049).
 */
data class CooldownState(
    val retryAfterSeconds: Int = 0,
    val blockedUntil: Instant? = null,
) {
    val isBlocked: Boolean
        get() = blockedUntil?.isAfter(Instant.now()) ?: (retryAfterSeconds > 0)
}
