package com.kipu.app.feature.accounts.domain

data class DuePrincipalInstallment(
    val id: String,
    val dueDateEpochDay: Long,
    val purchaseOccurredAtMillis: Long,
    val installmentNumber: Int,
    val outstandingPrincipalMinor: Long,
)

data class PrincipalPaymentAllocation(
    val installmentId: String,
    val allocatedMinor: Long,
    val fullyPaid: Boolean,
)

/** Applies a payment to posted principal in strict due-date FIFO order. */
object CreditPaymentAllocator {
    fun allocate(paymentMinor: Long, installments: List<DuePrincipalInstallment>): List<PrincipalPaymentAllocation> {
        require(paymentMinor > 0L) { "Payment must be positive" }
        require(installments.all { it.outstandingPrincipalMinor > 0L }) { "Outstanding principal must be positive" }
        val ordered = installments.sortedWith(
            compareBy<DuePrincipalInstallment>(
                { it.dueDateEpochDay },
                { it.purchaseOccurredAtMillis },
                { it.installmentNumber },
                { it.id },
            ),
        )
        val totalOutstanding = ordered.fold(0L) { sum, item -> Math.addExact(sum, item.outstandingPrincipalMinor) }
        require(paymentMinor <= totalOutstanding) { "Payment exceeds outstanding principal" }

        var remaining = paymentMinor
        return ordered.mapNotNull { installment ->
            if (remaining == 0L) return@mapNotNull null
            val applied = minOf(remaining, installment.outstandingPrincipalMinor)
            remaining -= applied
            PrincipalPaymentAllocation(
                installmentId = installment.id,
                allocatedMinor = applied,
                fullyPaid = applied == installment.outstandingPrincipalMinor,
            )
        }.also { check(remaining == 0L) { "Payment allocation is incomplete" } }
    }
}
