package com.kipu.app.feature.debts.domain.usecase

import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtPlanResult
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import com.kipu.app.feature.debts.domain.model.DebtSchedulePlan
import com.kipu.app.feature.debts.domain.model.DebtSummary
import java.time.DateTimeException
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/** Creates an exact-sum principal plan; it never creates a payment or financial movement. */
class SetDebtSchedule @Inject constructor() {
    fun plan(
        debt: DebtSummary,
        installmentCount: Int,
        firstDueDate: LocalDate,
        reminderLeadDays: Int?,
        firstInstallmentNumber: Int,
        operationId: String,
    ): DebtPlanResult<DebtSchedulePlan> {
        if (debt.status != DebtLifecycleStatus.ACTIVE) return DebtPlanResult.Invalid("DEBT_NOT_ACTIVE")
        if (debt.remainingPrincipalMinor <= 0L) return DebtPlanResult.Invalid("NO_REMAINING_PRINCIPAL")
        if (installmentCount !in 1..MAX_INSTALLMENTS || operationId.isBlank()) {
            return DebtPlanResult.Invalid("INVALID_INSTALLMENT_COUNT")
        }
        if (firstInstallmentNumber <= 0 || firstInstallmentNumber > MAX_INSTALLMENT_NUMBER - installmentCount + 1) {
            return DebtPlanResult.Invalid("INVALID_INSTALLMENT_SEQUENCE")
        }
        if (reminderLeadDays != null && reminderLeadDays !in 0..365) {
            return DebtPlanResult.Invalid("INVALID_REMINDER_LEAD_DAYS")
        }

        val base = debt.remainingPrincipalMinor / installmentCount
        val remainder = debt.remainingPrincipalMinor % installmentCount
        val installments = try {
            List(installmentCount) { index ->
                val number = firstInstallmentNumber + index
                DebtScheduleItem(
                    id = stableInstallmentId(operationId, number),
                    installmentNumber = number,
                    dueDate = firstDueDate.plusMonths(index.toLong()),
                    principalMinor = base + if (index.toLong() < remainder) 1L else 0L,
                )
            }
        } catch (_: DateTimeException) {
            return DebtPlanResult.Invalid("INVALID_INSTALLMENT_DATE")
        } catch (_: ArithmeticException) {
            return DebtPlanResult.Invalid("INSTALLMENT_AMOUNT_OVERFLOW")
        }

        return DebtPlanResult.Valid(
            DebtSchedulePlan(
                debtId = debt.debtId,
                expectedRevision = debt.revision,
                installments = installments,
                reminderLeadDays = reminderLeadDays,
            ),
        )
    }

    fun cancel(debt: DebtSummary): DebtPlanResult<DebtSchedulePlan> {
        if (debt.status != DebtLifecycleStatus.ACTIVE) return DebtPlanResult.Invalid("DEBT_NOT_ACTIVE")
        return DebtPlanResult.Valid(
            DebtSchedulePlan(
                debtId = debt.debtId,
                expectedRevision = debt.revision,
                installments = emptyList(),
                reminderLeadDays = null,
                cancelSchedule = true,
            ),
        )
    }

    private fun stableInstallmentId(operationId: String, installmentNumber: Int): String =
        UUID.nameUUIDFromBytes("kipu:debt-installment:$operationId:$installmentNumber".toByteArray(Charsets.UTF_8)).toString()

    private companion object {
        const val MAX_INSTALLMENTS = 120
        const val MAX_INSTALLMENT_NUMBER = Short.MAX_VALUE.toInt()
    }
}
