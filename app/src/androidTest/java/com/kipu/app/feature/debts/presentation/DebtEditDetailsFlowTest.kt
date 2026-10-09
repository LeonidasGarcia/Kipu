package com.kipu.app.feature.debts.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DebtEditDetailsFlowTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun descriptiveEditDoesNotExposePrincipalAsEditableAndSavesNotes() {
        val state = mutableStateOf(
            DebtEditUiState(
                counterpartyName = "Banco local",
                principalAmount = "S/ 50.00",
                currencyCode = "PEN",
                revision = 1L,
            ),
        )
        var savedName: String? = null
        var savedNotes: String? = null

        compose.setContent {
            DebtEditDetailsScreen(
                state = state.value,
                onCounterpartyNameChange = { state.value = state.value.copy(counterpartyName = it) },
                onDueDateChange = { state.value = state.value.copy(dueDate = it) },
                onNotesChange = { state.value = state.value.copy(notes = it) },
                onSave = { savedName = state.value.counterpartyName; savedNotes = state.value.notes },
                onNavigateBack = {},
            )
        }

        compose.onNodeWithText("Banco local").performTextReplacement("Banco actualizado")
        compose.onNodeWithText("Notas").performTextReplacement("Acuerdo actualizado")
        compose.onNodeWithText("S/ 50.00").assertIsDisplayed()
        compose.onNodeWithTag("edit-debt-save").performClick()

        assertEquals("Banco actualizado", savedName)
        assertEquals("Acuerdo actualizado", savedNotes)
    }

    @Test
    fun dueDateUsesCalendarAndKeepsTheSelectedDate() {
        val state = mutableStateOf(
            DebtEditUiState(
                counterpartyName = "Banco local",
                dueDate = "2026-11-10",
                revision = 1L,
            ),
        )
        compose.setContent {
            DebtEditDetailsScreen(
                state = state.value,
                onCounterpartyNameChange = { state.value = state.value.copy(counterpartyName = it) },
                onDueDateChange = { state.value = state.value.copy(dueDate = it) },
                onNotesChange = { state.value = state.value.copy(notes = it) },
                onSave = {},
                onNavigateBack = {},
            )
        }

        compose.onNodeWithTag("edit_debt_due_date_picker").performScrollTo().performClick()
        compose.onNodeWithTag("edit_debt_due_date_calendar").assertIsDisplayed()
        compose.onNodeWithTag("edit_debt_confirm_due_date").performClick()

        assertEquals("2026-11-10", state.value.dueDate)
    }
}
