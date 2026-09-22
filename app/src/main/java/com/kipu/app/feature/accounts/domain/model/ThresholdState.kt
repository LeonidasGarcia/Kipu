package com.kipu.app.feature.accounts.domain.model

import com.kipu.app.core.finance.domain.model.CardId

enum class UtilizationThreshold(val percentage: Int, val message: String) {
    PERCENT_50(50, "Has alcanzado el 50% de tu línea de crédito"),
    PERCENT_80(80, "Has alcanzado el 80% de tu línea de crédito"),
    PERCENT_100(100, "Has alcanzado o superado el 100% de tu línea de crédito");

    companion object {
        fun forUtilization(pct: Double): List<UtilizationThreshold> =
            entries.filter { pct >= it.percentage }
    }
}

data class CardThresholdState(
    val cardId: CardId,
    val crossedThresholds: Set<UtilizationThreshold> = emptySet(),
) {
    fun evaluate(
        previousUtilization: Double,
        newUtilization: Double,
    ): Pair<CardThresholdState, List<UtilizationThreshold>> {
        val triggered = mutableListOf<UtilizationThreshold>()
        val updatedCrossed = crossedThresholds.toMutableSet()

        for (threshold in UtilizationThreshold.entries) {
            val thresholdPct = threshold.percentage.toDouble()
            if (newUtilization >= thresholdPct) {
                if (!updatedCrossed.contains(threshold) && previousUtilization < thresholdPct) {
                    triggered.add(threshold)
                    updatedCrossed.add(threshold)
                } else if (!updatedCrossed.contains(threshold)) {
                    updatedCrossed.add(threshold)
                }
            } else {
                // Re-arm: dropped strictly below threshold
                updatedCrossed.remove(threshold)
            }
        }

        return Pair(copy(crossedThresholds = updatedCrossed), triggered)
    }
}
