package com.kipu.app.feature.debts.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DebtCommandHasherTest {
    @Test
    fun `sha256 is deterministic lowercase hex for canonical payload`() {
        assertEquals(
            "d69e117d691ca1233d28a7ec3d31d26889763844fa6a615fd3cb3c42f6fab262",
            DebtCommandHasher.sha256("amount=125;currency=PEN"),
        )
    }

    @Test
    fun `blank canonical payload is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            DebtCommandHasher.sha256(" ")
        }
    }
}
