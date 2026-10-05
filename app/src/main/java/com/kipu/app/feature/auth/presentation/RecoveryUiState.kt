package com.kipu.app.feature.auth.presentation

data class RecoveryUiState(
    val email: String = "",
    val emailError: String? = null,
    val newPassword: String = "",
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val isRequestAccepted: Boolean = false,
    val isPasswordResetSuccess: Boolean = false,
    val errorMessage: String? = null,
    val submittedEmail: String = "",
    val resendSeconds: Int = 0,
)
