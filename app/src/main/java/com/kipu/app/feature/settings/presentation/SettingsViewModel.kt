package com.kipu.app.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.session.PendingChangesRepository
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.auth.domain.AuthRepository
import com.kipu.app.feature.settings.data.sync.ProfileSyncScheduler
import com.kipu.app.feature.settings.domain.ProfilePreferencesRepository
import com.kipu.app.feature.settings.domain.model.ProfilePreferenceDelta
import com.kipu.app.feature.settings.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ProfilePreferencesRepository,
    private val sessionCoordinator: SessionCoordinator,
    private val profileSyncScheduler: ProfileSyncScheduler,
    private val authRepository: AuthRepository,
    private val pendingChangesRepository: PendingChangesRepository,
) : ViewModel() {

    constructor(
        repository: ProfilePreferencesRepository,
        sessionCoordinator: SessionCoordinator,
        profileSyncScheduler: ProfileSyncScheduler,
    ) : this(
        repository = repository,
        sessionCoordinator = sessionCoordinator,
        profileSyncScheduler = profileSyncScheduler,
        authRepository = object : AuthRepository {
            override val cooldownState = kotlinx.coroutines.flow.MutableStateFlow(com.kipu.app.feature.auth.domain.model.CooldownState(0))
            override suspend fun register(credentials: com.kipu.app.feature.auth.domain.model.AuthCredentials) = Result.success(com.kipu.app.feature.auth.domain.model.AuthResult.Success("test"))
            override suspend fun signIn(credentials: com.kipu.app.feature.auth.domain.model.AuthCredentials) = Result.success(com.kipu.app.feature.auth.domain.model.AuthResult.Success("test"))
            override suspend fun signOut(explicit: Boolean): Result<Unit> = Result.success(Unit)
            override suspend fun restoreSession(): Result<com.kipu.app.feature.auth.domain.model.AuthResult?> = Result.success(null)
        },
        pendingChangesRepository = object : PendingChangesRepository {
            override suspend fun count(userId: UUID) = 0
            override suspend fun markWaitingForAuth(userId: UUID) = Unit
        },
    )

    private val _uiState = MutableStateFlow(SettingsUiState(isLoading = true))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val currentUserId: UUID?
        get() = sessionCoordinator.currentOwner?.verifiedUserId?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        }

    init {
        val userId = currentUserId
        if (userId != null) {
            viewModelScope.launch {
                repository.observeProfile(userId).collectLatest { profile ->
                    if (profile != null) {
                        _uiState.update { current ->
                            current.copy(
                                isLoading = false,
                                displayName = if (current.isSaving) current.displayName else profile.displayName,
                                currencyCode = if (current.isSaving) current.currencyCode else profile.currencyCode,
                                monthStart = if (current.isSaving) current.monthStart else profile.monthStart,
                                hideBalances = profile.hideBalances,
                                themeMode = if (current.isSaving) current.themeMode else profile.themeMode,
                                syncState = profile.syncState,
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
            }
            viewModelScope.launch {
                repository.refreshProfile(userId)
            }
        } else {
            _uiState.update { it.copy(isLoading = false, errorMessage = "No hay una sesión de usuario activa") }
        }
    }

    fun onDisplayNameChanged(name: String) {
        _uiState.update { it.copy(displayName = name) }
    }

    fun onCurrencyCodeChanged(code: String) {
        val sanitized = code.trim().uppercase().take(3)
        _uiState.update { it.copy(currencyCode = sanitized) }
    }

    fun onMonthStartChanged(day: Int) {
        val error = if (day in 1..28) null else "El día de inicio de mes debe estar entre 1 y 28"
        _uiState.update { it.copy(monthStart = day, monthStartError = error) }
    }

    fun onThemeModeChanged(mode: ThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun toggleHideBalances() {
        val userId = currentUserId ?: return
        val currentHide = _uiState.value.hideBalances
        val target = !currentHide
        viewModelScope.launch {
            repository.setHideBalances(userId, target)
            profileSyncScheduler.scheduleSync(userId)
        }
    }

    fun savePreferences() {
        val userId = currentUserId ?: run {
            _uiState.update { it.copy(errorMessage = "No hay usuario activo para guardar") }
            return
        }

        val state = _uiState.value
        if (state.monthStart !in 1..28) {
            _uiState.update { it.copy(monthStartError = "El día de inicio de mes debe estar entre 1 y 28") }
            return
        }
        if (state.currencyCode.length != 3) {
            _uiState.update { it.copy(errorMessage = "El código de moneda debe tener exactamente 3 letras") }
            return
        }

        _uiState.update { it.copy(isSaving = true, errorMessage = null, infoMessage = null) }
        viewModelScope.launch {
            val delta = ProfilePreferenceDelta(
                displayName = state.displayName,
                currencyCode = state.currencyCode,
                monthStart = state.monthStart,
                themeMode = state.themeMode,
            )
            val result = repository.updatePreferences(userId, delta)
            if (result.isSuccess) {
                profileSyncScheduler.scheduleSync(userId)
                _uiState.update { it.copy(isSaving = false, infoMessage = "Preferencias guardadas correctamente") }
            } else {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Error al guardar preferencias",
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    fun requestSignOut() {
        val userId = currentUserId ?: return
        viewModelScope.launch {
            val count = runCatching { pendingChangesRepository.count(userId) }.getOrDefault(0)
            _uiState.update { it.copy(showSignOutDialog = true, pendingChangesCount = count) }
        }
    }

    fun dismissSignOutDialog() {
        _uiState.update { it.copy(showSignOutDialog = false) }
    }

    fun confirmSignOut(onSignOutComplete: () -> Unit) {
        _uiState.update { it.copy(showSignOutDialog = false) }
        viewModelScope.launch {
            runCatching { authRepository.signOut(explicit = true) }
            sessionCoordinator.clearActiveOwner(explicit = true)
            onSignOutComplete()
        }
    }
}
