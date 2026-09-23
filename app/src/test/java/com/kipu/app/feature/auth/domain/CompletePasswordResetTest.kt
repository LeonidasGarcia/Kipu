package com.kipu.app.feature.auth.domain

import com.kipu.app.feature.auth.data.RecoverySessionInstaller
import io.github.jan.supabase.SupabaseClient
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletePasswordResetTest {
    @Test
    fun `password cannot change without a verified recovery session`() = runTest {
        val installer = mockk<RecoverySessionInstaller>()
        every { installer.isReady() } returns false
        val useCase = CompletePasswordReset(mockk<SupabaseClient>(), installer)

        val result = useCase("ValidPassword123!")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("enlace de recuperación") == true)
    }
}
