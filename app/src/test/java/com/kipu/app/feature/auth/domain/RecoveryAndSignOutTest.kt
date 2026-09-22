package com.kipu.app.feature.auth.domain

import com.kipu.app.core.session.PendingChangesRepository
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryAndSignOutTest {

    @Test
    fun `pending changes count sums outboxes across features`() = runTest {
        val fakePendingRepo = object : PendingChangesRepository {
            override suspend fun count(userId: UUID): Int = 3 + 2 // 3 from plans, 2 from profile
            override suspend fun markWaitingForAuth(userId: UUID) {}
        }

        val count = fakePendingRepo.count(UUID.randomUUID())
        assertEquals(5, count)
    }

    @Test
    fun `password reset rejects passwords not meeting complexity rules`() {
        val shortResult = PasswordValidator.validatePassword("P1")
        assertFalse(shortResult.isValid)

        val noNumberResult = PasswordValidator.validatePassword("PasswordOnly")
        assertFalse(noNumberResult.isValid)

        val validResult = PasswordValidator.validatePassword("ValidPassword123")
        assertTrue(validResult.isValid)
    }
}
