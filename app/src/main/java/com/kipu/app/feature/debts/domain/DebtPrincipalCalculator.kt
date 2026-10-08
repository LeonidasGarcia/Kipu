package com.kipu.app.feature.debts.domain

import com.kipu.app.feature.debts.domain.model.DebtEventType
import com.kipu.app.feature.debts.domain.model.DebtPrincipalEvent
import java.math.BigInteger

/** Derives remaining principal from the opening amount and valid principal deltas. */
object DebtPrincipalCalculator {
    fun remainingPrincipal(
        openingPrincipalMinor: Long,
        events: Iterable<DebtPrincipalEvent>,
    ): Long {
        require(openingPrincipalMinor >= 0L) { "Opening principal cannot be negative" }

        var remaining = BigInteger.valueOf(openingPrincipalMinor)
        for (event in events) {
            require(event.amountMinor > 0L) { "Event amount must be positive" }
            if (event.linkedTransactionVoided) continue

            val delta = event.principalDeltaMinor ?: when (event.eventType) {
                DebtEventType.PAYMENT -> -event.amountMinor
                DebtEventType.DISBURSEMENT,
                DebtEventType.ADJUSTMENT,
                DebtEventType.FORGIVENESS
                -> 0L
            }
            remaining += BigInteger.valueOf(delta)
        }

        return remaining.max(BigInteger.ZERO).min(BigInteger.valueOf(Long.MAX_VALUE)).toLong()
    }
}
