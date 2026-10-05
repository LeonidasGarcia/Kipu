package com.kipu.app.feature.auth.presentation

import com.kipu.app.feature.auth.domain.CompletePasswordReset
import com.kipu.app.feature.auth.domain.RequestPasswordRecovery
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.mockk.mockk
import com.kipu.app.feature.auth.data.RecoverySessionInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
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
    private var requests = 0

    private val fakeRequestRecovery = object : RequestPasswordRecovery(
        authApi = com.kipu.app.feature.auth.data.remote.AuthApi(io.ktor.client.HttpClient()),
    ) {
        override suspend fun invoke(email: String): Result<Unit> { requests++; return recoveryResult }
    }

    private var fakeResetResult: Result<Unit> = Result.success(Unit)
    private val fakeInstaller = mockk<RecoverySessionInstaller>(relaxed = true)
    private val fakeCompleteReset = object : CompletePasswordReset(
        supabaseClient = dummyClient,
        recoverySessionInstaller = fakeInstaller,
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
        val viewModel = RecoveryViewModel(fakeRequestRecovery, fakeCompleteReset, fakeInstaller)
        viewModel.onEmailChanged("not-an-email")
        viewModel.submitRecoveryRequest()

        assertNotNull(viewModel.uiState.value.emailError)
    }

    @Test
    fun `recovery request with valid email sets request accepted neutrally`() = runTest {
        val viewModel = RecoveryViewModel(fakeRequestRecovery, fakeCompleteReset, fakeInstaller)
        viewModel.onEmailChanged("user@example.com")
        viewModel.submitRecoveryRequest()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isRequestAccepted)
    }

    @Test
    fun `recovery network failure is visible and can be retried`() = runTest {
        recoveryResult = Result.failure(IllegalStateException("Se requiere conexión a internet."))
        val viewModel = RecoveryViewModel(fakeRequestRecovery, fakeCompleteReset, fakeInstaller)
        viewModel.onEmailChanged("user@example.com")

        viewModel.submitRecoveryRequest()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isRequestAccepted)
        assertEquals("Se requiere conexión a internet.", viewModel.uiState.value.errorMessage)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `new password validation rejects short password`() {
        val viewModel = RecoveryViewModel(fakeRequestRecovery, fakeCompleteReset, fakeInstaller)
        viewModel.onNewPasswordChanged("Short1")
        viewModel.submitNewPassword()

        assertNotNull(viewModel.uiState.value.passwordError)
    }

    @Test fun `recovery prevents duplicate sends and allows resend after cooldown`() = runTest {
        val viewModel = RecoveryViewModel(fakeRequestRecovery, fakeCompleteReset, fakeInstaller)
        viewModel.onEmailChanged("user@example.com")
        viewModel.submitRecoveryRequest()
        assertTrue(viewModel.uiState.value.isLoading)
        viewModel.submitRecoveryRequest()
        runCurrent()
        assertTrue(viewModel.uiState.value.isRequestAccepted)
        assertEquals(45, viewModel.uiState.value.resendSeconds)
        viewModel.submitRecoveryRequest()
        assertEquals(1, requests)
        advanceUntilIdle()
        viewModel.submitRecoveryRequest()
        advanceUntilIdle()
        assertEquals(2, requests)
    }

    @Test fun `accepted recovery checkpoint restores without saving passwords`() = runTest {
        val handle = androidx.lifecycle.SavedStateHandle()
        val first = RecoveryViewModel(fakeRequestRecovery, fakeCompleteReset, fakeInstaller, handle)
        first.onEmailChanged("user@example.com")
        first.submitRecoveryRequest()
        advanceUntilIdle()
        val second = RecoveryViewModel(fakeRequestRecovery, fakeCompleteReset, fakeInstaller, handle)
        assertTrue(second.uiState.value.isRequestAccepted)
        assertEquals("user@example.com", second.uiState.value.submittedEmail)
        assertEquals("", second.uiState.value.newPassword)
    }

    @Test fun `failed resend preserves accepted step`() = runTest {
        val viewModel = RecoveryViewModel(fakeRequestRecovery, fakeCompleteReset, fakeInstaller)
        viewModel.onEmailChanged("user@example.com")
        viewModel.submitRecoveryRequest()
        advanceUntilIdle()
        recoveryResult = Result.failure(Exception("Se requiere conexión a internet."))
        viewModel.submitRecoveryRequest()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isRequestAccepted)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }
}
