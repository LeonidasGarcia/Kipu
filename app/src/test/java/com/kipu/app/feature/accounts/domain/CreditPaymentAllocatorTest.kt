package com.kipu.app.feature.accounts.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditPaymentAllocatorTest {
    @Test
    fun `payment amortizes the oldest due installment first then partially reduces the current one`() {
        val schedule = listOf(
            DuePrincipalInstallment("later", dueDateEpochDay = 20, purchaseOccurredAtMillis = 1, installmentNumber = 1, outstandingPrincipalMinor = 300),
            DuePrincipalInstallment("oldest", dueDateEpochDay = 10, purchaseOccurredAtMillis = 2, installmentNumber = 1, outstandingPrincipalMinor = 100),
            DuePrincipalInstallment("same-day-later", dueDateEpochDay = 10, purchaseOccurredAtMillis = 3, installmentNumber = 1, outstandingPrincipalMinor = 200),
        )

        val allocations = CreditPaymentAllocator.allocate(paymentMinor = 250, installments = schedule)

        assertEquals(listOf("oldest", "same-day-later"), allocations.map { it.installmentId })
        assertEquals(listOf(100L, 150L), allocations.map { it.allocatedMinor })
        assertTrue(allocations.first().fullyPaid)
        assertFalse(allocations.last().fullyPaid)
    }

    @Test
    fun `payment rejects amounts above all posted principal`() {
        val error = runCatching {
            CreditPaymentAllocator.allocate(
                paymentMinor = 101,
                installments = listOf(
                    DuePrincipalInstallment("one", 1, 1, 1, 100),
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
}
