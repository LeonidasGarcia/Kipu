package com.kipu.app.core.security

import com.kipu.app.core.security.model.LocalLockState
import com.kipu.app.core.security.model.LockReason
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.settings.data.local.DeviceAccountSettingsDao
import com.kipu.app.feature.settings.data.local.DeviceAccountSettingsEntity
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

class LocalLockTest {

    private val userId = UUID.randomUUID()

    private class FakeDeviceSettingsDao : DeviceAccountSettingsDao {
        var settings: DeviceAccountSettingsEntity? = null

        override suspend fun findSettings(userId: UUID): DeviceAccountSettingsEntity? = settings
        override fun observeSettings(userId: UUID): Flow<DeviceAccountSettingsEntity?> = MutableStateFlow(settings)
        override suspend fun putSettings(settings: DeviceAccountSettingsEntity) {
            this.settings = settings
        }
        override suspend fun deleteSettings(userId: UUID) {
            settings = null
        }
    }

    private class FakeCoordinator(override val currentOwner: LocalOwner?) : SessionCoordinator {
        override val remoteSession = MutableStateFlow<com.kipu.app.core.session.RemoteSession>(com.kipu.app.core.session.RemoteSession.Absent)
        override val localAccess = MutableStateFlow<com.kipu.app.core.session.LocalAccess>(
            currentOwner?.let { com.kipu.app.core.session.LocalAccess.Available(it.verifiedUserId, com.kipu.app.core.session.RemoteSession.Absent) }
                ?: com.kipu.app.core.session.LocalAccess.NoOwner
        )
        override suspend fun setActiveOwner(userId: String) {}
        override suspend fun clearActiveOwner(explicit: Boolean) {}
        override suspend fun updateRemoteSession(session: com.kipu.app.core.session.RemoteSession) {}
        override suspend fun updateLockState(isLocked: Boolean, reason: String) {}
    }

    @Test
    fun `when disabled, backgrounding and foregrounding keeps state DISABLED`() = runTest {
        var currentTime = 100_000L
        val dao = FakeDeviceSettingsDao()
        val coordinator = FakeCoordinator(LocalOwner(userId.toString(), false))
        val lockCoordinator = LocalLockCoordinator(
            dao = dao,
            sessionCoordinator = coordinator,
            monotonicClock = { currentTime },
            scope = CoroutineScope(Dispatchers.Unconfined),
        )

        assertEquals(LocalLockState.DISABLED, lockCoordinator.lockState.value)

        lockCoordinator.onAppBackgrounded()
        currentTime += 120_000L // 2 minutes later
        lockCoordinator.onAppForegrounded()

        assertEquals(LocalLockState.DISABLED, lockCoordinator.lockState.value)
    }

    @Test
    fun `backgrounding for 59s does not lock, backgrounding for 60s locks UI`() = runTest {
        var currentTime = 100_000L
        val dao = FakeDeviceSettingsDao()
        val coordinator = FakeCoordinator(LocalOwner(userId.toString(), false))
        val lockCoordinator = LocalLockCoordinator(
            dao = dao,
            sessionCoordinator = coordinator,
            monotonicClock = { currentTime },
            scope = CoroutineScope(Dispatchers.Unconfined),
        )

        lockCoordinator.setLocalUnlockEnabled(userId, true)
        assertEquals(LocalLockState.UNLOCKED, lockCoordinator.lockState.value)

        // Test 59 seconds: should not lock
        lockCoordinator.onAppBackgrounded()
        currentTime += 59_000L
        lockCoordinator.onAppForegrounded()
        assertEquals(LocalLockState.UNLOCKED, lockCoordinator.lockState.value)

        // Test 60 seconds: should lock
        lockCoordinator.onAppBackgrounded()
        currentTime += 60_000L
        lockCoordinator.onAppForegrounded()
        assertEquals(LocalLockState.LOCKED, lockCoordinator.lockState.value)
        assertEquals(LockReason.BACKGROUND_TIMEOUT, lockCoordinator.lastLockReason.value)

        // Unlock resets to UNLOCKED
        lockCoordinator.unlock()
        assertEquals(LocalLockState.UNLOCKED, lockCoordinator.lockState.value)
    }
}
