package com.kipu.app.feature.settings.presentation

import com.kipu.app.feature.settings.domain.model.CapabilityState
import com.kipu.app.feature.settings.domain.model.ConsentState
import com.kipu.app.feature.settings.domain.model.DeviceAuthorization
import com.kipu.app.feature.settings.domain.model.PermissionSource

data class PermissionItemUiState(
    val source: PermissionSource,
    val title: String,
    val description: String,
    val rationale: String,
    val deviceAuth: DeviceAuthorization = DeviceAuthorization.NOT_REQUESTED,
    val consentState: ConsentState = ConsentState.NOT_EXPLAINED,
    val capabilityState: CapabilityState = CapabilityState.UNKNOWN,
    val isProcessingAuthorized: Boolean = false,
    val requiresPremium: Boolean = false,
)

data class PermissionsUiState(
    val isLoading: Boolean = false,
    val isPremiumUser: Boolean = false,
    val items: List<PermissionItemUiState> = emptyList(),
    val showRationaleFor: PermissionSource? = null,
    val message: String? = null,
)
