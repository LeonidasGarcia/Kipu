package com.kipu.app.feature.movements.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Rule
import org.junit.Test

class SavingMovementDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun savingDialogExplainsProgressAndExposesAccessibleStatus() {
        compose.setContent {
            KipuTheme {
                SavingMovementDialog()
            }
        }

        compose.onNodeWithTag("dialog_saving_movement").assertIsDisplayed()
        compose.onNodeWithText("Guardando movimiento").assertIsDisplayed()
        compose.onNodeWithContentDescription("Guardando movimiento").assertIsDisplayed()
        compose.onNodeWithText("Estamos guardando el movimiento y preparando su sincronización.").assertIsDisplayed()
    }
}
