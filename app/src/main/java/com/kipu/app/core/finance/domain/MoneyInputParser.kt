package com.kipu.app.core.finance.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** Parses user-entered PEN/USD amounts without binary floating-point rounding. */
object MoneyInputParser {
    private val amountPattern = Regex("(?:\\d+(?:\\.\\d{0,2})?|\\.\\d{1,2})")

    fun parseMinorUnits(input: String): Long? {
        val normalized = input.trim().replace(',', '.')
        if (normalized.isEmpty() || !amountPattern.matches(normalized)) return null

        val canonical = if (normalized.startsWith('.')) "0$normalized" else normalized
        return runCatching {
            BigDecimal(canonical)
                .setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact()
                .takeIf { it in 0..com.kipu.app.core.finance.domain.model.Money.MAX_MONEY_MINOR }
        }.getOrNull()
    }
}
