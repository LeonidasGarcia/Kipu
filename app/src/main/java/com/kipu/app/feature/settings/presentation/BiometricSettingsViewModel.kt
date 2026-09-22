package com.kipu.app.feature.settings.presentation

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.security.LocalAuthenticatorGateway
import com.kipu.app.core.security.LocalLockCoordinator
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.settings.data.local.DeviceAccountSettingsDao
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class BiometricSettingsViewModel @Inject constructor(
    private val dao: DeviceAccountSettingsDao,
    private val lockCoordinator: LocalLockCoordinator,
    private val gateway: LocalAuthenticatorGateway,
    private val sessionCoordinator: SessionCoordinator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BiometricSettingsUiState(isLoading = true))
    val uiState: StateFlow<BiometricSettingsUiState> = _uiState.asStateFlow()

    private val currentUserId: UUID?
        get() = sessionCoordinator.currentOwner?.verifiedUserId?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        }

    init {
        loadSettings()
    }

    fun loadSettings() {
        val userId = currentUserId
        val capability = gateway.getCapability()

        if (userId == null) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    hasBiometrics = capability.hasBiometrics,
                    hasDeviceCredential = capability.hasDeviceCredential,
                    canAuthenticate = capability.canAuthenticate,
                )
            }
            return
        }

        viewModelScope.launch {
            val settings = dao.findSettings(userId)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isLocalUnlockEnabled = settings?.localUnlockEnabled == true,
                    hasBiometrics = capability.hasBiometrics,
                    hasDeviceCredential = capability.hasDeviceCredential,
                    canAuthenticate = capability.canAuthenticate,
                )
            }
        }
    }

    fun toggleLocalUnlock(activity: FragmentActivity? = null) {
        val userId = currentUserId ?: return
        val currentEnabled = _uiState.value.isLocalUnlockEnabled

        if (currentEnabled) {
            viewModelScope.launch {
                lockCoordinator.setLocalUnlockEnabled(userId, false)
                _uiState.update {
                    it.copy(
                        isLocalUnlockEnabled = false,
                        infoMessage = "Desbloqueo biométrico desactivado",
                    )
                }
            }
        } else {
            if (!_uiState.value.canAuthenticate) {
                _uiState.update {
                    it.copy(errorMessage = "El dispositivo no tiene configurada biometría ni PIN/patrón de bloqueo")
                }
                return
            }

            val act = activity ?: return
            gateway.authenticate(
                activity = act,
                title = "Habilitar Desbloqueo Local",
                subtitle = "Confirma tu identidad para activar la protección de Kipu",
            ) { success, error ->
                if (success) {
                    viewModelScope.launch {
                        lockCoordinator.setLocalUnlockEnabled(userId, true)
                        _uiState.update {
                            it.copy(
                                isLocalUnlockEnabled = true,
                                infoMessage = "Desbloqueo local activado correctamente",
                            )
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(errorMessage = error ?: "Autenticación cancelada o fallida")
                    }
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }
}
