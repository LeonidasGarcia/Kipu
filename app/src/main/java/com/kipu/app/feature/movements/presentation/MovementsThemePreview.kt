package com.kipu.app.feature.movements.presentation

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kipu.app.ui.theme.KipuTheme
import com.kipu.app.ui.theme.rememberKipuColors

@Composable
private fun MovementsThemePreviewContent() {
    val colors = rememberKipuColors()
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Registrar gasto", style = MaterialTheme.typography.titleLarge, color = colors.inkPrimary)
        Surface(color = colors.surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, colors.border)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MONTO DEL GASTO", color = colors.inkSecondary, style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(value = "48.90", onValueChange = {}, label = { Text("Importe") }, modifier = Modifier.fillMaxWidth())
            }
        }
        Surface(color = colors.surface, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, colors.border)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Supermercado", color = colors.inkPrimary)
                    Text("Cuenta de sueldo · Hoy", color = colors.inkSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Text("− S/ 48.90", color = colors.debt, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Preview(name = "Movimientos · Claro", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
private fun MovementsLightPreview() {
    KipuTheme(darkTheme = false) { MovementsThemePreviewContent() }
}

@Preview(name = "Movimientos · Oscuro", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun MovementsDarkPreview() {
    KipuTheme(darkTheme = true) { MovementsThemePreviewContent() }
}
