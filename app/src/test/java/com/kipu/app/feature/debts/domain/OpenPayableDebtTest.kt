package com.kipu.app.feature.debts.domain

import com.kipu.app.feature.debts.domain.model.DebtCommandIdentity
import com.kipu.app.feature.debts.domain.model.DebtDescriptionPatch
import com.kipu.app.feature.debts.domain.model.DebtDescriptionState
import com.kipu.app.feature.debts.domain.model.DebtDetailsEditResult
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.feature.debts.domain.model.DebtPlanResult
import com.kipu.app.feature.debts.domain.model.OpenDebtCommand
import com.kipu.app.feature.debts.domain.usecase.OpenPayableDebt
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenPayableDebtTest {
    @Test
    fun `new payable opening increases cash and liability by the same principal`() {
        val result = OpenPayableDebt().plan(command(DebtOpeningMode.NEW_CASH_FLOW, "cash-1"))

        assertTrue(result is DebtPlanResult.Valid)
        val plan = (result as DebtPlanResult.Valid).value
        assertEquals(5_000L, plan.principalMinor)
        assertEquals(5_000L, plan.cashDeltaMinor)
        assertEquals("DEBT_DISBURSEMENT", plan.operationKind)
        assertEquals("INCOME", plan.movementType)
    }

    @Test
    fun `historical payable opening records liability without cash flow`() {
        val result = OpenPayableDebt().plan(command(DebtOpeningMode.HISTORICAL, null))

        assertTrue(result is DebtPlanResult.Valid)
        val plan = (result as DebtPlanResult.Valid).value
        assertEquals(5_000L, plan.principalMinor)
        assertEquals(0L, plan.cashDeltaMinor)
        assertEquals(null, plan.accountId)
    }

    @Test
    fun `descriptive edit cannot change principal currency status or opening date`() {
        val original = DebtDescriptionState(
            debtId = "debt-1",
            counterpartyName = "Proveedor",
            principalMinor = 5_000L,
            currencyCode = "PEN",
            openedOn = LocalDate.parse("2026-10-01"),
            dueDate = null,
            notes = null,
            status = DebtLifecycleStatus.ACTIVE,
            revision = 1L,
        )
        val result = DebtDescriptionEditor.apply(
            original,
            DebtDescriptionPatch(
                counterpartyName = "Proveedor nuevo",
                dueDate = LocalDate.parse("2026-12-01"),
                notes = "Renovación del acuerdo",
                expectedRevision = 1L,
            ),
        )

        assertTrue(result is DebtDetailsEditResult.Applied)
        val edited = (result as DebtDetailsEditResult.Applied).state
        assertEquals(5_000L, edited.principalMinor)
        assertEquals("PEN", edited.currencyCode)
        assertEquals(LocalDate.parse("2026-10-01"), edited.openedOn)
        assertEquals(DebtLifecycleStatus.ACTIVE, edited.status)
        assertEquals("Proveedor nuevo", edited.counterpartyName)
        assertEquals(2L, edited.revision)
    }

    private fun command(mode: DebtOpeningMode, accountId: String?) = OpenDebtCommand(
        identity = DebtCommandIdentity(
            "82000000-0000-4000-8000-000000000001",
            DebtCommandHasher.sha256("payable:$mode:$accountId"),
        ),
        debtId = "82000000-0000-4000-8000-000000000002",
        obligationType = DebtObligationType.PAYABLE,
        counterpartyName = "  Proveedor   del   barrio ",
        principalMinor = 5_000L,
        currencyCode = "PEN",
        openedOn = LocalDate.parse("2026-10-01"),
        openingMode = mode,
        accountId = accountId,
    )
}
