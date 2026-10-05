package com.kipu.app.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.feature.auth.domain.CompletePasswordReset
import com.kipu.app.feature.auth.domain.PasswordValidator
import com.kipu.app.feature.auth.domain.RequestPasswordRecovery
import com.kipu.app.feature.auth.data.RecoverySessionInstaller
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.lifecycle.SavedStateHandle
import com.kipu.app.feature.auth.domain.RecoveryRequestException
import kotlinx.coroutines.Job

@HiltViewModel
class RecoveryViewModel @Inject constructor(
    private val requestPasswordRecovery: RequestPasswordRecovery,
    private val completePasswordReset: CompletePasswordReset,
    private val recoverySessionInstaller: RecoverySessionInstaller,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecoveryUiState(
        email = savedStateHandle["email"] ?: "",
        submittedEmail = savedStateHandle["submittedEmail"] ?: "",
        isRequestAccepted = savedStateHandle["accepted"] ?: false,
    ))
    val uiState: StateFlow<RecoveryUiState> = _uiState.asStateFlow()
    private var cooldownJob: Job? = null

    init {
        val until = savedStateHandle.get<Long>("resendUntil") ?: 0L
        val remaining = ((until - System.currentTimeMillis() + 999) / 1_000).coerceIn(0, 3600).toInt()
        if (remaining > 0) startCooldown(remaining)
    }

    private fun startCooldown(seconds: Int) {
        cooldownJob?.cancel()
        savedStateHandle["resendUntil"] = System.currentTimeMillis() + seconds * 1_000L
        _uiState.update { it.copy(resendSeconds = seconds) }
        cooldownJob = viewModelScope.launch {
            repeat(seconds) {
                delay(1_000)
                _uiState.update { state -> state.copy(resendSeconds = (state.resendSeconds - 1).coerceAtLeast(0)) }
            }
            savedStateHandle["resendUntil"] = 0L
        }
    }

    fun showInvalidRecoveryLinkIfNeeded() {
        if (!recoverySessionInstaller.isReady()) {
            _uiState.update {
                it.copy(errorMessage = "El enlace de recuperación es inválido, ya fue usado o ha vencido.")
            }
        }
    }

    fun onEmailChanged(email: String) {
        savedStateHandle["email"] = email
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
        if (_uiState.value.isLoading || _uiState.value.resendSeconds > 0) return
        val email = _uiState.value.submittedEmail.ifEmpty { _uiState.value.email }
        val emailValidation = PasswordValidator.validateEmail(email)
        if (!emailValidation.isValid) {
            _uiState.update { it.copy(emailError = (emailValidation as PasswordValidator.ValidationResult.Invalid).reason) }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            requestPasswordRecovery(email).fold(
                onSuccess = {
                    savedStateHandle["accepted"] = true
                    savedStateHandle["submittedEmail"] = email
                    // The same confirmation is shown for existing and unknown emails.
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRequestAccepted = true,
                            errorMessage = null,
                            submittedEmail = email,
                            resendSeconds = 45,
                        )
                    }
                    // A finite cooldown provides feedback and prevents accidental repeated sends.
                    startCooldown(45)
                },
                onFailure = { error ->
                    if (error is RecoveryRequestException && error.retryAfterSeconds > 0) {
                        startCooldown(error.retryAfterSeconds)
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message
                                ?: "No se pudo solicitar la recuperación. Intenta nuevamente.",
                        )
                    }
                },
            )
        }
    }

    fun submitNewPassword() {
        if (_uiState.value.isLoading || _uiState.value.isPasswordResetSuccess) return
        val password = _uiState.value.newPassword
        val passwordValidation = PasswordValidator.validatePassword(password)
        if (!passwordValidation.isValid) {
            _uiState.update { it.copy(passwordError = (passwordValidation as PasswordValidator.ValidationResult.Invalid).reason) }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null, newPassword = "") }
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
