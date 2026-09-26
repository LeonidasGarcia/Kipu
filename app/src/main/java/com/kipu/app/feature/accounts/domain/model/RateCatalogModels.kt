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
    }
}

data class CreditProductReference(
    val id: String,
    val institutionCode: String,
    val institutionName: String,
    val productName: String,
    val cardNetwork: String?,
    val penTeaMinBps: Int?,
    val penTeaMaxBps: Int?,
    val usdTeaMinBps: Int?,
    val usdTeaMaxBps: Int?,
    val publishedTeaSummary: String?,
    val publishedTceaSummary: String?,
    val membershipFeePenMinor: Long?,
    val membershipFeeUsdMinor: Long?,
    val membershipCondition: String?,
    val sourceUrl: String?,
    val verificationStatus: String?,
    val catalogAsOf: LocalDate?,
    val effectiveTo: LocalDate?,
)

data class CreditUtilizationNotification(
    val id: String,
    val cardId: String,
    val title: String,
    val body: String,
    val eventKey: String?,
    val createdAt: String,
)

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
