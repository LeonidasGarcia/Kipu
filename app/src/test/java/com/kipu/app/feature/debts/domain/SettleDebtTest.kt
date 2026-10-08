package com.kipu.app.feature.debts.domain

import com.kipu.app.feature.debts.domain.model.DebtCommandIdentity
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtPlanResult
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.feature.debts.domain.model.DebtSettlementPlan
import com.kipu.app.feature.debts.domain.model.SettleDebtCommand
import com.kipu.app.feature.debts.domain.usecase.SettleDebt
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettleDebtTest {
    private val useCase = SettleDebt()

    @Test
    fun `payable settlement splits principal and categorized interest`() {
        val result = plan(DebtObligationType.PAYABLE)

        assertTrue(result is DebtPlanResult.Valid)
        val settlement = (result as DebtPlanResult.Valid).value
        assertEquals(-2_100L, settlement.cashDeltaMinor)
        assertEquals(-2_000L, settlement.principalDeltaMinor)
        assertEquals(0L, settlement.operatingIncomeMinor)
        assertEquals(100L, settlement.operatingExpenseMinor)
        assertEquals("EXPENSE", settlement.principalMovementType)
        assertEquals("DEBT_PAYMENT", settlement.principalOperationKind)
        assertEquals("EXPENSE", settlement.interestMovementType)
        assertEquals("DEBT_AMORTIZATION", settlement.interestOperationKind)
        assertEquals(8_000L, settlement.remainingPrincipalMinor)
    }

    @Test
    fun `receivable settlement adds principal and interest to cash but only interest is operating income`() {
        val result = plan(DebtObligationType.RECEIVABLE)

        assertTrue(result is DebtPlanResult.Valid)
        val settlement = (result as DebtPlanResult.Valid).value
        assertEquals(2_100L, settlement.cashDeltaMinor)
        assertEquals(-2_000L, settlement.principalDeltaMinor)
        assertEquals(100L, settlement.operatingIncomeMinor)
        assertEquals(0L, settlement.operatingExpenseMinor)
        assertEquals("INCOME", settlement.principalMovementType)
        assertEquals("DEBT_PAYMENT", settlement.principalOperationKind)
        assertEquals("INCOME", settlement.interestMovementType)
        assertEquals("DEBT_AMORTIZATION", settlement.interestOperationKind)
    }

    @Test
    fun `over settlement and stale revision are rejected`() {
        assertEquals(
            "PRINCIPAL_EXCEEDS_REMAINING",
            invalidCode(plan(DebtObligationType.PAYABLE, principal = 10_001L)),
        )
        assertEquals(
            "DEBT_REVISION_CONFLICT",
            invalidCode(plan(DebtObligationType.PAYABLE, expectedRevision = 6L)),
        )
    }

    @Test
    fun `interest requires an eligible expense category for payable debt`() {
        assertEquals(
            "INTEREST_CATEGORY_REQUIRED",
            invalidCode(plan(DebtObligationType.PAYABLE, categoryId = null)),
        )
    }

    @Test
    fun `currency mismatch and installment over allocation are rejected`() {
        assertEquals(
            "ACCOUNT_CURRENCY_MISMATCH",
            invalidCode(plan(DebtObligationType.PAYABLE, accountCurrency = "USD")),
        )
        assertEquals(
            "INSTALLMENT_PRINCIPAL_EXCEEDED",
            invalidCode(plan(DebtObligationType.PAYABLE, installmentRemaining = 1_999L)),
        )
    }

    @Test
    fun `stable principal and interest movement ids form one void group`() {
        val result = plan(DebtObligationType.PAYABLE)

        assertTrue(result is DebtPlanResult.Valid)
        val settlement = (result as DebtPlanResult.Valid).value
        assertEquals(
            listOf(settlement.principalTransactionId, settlement.interestTransactionId),
            settlement.groupedTransactionIds,
        )
        assertEquals(2, settlement.groupedTransactionIds.distinct().size)
    }

    private fun plan(
        type: DebtObligationType,
        principal: Long = 2_000L,
        interest: Long = 100L,
        expectedRevision: Long = 5L,
        accountCurrency: String = "PEN",
        categoryId: String? = "interest-category",
        installmentRemaining: Long? = null,
    ): DebtPlanResult<DebtSettlementPlan> = useCase.plan(
        debt = debt(type),
        command = command(principal, interest, expectedRevision, categoryId, installmentRemaining),
        accountCurrencyCode = accountCurrency,
        installmentPrincipalRemainingMinor = installmentRemaining,
    )

    private fun invalidCode(result: DebtPlanResult<*>): String =
        (result as DebtPlanResult.Invalid).code

    private fun debt(type: DebtObligationType) = DebtSummary(
        debtId = DEBT_ID,
        userId = OWNER_ID,
        obligationType = type,
        counterpartyName = "Tienda",
        principalMinor = 10_000L,
        remainingPrincipalMinor = 10_000L,
        currencyCode = "PEN",
        openedOn = LocalDate.parse("2026-10-01"),
        dueDate = null,
        reminderLeadDays = null,
        notes = null,
        status = DebtLifecycleStatus.ACTIVE,
        syncState = "SYNCED",
        revision = 5L,
    )

    private fun command(
        principal: Long,
        interest: Long,
        expectedRevision: Long,
        categoryId: String?,
        installmentRemaining: Long?,
    ) = SettleDebtCommand(
        identity = DebtCommandIdentity(OPERATION_ID, DebtCommandHasher.sha256("settle-debt")),
        debtId = DEBT_ID,
        expectedRevision = expectedRevision,
        accountId = ACCOUNT_ID,
        principalMinor = principal,
        interestMinor = interest,
        interestCategoryId = categoryId,
        installmentId = INSTALLMENT_ID.takeIf { installmentRemaining != null },
        occurredAt = 1_791_000_000_000L,
    )

    private companion object {
        const val OWNER_ID = "82000000-0000-4000-8000-000000000001"
        const val DEBT_ID = "82000000-0000-4000-8000-000000000002"
        const val OPERATION_ID = "82000000-0000-4000-8000-000000000003"
        const val ACCOUNT_ID = "82000000-0000-4000-8000-000000000004"
        const val INSTALLMENT_ID = "82000000-0000-4000-8000-000000000005"
    }
}
