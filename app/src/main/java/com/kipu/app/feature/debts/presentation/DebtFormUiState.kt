package com.kipu.app.feature.debts.presentation

import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.feature.debts.domain.DebtAmountParser
import java.time.LocalDate

data class DebtAccountOption(
    val id: String,
    val name: String,
    val currencyCode: String,
    val balanceMinor: Long? = null,
)

data class DebtFormUiState(
    val counterpartyName: String = "",
    val principalAmount: String = "",
    val currencyCode: String = "PEN",
    val openingMode: DebtOpeningMode = DebtOpeningMode.NEW_CASH_FLOW,
    val selectedAccountId: String? = null,
    val availableAccounts: List<DebtAccountOption> = emptyList(),
    val openedOn: LocalDate = LocalDate.now(),
    val dueDate: LocalDate? = null,
    val dueDateInput: String = "",
    val reminderLeadDays: Int? = null,
    val notes: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

internal fun DebtFormUiState.isValidForSave(): Boolean {
    val amountIsPositive = DebtAmountParser.toMinorUnits(principalAmount) != null
    val accountIsValid = openingMode == DebtOpeningMode.HISTORICAL || availableAccounts.any { account ->
        account.id == selectedAccountId && account.currencyCode == currencyCode
    }
    val dueDateIsValid = dueDateInput.isBlank() || dueDate != null
    return counterpartyName.trim().isNotEmpty() && amountIsPositive && accountIsValid && dueDateIsValid && !isSaving
}
