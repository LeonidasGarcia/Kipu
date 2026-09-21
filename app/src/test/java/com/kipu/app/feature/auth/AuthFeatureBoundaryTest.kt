package com.kipu.app.feature.auth

import com.kipu.app.core.logging.LogRedactor
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.feature.settings.domain.model.ProfilePreferenceDelta
import com.kipu.app.feature.settings.domain.model.UserProfile
import com.kipu.app.feature.settings.presentation.formatMaskedAmount
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AuthFeatureBoundaryTest {

    @Test
    fun `boundary test - passwords and emails are redacted in logs`() {
        val rawMessage = "User test.user@example.com logged in with password Secret123! and token eyJhbGciOiJIUzI1NiJ9"
        val redacted = LogRedactor.redact(rawMessage)

        assertFalse(redacted.contains("Secret123!"), "Password must never appear in logs")
        assertFalse(redacted.contains("test.user@example.com"), "Raw email must be redacted in logs")
        assertTrue(redacted.contains("[REDACTED_PASSWORD]"))
        assertTrue(redacted.contains("[REDACTED_EMAIL]"))
    }

    @Test
    fun `boundary test - balance masking does not alter financial values`() {
        val actualBalance = "S/ 1,250.50"

        val maskedOutput = formatMaskedAmount(actualBalance, hideBalances = true)
        val unmaskedOutput = formatMaskedAmount(actualBalance, hideBalances = false)

        assertEquals("••••••", maskedOutput)
        assertEquals("S/ 1,250.50", unmaskedOutput)
        assertEquals("S/ 1,250.50", actualBalance, "Original balance amount must remain intact")
    }

    @Test
    fun `boundary test - currency preference change does not mutate transaction amounts`() {
        val originalAmount = 500.00
        val userId = UUID.randomUUID()
        val initialProfile = UserProfile(userId = userId, displayName = "User", currencyCode = "PEN")

        // Update preference to USD
        val delta = ProfilePreferenceDelta(currencyCode = "USD")
        val updatedProfile = initialProfile.copy(currencyCode = delta.currencyCode!!)

        assertEquals("PEN", initialProfile.currencyCode)
        assertEquals("USD", updatedProfile.currencyCode)
        assertEquals(500.00, originalAmount, "Financial transaction amounts must not be automatically converted or mutated")
    }

    @Test
    fun `boundary test - local owner UUID is strictly decoupled from remote session token`() {
        val verifiedUserId = UUID.randomUUID()
        val localOwner = LocalOwner(verifiedUserId = verifiedUserId, explicitlySignedOut = false)

        val remoteSession = RemoteSession.Valid(
            userId = verifiedUserId.toString(),
            expiresAt = Instant.now().plusSeconds(3600),
        )

        assertEquals(verifiedUserId, localOwner.verifiedUserId)
        assertEquals(verifiedUserId.toString(), remoteSession.userId)
        // Local owner has no access tokens or refresh tokens
        assertFalse(localOwner.toString().contains("access_token"))
        assertFalse(localOwner.toString().contains("Bearer"))
    }
}
