package com.kipu.app.feature.debts.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.feature.debts.domain.model.DebtSummary
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PayableDebtFlowTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun payableFormExplainsNewAndHistoricalOpeningAndSubmitsTheSelectedBasis() {
        val state = mutableStateOf(
            DebtFormUiState(counterpartyName = "Banco local", principalAmount = "50.00"),
        )
        var submittedMode: DebtOpeningMode? = null
        compose.setContent {
            PayableDebtFormScreen(
                state = state.value,
                onCounterpartyNameChange = { state.value = state.value.copy(counterpartyName = it) },
                onPrincipalAmountChange = { state.value = state.value.copy(principalAmount = it) },
                onCurrencyChange = { state.value = state.value.copy(currencyCode = it) },
                onOpeningModeChange = { state.value = state.value.copy(openingMode = it) },
                onAccountSelected = { state.value = state.value.copy(selectedAccountId = it) },
                onDueDateChange = { state.value = state.value.copy(dueDate = it) },
                onNotesChange = { state.value = state.value.copy(notes = it) },
                onSave = { submittedMode = state.value.openingMode },
                onNavigateBack = {},
            )
        }

        compose.onNodeWithText("Me prestaron").assertIsDisplayed()
        compose.onNodeWithText("El saldo de la cuenta aumenta por el principal.").assertIsDisplayed()
        compose.onNodeWithTag("opening-historical").performClick()
        compose.onNodeWithText("El saldo ya lo refleja; no habrá un segundo movimiento.").assertIsDisplayed()
        compose.onNodeWithTag("save-debt").performClick()

        assertEquals(DebtOpeningMode.HISTORICAL, submittedMode)
    }

    @Test
    fun debtDetailAllowsDescriptionEditButBlocksDeletionWhenHistoryExists() {
        var edits = 0
        var deletes = 0
        compose.setContent {
            DebtDetailScreen(
                debt = debtSummary(),
                hasFinancialHistory = true,
                onNavigateBack = {},
                onEdit = { edits++ },
                onDelete = { deletes++ },
            )
        }

        compose.onNodeWithText("Editar datos").performClick()
        compose.onNodeWithText("Apertura histórica").assertIsDisplayed()
        compose.onNodeWithText("Eliminar deuda").assertIsNotEnabled()
        compose.onNodeWithText("El saldo pendiente, la apertura y cualquier movimiento se conservan como historial.").assertIsDisplayed()

        assertEquals(1, edits)
        assertEquals(0, deletes)
    }

    @Test
    fun debtWithoutFinancialHistoryRequiresConfirmationBeforeDeletion() {
        var deletes = 0
        compose.setContent {
            DebtDetailScreen(
                debt = debtSummary(),
                hasFinancialHistory = false,
                onNavigateBack = {},
                onEdit = {},
                onDelete = { deletes++ },
            )
        }

        compose.onNodeWithText("Eliminar deuda").performClick()
        compose.onNodeWithText("¿Eliminar esta deuda?").assertIsDisplayed()
        assertEquals(0, deletes)
        compose.onNodeWithTag("confirm-delete-debt").performClick()

        assertEquals(1, deletes)
    }

    private fun debtSummary() = DebtSummary(
        debtId = "86000000-0000-4000-8000-000000000001",
        userId = "owner",
        obligationType = DebtObligationType.PAYABLE,
        counterpartyName = "Banco local",
        principalMinor = 5_000L,
        remainingPrincipalMinor = 4_000L,
        currencyCode = "PEN",
        openedOn = LocalDate.parse("2026-10-01"),
        dueDate = null,
        reminderLeadDays = null,
        notes = null,
        status = DebtLifecycleStatus.ACTIVE,
        syncState = "SYNCED",
        revision = 2L,
    )
}
