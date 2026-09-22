package com.kipu.app.feature.settings.domain

import com.kipu.app.feature.settings.domain.model.AccountConsent
import com.kipu.app.feature.settings.domain.model.CapabilityState
import com.kipu.app.feature.settings.domain.model.ConsentState
import com.kipu.app.feature.settings.domain.model.DeviceAuthorization
import com.kipu.app.feature.settings.domain.model.PermissionSource
import com.kipu.app.feature.settings.domain.model.ProcessingAuthorization
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionSourcePolicyTest {

    private val userId = UUID.randomUUID()

    @Test
    fun `own notifications authorized when device and account consent are granted`() {
        val consent = AccountConsent(
            userId = userId,
            source = PermissionSource.OWN_NOTIFICATIONS,
            consentState = ConsentState.GRANTED,
            capabilityState = CapabilityState.ALLOWED,
        )

        val result = ProcessingAuthorization.evaluate(
            source = PermissionSource.OWN_NOTIFICATIONS,
            deviceAuth = DeviceAuthorization.GRANTED,
            accountConsent = consent,
            isPremiumUser = false,
        )

        assertTrue(result.isAuthorized)
    }

    @Test
    fun `own notifications rejected when device permission is denied`() {
        val consent = AccountConsent(
            userId = userId,
            source = PermissionSource.OWN_NOTIFICATIONS,
            consentState = ConsentState.GRANTED,
            capabilityState = CapabilityState.ALLOWED,
        )

        val result = ProcessingAuthorization.evaluate(
            source = PermissionSource.OWN_NOTIFICATIONS,
            deviceAuth = DeviceAuthorization.DENIED,
            accountConsent = consent,
            isPremiumUser = false,
        )

        assertFalse(result.isAuthorized)
        assertEquals("Permiso del dispositivo no otorgado", result.reasonNotAuthorized)
    }

    @Test
    fun `own notifications rejected when account consent is revoked`() {
        val consent = AccountConsent(
            userId = userId,
            source = PermissionSource.OWN_NOTIFICATIONS,
            consentState = ConsentState.REVOKED,
            capabilityState = CapabilityState.ALLOWED,
        )

        val result = ProcessingAuthorization.evaluate(
            source = PermissionSource.OWN_NOTIFICATIONS,
            deviceAuth = DeviceAuthorization.GRANTED,
            accountConsent = consent,
            isPremiumUser = false,
        )

        assertFalse(result.isAuthorized)
        assertEquals("Consentimiento de la cuenta no otorgado", result.reasonNotAuthorized)
    }

    @Test
    fun `notification listener requires premium tier even if device and consent granted`() {
        val consent = AccountConsent(
            userId = userId,
            source = PermissionSource.OTHER_APP_NOTIFICATION_CONTENT,
            consentState = ConsentState.GRANTED,
            capabilityState = CapabilityState.ALLOWED,
        )

        // Free tier
        val freeResult = ProcessingAuthorization.evaluate(
            source = PermissionSource.OTHER_APP_NOTIFICATION_CONTENT,
            deviceAuth = DeviceAuthorization.GRANTED,
            accountConsent = consent,
            isPremiumUser = false,
        )
        assertFalse(freeResult.isAuthorized)
        assertEquals("Requiere suscripción Premium para captura de notificaciones", freeResult.reasonNotAuthorized)

        // Premium tier
        val premiumResult = ProcessingAuthorization.evaluate(
            source = PermissionSource.OTHER_APP_NOTIFICATION_CONTENT,
            deviceAuth = DeviceAuthorization.GRANTED,
            accountConsent = consent,
            isPremiumUser = true,
        )
        assertTrue(premiumResult.isAuthorized)
    }
}
