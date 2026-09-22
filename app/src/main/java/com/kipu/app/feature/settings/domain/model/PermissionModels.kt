package com.kipu.app.feature.settings.domain.model

import java.time.Instant
import java.util.UUID

enum class PermissionSource {
    OWN_NOTIFICATIONS,
    OTHER_APP_NOTIFICATION_CONTENT,
}

enum class DeviceAuthorization {
    NOT_APPLICABLE,
    NOT_REQUESTED,
    GRANTED,
    DENIED,
    REVOKED,
    UNAVAILABLE,
}

enum class ConsentState {
    NOT_EXPLAINED,
    EXPLAINED,
    GRANTED,
    DENIED,
    REVOKED,
}

enum class CapabilityState {
    UNKNOWN,
    ALLOWED,
    DENIED,
}

data class InstallationPermission(
    val source: PermissionSource,
    val deviceAuthorization: DeviceAuthorization,
    val lastCheckedAt: Instant = Instant.now(),
)

data class AccountConsent(
    val userId: UUID,
    val source: PermissionSource,
    val consentState: ConsentState,
    val capabilityState: CapabilityState,
    val explanationVersion: Int = 1,
    val updatedAt: Instant = Instant.now(),
)

data class ProcessingAuthorization(
    val source: PermissionSource,
    val isAuthorized: Boolean,
    val reasonNotAuthorized: String? = null,
) {
    companion object {
        fun evaluate(
            source: PermissionSource,
            deviceAuth: DeviceAuthorization,
            accountConsent: AccountConsent?,
            isPremiumUser: Boolean = false,
        ): ProcessingAuthorization {
            if (deviceAuth != DeviceAuthorization.GRANTED) {
                return ProcessingAuthorization(
                    source = source,
                    isAuthorized = false,
                    reasonNotAuthorized = "Permiso del dispositivo no otorgado",
                )
            }

            if (accountConsent == null || accountConsent.consentState != ConsentState.GRANTED) {
                return ProcessingAuthorization(
                    source = source,
                    isAuthorized = false,
                    reasonNotAuthorized = "Consentimiento de la cuenta no otorgado",
                )
            }

            if (source == PermissionSource.OTHER_APP_NOTIFICATION_CONTENT) {
                if (!isPremiumUser || accountConsent.capabilityState == CapabilityState.DENIED) {
                    return ProcessingAuthorization(
                        source = source,
                        isAuthorized = false,
                        reasonNotAuthorized = "Requiere suscripción Premium para captura de notificaciones",
                    )
                }
            }

            return ProcessingAuthorization(source = source, isAuthorized = true)
        }
    }
}
