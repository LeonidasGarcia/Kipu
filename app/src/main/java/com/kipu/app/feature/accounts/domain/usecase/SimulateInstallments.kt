package com.kipu.app.feature.accounts.domain.usecase

import com.kipu.app.core.finance.domain.CreditCalculations
import com.kipu.app.core.finance.domain.InstallmentCalculator
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.InstallmentSimulation
import com.kipu.app.feature.accounts.domain.model.PurchaseCandidate
import com.kipu.app.feature.accounts.domain.model.RateSource
import java.time.ZoneId
import javax.inject.Inject

/** Pure preview use case. It never persists a transaction, debt, or ledger entry. */
class SimulateInstallments @Inject constructor() {
    operator fun invoke(
        candidate: PurchaseCandidate,
        card: CreditCard,
        installmentsCount: Int = candidate.suggestedInstallments,
        acceptedReferenceTeaBps: Int? = null,
        zeroInterestPromotion: Boolean = false,
    ): InstallmentSimulation {
        require(candidate.cardId == card.id) { "Purchase candidate belongs to another card" }
        require(candidate.amount.currency == card.currency) { "Purchase currency does not match the card" }

        val personalTea = if (zeroInterestPromotion) null else card.personalTeaBps?.takeIf { it > 0 }
        val teaBps = if (zeroInterestPromotion) 0 else personalTea ?: acceptedReferenceTeaBps?.takeIf { it > 0 }
        val rateSource = when {
            zeroInterestPromotion -> RateSource.NONE
            personalTea != null -> RateSource.PERSONAL_TEA
            teaBps != null -> RateSource.REFERENTIAL_CATALOG
            else -> RateSource.NONE
        }
        val purchaseDateInPeru = candidate.occurredAt.atZone(ZoneId.of("America/Lima")).toLocalDate()
        val firstDueDate = CreditCalculations.calculateFirstInstallmentDueDate(
            purchaseDate = purchaseDateInPeru,
            preferredClosingDay = card.billingDay,
            preferredDueDay = card.dueDay,
        )

        val simulation = InstallmentCalculator.simulate(
            cardId = card.id,
            principal = candidate.amount,
            installmentsCount = installmentsCount,
            firstDueDate = firstDueDate,
            dueDay = card.dueDay,
            teaBps = teaBps,
            rateSource = rateSource,
            candidateId = candidate.id,
        )
        return if (zeroInterestPromotion) {
            simulation.copy(
                disclaimer = "Vista previa basada en la promoción sin intereses que indicaste del comercio. Kipu no verifica la promoción; el registro de la compra conserva únicamente el principal.",
            )
        } else simulation
    }
}
