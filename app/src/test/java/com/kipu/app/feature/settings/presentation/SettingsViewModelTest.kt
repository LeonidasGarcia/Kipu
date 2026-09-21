package com.kipu.app.feature.settings.presentation

import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.settings.data.sync.ProfileSyncScheduler
import com.kipu.app.feature.settings.domain.ProfilePreferencesRepository
import com.kipu.app.feature.settings.domain.model.ProfilePreferenceDelta
import com.kipu.app.feature.settings.domain.model.SyncState
import com.kipu.app.feature.settings.domain.model.ThemeMode
import com.kipu.app.feature.settings.domain.model.UserProfile
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val userId = UUID.randomUUID()

    private class FakeSessionCoordinator(override val currentOwner: LocalOwner?) : SessionCoordinator {
        override val remoteSession = MutableStateFlow(com.kipu.app.core.session.RemoteSession.Absent)
        override val localAccess = MutableStateFlow(com.kipu.app.core.session.LocalAccess.Unlocked(userId))
        override suspend fun setActiveOwner(userId: UUID) {}
        override suspend fun clearActiveOwner(explicit: Boolean) {}
        override fun updateRemoteSession(session: com.kipu.app.core.session.RemoteSession) {}
        override fun setLocalLocked(reason: String) {}
        override fun setLocalUnlocked() {}
        override fun notifyUserActivity() {}
    }

    private class FakeProfilePreferencesRepository : ProfilePreferencesRepository {
        val profileFlow = MutableStateFlow<UserProfile?>(null)
        var lastUpdatedDelta: ProfilePreferenceDelta? = null
        var syncPendingCalled = false

        override fun observeProfile(userId: UUID): Flow<UserProfile?> = profileFlow

        override suspend fun getProfile(userId: UUID): UserProfile? = profileFlow.value

        override suspend fun updatePreferences(userId: UUID, delta: ProfilePreferenceDelta): Result<UserProfile> {
            lastUpdatedDelta = delta
            val current = profileFlow.value ?: UserProfile(userId = userId, displayName = "")
            val updated = current.copy(
                displayName = delta.displayName ?: current.displayName,
                currencyCode = delta.currencyCode ?: current.currencyCode,
                monthStart = delta.monthStart ?: current.monthStart,
                hideBalances = delta.hideBalances ?: current.hideBalances,
                themeMode = delta.themeMode ?: current.themeMode,
                syncState = SyncState.PENDING,
            )
            profileFlow.value = updated
            return Result.success(updated)
        }

        override suspend fun setHideBalances(userId: UUID, hideBalances: Boolean): Result<UserProfile> {
            return updatePreferences(userId, ProfilePreferenceDelta(hideBalances = hideBalances))
        }

        override suspend fun syncPendingPreferences(userId: UUID): Result<Unit> {
            syncPendingCalled = true
            return Result.success(Unit)
        }

        override suspend fun refreshProfile(userId: UUID): Result<UserProfile> {
            return Result.success(profileFlow.value ?: UserProfile(userId = userId, displayName = ""))
        }
    }

    private class FakeProfileSyncScheduler : ProfileSyncScheduler {
        var scheduledUserId: UUID? = null
        override fun scheduleSync(userId: UUID) {
            scheduledUserId = userId
        }
    }

    @Test
    fun `initial load reflects user profile from repository`() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        try {
            val repository = FakeProfilePreferencesRepository()
            repository.profileFlow.value = UserProfile(
                userId = userId,
                displayName = "María",
                currencyCode = "PEN",
                monthStart = 15,
                hideBalances = false,
                themeMode = ThemeMode.DARK,
            )
            val coordinator = FakeSessionCoordinator(LocalOwner(userId, false))
            val viewModel = SettingsViewModel(
                repository = repository,
                sessionCoordinator = coordinator,
                profileSyncScheduler = FakeProfileSyncScheduler(),
            )

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertEquals("María", state.displayName)
            assertEquals("PEN", state.currencyCode)
            assertEquals(15, state.monthStart)
            assertEquals(ThemeMode.DARK, state.themeMode)
            assertFalse(state.hideBalances)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `validating month start accepts 1 to 28 and rejects out of bounds`() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        try {
            val repository = FakeProfilePreferencesRepository()
            val coordinator = FakeSessionCoordinator(LocalOwner(userId, false))
            val viewModel = SettingsViewModel(
                repository = repository,
                sessionCoordinator = coordinator,
                profileSyncScheduler = FakeProfileSyncScheduler(),
            )

            advanceUntilIdle()

            viewModel.onMonthStartChanged(15)
            assertNull(viewModel.uiState.value.monthStartError)

            viewModel.onMonthStartChanged(0)
            assertNotNull(viewModel.uiState.value.monthStartError)

            viewModel.onMonthStartChanged(29)
            assertNotNull(viewModel.uiState.value.monthStartError)

            viewModel.onMonthStartChanged(28)
            assertNull(viewModel.uiState.value.monthStartError)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `toggling hide balances updates state immediately`() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        try {
            val repository = FakeProfilePreferencesRepository()
            repository.profileFlow.value = UserProfile(userId = userId, displayName = "Test", hideBalances = false)
            val coordinator = FakeSessionCoordinator(LocalOwner(userId, false))
            val scheduler = FakeProfileSyncScheduler()
            val viewModel = SettingsViewModel(
                repository = repository,
                sessionCoordinator = coordinator,
                profileSyncScheduler = scheduler,
            )

            advanceUntilIdle()

            viewModel.toggleHideBalances()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.hideBalances)
            assertEquals(true, repository.lastUpdatedDelta?.hideBalances)
            assertEquals(userId, scheduler.scheduledUserId)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
