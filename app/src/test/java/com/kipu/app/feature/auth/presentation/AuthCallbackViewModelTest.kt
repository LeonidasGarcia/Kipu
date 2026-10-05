package com.kipu.app.feature.auth.presentation

import com.kipu.app.feature.auth.data.RecoverySessionInstaller
import com.kipu.app.navigation.AUTH_RESET_PASSWORD_ROUTE
import com.kipu.app.navigation.DeepLinkResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthCallbackViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val installer = mockk<RecoverySessionInstaller>()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun `recreated activity does not reexchange a recovery code in progress`() = runTest {
        val retrieveUser = CompletableDeferred<Unit>()
        coEvery { installer.install(any()) } coAnswers { retrieveUser.await(); Result.success(Unit) }
        val viewModel = AuthCallbackViewModel(installer)
        viewModel.handle(DeepLinkResult.ResetPassword("https://kipu.app/auth/recovery?code=test"), null)
        runCurrent()
        assertTrue(viewModel.state.value.handledCallback)
        // Activity recreation keeps the ViewModel; the deep-link handler marks a replay consumed.
        viewModel.handle(DeepLinkResult.InvalidOrConsumed, null)
        runCurrent()
        retrieveUser.complete(Unit)
        advanceUntilIdle()
        assertEquals(AUTH_RESET_PASSWORD_ROUTE, viewModel.state.value.targetRoute)
        coVerify(exactly = 1) { installer.install(any()) }
        viewModel.consumeNavigation()
        assertNull(viewModel.state.value.targetRoute)
        assertTrue(viewModel.state.value.handledCallback)
    }

    @Test fun `callback cancels suspended restoration before session installation`() = runTest {
        val restore = launch { awaitCancellation() }
        coEvery { installer.install(any()) } coAnswers {
            assertTrue(restore.isCancelled)
            Result.success(Unit)
        }
        val viewModel = AuthCallbackViewModel(installer)
        viewModel.handle(DeepLinkResult.ResetPassword("https://kipu.app/auth/recovery?code=test"), restore)
        advanceUntilIdle()
        assertEquals(AUTH_RESET_PASSWORD_ROUTE, viewModel.state.value.targetRoute)
    }

    @Test fun `distinct callback waits for the active exchange without being discarded`() = runTest {
        val firstExchange = CompletableDeferred<Unit>()
        val first = "https://kipu.app/auth/recovery?code=first"
        val second = "https://kipu.app/auth/recovery?code=second"
        coEvery { installer.install(first) } coAnswers { firstExchange.await(); Result.success(Unit) }
        coEvery { installer.install(second) } returns Result.success(Unit)
        val viewModel = AuthCallbackViewModel(installer)
        viewModel.handle(DeepLinkResult.ResetPassword(first), null)
        runCurrent()
        viewModel.handle(DeepLinkResult.ResetPassword(first), null)
        viewModel.handle(DeepLinkResult.ResetPassword(second), null)
        runCurrent()
        coVerify(exactly = 0) { installer.install(second) }
        firstExchange.complete(Unit)
        advanceUntilIdle()
        coVerify(exactly = 1) { installer.install(first) }
        coVerify(exactly = 1) { installer.install(second) }
        assertEquals(AUTH_RESET_PASSWORD_ROUTE, viewModel.state.value.targetRoute)
    }
}
