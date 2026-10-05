package com.kipu.app.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.feature.auth.domain.AuthRepository
import com.kipu.app.feature.auth.domain.PasswordValidator
import com.kipu.app.feature.auth.domain.model.AuthCredentials
import com.kipu.app.feature.auth.domain.model.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<AuthNavigationEvent>()
    val navigationEvents: SharedFlow<AuthNavigationEvent> = _navigationEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            authRepository.cooldownState.collectLatest { cooldown ->
                val seconds = cooldown.blockedUntil?.let {
                    ((Duration.between(Instant.now(), it).toMillis() + 999) / 1_000)
                        .coerceAtLeast(0)
                        .toInt()
                } ?: cooldown.retryAfterSeconds
                for (remaining in seconds downTo 1) {
                    _uiState.update { it.copy(cooldownSeconds = remaining) }
                    delay(1_000)
                }
                _uiState.update { it.copy(cooldownSeconds = 0) }
            }
        }
    }

    fun onEmailChanged(email: String) {
        _uiState.update {
            it.copy(
                email = email,
                emailError = null,
                generalError = null,
            )
        }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update {
            it.copy(
                password = password,
                passwordError = null,
                generalError = null,
            )
        }
    }

    fun login() {
        val currentState = _uiState.value
        if (currentState.isLoading || currentState.cooldownSeconds > 0) return
        val emailValidation = PasswordValidator.validateEmail(currentState.email)
        if (!emailValidation.isValid) {
            _uiState.update { it.copy(emailError = (emailValidation as PasswordValidator.ValidationResult.Invalid).reason) }
            return
        }
        if (currentState.password.isBlank()) {
            _uiState.update { it.copy(passwordError = "Ingresa tu contraseña.") }
            return
        }

        val credentials = AuthCredentials(currentState.email, currentState.password)
        // Clear password from state immediately after capturing credentials (FR-006)
        _uiState.update { it.copy(password = "", isLoading = true, generalError = null) }

        viewModelScope.launch {
            val result = authRepository.signIn(credentials)
            result.fold(
                onSuccess = { authResult ->
                    _uiState.update { it.copy(isLoading = false) }
                    when (authResult) {
                        is AuthResult.Success -> {
                            _navigationEvents.emit(AuthNavigationEvent.NavigateToHome(authResult.userId))
                        }
                        else -> {
                            _uiState.update { it.copy(generalError = "Respuesta de autenticación inesperada.") }
                        }
                    }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            generalError = if (authRepository.cooldownState.value.isBlocked) null
                                else throwable.message ?: "Error al iniciar sesión.",
                        )
                    }
                }
            )
        }
    }

    fun register() {
        val currentState = _uiState.value
        if (currentState.isLoading || currentState.cooldownSeconds > 0) return
        val emailValidation = PasswordValidator.validateEmail(currentState.email)
        if (!emailValidation.isValid) {
            _uiState.update { it.copy(emailError = (emailValidation as PasswordValidator.ValidationResult.Invalid).reason) }
            return
        }
        val passwordValidation = PasswordValidator.validatePassword(currentState.password)
        if (!passwordValidation.isValid) {
            _uiState.update { it.copy(passwordError = (passwordValidation as PasswordValidator.ValidationResult.Invalid).reason) }
            return
        }

        val credentials = AuthCredentials(currentState.email, currentState.password)
        // Clear password from state immediately (FR-006)
        _uiState.update { it.copy(password = "", isLoading = true, generalError = null) }

        viewModelScope.launch {
            val result = authRepository.register(credentials)
            result.fold(
                onSuccess = { authResult ->
                    _uiState.update { it.copy(isLoading = false) }
                    when (authResult) {
                        is AuthResult.AccountAlreadyExists -> {
                            // FR-051: Offer sign-in when account already exists
                            _uiState.update { it.copy(showExistingAccountDialog = true) }
                        }
                        is AuthResult.ConfirmationRequired -> {
                            _uiState.update { it.copy(confirmationRequiredEmail = authResult.email) }
                        }
                        is AuthResult.Success -> {
                            _navigationEvents.emit(AuthNavigationEvent.NavigateToHome(authResult.userId))
                        }
                    }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            generalError = throwable.message ?: "Error al registrar la cuenta.",
                        )
                    }
                }
            )
        }
    }

    fun dismissExistingAccountDialog() {
        _uiState.update { it.copy(showExistingAccountDialog = false) }
    }

    fun dismissConfirmationDialog() {
        _uiState.update { it.copy(confirmationRequiredEmail = null) }
    }
}
