package com.kipu.app.feature.settings.presentation

import com.kipu.app.feature.settings.domain.model.SyncState
import com.kipu.app.feature.settings.domain.model.ThemeMode

data class SettingsUiState(
    val isLoading: Boolean = false,
    val displayName: String = "",
    val currencyCode: String = "PEN",
    val monthStart: Int = 1,
    val hideBalances: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val syncState: SyncState = SyncState.SYNCED,
    val monthStartError: String? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val isSaving: Boolean = false,
    val showSignOutDialog: Boolean = false,
    val pendingChangesCount: Int = 0,
)
