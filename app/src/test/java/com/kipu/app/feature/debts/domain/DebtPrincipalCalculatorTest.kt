package com.kipu.app.feature.debts.domain

import com.kipu.app.feature.debts.domain.model.DebtEventType
import com.kipu.app.feature.debts.domain.model.DebtPrincipalEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class DebtPrincipalCalculatorTest {
    @Test
    fun `legacy PAYMENT amount reduces opening principal`() {
        assertEquals(
            750L,
            DebtPrincipalCalculator.remainingPrincipal(
                openingPrincipalMinor = 1_000L,
                events = listOf(DebtPrincipalEvent(DebtEventType.PAYMENT, amountMinor = 250L)),
            ),
        )
    }

    @Test
    fun `signed adjustment and forgiveness deltas change only principal`() {
        assertEquals(
            725L,
            DebtPrincipalCalculator.remainingPrincipal(
                openingPrincipalMinor = 1_000L,
                events = listOf(
                    DebtPrincipalEvent(DebtEventType.PAYMENT, amountMinor = 250L, principalDeltaMinor = -200L),
                    DebtPrincipalEvent(DebtEventType.ADJUSTMENT, amountMinor = 75L, principalDeltaMinor = -75L),
                    DebtPrincipalEvent(DebtEventType.FORGIVENESS, amountMinor = 50L, principalDeltaMinor = 0L),
                ),
            ),
        )
    }

    @Test
    fun `payment linked to a voided transaction no longer reduces derived balance`() {
        assertEquals(
            1_000L,
            DebtPrincipalCalculator.remainingPrincipal(
                openingPrincipalMinor = 1_000L,
                events = listOf(
                    DebtPrincipalEvent(
                        DebtEventType.PAYMENT,
                        amountMinor = 400L,
                        linkedTransactionVoided = true,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `settled balance is derived as zero from principal events`() {
        assertEquals(
            0L,
            DebtPrincipalCalculator.remainingPrincipal(
                openingPrincipalMinor = 800L,
                events = listOf(
                    DebtPrincipalEvent(DebtEventType.PAYMENT, amountMinor = 500L),
                    DebtPrincipalEvent(DebtEventType.PAYMENT, amountMinor = 300L),
                ),
            ),
        )
    }
}
