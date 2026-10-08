package com.kipu.app.feature.debts.domain.usecase

import com.kipu.app.feature.debts.domain.model.CloseDebtAction
import com.kipu.app.feature.debts.domain.model.DebtClosureCommand
import com.kipu.app.feature.debts.domain.model.DebtClosurePlan
import com.kipu.app.feature.debts.domain.model.DebtEventType
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtPlanResult
import com.kipu.app.feature.debts.domain.model.DebtSummary
import javax.inject.Inject

/** Validates an auditable lifecycle action without creating cash or ledger effects. */
class CloseDebt @Inject constructor() {
    fun plan(debt: DebtSummary, command: DebtClosureCommand): DebtPlanResult<DebtClosurePlan> {
        if (command.debtId != debt.debtId) return DebtPlanResult.Invalid("DEBT_NOT_FOUND")
        if (debt.status != DebtLifecycleStatus.ACTIVE) return DebtPlanResult.Invalid("DEBT_NOT_ACTIVE")
        if (command.expectedRevision != debt.revision) return DebtPlanResult.Invalid("DEBT_REVISION_CONFLICT")
        if (command.action != CloseDebtAction.SETTLE &&
            (command.reason.isNullOrBlank() || command.reason.trim().length > MAX_REASON_LENGTH)
        ) {
            return DebtPlanResult.Invalid("REASON_REQUIRED")
        }

        val remaining = debt.remainingPrincipalMinor
        val plan = when (command.action) {
            CloseDebtAction.SETTLE -> {
                if (remaining != 0L) return DebtPlanResult.Invalid("BALANCE_REMAINS")
                DebtClosurePlan(debt.debtId, DebtLifecycleStatus.SETTLED, 0L, null, 0L, 0L, command.reason)
            }
            CloseDebtAction.CANCEL -> DebtClosurePlan(
                debt.debtId,
                DebtLifecycleStatus.CANCELLED,
                remaining,
                null,
                0L,
                0L,
                command.reason?.trim(),
            )
            CloseDebtAction.ADJUST -> {
                val delta = command.principalDeltaMinor
                    ?: return DebtPlanResult.Invalid("ADJUSTMENT_REQUIRED")
                if (delta == 0L || delta > MAX_EVENT_AMOUNT_MINOR || delta < -MAX_EVENT_AMOUNT_MINOR) {
                    return DebtPlanResult.Invalid("INVALID_ADJUSTMENT")
                }
                val next = try {
                    Math.addExact(remaining, delta)
                } catch (_: ArithmeticException) {
                    return DebtPlanResult.Invalid("ADJUSTMENT_OVERFLOW")
                }
                if (next < 0L) return DebtPlanResult.Invalid("ADJUSTMENT_EXCEEDS_BALANCE")
                DebtClosurePlan(
                    debt.debtId,
                    if (next == 0L) DebtLifecycleStatus.SETTLED else DebtLifecycleStatus.ACTIVE,
                    next,
                    DebtEventType.ADJUSTMENT,
                    kotlin.math.abs(delta),
                    delta,
                    command.reason?.trim(),
                )
            }
            CloseDebtAction.FORGIVE -> {
                val amount = command.amountMinor
                    ?: return DebtPlanResult.Invalid("FORGIVENESS_REQUIRED")
                if (amount <= 0L || amount > MAX_EVENT_AMOUNT_MINOR) return DebtPlanResult.Invalid("INVALID_FORGIVENESS")
                if (amount > remaining) return DebtPlanResult.Invalid("FORGIVENESS_EXCEEDS_BALANCE")
                val next = remaining - amount
                DebtClosurePlan(
                    debt.debtId,
                    if (next == 0L) DebtLifecycleStatus.SETTLED else DebtLifecycleStatus.ACTIVE,
                    next,
                    DebtEventType.FORGIVENESS,
                    amount,
                    -amount,
                    command.reason?.trim(),
                )
            }
        }
        return DebtPlanResult.Valid(plan)
    }

    private companion object {
        const val MAX_EVENT_AMOUNT_MINOR = 99_999_999_999_999L
        const val MAX_REASON_LENGTH = 500
    }
}
