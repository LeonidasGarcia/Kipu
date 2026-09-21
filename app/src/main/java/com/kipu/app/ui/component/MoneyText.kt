package com.kipu.app.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * MoneyText renders financial amounts with support for privacy masking (HU-04).
 * When [isMasked] is true, it renders "••••••" instead of the sensitive monetary figure.
 */
@Composable
fun MoneyText(
    amount: String,
    modifier: Modifier = Modifier,
    currencySymbol: String? = null,
    isMasked: Boolean = false,
    color: Color = Color.Unspecified,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    fontWeight: FontWeight? = null,
) {
    val displayValue = if (isMasked) {
        "••••••"
    } else {
        if (currencySymbol != null) "$currencySymbol $amount" else amount
    }

    Text(
        text = displayValue,
        modifier = modifier,
        color = color,
        style = style,
        fontWeight = fontWeight,
    )
}
