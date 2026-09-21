package com.kipu.app.feature.auth.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignOutFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun signOutDialogShowsWarningWhenPendingChangesExist() {
        var confirmed = false
        var dismissed = false

        composeTestRule.setContent {
            SignOutDialog(
                pendingCount = 3,
                onConfirmSignOut = { confirmed = true },
                onDismiss = { dismissed = true },
            )
        }

        composeTestRule.onNodeWithText("Cerrar Sesión").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Tienes 3 cambio(s) pendiente(s) de sincronizar. " +
                "Si cierras sesión ahora, estos cambios permanecerán guardados de forma segura en este dispositivo, " +
                "pero no estarán disponibles para otra cuenta ni se sincronizarán hasta que vuelvas a iniciar sesión."
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Cerrar sesión de todos modos").assertIsDisplayed()

        composeTestRule.onNodeWithText("Cerrar sesión de todos modos").performClick()
        assertTrue(confirmed)
    }

    @Test
    fun signOutDialogStandardWhenZeroPendingChanges() {
        composeTestRule.setContent {
            SignOutDialog(
                pendingCount = 0,
                onConfirmSignOut = {},
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("¿Estás seguro de que deseas cerrar sesión?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cerrar sesión").assertIsDisplayed()
    }
}
