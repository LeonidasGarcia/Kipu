package com.kipu.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.content.res.Configuration

/** Semantic Kipu surfaces and foregrounds for components that need exact product tokens. */
@Immutable
data class KipuUiColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceSubtle: Color,
    val surfaceVariant: Color,
    val cardVisualBackground: Color,
    val selectedSurface: Color,
    val border: Color,
    val inkPrimary: Color,
    val inkSecondary: Color,
    val primary: Color,
    val primaryText: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val positive: Color,
    val positiveContainer: Color,
    val onPositiveContainer: Color,
    val debt: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val dragHandle: Color,
    val outline: Color = border,
    val onSurfaceVariant: Color = inkSecondary,
    val surfaceContainer: Color = surfaceVariant,
)

/**
 * Resolves Kipu's semantic UI palette from the active Material theme color scheme.
 * General roles map directly to MaterialTheme.colorScheme, while keeping semantic
 * financial extensions (positive, debt, warning) and card visual tokens.
 */
@Composable
fun rememberKipuColors(): KipuUiColors {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        val isDark = scheme.background.luminance() < 0.5f
        KipuUiColors(
            isDark = isDark,
            background = scheme.background,
            surface = scheme.surface,
            surfaceSubtle = if (isDark) scheme.surfaceContainerLow else scheme.surfaceContainerLowest,
            surfaceVariant = scheme.surfaceVariant,
            cardVisualBackground = if (isDark) Color(0xFF1E3A8A) else Color(0xFF0F172A),
            selectedSurface = if (isDark) Color(0xFF123B38) else Color(0xFFF0FDF4),
            border = scheme.outlineVariant,
            inkPrimary = scheme.onSurface,
            inkSecondary = scheme.onSurfaceVariant,
            primary = scheme.primary,
            primaryText = scheme.primary,
            onPrimary = scheme.onPrimary,
            primaryContainer = scheme.primaryContainer,
            onPrimaryContainer = scheme.onPrimaryContainer,
            positive = if (isDark) Color(0xFF86EFAC) else Color(0xFF15803D),
            positiveContainer = if (isDark) Color(0xFF14532D) else Color(0xFFDCFCE7),
            onPositiveContainer = if (isDark) Color(0xFF86EFAC) else Color(0xFF14532D),
            debt = if (isDark) Color(0xFFFCA5A5) else Color(0xFFB91C1C),
            warning = if (isDark) Color(0xFFFCD34D) else Color(0xFF92400E),
            warningContainer = if (isDark) Color(0xFF422006) else Color(0xFFFEF3C7),
            onWarningContainer = if (isDark) Color(0xFFFCD34D) else Color(0xFF78350F),
            dragHandle = if (isDark) scheme.outline else scheme.outlineVariant,
            outline = scheme.outline,
            onSurfaceVariant = scheme.onSurfaceVariant,
            surfaceContainer = scheme.surfaceContainer,
        )
    }
}

@Composable
private fun KipuSemanticColorPreviewContent() {
    val colors = rememberKipuColors()
    Surface(color = colors.background) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Mi Dinero Real", color = colors.inkPrimary, style = MaterialTheme.typography.titleMedium)
                    Text("Disponible S/ 5,210.00", color = colors.inkSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(color = colors.positiveContainer, shape = RoundedCornerShape(8.dp)) {
                            Text("Principal", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = colors.onPositiveContainer)
                        }
                        Surface(color = if (colors.isDark) Color(0xFF7F1D1D) else Color(0xFFFEE2E2), shape = RoundedCornerShape(8.dp)) {
                            Text("Deuda S/ 1,420", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = colors.debt)
                        }
                    }
                    Button(
                        onClick = {},
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                    ) {
                        Text("Registrar movimiento")
                    }
                }
            }
        }
    }
}

@Preview(name = "Kipu · Claro", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
private fun KipuLightThemePreview() {
    KipuTheme(darkTheme = false) { KipuSemanticColorPreviewContent() }
}

@Preview(name = "Kipu · Oscuro", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun KipuDarkThemePreview() {
    KipuTheme(darkTheme = true) { KipuSemanticColorPreviewContent() }
}
