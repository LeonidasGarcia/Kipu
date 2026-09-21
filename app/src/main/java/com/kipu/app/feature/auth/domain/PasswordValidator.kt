package com.kipu.app.feature.auth.domain

/**
 * Validates password complexity and email format according to product policy.
 * Policy: 8-72 characters, at least one letter and at least one digit.
 */
object PasswordValidator {

    private const val MIN_LENGTH = 8
    private const val MAX_LENGTH = 72

    private val EMAIL_PATTERN = Regex(
        "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    )

    fun hasMinLength(password: String): Boolean = password.length >= MIN_LENGTH
    fun hasLetter(password: String): Boolean = password.any { it.isLetter() }
    fun hasNumber(password: String): Boolean = password.any { it.isDigit() }
    fun hasSpecialChar(password: String): Boolean = password.any { !it.isLetterOrDigit() }
    fun passwordsMatch(password: String, confirm: String): Boolean = password.isNotEmpty() && password == confirm

    fun validatePassword(password: String): ValidationResult {
        if (!hasMinLength(password)) {
            return ValidationResult.Invalid("La contraseña debe tener al menos 8 caracteres.")
        }
        if (password.length > MAX_LENGTH) {
            return ValidationResult.Invalid("La contraseña no debe superar los 72 caracteres.")
        }
        if (!hasLetter(password)) {
            return ValidationResult.Invalid("La contraseña debe incluir al menos una letra.")
        }
        if (!hasNumber(password)) {
            return ValidationResult.Invalid("La contraseña debe incluir al menos un número.")
        }
        return ValidationResult.Valid
    }

    fun validatePasswordConfirmation(password: String, confirm: String): ValidationResult {
        if (confirm.isEmpty()) {
            return ValidationResult.Invalid("Por favor confirma tu contraseña.")
        }
        if (password != confirm) {
            return ValidationResult.Invalid("Las contraseñas no coinciden.")
        }
        return ValidationResult.Valid
    }

    fun validateEmail(email: String): ValidationResult {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid("El correo no puede estar vacío.")
        }
        if (!EMAIL_PATTERN.matches(trimmed)) {
            return ValidationResult.Invalid("Formato de correo no válido.")
        }
        return ValidationResult.Valid
    }

    fun normalizeEmail(email: String): String = email.trim().lowercase()

    sealed interface ValidationResult {
        data object Valid : ValidationResult
        data class Invalid(val reason: String) : ValidationResult
        val isValid: Boolean get() = this is Valid
    }
}
