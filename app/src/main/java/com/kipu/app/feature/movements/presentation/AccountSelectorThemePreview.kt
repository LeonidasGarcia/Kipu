package com.kipu.app.feature.movements.presentation

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kipu.app.ui.theme.KipuTheme
import com.kipu.app.ui.theme.rememberKipuColors

@Composable
private fun AccountSelectorThemePreviewContent() {
    val colors = rememberKipuColors()
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Seleccionar cuenta", color = colors.inkPrimary, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChipPreview("Todas (4)", selected = true)
            FilterChipPreview("Cuentas (2)", selected = false)
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = colors.surfaceVariant,
        ) {
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cuenta de sueldo · Principal", color = colors.inkPrimary)
                Text("S/ 3,850.00", color = colors.inkPrimary)
            }
        }
        Surface(shape = RoundedCornerShape(8.dp), color = if (colors.isDark) colors.surfaceVariant else colors.positiveContainer) {
            Text("Deuda S/ 1,420.00", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = colors.debt)
        }
    }
}

@Composable
private fun FilterChipPreview(label: String, selected: Boolean) {
    val colors = rememberKipuColors()
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) colors.primary else colors.surfaceVariant,
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = if (selected) colors.onPrimary else colors.inkSecondary,
        )
    }
}

@Preview(name = "Selector de cuenta · Claro", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
private fun AccountSelectorLightPreview() {
    KipuTheme(darkTheme = false) { AccountSelectorThemePreviewContent() }
}

@Preview(name = "Selector de cuenta · Oscuro", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun AccountSelectorDarkPreview() {
    KipuTheme(darkTheme = true) { AccountSelectorThemePreviewContent() }
}
