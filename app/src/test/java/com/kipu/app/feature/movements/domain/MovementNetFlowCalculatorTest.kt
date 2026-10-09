package com.kipu.app.feature.movements.domain

import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.Transaction
import com.kipu.app.feature.movements.domain.model.TransactionStatus
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Test

class MovementNetFlowCalculatorTest {
    private fun movement(type: MovementType = MovementType.INCOME, amount: Long = 100L) = Transaction(
        id = "movement", userId = "owner", type = type, amountMinor = amount, currency = "PEN",
        sourceAccountId = "account", categoryId = "category", occurredAt = 0L,
        destinationAccountId = if (type == MovementType.TRANSFER) "other-account" else null,
    )

    @Test fun incomeMinusExpensesUsesIntegerMinorUnits() {
        assertEquals(mapOf("PEN" to BigInteger.valueOf(70)), MovementNetFlowCalculator.calculate(
            listOf(movement(amount = 100), movement(MovementType.EXPENSE, 30))))
    }

    @Test fun expenseOnlyHistoryCanHaveNegativeNetFlow() {
        assertEquals(mapOf("PEN" to BigInteger.valueOf(-30)),
            MovementNetFlowCalculator.calculate(listOf(movement(MovementType.EXPENSE, 30))))
    }

    @Test fun currenciesAreNormalizedAndNeverCombined() {
        assertEquals(mapOf("PEN" to BigInteger.valueOf(100), "USD" to BigInteger.valueOf(-30)),
            MovementNetFlowCalculator.calculate(listOf(movement().copy(currency = "pen"),
                movement(MovementType.EXPENSE, 30).copy(currency = "usd"))))
    }

    @Test fun activeConfirmedAndRevisedHistoryContributes() {
        val transactions = TransactionStatus.entries.map { movement().copy(status = it) }
        assertEquals(mapOf("PEN" to BigInteger.valueOf(300)), MovementNetFlowCalculator.calculate(transactions))
    }

    @Test fun openingsAdjustmentsReversalsAndLegacyCardPaymentsAreExcluded() {
        val transactions = listOf("OPENING", "ADJUSTMENT", "REVERSAL", "CARD_PAYMENT_CASH")
            .map { movement().copy(legacyKind = it) }
        assertEquals(emptyMap<String, BigInteger>(), MovementNetFlowCalculator.calculate(transactions))
    }

    @Test fun transfersAndCardPaymentsDoNotCreateIncomeOrExpense() {
        val transfer = movement(MovementType.TRANSFER)
        val payment = transfer.copy(operationKind = "CARD_PAYMENT", destinationAccountId = null, cardId = "card")
        assertEquals(emptyMap<String, BigInteger>(), MovementNetFlowCalculator.calculate(listOf(transfer, payment)))
    }

    @Test fun cardPurchasesContributeAsExpenses() {
        val purchase = movement(MovementType.EXPENSE, 50).copy(sourceAccountId = null,
            operationKind = "CARD_PURCHASE", cardId = "card", installmentCount = 3)
        assertEquals(mapOf("PEN" to BigInteger.valueOf(-50)), MovementNetFlowCalculator.calculate(listOf(purchase)))
    }

    @Test fun aggregationDoesNotOverflowLong() {
        val maximum = movement(amount = Long.MAX_VALUE)
        assertEquals(mapOf("PEN" to BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO)),
            MovementNetFlowCalculator.calculate(listOf(maximum, maximum.copy(id = "second"))))
    }

    @Test fun emptyHistoryHasNoCurrencyTotals() {
        assertEquals(emptyMap<String, BigInteger>(), MovementNetFlowCalculator.calculate(emptyList()))
    }
}
