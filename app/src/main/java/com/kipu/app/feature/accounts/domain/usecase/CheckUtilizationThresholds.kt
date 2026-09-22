package com.kipu.app.feature.accounts.domain.usecase

import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.feature.accounts.domain.model.CardThresholdState
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.UtilizationThreshold
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class UtilizationAlert(
    val cardId: CardId,
    val cardAlias: String,
    val threshold: UtilizationThreshold,
    val currentUtilizationPercentage: Double,
    val message: String,
)

@Singleton
class CheckUtilizationThresholds @Inject constructor() {

    private val cardStates = ConcurrentHashMap<String, CardThresholdState>()

    fun evaluateUtilizationChange(
        card: CreditCard,
        previousDebtMinorUnits: Long,
        newDebtMinorUnits: Long,
    ): List<UtilizationAlert> {
        if (card.creditLimitMinorUnits <= 0L) return emptyList()

        val prevPct = (previousDebtMinorUnits.toDouble() / card.creditLimitMinorUnits.toDouble()) * 100.0
        val newPct = (newDebtMinorUnits.toDouble() / card.creditLimitMinorUnits.toDouble()) * 100.0

        val currentState = cardStates.getOrPut(card.id.value) {
            CardThresholdState(card.id)
        }

        val (newState, triggeredThresholds) = currentState.evaluate(prevPct, newPct)
        cardStates[card.id.value] = newState

        val displayName = card.alias ?: "${card.issuer} ${card.network} •••• ${card.lastFourDigits}"

        return triggeredThresholds.map { threshold ->
            val formattedPct = "%.1f".format(newPct)
            UtilizationAlert(
                cardId = card.id,
                cardAlias = displayName,
                threshold = threshold,
                currentUtilizationPercentage = newPct,
                message = "$displayName: ${threshold.message} ($formattedPct%)",
            )
        }
    }

    fun getActiveAlerts(card: CreditCard, debtMinorUnits: Long): List<UtilizationAlert> {
        if (card.creditLimitMinorUnits <= 0L) return emptyList()

        val pct = (debtMinorUnits.toDouble() / card.creditLimitMinorUnits.toDouble()) * 100.0
        val displayName = card.alias ?: "${card.issuer} ${card.network} •••• ${card.lastFourDigits}"
        val formattedPct = "%.1f".format(pct)

        return UtilizationThreshold.forUtilization(pct).map { threshold ->
            UtilizationAlert(
                cardId = card.id,
                cardAlias = displayName,
                threshold = threshold,
                currentUtilizationPercentage = pct,
                message = "$displayName: ${threshold.message} ($formattedPct%)",
            )
        }
    }

    fun resetCardState(cardId: CardId) {
        cardStates.remove(cardId.value)
    }
}
