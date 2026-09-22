package com.kipu.app.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

val LocalBalanceMasked = compositionLocalOf { false }

private val AmountFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

fun formatMinorUnits(minorUnits: Long): String {
    val isNegative = minorUnits < 0
    val absVal = kotlin.math.abs(minorUnits)
    val major = absVal / 100
    val cents = absVal % 100
    val formatted = AmountFormat.format(major + cents / 100.0)
    return if (isNegative) "-$formatted" else formatted
}

fun Currency.symbol(): String = when (this) {
    Currency.PEN -> "S/"
    Currency.USD -> "$"
}

/**
 * MoneyText renders financial amounts with tabular numbers and privacy masking (HU-04, RT-05, RT-06).
 * When [isMasked] is true:
 * - Semantics expose only "Monto oculto" (raw amount is completely absent from semantics tree).
 * - Visual rendering displays "••••••" or "$ ••••••" / "S/ ••••••".
 */
@Composable
fun MoneyText(
    amount: String,
    modifier: Modifier = Modifier,
    currencySymbol: String? = null,
    isMasked: Boolean = LocalBalanceMasked.current,
    color: Color = Color.Unspecified,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    fontWeight: FontWeight? = null,
) {
    val displayValue = if (isMasked) {
        if (currencySymbol != null) "$currencySymbol ••••••" else "••••••"
    } else {
        if (currencySymbol != null) "$currencySymbol $amount" else amount
    }

    val finalStyle = if (style.fontFeatureSettings?.contains("tnum") == true) {
        style
    } else {
        style.copy(fontFeatureSettings = (style.fontFeatureSettings?.let { "$it, tnum" } ?: "tnum"))
    }

    Text(
        text = displayValue,
        modifier = modifier.semantics {
            contentDescription = if (isMasked) {
                "Monto oculto"
            } else {
                if (currencySymbol != null) "$currencySymbol $amount" else amount
            }
        },
        color = color,
        style = finalStyle,
        fontWeight = fontWeight,
    )
}

@Composable
fun MoneyText(
    money: Money,
    modifier: Modifier = Modifier,
    isMasked: Boolean = LocalBalanceMasked.current,
    color: Color = Color.Unspecified,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    fontWeight: FontWeight? = null,
) {
    MoneyText(
        amount = formatMinorUnits(money.minorUnits),
        modifier = modifier,
        currencySymbol = money.currency.symbol(),
        isMasked = isMasked,
        color = color,
        style = style,
        fontWeight = fontWeight,
    )
}
