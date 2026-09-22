package com.kipu.app.feature.accounts.domain

import com.kipu.app.core.finance.domain.InstallmentCalculator
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.CandidateStatus
import com.kipu.app.feature.accounts.domain.model.InstallmentSimulation
import com.kipu.app.feature.accounts.domain.model.PurchaseCandidate
import com.kipu.app.feature.accounts.domain.model.RateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class InstallmentSimulationTest {

    private val cardId = CardId.generate()

    @Test
    fun `single installment has zero interest and exact amount`() {
        val principal = Money(15000, Currency.PEN) // S/ 150.00
        val simulation = InstallmentCalculator.simulate(
            cardId = cardId,
            principal = principal,
            installmentsCount = 1,
            firstDueDate = LocalDate.of(2026, 3, 15),
            dueDay = 15,
            teaBps = 3500 // 35%
        )

        assertEquals(1, simulation.installmentsCount)
        assertEquals(0L, simulation.totalInterest.minorUnits)
        assertEquals(principal, simulation.totalFinanced)
        assertEquals(1, simulation.schedule.size)
        assertEquals(principal, simulation.schedule[0].amount)
        assertNull(simulation.appliedTeaBps)
        assertEquals(InstallmentSimulation.DISCLAIMER_WITHOUT_RATE, simulation.disclaimer)
    }

    @Test
    fun `simulation without TEA distributes principal cents exactly`() {
        val principal = Money(10000, Currency.PEN) // S/ 100.00 in 3 installments -> 33.34, 33.33, 33.33
        val simulation = InstallmentCalculator.simulate(
            cardId = cardId,
            principal = principal,
            installmentsCount = 3,
            firstDueDate = LocalDate.of(2026, 4, 20),
            dueDay = 20,
            teaBps = null
        )

        assertEquals(3, simulation.schedule.size)
        assertEquals(3334L, simulation.schedule[0].amount.minorUnits)
        assertEquals(3333L, simulation.schedule[1].amount.minorUnits)
        assertEquals(3333L, simulation.schedule[2].amount.minorUnits)

        val totalSum = simulation.schedule.sumOf { it.amount.minorUnits }
        assertEquals(principal.minorUnits, totalSum)
        assertEquals(0L, simulation.totalInterest.minorUnits)
        assertEquals(RateSource.NONE, simulation.rateSource)
    }

    @Test
    fun `simulation with TEA distributes total financed cents deterministically and sum matches totalFinanced`() {
        val principal = Money(150000, Currency.PEN) // S/ 1500.00
        val simulation = InstallmentCalculator.simulate(
            cardId = cardId,
            principal = principal,
            installmentsCount = 6,
            firstDueDate = LocalDate.of(2026, 3, 10),
            dueDay = 10,
            teaBps = 3500, // 35% TEA
            rateSource = RateSource.PERSONAL_TEA
        )

        assertEquals(6, simulation.schedule.size)
        assertEquals(3500, simulation.appliedTeaBps)
        assertTrue(simulation.totalInterest.minorUnits > 0L)
        assertEquals(
            simulation.totalFinanced.minorUnits,
            principal.minorUnits + simulation.totalInterest.minorUnits
        )

        val scheduleSum = simulation.schedule.sumOf { it.amount.minorUnits }
        assertEquals(simulation.totalFinanced.minorUnits, scheduleSum)

        // Verify each installment is within 1 cent of the others
        val amounts = simulation.schedule.map { it.amount.minorUnits }
        val maxAmount = amounts.maxOrNull()!!
        val minAmount = amounts.minOrNull()!!
        assertTrue(maxAmount - minAmount <= 1L)

        assertEquals(InstallmentSimulation.DISCLAIMER_WITH_RATE, simulation.disclaimer)
    }

    @Test
    fun `36 installments simulation satisfies exact cent distribution invariant`() {
        val principal = Money(1000000, Currency.PEN) // S/ 10,000.00
        val simulation = InstallmentCalculator.simulate(
            cardId = cardId,
            principal = principal,
            installmentsCount = 36,
            firstDueDate = LocalDate.of(2026, 1, 31),
            dueDay = 31,
            teaBps = 2850 // 28.50%
        )

        assertEquals(36, simulation.schedule.size)
        val scheduleSum = simulation.schedule.sumOf { it.amount.minorUnits }
        assertEquals(simulation.totalFinanced.minorUnits, scheduleSum)

        // Verify February due date handles short month (day 31 -> 28 in 2026)
        val febInstallment = simulation.schedule[1] // Month 2 (February 2026)
        assertEquals(LocalDate.of(2026, 2, 28), febInstallment.dueDate)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `installments count less than 1 throws exception`() {
        InstallmentCalculator.simulate(
            cardId = cardId,
            principal = Money(10000, Currency.PEN),
            installmentsCount = 0,
            firstDueDate = LocalDate.of(2026, 3, 1),
            dueDay = 1
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `installments count greater than 36 throws exception`() {
        InstallmentCalculator.simulate(
            cardId = cardId,
            principal = Money(10000, Currency.PEN),
            installmentsCount = 37,
            firstDueDate = LocalDate.of(2026, 3, 1),
            dueDay = 1
        )
    }

    @Test
    fun `purchase candidate validates amount and merchant`() {
        val candidate = PurchaseCandidate(
            id = "cand-1",
            cardId = cardId,
            amount = Money(25000, Currency.PEN),
            merchant = "Supermercado Metro",
            occurredAt = Instant.now(),
            suggestedInstallments = 3,
            status = CandidateStatus.PENDING
        )

        assertEquals("Supermercado Metro", candidate.merchant)
        assertEquals(CandidateStatus.PENDING, candidate.status)
        assertEquals(3, candidate.suggestedInstallments)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `purchase candidate with zero amount throws exception`() {
        PurchaseCandidate(
            id = "cand-invalid",
            cardId = cardId,
            amount = Money(0, Currency.PEN),
            merchant = "Metro",
            occurredAt = Instant.now()
        )
    }
}
