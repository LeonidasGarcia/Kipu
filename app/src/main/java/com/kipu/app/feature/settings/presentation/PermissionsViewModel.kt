package com.kipu.app.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.settings.data.local.AccountSourceConsentEntity
import com.kipu.app.feature.settings.data.local.PermissionConsentDao
import com.kipu.app.feature.settings.domain.PermissionSourceGateway
import com.kipu.app.feature.settings.domain.model.CapabilityState
import com.kipu.app.feature.settings.domain.model.ConsentState
import com.kipu.app.feature.settings.domain.model.DeviceAuthorization
import com.kipu.app.feature.settings.domain.model.PermissionSource
import com.kipu.app.feature.settings.domain.model.ProcessingAuthorization
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val dao: PermissionConsentDao,
    private val gateway: PermissionSourceGateway,
    private val sessionCoordinator: SessionCoordinator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PermissionsUiState(isLoading = true))
    val uiState: StateFlow<PermissionsUiState> = _uiState.asStateFlow()

    private val currentUserId: UUID?
        get() = sessionCoordinator.currentOwner?.verifiedUserId?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        }

    init {
        refresh()
    }

    fun refresh() {
        val userId = currentUserId
        if (userId == null) {
            _uiState.update { it.copy(isLoading = false, message = "No hay usuario activo") }
            return
        }

        viewModelScope.launch {
            gateway.refreshAndSaveDeviceAuthorizations()
            dao.observeAccountConsents(userId).collectLatest { consents ->
                val consentMap = consents.associateBy { it.source }
                val isPremium = _uiState.value.isPremiumUser

                val items = PermissionSource.entries.map { source ->
                    val deviceAuth = gateway.checkDeviceAuthorization(source)
                    val consentEntity = consentMap[source.name]

                    val consentState = consentEntity?.let {
                        runCatching { ConsentState.valueOf(it.consentState) }.getOrDefault(ConsentState.NOT_EXPLAINED)
                    } ?: ConsentState.NOT_EXPLAINED

                    val capabilityState = consentEntity?.let {
                        runCatching { CapabilityState.valueOf(it.capabilityState) }.getOrDefault(CapabilityState.UNKNOWN)
                    } ?: CapabilityState.UNKNOWN

                    val accountConsentModel = com.kipu.app.feature.settings.domain.model.AccountConsent(
                        userId = userId,
                        source = source,
                        consentState = consentState,
                        capabilityState = capabilityState,
                    )

                    val authEval = ProcessingAuthorization.evaluate(
                        source = source,
                        deviceAuth = deviceAuth,
                        accountConsent = accountConsentModel,
                        isPremiumUser = isPremium,
                    )

                    when (source) {
                        PermissionSource.OWN_NOTIFICATIONS -> PermissionItemUiState(
                            source = source,
                            title = "Notificaciones de Kipu",
                            description = "Recordatorios de presupuesto, vencimientos de deudas y balances.",
                            rationale = "Para enviarte alertas oportunas sobre tus finanzas personales, necesitamos permiso para mostrar notificaciones en este dispositivo. Puedes activarlo o desactivarlo en cualquier momento.",
                            deviceAuth = deviceAuth,
                            consentState = consentState,
                            capabilityState = capabilityState,
                            isProcessingAuthorized = authEval.isAuthorized,
                            requiresPremium = false,
                        )
                        PermissionSource.OTHER_APP_NOTIFICATION_CONTENT -> PermissionItemUiState(
                            source = source,
                            title = "Lectura de Notificaciones Bancarias",
                            description = "Sugerir transacciones automáticamente desde notificaciones bancarias.",
                            rationale = "Kipu puede interpretar únicamente las alertas emitidas por aplicaciones bancarias compatibles para sugerir borradores de gastos. Ningún dato sensible sale de tu dispositivo. Esta función requiere el plan Premium.",
                            deviceAuth = deviceAuth,
                            consentState = consentState,
                            capabilityState = capabilityState,
                            isProcessingAuthorized = authEval.isAuthorized,
                            requiresPremium = true,
                        )
                    }
                }

                _uiState.update { it.copy(isLoading = false, items = items) }
            }
        }
    }

    fun showRationale(source: PermissionSource) {
        _uiState.update { it.copy(showRationaleFor = source) }
    }

    fun dismissRationale() {
        _uiState.update { it.copy(showRationaleFor = null) }
    }

    fun grantConsent(source: PermissionSource) {
        val userId = currentUserId ?: return
        if (source == PermissionSource.OTHER_APP_NOTIFICATION_CONTENT && !_uiState.value.isPremiumUser) {
            _uiState.update {
                it.copy(
                    showRationaleFor = null,
                    message = "Esta fuente de automatización requiere una suscripción Premium activa.",
                )
            }
            return
        }

        viewModelScope.launch {
            dao.putAccountConsent(
                AccountSourceConsentEntity(
                    userId = userId,
                    source = source.name,
                    consentState = ConsentState.GRANTED.name,
                    capabilityState = CapabilityState.ALLOWED.name,
                    explanationVersion = 1,
                    updatedAt = Instant.now(),
                )
            )
            _uiState.update { it.copy(showRationaleFor = null) }
        }
    }

    fun revokeConsent(source: PermissionSource) {
        val userId = currentUserId ?: return
        viewModelScope.launch {
            dao.putAccountConsent(
                AccountSourceConsentEntity(
                    userId = userId,
                    source = source.name,
                    consentState = ConsentState.REVOKED.name,
                    capabilityState = CapabilityState.DENIED.name,
                    explanationVersion = 1,
                    updatedAt = Instant.now(),
                )
            )
        }
    }

    fun setPremiumUserForTesting(isPremium: Boolean) {
        _uiState.update { it.copy(isPremiumUser = isPremium) }
        refresh()
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
