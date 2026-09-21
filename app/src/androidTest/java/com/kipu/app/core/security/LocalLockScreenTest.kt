package com.kipu.app.core.security

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.security.model.LocalAuthenticatorCapability
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalLockScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class FakeGateway : LocalAuthenticatorGateway {
        var authenticateCalled = false
        override fun getCapability(): LocalAuthenticatorCapability =
            LocalAuthenticatorCapability(hasBiometrics = true, hasDeviceCredential = true, canAuthenticate = true)

        override fun authenticate(
            activity: FragmentActivity,
            title: String,
            subtitle: String,
            onResult: (Boolean, String?) -> Unit,
        ) {
            authenticateCalled = true
        }
    }

    @Test
    fun lockScreenOverlay_displays_locked_state_and_triggers_prompt() {
        val gateway = FakeGateway()
        var unlockSuccessCalled = false
        var signOutRequested = false

        composeTestRule.setContent {
            LockScreenOverlay(
                gateway = gateway,
                onUnlockSuccess = { unlockSuccessCalled = true },
                onSignOutRequested = { signOutRequested = true },
            )
        }

        composeTestRule.onNodeWithText("Kipu está protegido").assertIsDisplayed()
        composeTestRule.onNodeWithText("Desbloquear").assertIsDisplayed()

        composeTestRule.onNodeWithText("Desbloquear").performClick()
        assertTrue(gateway.authenticateCalled)

        composeTestRule.onNodeWithText("Cerrar sesión").performClick()
        assertTrue(signOutRequested)
    }
}
