package com.kipu.app.feature.debts.domain.usecase

import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.feature.debts.domain.model.DebtPlanResult
import com.kipu.app.feature.debts.domain.model.OpenDebtCommand
import com.kipu.app.feature.debts.domain.model.PayableOpeningPlan

/** Pure PAYABLE plan; persistence and account ownership checks stay in the repository/server. */
class OpenPayableDebt {
    fun plan(command: OpenDebtCommand): DebtPlanResult<PayableOpeningPlan> {
        if (command.obligationType != DebtObligationType.PAYABLE) return DebtPlanResult.Invalid("WRONG_OBLIGATION_TYPE")
        if (command.principalMinor <= 0L) return DebtPlanResult.Invalid("INVALID_PRINCIPAL")
        if (command.currencyCode !in SUPPORTED_CURRENCIES) return DebtPlanResult.Invalid("UNSUPPORTED_CURRENCY")
        if (command.openingMode == DebtOpeningMode.NEW_CASH_FLOW && command.accountId.isNullOrBlank()) {
            return DebtPlanResult.Invalid("ACCOUNT_REQUIRED")
        }
        if (command.reminderLeadDays != null && command.reminderLeadDays !in 0..365) {
            return DebtPlanResult.Invalid("INVALID_REMINDER_LEAD_DAYS")
        }
        val normalizedName = command.counterpartyName.trim().replace(WHITESPACE, " ")
        if (normalizedName.isBlank()) return DebtPlanResult.Invalid("COUNTERPARTY_REQUIRED")

        val cashDelta = if (command.openingMode == DebtOpeningMode.NEW_CASH_FLOW) command.principalMinor else 0L
        return DebtPlanResult.Valid(
            PayableOpeningPlan(
                debtId = command.debtId,
                principalMinor = command.principalMinor,
                currencyCode = command.currencyCode,
                accountId = command.accountId,
                cashDeltaMinor = cashDelta,
                movementType = "INCOME",
                operationKind = "DEBT_DISBURSEMENT",
                counterpartyName = normalizedName,
            ),
        )
    }

    private companion object {
        val SUPPORTED_CURRENCIES = setOf("PEN", "USD")
        val WHITESPACE = Regex("\\s+")
    }
}
