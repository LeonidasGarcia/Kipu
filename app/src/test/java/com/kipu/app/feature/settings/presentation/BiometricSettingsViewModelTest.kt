package com.kipu.app.feature.settings.presentation

import androidx.fragment.app.FragmentActivity
import com.kipu.app.core.security.LocalAuthenticatorGateway
import com.kipu.app.core.security.LocalLockCoordinator
import com.kipu.app.core.security.model.LocalAuthenticatorCapability
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.settings.data.local.DeviceAccountSettingsDao
import com.kipu.app.feature.settings.data.local.DeviceAccountSettingsEntity
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
class BiometricSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val userId = UUID.randomUUID()

    private class FakeDeviceDao : DeviceAccountSettingsDao {
        var entity: DeviceAccountSettingsEntity? = null
        override suspend fun findSettings(userId: UUID): DeviceAccountSettingsEntity? = entity
        override fun observeSettings(userId: UUID): Flow<DeviceAccountSettingsEntity?> = MutableStateFlow(entity)
        override suspend fun putSettings(settings: DeviceAccountSettingsEntity) {
            entity = settings
        }
        override suspend fun deleteSettings(userId: UUID) {
            entity = null
        }
    }

    private class FakeGateway(var canAuth: Boolean = true) : LocalAuthenticatorGateway {
        var authSuccess: Boolean = true

        override fun getCapability(): LocalAuthenticatorCapability = LocalAuthenticatorCapability(
            hasBiometrics = true,
            hasDeviceCredential = true,
            canAuthenticate = canAuth,
        )

        override fun authenticate(
            activity: FragmentActivity,
            title: String,
            subtitle: String,
            onResult: (Boolean, String?) -> Unit,
        ) {
            onResult(authSuccess, if (authSuccess) null else "Error de autenticación")
        }
    }

    private class FakeCoordinator(override val currentOwner: LocalOwner?) : SessionCoordinator {
        override val remoteSession = MutableStateFlow(com.kipu.app.core.session.RemoteSession.Absent)
        override val localAccess = MutableStateFlow(com.kipu.app.core.session.LocalAccess.Unlocked(UUID.randomUUID()))
        override suspend fun setActiveOwner(userId: UUID) {}
        override suspend fun clearActiveOwner(explicit: Boolean) {}
        override fun updateRemoteSession(session: com.kipu.app.core.session.RemoteSession) {}
        override fun setLocalLocked(reason: String) {}
        override fun setLocalUnlocked() {}
        override fun notifyUserActivity() {}
    }

    @Test
    fun `loadSettings reflects capability and stored preference`() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        try {
            val dao = FakeDeviceDao()
            dao.entity = DeviceAccountSettingsEntity(userId = userId, localUnlockEnabled = true)
            val gateway = FakeGateway(canAuth = true)
            val coordinator = FakeCoordinator(LocalOwner(userId, false))
            val lockCoordinator = LocalLockCoordinator(dao, coordinator, { 0L }, CoroutineScope(Dispatchers.Unconfined))

            val viewModel = BiometricSettingsViewModel(dao, lockCoordinator, gateway, coordinator)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertTrue(state.isLocalUnlockEnabled)
            assertTrue(state.canAuthenticate)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `attempting to enable when device cannot authenticate sets error message`() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        try {
            val dao = FakeDeviceDao()
            val gateway = FakeGateway(canAuth = false)
            val coordinator = FakeCoordinator(LocalOwner(userId, false))
            val lockCoordinator = LocalLockCoordinator(dao, coordinator, { 0L }, CoroutineScope(Dispatchers.Unconfined))

            val viewModel = BiometricSettingsViewModel(dao, lockCoordinator, gateway, coordinator)
            advanceUntilIdle()

            val activity = Mockito.mock(FragmentActivity::class.java)
            viewModel.toggleLocalUnlock(activity)
            advanceUntilIdle()

            assertNotNull(viewModel.uiState.value.errorMessage)
            assertFalse(viewModel.uiState.value.isLocalUnlockEnabled)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
