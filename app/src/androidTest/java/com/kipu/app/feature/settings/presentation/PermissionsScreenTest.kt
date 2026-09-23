package com.kipu.app.feature.settings.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.settings.data.local.AccountSourceConsentEntity
import com.kipu.app.feature.settings.data.local.InstallationPermissionStateEntity
import com.kipu.app.feature.settings.data.local.PermissionConsentDao
import com.kipu.app.feature.settings.domain.PermissionSourceGateway
import com.kipu.app.feature.settings.domain.model.DeviceAuthorization
import com.kipu.app.feature.settings.domain.model.PermissionSource
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val userId = UUID.randomUUID()

    private class FakePermissionDao : PermissionConsentDao {
        val consents = MutableStateFlow<List<AccountSourceConsentEntity>>(emptyList())
        override suspend fun findInstallationPermission(source: String): InstallationPermissionStateEntity? = null
        override fun observeInstallationPermissions(): Flow<List<InstallationPermissionStateEntity>> = MutableStateFlow(emptyList())
        override suspend fun putInstallationPermission(entity: InstallationPermissionStateEntity) {}
        override suspend fun findAccountConsent(userId: UUID, source: String): AccountSourceConsentEntity? = null
        override fun observeAccountConsents(userId: UUID): Flow<List<AccountSourceConsentEntity>> = consents
        override suspend fun putAccountConsent(entity: AccountSourceConsentEntity) {}
        override suspend fun revokeAllAccountConsents(userId: UUID) {}
    }

    private class FakeGateway : PermissionSourceGateway {
        override fun checkDeviceAuthorization(source: PermissionSource): DeviceAuthorization = DeviceAuthorization.GRANTED
        override fun createSettingsIntent(source: PermissionSource): android.content.Intent = android.content.Intent()
        override suspend fun refreshAndSaveDeviceAuthorizations(): Map<PermissionSource, DeviceAuthorization> =
            mapOf(PermissionSource.OWN_NOTIFICATIONS to DeviceAuthorization.GRANTED)
    }

    private class FakeSessionCoordinator(override val currentOwner: LocalOwner?) : SessionCoordinator {
        override val remoteSession = MutableStateFlow(com.kipu.app.core.session.RemoteSession.Absent)
        override val localAccess = MutableStateFlow<com.kipu.app.core.session.LocalAccess>(
            com.kipu.app.core.session.LocalAccess.Available(currentOwner!!.verifiedUserId, com.kipu.app.core.session.RemoteSession.Absent)
        )
        override suspend fun setActiveOwner(userId: String) {}
        override suspend fun clearActiveOwner(explicit: Boolean) {}
        override suspend fun updateRemoteSession(session: com.kipu.app.core.session.RemoteSession) {}
        override suspend fun updateLockState(isLocked: Boolean, reason: String) {}
    }

    @Test
    fun screen_displays_guarantee_banner_and_permission_cards() {
        val viewModel = PermissionsViewModel(FakePermissionDao(), FakeGateway(), FakeSessionCoordinator(LocalOwner(userId.toString(), false)))

        composeTestRule.setContent {
            PermissionsScreen(
                viewModel = viewModel,
                onNavigateBack = {},
                onOpenSettingsIntent = {},
            )
        }

        composeTestRule.onNodeWithText("Permisos y Automatización").assertIsDisplayed()
        composeTestRule.onNodeWithText("El registro manual siempre permanece 100% operativo sin importar el estado de estos permisos opcionales.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Notificaciones de Kipu").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lectura de Notificaciones Bancarias").assertIsDisplayed()
    }

    @Test
    fun clicking_explanation_opens_rationale_dialog() {
        val viewModel = PermissionsViewModel(FakePermissionDao(), FakeGateway(), FakeSessionCoordinator(LocalOwner(userId.toString(), false)))

        composeTestRule.setContent {
            PermissionsScreen(
                viewModel = viewModel,
                onNavigateBack = {},
                onOpenSettingsIntent = {},
            )
        }

        val explanationButtons = composeTestRule.onAllNodes(androidx.compose.ui.test.hasText("Explicación"))
        explanationButtons[0].performClick()

        composeTestRule.onNodeWithText("Aceptar y Continuar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cerrar").assertIsDisplayed()
    }
}
