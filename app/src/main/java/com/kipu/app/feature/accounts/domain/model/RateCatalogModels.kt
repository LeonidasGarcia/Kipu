package com.kipu.app.feature.accounts.domain.model

import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import java.time.Instant
import java.time.LocalDate

data class RateReference(
    val id: String,
    val institution: String,
    val productName: String,
    val currency: Currency,
    val minTeaBps: Int,
    val maxTeaBps: Int,
    val verifiedAt: LocalDate,
    val isOutdated: Boolean = false,
) {
    init {
        require(minTeaBps >= 0) { "minTeaBps cannot be negative" }
        require(maxTeaBps >= minTeaBps) { "maxTeaBps must be >= minTeaBps" }
    }

    val minTeaPercentage: Double
        get() = minTeaBps / 100.0

    val maxTeaPercentage: Double
        get() = maxTeaBps / 100.0

    companion object {
        const val DISCLAIMER: String =
            "Las tasas mostradas son referenciales y provienen de tarifarios públicos de entidades reguladas por la SBS. La tasa real de tu tarjeta depende exclusivamente de tu contrato con la entidad emisora."

        val DEFAULT_PERU_CATALOG: List<RateReference> = listOf(
            RateReference("bcp-classic-pen", "BCP", "Visa Clásica", Currency.PEN, 2990, 8990, LocalDate.of(2026, 1, 15)),
            RateReference("bcp-gold-pen", "BCP", "Visa Oro", Currency.PEN, 2690, 7990, LocalDate.of(2026, 1, 15)),
            RateReference("bbva-zero-pen", "BBVA", "Visa Cero", Currency.PEN, 3500, 8490, LocalDate.of(2026, 2, 1)),
            RateReference("bbva-signature-pen", "BBVA", "Visa Signature", Currency.PEN, 2190, 6990, LocalDate.of(2026, 2, 1)),
            RateReference("ibk-benefit-pen", "Interbank", "Visa Benefit", Currency.PEN, 3100, 8600, LocalDate.of(2026, 1, 20)),
            RateReference("scotia-smart-pen", "Scotiabank", "Mastercard Smart", Currency.PEN, 2800, 8200, LocalDate.of(2026, 2, 10)),
            RateReference("bcp-classic-usd", "BCP", "Visa Clásica USD", Currency.USD, 1990, 4990, LocalDate.of(2026, 1, 15)),
            RateReference("bbva-gold-usd", "BBVA", "Visa Oro USD", Currency.USD, 1890, 4590, LocalDate.of(2026, 2, 1)),
        )
    }
}

data class PersonalTea(
    val cardId: CardId,
    val teaBps: Int,
    val effectiveFrom: Instant = Instant.now(),
) {
    init {
        require(teaBps in 0..100_000) { "TEA basis points must be between 0 and 100,000 (0% to 1000%): $teaBps" }
    }

    val percentage: Double
        get() = teaBps / 100.0
}
