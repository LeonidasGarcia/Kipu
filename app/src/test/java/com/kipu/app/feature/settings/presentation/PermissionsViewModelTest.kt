package com.kipu.app.feature.settings.presentation

import android.content.Intent
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.settings.data.local.AccountSourceConsentEntity
import com.kipu.app.feature.settings.data.local.InstallationPermissionStateEntity
import com.kipu.app.feature.settings.data.local.PermissionConsentDao
import com.kipu.app.feature.settings.domain.PermissionSourceGateway
import com.kipu.app.feature.settings.domain.model.DeviceAuthorization
import com.kipu.app.feature.settings.domain.model.PermissionSource
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
class PermissionsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val userId = UUID.randomUUID()

    private class FakePermissionDao : PermissionConsentDao {
        val installationState = mutableMapOf<String, InstallationPermissionStateEntity>()
        val accountConsents = MutableStateFlow<List<AccountSourceConsentEntity>>(emptyList())

        override suspend fun findInstallationPermission(source: String): InstallationPermissionStateEntity? =
            installationState[source]

        override fun observeInstallationPermissions(): Flow<List<InstallationPermissionStateEntity>> =
            MutableStateFlow(installationState.values.toList())

        override suspend fun putInstallationPermission(entity: InstallationPermissionStateEntity) {
            installationState[entity.source] = entity
        }

        override suspend fun findAccountConsent(userId: UUID, source: String): AccountSourceConsentEntity? =
            accountConsents.value.find { it.userId == userId && it.source == source }

        override fun observeAccountConsents(userId: UUID): Flow<List<AccountSourceConsentEntity>> =
            accountConsents

        override suspend fun putAccountConsent(entity: AccountSourceConsentEntity) {
            val list = accountConsents.value.filterNot { it.userId == entity.userId && it.source == entity.source }.toMutableList()
            list.add(entity)
            accountConsents.value = list
        }

        override suspend fun revokeAllAccountConsents(userId: UUID) {
            accountConsents.value = accountConsents.value.map {
                if (it.userId == userId) it.copy(consentState = "REVOKED") else it
            }
        }
    }

    private class FakeGateway : PermissionSourceGateway {
        var ownNotificationsAuth = DeviceAuthorization.GRANTED
        var listenerAuth = DeviceAuthorization.DENIED

        override fun checkDeviceAuthorization(source: PermissionSource): DeviceAuthorization {
            return when (source) {
                PermissionSource.OWN_NOTIFICATIONS -> ownNotificationsAuth
                PermissionSource.OTHER_APP_NOTIFICATION_CONTENT -> listenerAuth
            }
        }

        override fun createSettingsIntent(source: PermissionSource): Intent = Intent()

        override suspend fun refreshAndSaveDeviceAuthorizations(): Map<PermissionSource, DeviceAuthorization> {
            return mapOf(
                PermissionSource.OWN_NOTIFICATIONS to ownNotificationsAuth,
                PermissionSource.OTHER_APP_NOTIFICATION_CONTENT to listenerAuth,
            )
        }
    }

    private class FakeSessionCoordinator(override val currentOwner: LocalOwner?) : SessionCoordinator {
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
    fun `showRationale and dismissRationale toggle state`() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        try {
            val dao = FakePermissionDao()
            val gateway = FakeGateway()
            val coordinator = FakeSessionCoordinator(LocalOwner(userId, false))
            val viewModel = PermissionsViewModel(dao, gateway, coordinator)

            advanceUntilIdle()

            viewModel.showRationale(PermissionSource.OWN_NOTIFICATIONS)
            assertEquals(PermissionSource.OWN_NOTIFICATIONS, viewModel.uiState.value.showRationaleFor)

            viewModel.dismissRationale()
            assertNull(viewModel.uiState.value.showRationaleFor)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `granting notification listener on Free tier shows premium error and does not grant`() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        try {
            val dao = FakePermissionDao()
            val gateway = FakeGateway()
            val coordinator = FakeSessionCoordinator(LocalOwner(userId, false))
            val viewModel = PermissionsViewModel(dao, gateway, coordinator)

            advanceUntilIdle()

            viewModel.grantConsent(PermissionSource.OTHER_APP_NOTIFICATION_CONTENT)
            advanceUntilIdle()

            assertNotNull(viewModel.uiState.value.message)
            assertTrue(viewModel.uiState.value.message!!.contains("Premium"))
            val consent = dao.findAccountConsent(userId, PermissionSource.OTHER_APP_NOTIFICATION_CONTENT.name)
            assertNull(consent)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `granting and revoking own notifications updates dao and state`() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        try {
            val dao = FakePermissionDao()
            val gateway = FakeGateway()
            val coordinator = FakeSessionCoordinator(LocalOwner(userId, false))
            val viewModel = PermissionsViewModel(dao, gateway, coordinator)

            advanceUntilIdle()

            viewModel.grantConsent(PermissionSource.OWN_NOTIFICATIONS)
            advanceUntilIdle()

            val consent = dao.findAccountConsent(userId, PermissionSource.OWN_NOTIFICATIONS.name)
            assertNotNull(consent)
            assertEquals("GRANTED", consent?.consentState)

            viewModel.revokeConsent(PermissionSource.OWN_NOTIFICATIONS)
            advanceUntilIdle()

            val revokedConsent = dao.findAccountConsent(userId, PermissionSource.OWN_NOTIFICATIONS.name)
            assertEquals("REVOKED", revokedConsent?.consentState)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
