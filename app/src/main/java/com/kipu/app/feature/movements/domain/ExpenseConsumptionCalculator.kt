package com.kipu.app.feature.movements.domain

import com.kipu.app.feature.movements.domain.model.ExpenseConsumptionQuery
import com.kipu.app.feature.movements.domain.model.ExpenseConsumptionResult
import com.kipu.app.feature.movements.domain.model.MovementFinancialState
import com.kipu.app.feature.movements.domain.model.MovementRevisionHead
import com.kipu.app.feature.movements.domain.model.MovementType

/**
 * Pure in-memory calculation of expense consumption over authoritative movement revision heads.
 * Counts each movement once per effective payload, excluding VOIDED, non-expenses, and out-of-interval items.
 */
object ExpenseConsumptionCalculator {
    fun calculate(
        heads: Iterable<MovementRevisionHead>,
        query: ExpenseConsumptionQuery,
        datasetVersion: Long = 1L,
    ): ExpenseConsumptionResult {
        val total = heads
            .filter { head ->
                (head.financialState == MovementFinancialState.CONFIRMED || head.financialState == MovementFinancialState.REVISED) &&
                    head.payload.type == MovementType.EXPENSE &&
                    head.payload.currency == query.currency &&
                    head.payload.occurredAt >= query.fromInclusive &&
                    head.payload.occurredAt < query.toExclusive &&
                    (query.categoryIds.isEmpty() || head.payload.categoryId in query.categoryIds)
            }
            .sumOf { it.payload.amountMinor }
        return ExpenseConsumptionResult(total, datasetVersion)
    }
}
