package com.kipu.app.feature.debts.domain.usecase

import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtPlanResult
import com.kipu.app.feature.debts.domain.model.DebtSettlementPlan
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.feature.debts.domain.model.SettleDebtCommand
import java.security.MessageDigest
import javax.inject.Inject

/** Pure validation and accounting plan for paying or collecting debt principal. */
class SettleDebt @Inject constructor() {
    fun plan(
        debt: DebtSummary,
        command: SettleDebtCommand,
        accountCurrencyCode: String?,
        isInterestCategoryEligible: Boolean = !command.interestCategoryId.isNullOrBlank(),
        installmentPrincipalRemainingMinor: Long? = null,
    ): DebtPlanResult<DebtSettlementPlan> {
        if (command.debtId != debt.debtId) return DebtPlanResult.Invalid("DEBT_NOT_FOUND")
        if (debt.status != DebtLifecycleStatus.ACTIVE) return DebtPlanResult.Invalid("DEBT_NOT_ACTIVE")
        if (command.expectedRevision != debt.revision) return DebtPlanResult.Invalid("DEBT_REVISION_CONFLICT")
        if (command.accountId.isBlank() || accountCurrencyCode == null) {
            return DebtPlanResult.Invalid("ACCOUNT_REQUIRED")
        }
        if (accountCurrencyCode != debt.currencyCode) return DebtPlanResult.Invalid("ACCOUNT_CURRENCY_MISMATCH")
        if (command.principalMinor <= 0L || command.interestMinor < 0L) {
            return DebtPlanResult.Invalid("INVALID_SETTLEMENT_AMOUNT")
        }
        if (command.principalMinor > debt.remainingPrincipalMinor) {
            return DebtPlanResult.Invalid("PRINCIPAL_EXCEEDS_REMAINING")
        }
        if (command.interestMinor > 0L && debt.obligationType == DebtObligationType.PAYABLE &&
            (command.interestCategoryId.isNullOrBlank() || !isInterestCategoryEligible)
        ) {
            return DebtPlanResult.Invalid("INTEREST_CATEGORY_REQUIRED")
        }
        if (command.installmentId != null) {
            if (installmentPrincipalRemainingMinor == null || installmentPrincipalRemainingMinor <= 0L) {
                return DebtPlanResult.Invalid("INSTALLMENT_NOT_FOUND")
            }
            if (command.principalMinor > installmentPrincipalRemainingMinor) {
                return DebtPlanResult.Invalid("INSTALLMENT_PRINCIPAL_EXCEEDED")
            }
        } else if (installmentPrincipalRemainingMinor != null) {
            return DebtPlanResult.Invalid("INSTALLMENT_ID_REQUIRED")
        }

        val totalCashMinor = try {
            Math.addExact(command.principalMinor, command.interestMinor)
        } catch (_: ArithmeticException) {
            return DebtPlanResult.Invalid("SETTLEMENT_AMOUNT_OVERFLOW")
        }
        val isReceivable = debt.obligationType == DebtObligationType.RECEIVABLE
        val movementType = if (isReceivable) "INCOME" else "EXPENSE"
        val principalTransactionId = command.identity.operationId
        val interestTransactionId = command.interestMinor.takeIf { it > 0L }
            ?.let { stableId(command.identity.operationId, "interest") }

        return DebtPlanResult.Valid(
            DebtSettlementPlan(
                debtId = debt.debtId,
                eventId = stableId(command.identity.operationId, "settlement-event"),
                expectedRevision = command.expectedRevision,
                principalMinor = command.principalMinor,
                interestMinor = command.interestMinor,
                currencyCode = debt.currencyCode,
                accountId = command.accountId,
                installmentId = command.installmentId,
                occurredAt = command.occurredAt,
                cashDeltaMinor = if (isReceivable) totalCashMinor else -totalCashMinor,
                principalDeltaMinor = -command.principalMinor,
                remainingPrincipalMinor = debt.remainingPrincipalMinor - command.principalMinor,
                operatingIncomeMinor = if (isReceivable) command.interestMinor else 0L,
                operatingExpenseMinor = if (isReceivable) 0L else command.interestMinor,
                principalMovementType = movementType,
                interestMovementType = movementType.takeIf { command.interestMinor > 0L },
                principalTransactionId = principalTransactionId,
                interestTransactionId = interestTransactionId,
            ),
        )
    }

    private fun stableId(operationId: String, part: String): String =
        md5Uuid(
            when (part) {
                "interest" -> "kipu:debt-interest:$operationId"
                "settlement-event" -> "kipu:debt-settlement-event:$operationId"
                else -> "$operationId:$part"
            },
        )

    private fun md5Uuid(value: String): String {
        val hex = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}
