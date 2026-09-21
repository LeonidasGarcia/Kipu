package com.kipu.app.feature.auth.presentation

import com.kipu.app.feature.auth.domain.AuthRepository
import com.kipu.app.feature.auth.domain.model.AuthCredentials
import com.kipu.app.feature.auth.domain.model.AuthResult
import com.kipu.app.feature.auth.domain.model.CooldownState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakeCooldownState = MutableStateFlow(CooldownState())

    private var fakeSignInResult: Result<AuthResult> = Result.success(AuthResult.Success("user-123"))
    private var fakeRegisterResult: Result<AuthResult> = Result.success(AuthResult.ConfirmationRequired("test@example.com"))
    private var lastCapturedCredentials: AuthCredentials? = null

    private val fakeRepository = object : AuthRepository {
        override val cooldownState: StateFlow<CooldownState> = fakeCooldownState

        override suspend fun register(credentials: AuthCredentials): Result<AuthResult> {
            lastCapturedCredentials = credentials
            return fakeRegisterResult
        }

        override suspend fun signIn(credentials: AuthCredentials): Result<AuthResult> {
            lastCapturedCredentials = credentials
            return fakeSignInResult
        }

        override suspend fun signOut(explicit: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun restoreSession(): Result<AuthResult?> = Result.success(null)
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
    fun `initial state is empty and valid`() {
        val viewModel = AuthViewModel(fakeRepository)
        val state = viewModel.uiState.value
        assertEquals("", state.email)
        assertEquals("", state.password)
        assertNull(state.emailError)
        assertNull(state.passwordError)
        assertFalse(state.isLoading)
    }

    @Test
    fun `login with invalid email sets email error`() {
        val viewModel = AuthViewModel(fakeRepository)
        viewModel.onEmailChanged("invalid-email")
        viewModel.onPasswordChanged("Password123")
        viewModel.login()

        val state = viewModel.uiState.value
        assertNotNull(state.emailError)
        assertNull(lastCapturedCredentials)
    }

    @Test
    fun `login with invalid password sets password error`() {
        val viewModel = AuthViewModel(fakeRepository)
        viewModel.onEmailChanged("test@example.com")
        viewModel.onPasswordChanged("123")
        viewModel.login()

        val state = viewModel.uiState.value
        assertNotNull(state.passwordError)
        assertNull(lastCapturedCredentials)
    }

    @Test
    fun `login clears password from UI state immediately for security`() = runTest {
        val viewModel = AuthViewModel(fakeRepository)
        viewModel.onEmailChanged("test@example.com")
        viewModel.onPasswordChanged("Password123")
        viewModel.login()

        // Password should be wiped from state even while loading
        assertEquals("", viewModel.uiState.value.password)
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.password)
        assertEquals("test@example.com", lastCapturedCredentials?.email)
        assertEquals("Password123", lastCapturedCredentials?.password)
    }

    @Test
    fun `login success emits NavigateToHome event`() = runTest {
        fakeSignInResult = Result.success(AuthResult.Success("user-456"))
        val viewModel = AuthViewModel(fakeRepository)
        viewModel.onEmailChanged("test@example.com")
        viewModel.onPasswordChanged("Password123")

        var emittedEvent: AuthNavigationEvent? = null
        val job = launch {
            emittedEvent = viewModel.navigationEvents.first()
        }

        viewModel.login()
        advanceUntilIdle()

        assertTrue(emittedEvent is AuthNavigationEvent.NavigateToHome)
        assertEquals("user-456", (emittedEvent as AuthNavigationEvent.NavigateToHome).userId)
        job.cancel()
    }

    @Test
    fun `register with existing email sets existing account dialog per FR-051`() = runTest {
        fakeRegisterResult = Result.success(AuthResult.AccountAlreadyExists)
        val viewModel = AuthViewModel(fakeRepository)
        viewModel.onEmailChanged("existing@example.com")
        viewModel.onPasswordChanged("Password123")

        viewModel.register()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showExistingAccountDialog)
    }

    @Test
    fun `cooldown state updates reflect in uiState`() = runTest {
        val viewModel = AuthViewModel(fakeRepository)
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.cooldownSeconds)

        fakeCooldownState.value = CooldownState(retryAfterSeconds = 25)
        advanceUntilIdle()
        assertEquals(25, viewModel.uiState.value.cooldownSeconds)
    }
}
