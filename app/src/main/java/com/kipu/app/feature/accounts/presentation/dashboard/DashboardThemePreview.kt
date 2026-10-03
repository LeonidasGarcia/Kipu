package com.kipu.app.feature.accounts.presentation.dashboard

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
import com.kipu.app.ui.component.KipuCard
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.theme.KipuTheme
import com.kipu.app.ui.theme.rememberKipuColors

@Composable
private fun DashboardThemePreviewContent() {
    val colors = rememberKipuColors()
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Mi Dinero Real", style = MaterialTheme.typography.titleLarge, color = colors.inkPrimary)
        KipuCard(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("TOTAL DISPONIBLE", style = MaterialTheme.typography.labelSmall, color = colors.inkSecondary)
                MoneyText(minorUnits = 521000L, style = MaterialTheme.typography.headlineMedium, color = colors.inkPrimary)
                Surface(shape = RoundedCornerShape(14.dp), color = colors.surfaceVariant) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Cuenta de sueldo", color = colors.inkPrimary)
                        MoneyText(minorUnits = 385000L, color = colors.inkPrimary)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (colors.isDark) androidx.compose.ui.graphics.Color(0xFF7F1D1D) else androidx.compose.ui.graphics.Color(0xFFFEE2E2),
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Deuda", color = colors.debt)
                        MoneyText(minorUnits = 142000L, color = colors.debt)
                    }
                }
            }
        }
    }
}

@Preview(name = "Dashboard · Claro", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
private fun DashboardLightPreview() {
    KipuTheme(darkTheme = false) { DashboardThemePreviewContent() }
}

@Preview(name = "Dashboard · Oscuro", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun DashboardDarkPreview() {
    KipuTheme(darkTheme = true) { DashboardThemePreviewContent() }
}
