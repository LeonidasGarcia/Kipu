package com.kipu.app.feature.accounts.domain

import com.kipu.app.core.finance.domain.CreditCalculations
import com.kipu.app.core.finance.domain.model.Currency
import java.time.LocalDate
import java.time.Month
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditMetricsTest {

    @Test
    fun `short month February non leap year adjusts day 31 and 30 and 29 to 28`() {
        assertEquals(28, CreditCalculations.calculateEffectiveDay(31, 2023, Month.FEBRUARY))
        assertEquals(28, CreditCalculations.calculateEffectiveDay(30, 2023, Month.FEBRUARY))
        assertEquals(28, CreditCalculations.calculateEffectiveDay(29, 2023, Month.FEBRUARY))
        assertEquals(25, CreditCalculations.calculateEffectiveDay(25, 2023, Month.FEBRUARY))
    }

    @Test
    fun `short month February leap year adjusts day 31 and 30 to 29`() {
        assertEquals(29, CreditCalculations.calculateEffectiveDay(31, 2024, Month.FEBRUARY))
        assertEquals(29, CreditCalculations.calculateEffectiveDay(30, 2024, Month.FEBRUARY))
        assertEquals(29, CreditCalculations.calculateEffectiveDay(29, 2024, Month.FEBRUARY))
        assertEquals(28, CreditCalculations.calculateEffectiveDay(28, 2024, Month.FEBRUARY))
    }

    @Test
    fun `30 day month April adjusts day 31 to 30`() {
        assertEquals(30, CreditCalculations.calculateEffectiveDay(31, 2024, Month.APRIL))
        assertEquals(30, CreditCalculations.calculateEffectiveDay(30, 2024, Month.APRIL))
        assertEquals(29, CreditCalculations.calculateEffectiveDay(29, 2024, Month.APRIL))
    }

    @Test
    fun `calculateNextDate correctly advances to next month if reference date is past day`() {
        val refDate = LocalDate.of(2024, Month.JANUARY, 20)
        // Day 15 has passed in January, so next occurrence is Feb 15
        val nextDate = CreditCalculations.calculateNextDate(15, refDate)
        assertEquals(LocalDate.of(2024, Month.FEBRUARY, 15), nextDate)
    }

    @Test
    fun `calculateNextDate keeps current month if reference date is on or before day`() {
        val refDate = LocalDate.of(2024, Month.JANUARY, 10)
        val nextDate = CreditCalculations.calculateNextDate(15, refDate)
        assertEquals(LocalDate.of(2024, Month.JANUARY, 15), nextDate)
    }

    @Test
    fun `calculateCreditMetrics with normal usage`() {
        val metrics = CreditCalculations.calculateCreditMetrics(
            creditLimitMinorUnits = 5000_00L,
            debtMinorUnits = 1500_00L,
            currency = Currency.PEN,
        )

        assertEquals(5000_00L, metrics.creditLimit.minorUnits)
        assertEquals(1500_00L, metrics.debt.minorUnits)
        assertEquals(3500_00L, metrics.availableCredit.minorUnits)
        assertEquals(0L, metrics.overLimit.minorUnits)
        assertEquals(30.0, metrics.utilizationPercentage, 0.001)
    }

    @Test
    fun `calculateCreditMetrics when debt exceeds limit`() {
        val metrics = CreditCalculations.calculateCreditMetrics(
            creditLimitMinorUnits = 5000_00L,
            debtMinorUnits = 6000_00L,
            currency = Currency.PEN,
        )

        assertEquals(0L, metrics.availableCredit.minorUnits)
        assertEquals(1000_00L, metrics.overLimit.minorUnits)
        assertEquals(120.0, metrics.utilizationPercentage, 0.001)
    }

    @Test
    fun `calculateCreditMetrics with zero limit`() {
        val metrics = CreditCalculations.calculateCreditMetrics(
            creditLimitMinorUnits = 0L,
            debtMinorUnits = 0L,
            currency = Currency.PEN,
        )

        assertEquals(0L, metrics.availableCredit.minorUnits)
        assertEquals(0.0, metrics.utilizationPercentage, 0.001)
    }

    @Test
    fun `calculateCreditMetrics rejects negative values`() {
        val errLimit = runCatching {
            CreditCalculations.calculateCreditMetrics(-100L, 50L, Currency.PEN)
        }.exceptionOrNull()
        assertTrue(errLimit is IllegalArgumentException)

        val errDebt = runCatching {
            CreditCalculations.calculateCreditMetrics(100L, -50L, Currency.PEN)
        }.exceptionOrNull()
        assertTrue(errDebt is IllegalArgumentException)
    }
}
