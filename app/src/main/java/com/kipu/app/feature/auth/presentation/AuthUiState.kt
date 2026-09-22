package com.kipu.app.feature.auth.presentation

data class AuthUiState(
    val email: String = "",
    val emailError: String? = null,
    val password: String = "",
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val generalError: String? = null,
    val cooldownSeconds: Int = 0,
    val showExistingAccountDialog: Boolean = false,
    val confirmationRequiredEmail: String? = null,
) {
    val isFormValid: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && emailError == null && passwordError == null
}

sealed interface AuthNavigationEvent {
    data class NavigateToHome(val userId: String) : AuthNavigationEvent
    data object NavigateToLogin : AuthNavigationEvent
    data object NavigateToRegister : AuthNavigationEvent
    data object NavigateToRecovery : AuthNavigationEvent
}
