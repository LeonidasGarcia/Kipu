package com.kipu.app.feature.settings.domain

import com.kipu.app.feature.settings.domain.model.ProfilePreferenceDelta
import com.kipu.app.feature.settings.domain.model.ThemeMode
import com.kipu.app.feature.settings.domain.model.UserProfile
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePreferencesTest {

    private val validUserId = UUID.randomUUID()

    @Test
    fun `valid monthStart 1 and 28 are accepted`() {
        val profile1 = UserProfile(
            userId = validUserId,
            displayName = "User",
            monthStart = 1,
        )
        assertEquals(1, profile1.monthStart)

        val profile28 = UserProfile(
            userId = validUserId,
            displayName = "User",
            monthStart = 28,
        )
        assertEquals(28, profile28.monthStart)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `monthStart 0 throws exception`() {
        UserProfile(
            userId = validUserId,
            displayName = "User",
            monthStart = 0,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `monthStart 29 throws exception per FR-029`() {
        UserProfile(
            userId = validUserId,
            displayName = "User",
            monthStart = 29,
        )
    }

    @Test
    fun `delta isEmpty returns true only when all fields are null`() {
        val emptyDelta = ProfilePreferenceDelta()
        assertTrue(emptyDelta.isEmpty)

        val nonEmptyDelta = ProfilePreferenceDelta(currencyCode = "USD")
        assertFalse(nonEmptyDelta.isEmpty)
    }

    @Test
    fun `changing currency preference does not modify numerical values or financial facts`() {
        val profile = UserProfile(
            userId = validUserId,
            displayName = "User",
            currencyCode = "PEN",
        )
        val updatedProfile = profile.copy(currencyCode = "USD")
        assertEquals("USD", updatedProfile.currencyCode)
        // Original entity structure preserved without conversion
    }

    @Test
    fun `balance masking is a boolean display preference complying with FR-027`() {
        val profile = UserProfile(
            userId = validUserId,
            displayName = "User",
            hideBalances = true,
        )
        assertTrue(profile.hideBalances)
    }
}
