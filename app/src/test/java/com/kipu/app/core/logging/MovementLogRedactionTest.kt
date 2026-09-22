package com.kipu.app.core.logging

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementLogRedactionTest {

    @Test
    fun `redacts bearer tokens and jwt from log messages`() {
        val message = "Syncing transaction with Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.doNotLeakThis"
        val redacted = LogRedactor.redact(message)

        assertFalse("Token must not be present in output", redacted.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"))
        assertTrue("Expected redacted token indicator", redacted.contains("[REDACTED_"))
    }

    @Test
    fun `redacts user email from log messages`() {
        val message = "Error syncing movements for user test.account@gmail.com"
        val redacted = LogRedactor.redact(message)

        assertFalse("Raw email must not be present in output", redacted.contains("test.account@gmail.com"))
        assertTrue("Expected redacted email indicator", redacted.contains("[REDACTED_EMAIL]"))
    }
}
