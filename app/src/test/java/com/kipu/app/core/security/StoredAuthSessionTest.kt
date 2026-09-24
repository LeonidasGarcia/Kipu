package com.kipu.app.core.security

import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredAuthSessionTest {
    private val userId = "11111111-1111-1111-1111-111111111111"

    @Test
    fun `restored token uses absolute expiry and rejects legacy relative expiry`() {
        val now = Instant.ofEpochSecond(1_000_000)
        val stored = StoredAuthSession("access", "refresh", 3600, "bearer", userId, now.epochSecond + 120)
        val decoded = StoredAuthSession.parse(StoredAuthSession.encode(stored))!!

        assertTrue(decoded.hasValidAccessToken(now))
        assertFalse(decoded.hasValidAccessToken(now.plusSeconds(91)))
        assertFalse(stored.copy(expiresAtEpochSeconds = null).hasValidAccessToken(now))
    }

    @Test
    fun `malformed owner is rejected before restoring local access`() {
        val stored = StoredAuthSession("access", "refresh", 3600, "bearer", "not-a-uuid", 1_003_600)
        assertNull(StoredAuthSession.parse(StoredAuthSession.encode(stored)))
    }
}
