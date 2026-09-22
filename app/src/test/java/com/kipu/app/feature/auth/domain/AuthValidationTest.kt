package com.kipu.app.feature.auth.domain

import com.kipu.app.feature.auth.domain.model.CooldownState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidationTest {

    @Test
    fun `password shorter than 8 chars is invalid`() {
        val result = PasswordValidator.validatePassword("Ab1")
        assertFalse(result.isValid)
    }

    @Test
    fun `password longer than 72 chars is invalid`() {
        val longPassword = "A1" + "a".repeat(71)
        val result = PasswordValidator.validatePassword(longPassword)
        assertFalse(result.isValid)
    }

    @Test
    fun `password without letters is invalid`() {
        val result = PasswordValidator.validatePassword("12345678")
        assertFalse(result.isValid)
    }

    @Test
    fun `password without numbers is invalid`() {
        val result = PasswordValidator.validatePassword("abcdefgh")
        assertFalse(result.isValid)
    }

    @Test
    fun `password with 8 chars, letter and number is valid`() {
        val result = PasswordValidator.validatePassword("Passw0rd")
        assertTrue(result.isValid)
    }

    @Test
    fun `email validation rejects invalid formats`() {
        assertFalse(PasswordValidator.validateEmail("").isValid)
        assertFalse(PasswordValidator.validateEmail("plainaddress").isValid)
        assertFalse(PasswordValidator.validateEmail("@missinguser.com").isValid)
        assertFalse(PasswordValidator.validateEmail("user@.com").isValid)
        assertFalse(PasswordValidator.validateEmail("user@domain").isValid)
    }

    @Test
    fun `email validation accepts valid formats`() {
        assertTrue(PasswordValidator.validateEmail("user@example.com").isValid)
        assertTrue(PasswordValidator.validateEmail("user.name+tag@sub.domain.org").isValid)
    }

    @Test
    fun `email normalization lowercases and trims`() {
        val normalized = PasswordValidator.normalizeEmail("  User.Test@Example.COM  ")
        assertEquals("user.test@example.com", normalized)
    }

    @Test
    fun `cooldown state reports blocked status accurately`() {
        val activeCooldown = CooldownState(
            retryAfterSeconds = 15,
            blockedUntil = Instant.now().plusSeconds(15),
        )
        assertTrue(activeCooldown.isBlocked)

        val expiredCooldown = CooldownState(
            retryAfterSeconds = 0,
            blockedUntil = Instant.now().minusSeconds(1),
        )
        assertFalse(expiredCooldown.isBlocked)
    }
}
