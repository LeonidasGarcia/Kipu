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
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

val LocalBalanceMasked = compositionLocalOf { false }

fun formatMinorUnits(minorUnits: Long): String {
    val bd = BigDecimal.valueOf(minorUnits, 2)
    val df = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
    return df.format(bd)
}

fun Currency.symbol(): String = when (this) {
    Currency.PEN -> "S/"
    Currency.USD -> "$"
}

internal fun formatMoneyTextDisplay(
    amount: String,
    currencySymbol: String?,
    isMasked: Boolean,
): String {
    if (isMasked) {
        return if (currencySymbol != null) "$currencySymbol ••••••" else "••••••"
    }

    var text = amount.trim()
    var sign = ""

    if (text.startsWith("-") || text.startsWith("−")) {
        sign = "-"
        text = text.drop(1).trimStart()
    } else if (text.startsWith("+")) {
        sign = "+"
        text = text.drop(1).trimStart()
    }

    while (text.startsWith("-") || text.startsWith("+") || text.startsWith("−")) {
        text = text.drop(1).trimStart()
    }

    if (currencySymbol != null && text.startsWith(currencySymbol)) {
        text = text.removePrefix(currencySymbol).trimStart()
        if (sign.isEmpty()) {
            if (text.startsWith("-") || text.startsWith("−")) {
                sign = "-"
                text = text.drop(1).trimStart()
            } else if (text.startsWith("+")) {
                sign = "+"
                text = text.drop(1).trimStart()
            }
        }
    }

    while (text.startsWith("-") || text.startsWith("+") || text.startsWith("−")) {
        text = text.drop(1).trimStart()
    }

    val cleanAmount = text

    return if (currencySymbol != null) {
        if (sign.isNotEmpty()) "$sign$currencySymbol $cleanAmount" else "$currencySymbol $cleanAmount"
    } else {
        if (sign.isNotEmpty()) "$sign$cleanAmount" else cleanAmount
    }
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
    val displayValue = formatMoneyTextDisplay(amount, currencySymbol, isMasked)

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
                displayValue
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

@Composable
fun MoneyText(
    minorUnits: Long,
    modifier: Modifier = Modifier,
    currency: Currency = Currency.PEN,
    isMasked: Boolean = LocalBalanceMasked.current,
    color: Color = Color.Unspecified,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    fontWeight: FontWeight? = null,
) {
    MoneyText(
        money = Money(minorUnits = minorUnits, currency = currency),
        modifier = modifier,
        isMasked = isMasked,
        color = color,
        style = style,
        fontWeight = fontWeight,
    )
}
