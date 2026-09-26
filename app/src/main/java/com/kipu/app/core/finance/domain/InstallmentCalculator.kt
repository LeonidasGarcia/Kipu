package com.kipu.app.core.finance.domain

import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.InstallmentScheduleItem
import com.kipu.app.feature.accounts.domain.model.InstallmentSimulation
import com.kipu.app.feature.accounts.domain.model.RateSource
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.pow

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

        val teaDecimal = if (hasApplicableRate) teaBps / 10_000.0 else 0.0
        val temDecimal = if (hasApplicableRate) (1.0 + teaDecimal).pow(1.0 / 12.0) - 1.0 else 0.0
        val n = installmentsCount.toDouble()
        val exactPaymentMinor = if (hasApplicableRate) {
            principal.minorUnits.toDouble() * temDecimal / (1.0 - (1.0 + temDecimal).pow(-n))
        } else {
            principal.minorUnits.toDouble() / n
        }

        require(exactPaymentMinor.isFinite() && exactPaymentMinor > 0.0) {
            "Installment estimate is outside the supported range"
        }

        // Round the total once, then assign any indivisible minor-unit remainder to installment 1.
        val totalFinancedMinor = roundMinorUnits(exactPaymentMinor * n)
        val totalInterestMinor = Math.subtractExact(totalFinancedMinor, principal.minorUnits)
        require(totalInterestMinor >= 0L) { "Estimated interest cannot be negative" }
        // Keep the recurring installments at or below the theoretical amount so the
        // whole rounding remainder is assigned to installment 1. This also handles
        // amounts smaller than the installment count (for example S/ 0.01 over 36).
        val regularInstallmentMinor = floorMinorUnits(exactPaymentMinor)
        val regularInstallmentTotal = Math.multiplyExact(regularInstallmentMinor, (installmentsCount - 1).toLong())
        val firstInstallmentMinor = Math.subtractExact(totalFinancedMinor, regularInstallmentTotal)
        require(firstInstallmentMinor > 0L) { "Rounded first installment must remain positive" }

        // The French method has a fixed theoretical payment and a declining interest share.
        // Round each principal share to minor units, then reconcile the rounding residual in
        // the first installment so the principal schedule remains exact.
        val rawPrincipalPortions = if (hasApplicableRate) {
            val remaining = principal.minorUnits.toDouble()
            var outstanding = remaining
            val portions = mutableListOf<Double>()
            repeat(installmentsCount) { index ->
                val principalPortion = if (index == installmentsCount - 1) {
                    outstanding
                } else {
                    val interest = outstanding * temDecimal
                    (exactPaymentMinor - interest).coerceIn(0.0, outstanding)
                }
                portions += principalPortion
                outstanding = (outstanding - principalPortion).coerceAtLeast(0.0)
            }
            portions
        } else {
            val basePrincipal = principal.minorUnits / installmentsCount
            val principalRemainder = (principal.minorUnits % installmentsCount).toInt()
            (0 until installmentsCount).map { index ->
                (basePrincipal + if (index == 0) principalRemainder.toLong() else 0L).toDouble()
            }
        }

        val roundedPrincipalPortions = rawPrincipalPortions.map(::roundMinorUnits).toMutableList()
        val roundedPrincipalTotal = roundedPrincipalPortions.fold(0L, Math::addExact)
        val principalResidual = Math.subtractExact(principal.minorUnits, roundedPrincipalTotal)
        roundedPrincipalPortions[0] = Math.addExact(roundedPrincipalPortions[0], principalResidual)

        val schedule = (0 until installmentsCount).map { index ->
            val installmentNum = index + 1
            val itemAmountMinor = if (index == 0) firstInstallmentMinor else regularInstallmentMinor
            val itemPrincipalMinor = roundedPrincipalPortions[index]
            val itemInterestMinor = Math.subtractExact(itemAmountMinor, itemPrincipalMinor)
            require(itemPrincipalMinor >= 0L && itemInterestMinor >= 0L) {
                "Rounded French schedule produced a negative installment component"
            }

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

        check(schedule.sumOf { it.principalPortion.minorUnits } == principal.minorUnits)
        check(schedule.sumOf { it.interestPortion.minorUnits } == totalInterestMinor)
        check(schedule.sumOf { it.amount.minorUnits } == totalFinancedMinor)

        val totalFinanced = Money(totalFinancedMinor, principal.currency)
        val totalInterest = Money(totalInterestMinor, principal.currency)

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
            appliedTemPercentage = if (hasApplicableRate) temDecimal * 100.0 else null,
            totalInterest = totalInterest,
            totalFinanced = totalFinanced,
            schedule = schedule,
            disclaimer = disclaimer,
        )
    }

    private fun roundMinorUnits(value: Double): Long {
        require(value.isFinite() && value >= 0.0) { "Installment component is outside the supported range" }
        return BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }

    private fun floorMinorUnits(value: Double): Long {
        require(value.isFinite() && value >= 0.0) { "Installment component is outside the supported range" }
        return BigDecimal.valueOf(value).setScale(0, RoundingMode.DOWN).longValueExact()
    }
}
