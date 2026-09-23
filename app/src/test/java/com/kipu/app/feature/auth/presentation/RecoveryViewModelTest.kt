package com.kipu.app.feature.auth.presentation

import com.kipu.app.feature.auth.domain.CompletePasswordReset
import com.kipu.app.feature.auth.domain.RequestPasswordRecovery
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecoveryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val dummyClient: SupabaseClient = createSupabaseClient("https://dummy.supabase.co", "dummy-key") {}
    private var recoveryResult: Result<Unit> = Result.success(Unit)

    private val fakeRequestRecovery = object : RequestPasswordRecovery(
        authApi = com.kipu.app.feature.auth.data.remote.AuthApi(io.ktor.client.HttpClient()),
    ) {
        override suspend fun invoke(email: String): Result<Unit> = recoveryResult
    }

    private var fakeResetResult: Result<Unit> = Result.success(Unit)
    private val fakeCompleteReset = object : CompletePasswordReset(
        supabaseClient = dummyClient,
    ) {
        override suspend fun invoke(newPassword: String): Result<Unit> = fakeResetResult
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `recovery request with invalid email sets email error`() {
        val viewModel = RecoveryViewModel(fakeRequestRecovery, fakeCompleteReset)
        viewModel.onEmailChanged("not-an-email")
        viewModel.submitRecoveryRequest()

        assertNotNull(viewModel.uiState.value.emailError)
    }

    @Test
    fun `recovery request with valid email sets request accepted neutrally`() = runTest {
        val viewModel = RecoveryViewModel(fakeRequestRecovery, fakeCompleteReset)
        viewModel.onEmailChanged("user@example.com")
        viewModel.submitRecoveryRequest()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isRequestAccepted)
    }

    @Test
    fun `recovery network failure is visible and can be retried`() = runTest {
        recoveryResult = Result.failure(IllegalStateException("Se requiere conexión a internet."))
        val viewModel = RecoveryViewModel(fakeRequestRecovery, fakeCompleteReset)
        viewModel.onEmailChanged("user@example.com")

        viewModel.submitRecoveryRequest()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isRequestAccepted)
        assertEquals("Se requiere conexión a internet.", viewModel.uiState.value.errorMessage)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `new password validation rejects short password`() {
        val viewModel = RecoveryViewModel(fakeRequestRecovery, fakeCompleteReset)
        viewModel.onNewPasswordChanged("Short1")
        viewModel.submitNewPassword()

        assertNotNull(viewModel.uiState.value.passwordError)
    }
}
