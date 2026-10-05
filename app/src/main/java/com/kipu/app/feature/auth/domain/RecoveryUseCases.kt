package com.kipu.app.feature.auth.domain

import com.kipu.app.feature.auth.data.remote.ApiResponse
import com.kipu.app.feature.auth.data.remote.AuthApi
import com.kipu.app.feature.auth.data.RecoverySessionInstaller
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

class RecoveryRequestException(val retryAfterSeconds: Int = 0) : Exception(
    if (retryAfterSeconds > 0) "Demasiados intentos. Intenta en $retryAfterSeconds segundos."
    else "No se pudo solicitar la recuperación. Intenta nuevamente."
)

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
            return Result.failure(Exception("Formato de correo no válido."))
        }
        val normalized = PasswordValidator.normalizeEmail(email)
        return when (val response = authApi.recovery(com.kipu.app.feature.auth.data.remote.RecoveryRequestDto(email = normalized))) {
            is ApiResponse.Success -> Result.success(Unit)
            is ApiResponse.Error -> {
                if (response.statusCode == 429) {
                    val retry = response.retryAfter ?: 60
                    Result.failure(RecoveryRequestException(retry.coerceIn(1, 3600)))
                } else if (response.statusCode >= 500) {
                    Result.failure(RecoveryRequestException())
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
    private val recoverySessionInstaller: RecoverySessionInstaller,
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
        if (!recoverySessionInstaller.isReady()) {
            return Result.failure(Exception("El enlace de recuperación es inválido, ya fue usado o ha vencido."))
        }
        return try {
            supabaseClient.auth.updateUser {
                password = newPassword
            }
            recoverySessionInstaller.clear()
            runCatching { supabaseClient.auth.signOut() }
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(Exception("El enlace de recuperación es inválido, ya fue usado o ha vencido."))
        }
    }
}
