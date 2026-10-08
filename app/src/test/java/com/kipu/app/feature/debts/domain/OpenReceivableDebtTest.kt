package com.kipu.app.feature.debts.domain

import com.kipu.app.feature.debts.domain.model.DebtCommandIdentity
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.feature.debts.domain.model.DebtPlanResult
import com.kipu.app.feature.debts.domain.model.OpenDebtCommand
import com.kipu.app.feature.debts.domain.usecase.OpenReceivableDebt
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenReceivableDebtTest {
    @Test
    fun `new receivable opening decreases cash by principal without operating expense`() {
        val result = OpenReceivableDebt().plan(command(DebtOpeningMode.NEW_CASH_FLOW, "cash-1"))

        assertTrue(result is DebtPlanResult.Valid)
        val plan = (result as DebtPlanResult.Valid).value
        assertEquals(5_000L, plan.principalMinor)
        assertEquals(-5_000L, plan.cashDeltaMinor)
        assertEquals("DEBT_DISBURSEMENT", plan.operationKind)
        assertEquals("EXPENSE", plan.movementType)
        assertEquals("cash-1", plan.accountId)
    }

    @Test
    fun `historical receivable opening preserves prior cash deduction`() {
        val result = OpenReceivableDebt().plan(command(DebtOpeningMode.HISTORICAL, null))

        assertTrue(result is DebtPlanResult.Valid)
        val plan = (result as DebtPlanResult.Valid).value
        assertEquals(5_000L, plan.principalMinor)
        assertEquals(0L, plan.cashDeltaMinor)
        assertEquals(null, plan.accountId)
    }

    @Test
    fun `new receivable requires a source account`() {
        val result = OpenReceivableDebt().plan(command(DebtOpeningMode.NEW_CASH_FLOW, null))

        assertEquals(DebtPlanResult.Invalid("ACCOUNT_REQUIRED"), result)
    }

    @Test
    fun `receivable opening rejects other obligation types`() {
        val result = OpenReceivableDebt().plan(
            command(DebtOpeningMode.HISTORICAL, null).copy(obligationType = DebtObligationType.PAYABLE),
        )

        assertEquals(DebtPlanResult.Invalid("WRONG_OBLIGATION_TYPE"), result)
    }

    private fun command(mode: DebtOpeningMode, accountId: String?) = OpenDebtCommand(
        identity = DebtCommandIdentity(
            "83000000-0000-4000-8000-000000000001",
            DebtCommandHasher.sha256("receivable:$mode:$accountId"),
        ),
        debtId = "83000000-0000-4000-8000-000000000002",
        obligationType = DebtObligationType.RECEIVABLE,
        counterpartyName = "  Familiar   del   barrio ",
        principalMinor = 5_000L,
        currencyCode = "PEN",
        openedOn = LocalDate.parse("2026-10-01"),
        openingMode = mode,
        accountId = accountId,
    )
}
