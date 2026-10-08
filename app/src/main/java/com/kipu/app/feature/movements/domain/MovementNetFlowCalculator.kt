package com.kipu.app.feature.movements.domain

import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.Transaction
import com.kipu.app.feature.movements.domain.model.TransactionStatus
import java.math.BigInteger
import java.util.Locale

/** Net income minus expenses in the supplied history, per currency; never an account balance. */
object MovementNetFlowCalculator {
    private val nonFlowKinds = setOf("OPENING", "ADJUSTMENT", "REVERSAL", "CARD_PAYMENT_CASH")
    private val nonOperatingDebtKinds = setOf("DEBT_DISBURSEMENT", "DEBT_PAYMENT")

    fun calculate(transactions: Iterable<Transaction>): Map<String, BigInteger> {
        val totals = mutableMapOf<String, BigInteger>()
        for (transaction in transactions) {
            if (transaction.status != TransactionStatus.ACTIVE && transaction.status != TransactionStatus.CONFIRMED) continue
            val operationKind = transaction.operationKind ?: transaction.legacyKind
            if (transaction.legacyKind in nonFlowKinds ||
                operationKind?.uppercase(Locale.ROOT) in nonOperatingDebtKinds ||
                transaction.type == MovementType.TRANSFER
            ) continue
            val currency = transaction.currency.uppercase(Locale.ROOT)
            val amount = BigInteger.valueOf(transaction.amountMinor)
            val delta = if (transaction.type == MovementType.INCOME) amount else amount.negate()
            totals[currency] = (totals[currency] ?: BigInteger.ZERO) + delta
        }
        return totals.toMap()
    }
}
