package com.kipu.app.feature.accounts.domain.model

import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Money
import java.time.Instant
import java.time.LocalDate

enum class CandidateStatus {
    PENDING,
    CONFIRMED,
    REJECTED
}

enum class RateSource {
    PERSONAL_TEA,
    REFERENTIAL_CATALOG,
    NONE
}

data class PurchaseCandidate(
    val id: String,
    val cardId: CardId,
    val amount: Money,
    val merchant: String,
    val occurredAt: Instant,
    val suggestedInstallments: Int = 1,
    val status: CandidateStatus = CandidateStatus.PENDING,
    val categoryId: String? = null,
) {
    init {
        require(amount.minorUnits > 0) { "Purchase amount must be positive: ${amount.minorUnits}" }
        require(merchant.isNotBlank()) { "Merchant cannot be blank" }
        require(suggestedInstallments in 1..36) { "Suggested installments must be between 1 and 36: $suggestedInstallments" }
    }
}

data class InstallmentScheduleItem(
    val installmentNumber: Int,
    val dueDate: LocalDate,
    val amount: Money,
    val principalPortion: Money,
    val interestPortion: Money,
) {
    init {
        require(installmentNumber >= 1) { "Installment number must be >= 1: $installmentNumber" }
        require(amount.minorUnits >= 0) { "Installment amount cannot be negative" }
        require(principalPortion.currency == amount.currency && interestPortion.currency == amount.currency) {
            "Currency mismatch in installment schedule item"
        }
    }
}

data class InstallmentSimulation(
    val candidateId: String? = null,
    val cardId: CardId,
    val principal: Money,
    val installmentsCount: Int,
    val rateSource: RateSource,
    val appliedTeaBps: Int? = null,
    val appliedTemPercentage: Double? = null,
    val totalInterest: Money,
    val totalFinanced: Money,
    val schedule: List<InstallmentScheduleItem>,
    val disclaimer: String,
) {
    init {
        require(installmentsCount in 1..36) { "Installments count must be between 1 and 36: $installmentsCount" }
        require(schedule.size == installmentsCount) { "Schedule size (${schedule.size}) must match installments count ($installmentsCount)" }
        require(principal.currency == totalFinanced.currency && principal.currency == totalInterest.currency) {
            "Currency mismatch in simulation totals"
        }
        val sumSchedule = schedule.fold(0L) { acc, item -> Math.addExact(acc, item.amount.minorUnits) }
        require(sumSchedule == totalFinanced.minorUnits) {
            "Sum of schedule installments ($sumSchedule) does not match total financed (${totalFinanced.minorUnits})"
        }
    }

    companion object {
        const val DISCLAIMER_WITH_RATE =
            "Simulación informativa con tasa referencial o personal. No incluye seguro de desgravamen ni comisiones del emisor. Los intereses estimados no constituyen deuda real hasta su facturación contractual."

        const val DISCLAIMER_WITHOUT_RATE =
            "Distribución referencial sin intereses. Para estimar el costo financiero, configure la TEA de su tarjeta en el catálogo o ingrese su tasa contractual."
    }
}
