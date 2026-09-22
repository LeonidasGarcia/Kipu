package com.kipu.app.feature.settings.presentation

data class BiometricSettingsUiState(
    val isLoading: Boolean = false,
    val isLocalUnlockEnabled: Boolean = false,
    val hasBiometrics: Boolean = false,
    val hasDeviceCredential: Boolean = false,
    val canAuthenticate: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)
