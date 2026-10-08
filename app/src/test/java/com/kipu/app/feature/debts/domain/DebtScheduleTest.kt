package com.kipu.app.feature.debts.domain

import com.kipu.app.feature.debts.domain.model.CloseDebtAction
import com.kipu.app.feature.debts.domain.model.DebtCommandIdentity
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.feature.debts.domain.model.DebtPlanResult
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.feature.debts.domain.model.DebtClosureCommand
import com.kipu.app.feature.debts.domain.usecase.CloseDebt
import com.kipu.app.feature.debts.domain.usecase.SetDebtSchedule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebtScheduleTest {
    @Test
    fun monthlyScheduleSplitsMinorUnitRemainderDeterministicallyWithoutFinancialEffect() {
        val result = SetDebtSchedule().plan(
            debt = debt(remaining = 10_001L),
            installmentCount = 3,
            firstDueDate = LocalDate.parse("2026-11-10"),
            reminderLeadDays = 3,
            firstInstallmentNumber = 1,
            operationId = OPERATION_ID,
        )

        assertTrue(result is DebtPlanResult.Valid)
        val plan = (result as DebtPlanResult.Valid).value
        assertEquals(listOf(3_334L, 3_334L, 3_333L), plan.installments.map { it.principalMinor })
        assertEquals(10_001L, plan.installments.sumOf { it.principalMinor })
        assertEquals(listOf(1, 2, 3), plan.installments.map { it.installmentNumber })
        assertEquals(
            listOf("2026-11-10", "2026-12-10", "2027-01-10"),
            plan.installments.map { it.dueDate.toString() },
        )
        assertEquals(0L, plan.cashDeltaMinor)
        assertEquals(0L, plan.principalDeltaMinor)
        assertEquals(3, plan.reminderLeadDays)
    }

    @Test
    fun invalidScheduleCountReminderAndInactiveDebtAreRejected() {
        val planner = SetDebtSchedule()
        assertEquals(
            DebtPlanResult.Invalid("INVALID_INSTALLMENT_COUNT"),
            planner.plan(debt(), 0, LocalDate.parse("2026-11-10"), null, 1, OPERATION_ID),
        )
        assertEquals(
            DebtPlanResult.Invalid("INVALID_REMINDER_LEAD_DAYS"),
            planner.plan(debt(), 2, LocalDate.parse("2026-11-10"), 366, 1, OPERATION_ID),
        )
        assertEquals(
            DebtPlanResult.Invalid("DEBT_NOT_ACTIVE"),
            planner.plan(debt().copy(status = DebtLifecycleStatus.CANCELLED), 2, LocalDate.parse("2026-11-10"), null, 1, OPERATION_ID),
        )
    }

    @Test
    fun closureActionsKeepAccountingNeutralAndRequireAuditableReasons() {
        val closer = CloseDebt()
        val cancelled = closer.plan(
            debt(),
            DebtClosureCommand(identity(), DEBT_ID, 4L, CloseDebtAction.CANCEL, reason = "Acuerdo cancelado"),
        )
        assertTrue(cancelled is DebtPlanResult.Valid)
        assertEquals(DebtLifecycleStatus.CANCELLED, (cancelled as DebtPlanResult.Valid).value.status)
        assertEquals(8_000L, cancelled.value.remainingPrincipalMinor)
        assertEquals(0L, cancelled.value.cashDeltaMinor)

        assertEquals(
            DebtPlanResult.Invalid("REASON_REQUIRED"),
            closer.plan(debt(), DebtClosureCommand(identity(), DEBT_ID, 4L, CloseDebtAction.FORGIVE, amountMinor = 8_000L)),
        )
        val forgiven = closer.plan(
            debt(),
            DebtClosureCommand(identity(), DEBT_ID, 4L, CloseDebtAction.FORGIVE, amountMinor = 8_000L, reason = "Condonación acordada"),
        )
        assertTrue(forgiven is DebtPlanResult.Valid)
        assertEquals(DebtLifecycleStatus.SETTLED, (forgiven as DebtPlanResult.Valid).value.status)
        assertEquals(0L, forgiven.value.remainingPrincipalMinor)
        assertEquals(-8_000L, forgiven.value.principalDeltaMinor)
        assertEquals(0L, forgiven.value.cashDeltaMinor)
    }

    @Test
    fun settleRequiresZeroDerivedBalanceAndAdjustmentCannotMakeBalanceNegative() {
        val closer = CloseDebt()
        assertEquals(
            DebtPlanResult.Invalid("BALANCE_REMAINS"),
            closer.plan(debt(), DebtClosureCommand(identity(), DEBT_ID, 4L, CloseDebtAction.SETTLE, reason = "Pago completo")),
        )
        assertEquals(
            DebtPlanResult.Invalid("ADJUSTMENT_EXCEEDS_BALANCE"),
            closer.plan(
                debt(),
                DebtClosureCommand(identity(), DEBT_ID, 4L, CloseDebtAction.ADJUST, principalDeltaMinor = -8_001L, reason = "Corrección"),
            ),
        )
    }

    private fun debt(remaining: Long = 8_000L) = DebtSummary(
        debtId = DEBT_ID,
        userId = OWNER_ID,
        obligationType = DebtObligationType.PAYABLE,
        counterpartyName = "Proveedor",
        principalMinor = 10_000L,
        remainingPrincipalMinor = remaining,
        currencyCode = "PEN",
        openedOn = LocalDate.parse("2026-10-01"),
        dueDate = null,
        reminderLeadDays = null,
        notes = null,
        status = DebtLifecycleStatus.ACTIVE,
        syncState = "SYNCED",
        revision = 4L,
        openingMode = DebtOpeningMode.HISTORICAL,
    )

    private fun identity() = DebtCommandIdentity(OPERATION_ID, "a".repeat(64))

    private companion object {
        const val OWNER_ID = "88000000-0000-4000-8000-000000000001"
        const val DEBT_ID = "88000000-0000-4000-8000-000000000002"
        const val OPERATION_ID = "88000000-0000-4000-8000-000000000003"
    }
}
