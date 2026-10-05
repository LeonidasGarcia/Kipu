package com.kipu.app.feature.movements.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.Transaction
import com.kipu.app.feature.movements.domain.model.TransactionItem
import com.kipu.app.feature.movements.domain.model.TransactionStatus
import com.kipu.app.ui.theme.KipuTheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MovementHistoryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Composable
    private fun TestViewport(content: @Composable () -> Unit) {
        KipuTheme { Box(Modifier.fillMaxSize().systemBarsPadding()) { content() } }
    }

    private fun saveEvidence(name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "sprint4-ux").apply { mkdirs() }
        File(directory, name).outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun cardPaymentHistoryUsesExplicitTitleAndAccountToCardSubtitle() {
        val item = TransactionItem(
            transaction = Transaction(
                id = "payment-row",
                userId = "test-user",
                type = MovementType.TRANSFER,
                amountMinor = 2_500L,
                currency = "PEN",
                sourceAccountId = "bank-account",
                cardId = "credit-card",
                operationKind = "CARD_PAYMENT",
                occurredAt = 1_758_000_000_000L,
            ),
            sourceAccountAlias = "Ahorros",
            cardAlias = "Visa Oro",
        )

        compose.setContent {
            TestViewport { TransactionRow(item) }
        }

        compose.onNodeWithText("Pago de tarjeta").assertIsDisplayed()
        compose.onNodeWithText("Ahorros → Visa Oro").assertIsDisplayed()
    }

    @Test
    fun activeTransactionShowsEditAndVoidActionsAndNeverShowsDelete() {
        val item = TransactionItem(
            transaction = Transaction(
                id = "tx-active-1",
                userId = "test-user",
                type = MovementType.EXPENSE,
                amountMinor = 1_500L,
                currency = "PEN",
                sourceAccountId = "account-1",
                occurredAt = 1_758_000_000_000L,
                status = TransactionStatus.ACTIVE,
                categoryId = "ux-category",
            ),
            sourceAccountAlias = "Efectivo",
            categoryName = "Alimentación",
        )

        var editClicked = false
        var voidClicked = false

        compose.setContent {
            TestViewport {
                TransactionRow(
                    item = item,
                    onClick = { editClicked = true },
                    onVoidClick = { voidClicked = true },
                )
            }
        }

        // Open options menu
        compose.onNodeWithTag("tx_menu_tx-active-1").performClick()

        // "Editar" and "Anular" must be present
        compose.onNodeWithTag("action_edit_tx-active-1").assertIsDisplayed()
        compose.onNodeWithTag("action_void_tx-active-1").assertIsDisplayed()

        // "Eliminar" must NEVER be shown on financial movements
        compose.onNodeWithText("Eliminar").assertDoesNotExist()

        // Clicking "Anular" triggers callback
        compose.onNodeWithTag("action_void_tx-active-1").performClick()
        assertTrue(voidClicked)
        assertFalse(editClicked)
    }

    @Test
    fun voidedTransactionDisplaysVoidedBadgeAndOpensReadOnlyDetail() {
        val item = TransactionItem(
            transaction = Transaction(
                id = "tx-voided-1",
                userId = "test-user",
                type = MovementType.EXPENSE,
                amountMinor = 2_000L,
                currency = "PEN",
                sourceAccountId = "account-1",
                occurredAt = 1_758_000_000_000L,
                status = TransactionStatus.VOIDED,
                categoryId = "ux-category",
            ),
            sourceAccountAlias = "Efectivo",
            categoryName = "Farmacia",
        )

        var rowClicked = false

        compose.setContent {
            TestViewport {
                TransactionRow(
                    item = item,
                    onClick = { rowClicked = true },
                    onVoidClick = {},
                )
            }
        }

        saveEvidence("voided-row.png")
        // Must display "Anulado" badge
        compose.onNodeWithTag("tx_voided_badge_tx-voided-1", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Anulado").assertIsDisplayed()

        // Options menu should not be available for voided transaction
        compose.onNodeWithTag("tx_menu_tx-voided-1").assertDoesNotExist()

        // Clicking the row opens a read-only detail; no editor action is available.
        compose.onNodeWithTag("tx_row_tx-voided-1").performClick()
        assertTrue(rowClicked)
    }

    @Test
    fun voidMovementDialogStandardExpenseShowsConsequencesAndConfirms() {
        val item = TransactionItem(
            transaction = Transaction(
                id = "tx-expense-1",
                userId = "test-user",
                type = MovementType.EXPENSE,
                amountMinor = 3_500L,
                currency = "PEN",
                sourceAccountId = "account-1",
                occurredAt = 1_758_000_000_000L,
                categoryId = "ux-category",
            ),
            sourceAccountAlias = "Sueldo BCP",
            merchantName = "Restaurante Central",
        )

        var confirmedReason: String? = null
        var dismissed = false

        compose.setContent {
            TestViewport {
                VoidMovementDialog(
                    item = item,
                    onDismissRequest = { dismissed = true },
                    onConfirmVoid = { reason -> confirmedReason = reason },
                )
            }
        }

        compose.onNodeWithTag("dialog_void_movement").assertIsDisplayed()
        compose.onNodeWithText("¿Anular movimiento?").assertIsDisplayed()
        compose.onNodeWithText("Restaurante Central").assertIsDisplayed()
        compose.onNodeWithTag("text_void_consequences").assertIsDisplayed()
        compose.onNodeWithText("Se revertirá el impacto financiero de este gasto. Los registros contables originales se conservarán con un asiento de compensación.").assertIsDisplayed()

        // Type optional reason
        compose.onNodeWithTag("input_void_reason").performTextInput("Cobro erróneo")

        // Confirm void
        compose.onNodeWithTag("btn_confirm_void").performClick()
        assertEquals("Cobro erróneo", confirmedReason)
        assertFalse(dismissed)
    }

    @Test
    fun voidMovementDialogTransferShowsConsequencesForBothAccounts() {
        val item = TransactionItem(
            transaction = Transaction(
                id = "tx-transfer-1",
                userId = "test-user",
                type = MovementType.TRANSFER,
                amountMinor = 10_000L,
                currency = "PEN",
                sourceAccountId = "acc-origin",
                destinationAccountId = "acc-dest",
                occurredAt = 1_758_000_000_000L,
            ),
            sourceAccountAlias = "BCP Soles",
            destinationAccountAlias = "BBVA Soles",
        )

        var dismissed = false

        compose.setContent {
            TestViewport {
                VoidMovementDialog(
                    item = item,
                    onDismissRequest = { dismissed = true },
                    onConfirmVoid = {},
                )
            }
        }

        compose.onNodeWithTag("dialog_void_movement").assertIsDisplayed()
        compose.onNodeWithText("Esta acción revertirá los saldos en ambas cuentas (BCP Soles y BBVA Soles). Los registros originales se conservarán con asientos de compensación.").assertIsDisplayed()

        // Cancel dismissal without side-effects
        compose.onNodeWithTag("btn_cancel_void").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun voidMovementDialogSpecializedDisablesVoidAndWarns() {
        val item = TransactionItem(
            transaction = Transaction(
                id = "tx-specialized-card",
                userId = "test-user",
                type = MovementType.EXPENSE,
                amountMinor = 50_000L,
                currency = "PEN",
                sourceAccountId = null,
                cardId = "card-visa",
                operationKind = "CARD_PURCHASE",
                installmentCount = 3,
                occurredAt = 1_758_000_000_000L,
                categoryId = "ux-category",
            ),
            cardAlias = "Visa Signature",
        )

        var dismissed = false

        compose.setContent {
            TestViewport {
                VoidMovementDialog(
                    item = item,
                    onDismissRequest = { dismissed = true },
                    onConfirmVoid = {},
                )
            }
        }

        compose.onNodeWithTag("dialog_void_movement").assertIsDisplayed()
        compose.onNodeWithTag("text_void_specialized_warning").assertIsDisplayed()
        compose.onNodeWithText("Este movimiento tiene cuotas o una deuda asociada. Revísalo desde su gestión específica.").assertIsDisplayed()

        // Confirm button must NOT exist for specialized operations
        compose.onNodeWithTag("btn_confirm_void").assertDoesNotExist()

        // Cancel button is available
        compose.onNodeWithTag("btn_cancel_void").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun emptyMovementsStateWithActiveFiltersShowsClearActionAndInvokesCallback() {
        var clearClicked = false

        compose.setContent {
            TestViewport {
                CalmEmeraldEmptyMovementsState(
                    hasActiveFilters = true,
                    onClearFilters = { clearClicked = true },
                )
            }
        }

        compose.onNodeWithText("Sin resultados para los filtros").assertIsDisplayed()
        compose.onNodeWithTag("btn_empty_clear_filters").assertIsDisplayed()
        compose.onNodeWithTag("btn_empty_clear_filters").performClick()
        assertTrue(clearClicked)
    }

    @Test
    fun emptyMovementsStateWithoutFiltersShowsDefaultEmptyAndNoClearButton() {
        compose.setContent {
            TestViewport {
                CalmEmeraldEmptyMovementsState(
                    hasActiveFilters = false,
                    onClearFilters = null,
                )
            }
        }

        saveEvidence("empty-history.png")
        compose.onNodeWithText("Sin movimientos registrados").assertIsDisplayed()
        compose.onNodeWithTag("btn_empty_clear_filters").assertDoesNotExist()
    }

    @Test
    fun filterSheetResetOnlyChangesDraftUntilApply() {
        var applied: MovementFilterDraft? = null
        compose.setContent { KipuTheme { MovementFiltersSheet(
            MovementHistoryUiState(accessStatus = com.kipu.app.feature.movements.domain.model.MovementHistoryAccessDecision.Allowed,
                appliedFilters = AdvancedFiltersState(financialStates = setOf(com.kipu.app.feature.movements.domain.model.MovementFinancialState.VOIDED))),
            onDismiss = {}, onApply = { applied = it; it.validate() }) } }
        compose.onNodeWithTag("chip_filter_voided").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("btn_reset_advanced_filters").assertIsDisplayed().performClick()
        assertEquals(null, applied)
        compose.onNodeWithTag("btn_apply_filters").assertIsDisplayed().performClick()
        assertTrue(applied!!.financialStates.isEmpty())
    }
}
