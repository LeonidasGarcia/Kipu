package com.kipu.app.feature.auth.domain

import com.kipu.app.feature.auth.data.remote.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RecoveryRequestTest {
    private val api = mockk<AuthApi>()
    private val useCase = RequestPasswordRecovery(api)

    @Test fun `existent and unknown valid email get equivalent confirmation`() = runTest {
        coEvery { api.recovery(any()) } returns ApiResponse.Success(Unit)
        assertTrue(useCase("existing@example.com").isSuccess)
        assertTrue(useCase("unknown@example.com").isSuccess)
    }

    @Test fun `server failure is never shown as a successful send`() = runTest {
        coEvery { api.recovery(any()) } returns ApiResponse.Error(503, null)
        assertTrue(useCase("user@example.com").isFailure)
    }

    @Test fun `server retry time is finite and preserved`() = runTest {
        coEvery { api.recovery(any()) } returns ApiResponse.Error(429, null, 60)
        val failure = useCase("user@example.com").exceptionOrNull() as RecoveryRequestException
        assertEquals(60, failure.retryAfterSeconds)
    }

    @Test fun `invalid email rejected locally without remote request`() = runTest {
        assertTrue(useCase("invalid").isFailure)
        coVerify(exactly = 0) { api.recovery(any()) }
    }
}
