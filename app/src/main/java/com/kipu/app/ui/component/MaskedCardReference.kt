package com.kipu.app.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * MaskedCardReference displays a secure, masked card identifier (e.g. "•••• 1234").
 *
 * Rules (RF-C05, RN-02):
 * - Never displays or accepts full PAN or CVV.
 * - Accepts strictly exactly four ASCII digits.
 * - Accessible semantics: "Tarjeta que termina en [lastFourDigits]".
 */
@Composable
fun MaskedCardReference(
    lastFourDigits: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    fontWeight: FontWeight? = null,
) {
    val sanitized = lastFourDigits.trim()
    require(sanitized.length == 4 && sanitized.all { it.isDigit() }) {
        "lastFourDigits must be exactly 4 digits: '$lastFourDigits'"
    }

    val displayText = "•••• $sanitized"

    val finalStyle = if (style.fontFeatureSettings?.contains("tnum") == true) {
        style
    } else {
        style.copy(fontFeatureSettings = (style.fontFeatureSettings?.let { "$it, tnum" } ?: "tnum"))
    }

    Text(
        text = displayText,
        modifier = modifier.semantics {
            contentDescription = "Tarjeta terminada en $sanitized"
        },
        color = color,
        style = finalStyle,
        fontWeight = fontWeight,
    )
}
