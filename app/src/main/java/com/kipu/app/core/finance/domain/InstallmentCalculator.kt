package com.kipu.app.core.finance.domain

import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.InstallmentScheduleItem
import com.kipu.app.feature.accounts.domain.model.InstallmentSimulation
import com.kipu.app.feature.accounts.domain.model.RateSource
import java.time.LocalDate
import kotlin.math.pow
import kotlin.math.roundToLong

object InstallmentCalculator {

    /**
     * Simulates an installment plan with deterministic cent distribution.
     * Guaranteed: sum of schedule installment amounts == totalFinanced.
     */
    fun simulate(
        cardId: CardId,
        principal: Money,
        installmentsCount: Int,
        firstDueDate: LocalDate,
        dueDay: Int,
        teaBps: Int? = null,
        rateSource: RateSource = if (teaBps != null && teaBps > 0) RateSource.PERSONAL_TEA else RateSource.NONE,
        candidateId: String? = null,
    ): InstallmentSimulation {
        require(installmentsCount in 1..36) {
            "Installments count must be between 1 and 36: $installmentsCount"
        }
        require(principal.minorUnits > 0) {
            "Principal must be positive: ${principal.minorUnits}"
        }
        require(dueDay in 1..31) {
            "Due day must be between 1 and 31: $dueDay"
        }

        val hasApplicableRate = teaBps != null && teaBps > 0 && installmentsCount > 1

        val (totalFinancedMinor, totalInterestMinor, appliedTem) = if (!hasApplicableRate) {
            Triple(principal.minorUnits, 0L, null)
        } else {
            val teaDecimal = teaBps / 10000.0
            val temDecimal = (1.0 + teaDecimal).pow(1.0 / 12.0) - 1.0
            val n = installmentsCount.toDouble()
            val factor = (temDecimal * (1.0 + temDecimal).pow(n)) / ((1.0 + temDecimal).pow(n) - 1.0)
            val rawMonthlyPayment = principal.minorUnits * factor
            val roundedMonthly = rawMonthlyPayment.roundToLong()
            val computedTotalFinanced = Math.multiplyExact(roundedMonthly, installmentsCount.toLong())
            val computedTotalInterest = Math.max(0L, Math.subtractExact(computedTotalFinanced, principal.minorUnits))
            val finalTotalFinanced = Math.addExact(principal.minorUnits, computedTotalInterest)
            Triple(finalTotalFinanced, computedTotalInterest, temDecimal * 100.0)
        }

        val totalFinanced = Money(totalFinancedMinor, principal.currency)
        val totalInterest = Money(totalInterestMinor, principal.currency)

        // Cent distribution: totalFinancedMinor / installmentsCount with remainder distributed to first installments
        val baseInstallmentAmount = totalFinancedMinor / installmentsCount
        val remainderCents = (totalFinancedMinor % installmentsCount).toInt()

        val basePrincipal = principal.minorUnits / installmentsCount
        val principalRemainder = (principal.minorUnits % installmentsCount).toInt()

        val schedule = (0 until installmentsCount).map { index ->
            val installmentNum = index + 1
            val itemAmountMinor = baseInstallmentAmount + if (index < remainderCents) 1L else 0L
            val itemPrincipalMinor = basePrincipal + if (index < principalRemainder) 1L else 0L
            val itemInterestMinor = Math.max(0L, Math.subtractExact(itemAmountMinor, itemPrincipalMinor))

            val targetDate = firstDueDate.plusMonths(index.toLong())
            val effectiveDate = CreditCalculations.calculateEffectiveDate(dueDay, targetDate.year, targetDate.month)

            InstallmentScheduleItem(
                installmentNumber = installmentNum,
                dueDate = effectiveDate,
                amount = Money(itemAmountMinor, principal.currency),
                principalPortion = Money(itemPrincipalMinor, principal.currency),
                interestPortion = Money(itemInterestMinor, principal.currency),
            )
        }

        val disclaimer = if (hasApplicableRate) {
            InstallmentSimulation.DISCLAIMER_WITH_RATE
        } else {
            InstallmentSimulation.DISCLAIMER_WITHOUT_RATE
        }

        return InstallmentSimulation(
            candidateId = candidateId,
            cardId = cardId,
            principal = principal,
            installmentsCount = installmentsCount,
            rateSource = rateSource,
            appliedTeaBps = if (hasApplicableRate) teaBps else null,
            appliedTemPercentage = appliedTem,
            totalInterest = totalInterest,
            totalFinanced = totalFinanced,
            schedule = schedule,
            disclaimer = disclaimer,
        )
    }
}
