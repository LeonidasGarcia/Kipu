package com.kipu.app.feature.accounts.domain

import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.CandidateStatus
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.PurchaseCandidate
import com.kipu.app.feature.accounts.domain.model.RateSource
import com.kipu.app.feature.accounts.domain.usecase.SimulateInstallments
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class SimulateInstallmentsTest {
    private val cardId = CardId.generate()
    private val card = CreditCard(
        id = cardId,
        userId = UserId.generate(),
        alias = "Principal",
        issuer = "BCP",
        network = CardNetwork.VISA,
        lastFourDigits = "1234",
        currency = Currency.PEN,
        creditLimitMinorUnits = 500_000L,
        billingDay = 15,
        dueDay = 5,
        personalTeaBps = 3500,
    )

    @Test
    fun `uses personal TEA and purchase cycle date without posting accounting`() {
        val candidate = PurchaseCandidate(
            id = "candidate-1",
            cardId = cardId,
            amount = Money(10_000L, Currency.PEN),
            merchant = "Comercio",
            occurredAt = Instant.parse("2026-09-16T12:00:00Z"),
            suggestedInstallments = 3,
            status = CandidateStatus.PENDING,
        )

        val estimate = SimulateInstallments()(candidate, card)

        assertEquals(RateSource.PERSONAL_TEA, estimate.rateSource)
        assertEquals(3500, estimate.appliedTeaBps)
        assertEquals("candidate-1", estimate.candidateId)
        assertEquals("2026-11-05", estimate.schedule.first().dueDate.toString())
        assertEquals(10_000L, estimate.principal.minorUnits)
    }

    @Test
    fun `uses a referential TEA only when caller supplies the accepted rate`() {
        val noPersonalRate = card.copy(personalTeaBps = null)
        val candidate = PurchaseCandidate(
            id = "candidate-2",
            cardId = cardId,
            amount = Money(10_000L, Currency.PEN),
            merchant = "Comercio",
            occurredAt = Instant.parse("2026-09-15T12:00:00Z"),
            suggestedInstallments = 3,
        )

        val withoutAcceptance = SimulateInstallments()(candidate, noPersonalRate)
        val accepted = SimulateInstallments()(candidate, noPersonalRate, acceptedReferenceTeaBps = 3000)

        assertEquals(RateSource.NONE, withoutAcceptance.rateSource)
        assertEquals(0L, withoutAcceptance.totalInterest.minorUnits)
        assertEquals(RateSource.REFERENTIAL_CATALOG, accepted.rateSource)
        assertEquals(3000, accepted.appliedTeaBps)
    }
}
