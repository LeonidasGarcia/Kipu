package com.kipu.app.feature.movements.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DuplicateWarningDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cancelClosesWarningWithoutConfirming() {
        var confirmed = false
        var dismissed = false
        compose.setContent {
            KipuTheme {
                var showDialog by remember { mutableStateOf(true) }
                if (showDialog) {
                    DuplicateWarningDialog(
                        onConfirm = { confirmed = true },
                        onDismiss = {
                            dismissed = true
                            showDialog = false
                        },
                    )
                }
            }
        }

        compose.onNodeWithText("Posible Movimiento Duplicado").assertIsDisplayed()
        compose.onNodeWithTag("btn_cancel_duplicate").performClick()

        compose.onNodeWithTag("dialog_duplicate_warning").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(dismissed)
            assertFalse(confirmed)
        }
    }

    @Test
    fun confirmKeepsTheUserChosenDuplicate() {
        var confirmed = false
        var dismissed = false
        compose.setContent {
            KipuTheme {
                var showDialog by remember { mutableStateOf(true) }
                if (showDialog) {
                    DuplicateWarningDialog(
                        onConfirm = {
                            confirmed = true
                            showDialog = false
                        },
                        onDismiss = { dismissed = true },
                    )
                }
            }
        }

        compose.onNodeWithText(
            "Se ha detectado una transacción idéntica registrada recientemente en esta cuenta. ¿Deseas guardarla de todas formas?"
        ).assertIsDisplayed()
        compose.onNodeWithTag("btn_confirm_duplicate").performClick()

        compose.onNodeWithTag("dialog_duplicate_warning").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(confirmed)
            assertFalse(dismissed)
        }
    }
}
