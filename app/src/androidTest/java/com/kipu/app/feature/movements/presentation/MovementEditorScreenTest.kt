package com.kipu.app.feature.movements.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MovementEditorScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun editorDisplaysStandardFieldsAndChangeSummary() {
        val initialState = MovementEditorUiState(
            isLoading = false,
            transactionId = "tx-1",
            expectedRevision = 1L,
            movementType = MovementType.EXPENSE,
            currency = "PEN",
            initialAmountMinor = 2000L,
            initialSourceAccountId = "acc-1",
            initialCategoryId = "cat-food",
            initialCategoryName = "Alimentación",
            initialOccurredAt = 1727740800000L,
            initialNote = "Nota original",
            amountText = "15.00", // Changed from 20.00 to 15.00
            selectedSourceAccountId = "acc-1",
            selectedCategoryId = "cat-food",
            selectedCategoryName = "Alimentación",
            occurredAt = 1727740800000L,
            note = "Nota original",
            availableAccounts = listOf(
                Account(
                    id = com.kipu.app.core.finance.domain.model.AccountId("acc-1"),
                    userId = com.kipu.app.core.finance.domain.model.UserId("user-1"),
                    alias = "Cuenta BCP Soles",
                    type = com.kipu.app.feature.accounts.domain.model.AccountType.SAVINGS,
                    currency = com.kipu.app.core.finance.domain.model.Currency.PEN,
                    initialBalance = com.kipu.app.core.finance.domain.model.Money.zero(com.kipu.app.core.finance.domain.model.Currency.PEN),
                    openedAt = java.time.Instant.now(),
                ),
            ),
            availableCategories = listOf(
                CategoryOption("cat-food", "Alimentación", "restaurant", CategoryType.EXPENSE),
            ),
        )

        compose.setContent {
            KipuTheme {
                val state = remember { mutableStateOf(initialState) }
                MovementEditorContent(
                    uiState = state.value,
                    onAmountChanged = { state.value = state.value.copy(amountText = it) },
                    onSourceAccountSelected = {},
                    onDestinationAccountSelected = {},
                    onCategorySelected = {},
                    onMerchantChanged = {},
                    onDateChanged = {},
                    onNoteChanged = {},
                    onSave = {},
                    onClose = {},
                    onConfirmDiscard = {},
                    onDismissDiscardDialog = {},
                    onDiscardConflict = {},
                    onRedoConflict = {},
                )
            }
        }

        // Title and badges
        compose.onNodeWithText("Editar movimiento").assertIsDisplayed()
        compose.onNodeWithTag("badge_movement_type").assertIsDisplayed()
        compose.onNodeWithText("Gasto").assertIsDisplayed()

        // Amount input
        compose.onNodeWithTag("editor_amount_input").assertIsDisplayed()

        // Change summary section
        compose.onNodeWithTag("editor_change_summary").assertIsDisplayed()
        compose.onNodeWithTag("summary_amount").assertIsDisplayed()

        // Save button is enabled
        compose.onNodeWithTag("editor_btn_save").assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun onlyNoteChangedShowsSpecificSummary() {
        val initialState = MovementEditorUiState(
            isLoading = false,
            transactionId = "tx-1",
            expectedRevision = 1L,
            movementType = MovementType.EXPENSE,
            currency = "PEN",
            initialAmountMinor = 2000L,
            initialSourceAccountId = "acc-1",
            initialCategoryId = "cat-food",
            initialCategoryName = "Alimentación",
            initialOccurredAt = 1727740800000L,
            initialNote = "Nota original",
            amountText = "20.00", // Same amount
            selectedSourceAccountId = "acc-1",
            selectedCategoryId = "cat-food",
            selectedCategoryName = "Alimentación",
            occurredAt = 1727740800000L,
            note = "Nota editada", // Only note changed!
        )

        compose.setContent {
            KipuTheme {
                val state = remember { mutableStateOf(initialState) }
                MovementEditorContent(
                    uiState = state.value,
                    onAmountChanged = {},
                    onSourceAccountSelected = {},
                    onDestinationAccountSelected = {},
                    onCategorySelected = {},
                    onMerchantChanged = {},
                    onDateChanged = {},
                    onNoteChanged = {},
                    onSave = {},
                    onClose = {},
                    onConfirmDiscard = {},
                    onDismissDiscardDialog = {},
                    onDiscardConflict = {},
                    onRedoConflict = {},
                )
            }
        }

        compose.onNodeWithTag("editor_change_summary").assertIsDisplayed()
        compose.onNodeWithTag("summary_only_note").assertIsDisplayed()
        compose.onNodeWithText("Solo se modificó la nota (sin cambios en saldos ni asientos contables)").assertIsDisplayed()
    }

    @Test
    fun specializedMovementShowsWarningAndDisablesSave() {
        val state = MovementEditorUiState(
            isLoading = false,
            transactionId = "tx-specialized",
            expectedRevision = 1L,
            movementType = MovementType.EXPENSE,
            currency = "PEN",
            isSpecialized = true,
            specializedMessage = "Este movimiento tiene cuotas o una deuda asociada. Revísalo desde su gestión específica",
            initialAmountMinor = 5000L,
            amountText = "50.00",
        )

        compose.setContent {
            KipuTheme {
                MovementEditorContent(
                    uiState = state,
                    onAmountChanged = {},
                    onSourceAccountSelected = {},
                    onDestinationAccountSelected = {},
                    onCategorySelected = {},
                    onMerchantChanged = {},
                    onDateChanged = {},
                    onNoteChanged = {},
                    onSave = {},
                    onClose = {},
                    onConfirmDiscard = {},
                    onDismissDiscardDialog = {},
                    onDiscardConflict = {},
                    onRedoConflict = {},
                )
            }
        }

        compose.onNodeWithTag("warning_specialized_movement").assertIsDisplayed()
        compose.onNodeWithText("Este movimiento tiene cuotas o una deuda asociada. Revísalo desde su gestión específica").assertIsDisplayed()
        compose.onNodeWithTag("editor_btn_save").assertIsNotEnabled()
    }

    @Test
    fun conflictStateShowsOfficialVsProposedComparisonAndActions() {
        var discardClicked = false
        var redoClicked = false

        val state = MovementEditorUiState(
            isLoading = false,
            transactionId = "tx-conflict",
            expectedRevision = 2L,
            movementType = MovementType.EXPENSE,
            currency = "PEN",
            hasConflict = true,
            initialAmountMinor = 2000L,
            conflictProposedAmountMinor = 1800L,
            amountText = "20.00",
        )

        compose.setContent {
            KipuTheme {
                MovementEditorContent(
                    uiState = state,
                    onAmountChanged = {},
                    onSourceAccountSelected = {},
                    onDestinationAccountSelected = {},
                    onCategorySelected = {},
                    onMerchantChanged = {},
                    onDateChanged = {},
                    onNoteChanged = {},
                    onSave = {},
                    onClose = {},
                    onConfirmDiscard = {},
                    onDismissDiscardDialog = {},
                    onDiscardConflict = { discardClicked = true },
                    onRedoConflict = { redoClicked = true },
                )
            }
        }

        compose.onNodeWithTag("card_conflict_warning").assertIsDisplayed()
        compose.onNodeWithText("Este movimiento cambió en otro dispositivo").assertIsDisplayed()

        compose.onNodeWithTag("btn_conflict_discard").assertIsDisplayed().performClick()
        assertTrue(discardClicked)

        compose.onNodeWithTag("btn_conflict_redo").assertIsDisplayed().performClick()
        assertTrue(redoClicked)
    }

    @Test
    fun conflictWhenOfficialIsVoidedBlocksRedo() {
        val state = MovementEditorUiState(
            isLoading = false,
            transactionId = "tx-voided-conflict",
            expectedRevision = 3L,
            movementType = MovementType.EXPENSE,
            currency = "PEN",
            hasConflict = true,
            isOfficialVoided = true,
            initialAmountMinor = 2000L,
            amountText = "20.00",
        )

        compose.setContent {
            KipuTheme {
                MovementEditorContent(
                    uiState = state,
                    onAmountChanged = {},
                    onSourceAccountSelected = {},
                    onDestinationAccountSelected = {},
                    onCategorySelected = {},
                    onMerchantChanged = {},
                    onDateChanged = {},
                    onNoteChanged = {},
                    onSave = {},
                    onClose = {},
                    onConfirmDiscard = {},
                    onDismissDiscardDialog = {},
                    onDiscardConflict = {},
                    onRedoConflict = {},
                )
            }
        }

        compose.onNodeWithTag("text_conflict_voided").assertIsDisplayed()
        compose.onNodeWithText("El movimiento fue anulado en el servidor").assertIsDisplayed()
        compose.onNodeWithTag("btn_conflict_redo").assertDoesNotExist()
    }

    @Test
    fun discardConfirmationDialogShowsWhenUnsavedChanges() {
        var confirmDiscardClicked = false
        var cancelDiscardClicked = false

        val state = MovementEditorUiState(
            isLoading = false,
            transactionId = "tx-1",
            showConfirmDiscardDialog = true,
        )

        compose.setContent {
            KipuTheme {
                MovementEditorContent(
                    uiState = state,
                    onAmountChanged = {},
                    onSourceAccountSelected = {},
                    onDestinationAccountSelected = {},
                    onCategorySelected = {},
                    onMerchantChanged = {},
                    onDateChanged = {},
                    onNoteChanged = {},
                    onSave = {},
                    onClose = {},
                    onConfirmDiscard = { confirmDiscardClicked = true },
                    onDismissDiscardDialog = { cancelDiscardClicked = true },
                    onDiscardConflict = {},
                    onRedoConflict = {},
                )
            }
        }

        compose.onNodeWithTag("dialog_confirm_discard").assertIsDisplayed()
        compose.onNodeWithText("¿Descartar cambios?").assertIsDisplayed()
        compose.onNodeWithText("Tienes cambios sin guardar. ¿Deseas descartarlos?").assertIsDisplayed()

        compose.onNodeWithTag("btn_cancel_discard").assertIsDisplayed().performClick()
        assertTrue(cancelDiscardClicked)

        compose.onNodeWithTag("btn_confirm_discard").assertIsDisplayed().performClick()
        assertTrue(confirmDiscardClicked)
    }
}
