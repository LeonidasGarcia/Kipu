package com.kipu.app.core.finance.domain

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import java.time.LocalDate
import java.time.Month
import java.time.Year

data class CreditMetrics(
    val creditLimit: Money,
    val debt: Money,
    val availableCredit: Money,
    val overLimit: Money,
    val utilizationPercentage: Double,
)

object CreditCalculations {

    /**
     * Calculates effective day in a given month, handling short months (28, 29, 30 days)
     * for preferred days 29, 30, or 31.
     */
    fun calculateEffectiveDay(preferredDay: Int, year: Int, month: Month): Int {
        require(preferredDay in 1..31) { "Preferred day must be between 1 and 31: $preferredDay" }
        val maxDaysInMonth = month.length(Year.isLeap(year.toLong()))
        return minOf(preferredDay, maxDaysInMonth)
    }

    /**
     * Calculates the effective date for a given year and month according to the preferred day.
     */
    fun calculateEffectiveDate(preferredDay: Int, year: Int, month: Month): LocalDate {
        val effectiveDay = calculateEffectiveDay(preferredDay, year, month)
        return LocalDate.of(year, month, effectiveDay)
    }

    /**
     * Calculates the next calendar date for a cut-off or due day preference from a reference date.
     */
    fun calculateNextDate(preferredDay: Int, referenceDate: LocalDate = LocalDate.now()): LocalDate {
        val currentMonthEffectiveDay = calculateEffectiveDay(preferredDay, referenceDate.year, referenceDate.month)
        val candidateDate = LocalDate.of(referenceDate.year, referenceDate.month, currentMonthEffectiveDay)

        return if (!candidateDate.isBefore(referenceDate)) {
            candidateDate
        } else {
            val nextMonth = referenceDate.plusMonths(1)
            val nextEffectiveDay = calculateEffectiveDay(preferredDay, nextMonth.year, nextMonth.month)
            LocalDate.of(nextMonth.year, nextMonth.month, nextEffectiveDay)
        }
    }

    /**
     * Resolves the first installment due date from a purchase's billing cycle.
     * The closing day is inclusive. The first due date is the earliest effective due day
     * strictly after that cycle's effective close date.
     */
    fun calculateFirstInstallmentDueDate(
        purchaseDate: LocalDate,
        preferredClosingDay: Int,
        preferredDueDay: Int,
    ): LocalDate {
        require(preferredClosingDay in 1..31) { "Closing day must be between 1 and 31: $preferredClosingDay" }
        require(preferredDueDay in 1..31) { "Due day must be between 1 and 31: $preferredDueDay" }

        val effectiveCloseThisMonth = calculateEffectiveDate(
            preferredClosingDay,
            purchaseDate.year,
            purchaseDate.month,
        )
        val cycleClose = if (!purchaseDate.isAfter(effectiveCloseThisMonth)) {
            effectiveCloseThisMonth
        } else {
            val nextMonth = purchaseDate.plusMonths(1)
            calculateEffectiveDate(preferredClosingDay, nextMonth.year, nextMonth.month)
        }

        return calculateNextDate(preferredDueDay, cycleClose.plusDays(1))
    }

    /**
     * Calculates credit utilization and available limits.
     */
    fun calculateCreditMetrics(
        creditLimitMinorUnits: Long,
        debtMinorUnits: Long,
        currency: Currency,
    ): CreditMetrics {
        require(creditLimitMinorUnits >= 0L) { "Credit limit cannot be negative" }
        require(debtMinorUnits >= 0L) { "Debt cannot be negative" }

        val availableMinorUnits = maxOf(0L, creditLimitMinorUnits - debtMinorUnits)
        val overLimitMinorUnits = maxOf(0L, debtMinorUnits - creditLimitMinorUnits)
        val utilization = if (creditLimitMinorUnits > 0L) {
            (debtMinorUnits.toDouble() / creditLimitMinorUnits.toDouble()) * 100.0
        } else {
            0.0
        }

        return CreditMetrics(
            creditLimit = Money(creditLimitMinorUnits, currency),
            debt = Money(debtMinorUnits, currency),
            availableCredit = Money(availableMinorUnits, currency),
            overLimit = Money(overLimitMinorUnits, currency),
            utilizationPercentage = utilization,
        )
    }
}
