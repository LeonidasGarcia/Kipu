package com.kipu.app.core.finance.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** Parses user-entered PEN/USD amounts without binary floating-point rounding. */
object MoneyInputParser {
    private val digitsPattern = Regex("\\d+")

    fun parseMinorUnits(input: String): Long? {
        val value = input.trim()
        if (value.isEmpty() || value.any { !it.isDigit() && it != '.' && it != ',' }) return null

        val separators = value.filter { it == '.' || it == ',' }.toSet()
        val canonical = when (separators.size) {
            0 -> value
            1 -> normalizeSingleSeparator(value, separators.single()) ?: return null
            2 -> normalizeMixedSeparators(value) ?: return null
            else -> return null
        }

        return runCatching {
            BigDecimal(canonical)
                .setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact()
                .takeIf { it in 0..com.kipu.app.core.finance.domain.model.Money.MAX_MONEY_MINOR }
        }.getOrNull()
    }

    private fun normalizeSingleSeparator(value: String, separator: Char): String? {
        val parts = value.split(separator)
        if (parts.size == 2) {
            val (whole, fraction) = parts
            if (fraction.length in 1..2 && (whole.isEmpty() || digitsPattern.matches(whole))) {
                return "${whole.ifEmpty { "0" }}.$fraction"
            }
            // A single separator followed by three digits is ambiguous (decimal precision
            // versus grouping); require an unambiguous mixed or repeated-grouping form.
            return null
        }

        // Repeated instances of one separator are valid only as three-digit grouping.
        if (parts.firstOrNull().isNullOrEmpty() || parts.first().length !in 1..3 || !digitsPattern.matches(parts.first())) return null
        if (parts.drop(1).any { it.length != 3 || !digitsPattern.matches(it) }) return null
        return parts.joinToString("")
    }

    private fun normalizeMixedSeparators(value: String): String? {
        val decimalSeparator = if (value.lastIndexOf('.') > value.lastIndexOf(',')) '.' else ','
        val groupingSeparator = if (decimalSeparator == '.') ',' else '.'
        val decimalIndex = value.lastIndexOf(decimalSeparator)
        val whole = value.substring(0, decimalIndex)
        val fraction = value.substring(decimalIndex + 1)
        if (fraction.length !in 1..2 || !digitsPattern.matches(fraction)) return null

        val groups = whole.split(groupingSeparator)
        if (groups.size < 2 || groups.first().length !in 1..3 || !digitsPattern.matches(groups.first())) return null
        if (groups.drop(1).any { it.length != 3 || !digitsPattern.matches(it) }) return null
        if (whole.contains(decimalSeparator)) return null
        return "${groups.joinToString("")}.$fraction"
    }
}
