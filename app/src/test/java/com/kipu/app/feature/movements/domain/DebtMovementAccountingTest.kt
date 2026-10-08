package com.kipu.app.feature.movements.domain

import com.kipu.app.feature.movements.domain.model.ExpenseConsumptionQuery
import com.kipu.app.feature.movements.domain.model.MovementFinancialState
import com.kipu.app.feature.movements.domain.model.MovementRevisionHead
import com.kipu.app.feature.movements.domain.model.MovementRevisionPayload
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.Transaction
import java.math.BigInteger
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class DebtMovementAccountingTest {
    @Test
    fun `debt principal affects cash but not operating net flow while interest does`() {
        val movements = listOf(
            transaction("payable-opening", MovementType.INCOME, 2_000L, "DEBT_DISBURSEMENT"),
            transaction("receivable-opening", MovementType.EXPENSE, 300L, "DEBT_DISBURSEMENT"),
            transaction("payable-principal", MovementType.EXPENSE, 1_000L, "DEBT_PAYMENT"),
            transaction("receivable-principal", MovementType.INCOME, 700L, "DEBT_PAYMENT"),
            transaction("payable-interest", MovementType.EXPENSE, 30L, "STANDARD"),
            transaction("receivable-interest", MovementType.INCOME, 10L, "STANDARD"),
        )

        assertEquals(mapOf("PEN" to BigInteger.valueOf(-20L)), MovementNetFlowCalculator.calculate(movements))
    }

    @Test
    fun `budget consumption counts interest expense but excludes debt principal`() {
        val heads = listOf(
            head("principal", amountMinor = 5_000L, operationKind = "DEBT_PAYMENT"),
            head("interest", amountMinor = 250L, operationKind = "STANDARD"),
        )

        val result = ExpenseConsumptionCalculator.calculate(
            heads = heads,
            query = ExpenseConsumptionQuery("PEN", 0L, 10L, ZoneOffset.UTC),
        )

        assertEquals(250L, result.amountMinor)
    }

    private fun transaction(id: String, type: MovementType, amount: Long, operationKind: String) = Transaction(
        id = id,
        userId = "owner",
        type = type,
        amountMinor = amount,
        currency = "PEN",
        sourceAccountId = "account",
        categoryId = if (operationKind == "STANDARD" && type == MovementType.EXPENSE) "interest" else null,
        legacyKind = "DEBT_PRINCIPAL".takeIf { operationKind != "STANDARD" },
        occurredAt = 1L,
        operationKind = operationKind,
    )

    private fun head(id: String, amountMinor: Long, operationKind: String) = MovementRevisionHead(
        transactionId = id,
        userId = "owner",
        payload = MovementRevisionPayload(
            type = MovementType.EXPENSE,
            operationKind = operationKind,
            amountMinor = amountMinor,
            currency = "PEN",
            sourceAccountId = "account",
            categoryId = if (operationKind == "STANDARD") "interest" else null,
            occurredAt = 1L,
        ),
        financialState = MovementFinancialState.CONFIRMED,
        officialRevision = 1L,
    )
}
