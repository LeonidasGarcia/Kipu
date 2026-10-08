package com.kipu.app.feature.debts.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import org.junit.Rule
import org.junit.Test

class ReceivableDebtFlowTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun newLoanRequiresAnActiveAccountInTheSelectedCurrency() {
        val state = DebtFormUiState(
            counterpartyName = "Amiga",
            principalAmount = "100.00",
            currencyCode = "USD",
            openingMode = DebtOpeningMode.NEW_CASH_FLOW,
            selectedAccountId = "pen-account",
            availableAccounts = listOf(
                DebtAccountOption("pen-account", "Ahorros PEN", "PEN"),
                DebtAccountOption("usd-account", "Ahorros USD", "USD"),
            ),
        )
        compose.setContent {
            ReceivableDebtFormScreen(
                state = state,
                onCounterpartyNameChange = {},
                onPrincipalAmountChange = {},
                onCurrencyChange = {},
                onOpeningModeChange = {},
                onAccountSelected = {},
                onDueDateChange = {},
                onNotesChange = {},
                onSave = {},
                onNavigateBack = {},
            )
        }

        compose.onNodeWithText("Moneda distinta (PEN)").assertIsDisplayed()
        compose.onNodeWithTag("save-debt").assertIsNotEnabled()
    }
}
