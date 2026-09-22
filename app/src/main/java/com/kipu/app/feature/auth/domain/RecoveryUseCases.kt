package com.kipu.app.feature.auth.domain

import com.kipu.app.feature.auth.data.remote.ApiResponse
import com.kipu.app.feature.auth.data.remote.AuthApi
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class RequestPasswordRecovery @Inject constructor(
    private val authApi: AuthApi,
) {
    /**
     * Solicits password recovery instructions for the provided email via auth-access.
     * Complies with FR-008 and SC-002: always returns Success with the same neutral message
     * regardless of whether the email exists or not, and respects rate limiting (FR-050).
     */
    open suspend operator fun invoke(email: String): Result<Unit> {
        val validation = PasswordValidator.validateEmail(email)
        if (!validation.isValid) {
            // Even on invalid format, return success to maintain neutral behavior externally
            return Result.success(Unit)
        }
        val normalized = PasswordValidator.normalizeEmail(email)
        return when (val response = authApi.recovery(com.kipu.app.feature.auth.data.remote.RecoveryRequestDto(email = normalized))) {
            is ApiResponse.Success -> Result.success(Unit)
            is ApiResponse.Error -> {
                if (response.statusCode == 429) {
                    val retry = response.retryAfter ?: 60
                    Result.failure(Exception("Demasiados intentos. Intenta en $retry segundos."))
                } else {
                    Result.success(Unit)
                }
            }
            is ApiResponse.NetworkFailure -> {
                Result.failure(Exception("Se requiere conexión a internet para solicitar la recuperación."))
            }
        }
    }
}

@Singleton
open class CompletePasswordReset @Inject constructor(
    private val supabaseClient: SupabaseClient,
) {
    /**
     * Updates the password using the active authenticated session restored from recovery deep link.
     * Complies with FR-010 and FR-011.
     */
    open suspend operator fun invoke(newPassword: String): Result<Unit> {
        val validation = PasswordValidator.validatePassword(newPassword)
        if (!validation.isValid) {
            return Result.failure(Exception((validation as PasswordValidator.ValidationResult.Invalid).reason))
        }
        return try {
            supabaseClient.auth.updateUser {
                password = newPassword
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("El enlace de recuperación es inválido, ya fue usado o ha vencido."))
        }
    }
}
