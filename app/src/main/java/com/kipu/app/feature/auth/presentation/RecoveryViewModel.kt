package com.kipu.app.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.feature.auth.domain.CompletePasswordReset
import com.kipu.app.feature.auth.domain.PasswordValidator
import com.kipu.app.feature.auth.domain.RequestPasswordRecovery
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class RecoveryViewModel @Inject constructor(
    private val requestPasswordRecovery: RequestPasswordRecovery,
    private val completePasswordReset: CompletePasswordReset,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecoveryUiState())
    val uiState: StateFlow<RecoveryUiState> = _uiState.asStateFlow()

    fun onEmailChanged(email: String) {
        _uiState.update {
            it.copy(
                email = email,
                emailError = null,
                errorMessage = null,
            )
        }
    }

    fun onNewPasswordChanged(password: String) {
        _uiState.update {
            it.copy(
                newPassword = password,
                passwordError = null,
                errorMessage = null,
            )
        }
    }

    fun submitRecoveryRequest() {
        val email = _uiState.value.email
        val emailValidation = PasswordValidator.validateEmail(email)
        if (!emailValidation.isValid) {
            _uiState.update { it.copy(emailError = (emailValidation as PasswordValidator.ValidationResult.Invalid).reason) }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            requestPasswordRecovery(email).fold(
                onSuccess = {
                    // The same confirmation is shown for existing and unknown emails.
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRequestAccepted = true,
                            errorMessage = null,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRequestAccepted = false,
                            errorMessage = error.message
                                ?: "No se pudo solicitar la recuperación. Intenta nuevamente.",
                        )
                    }
                },
            )
        }
    }

    fun submitNewPassword() {
        val password = _uiState.value.newPassword
        val passwordValidation = PasswordValidator.validatePassword(password)
        if (!passwordValidation.isValid) {
            _uiState.update { it.copy(passwordError = (passwordValidation as PasswordValidator.ValidationResult.Invalid).reason) }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = completePasswordReset(password)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isPasswordResetSuccess = true,
                            newPassword = "",
                        )
                    }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "Error al actualizar la contraseña.",
                            newPassword = "",
                        )
                    }
                }
            )
        }
    }
}
