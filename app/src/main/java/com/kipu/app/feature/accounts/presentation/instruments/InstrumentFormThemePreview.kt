package com.kipu.app.feature.accounts.presentation.instruments

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
private fun InstrumentFormThemePreviewContent() {
    val colors = rememberKipuColors()
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Agregar instrumento", style = MaterialTheme.typography.titleLarge, color = colors.inkPrimary)
        Surface(color = colors.surfaceVariant, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, colors.border)) {
            Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = colors.primary, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                    Text("Ahorros / Débito", Modifier.padding(12.dp), color = colors.onPrimary)
                }
                Surface(color = colors.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                    Text("Tarjeta de crédito", Modifier.padding(12.dp), color = colors.inkSecondary)
                }
            }
        }
        OutlinedTextField(
            value = "Mi Cuenta Principal",
            onValueChange = {},
            label = { Text("Alias") },
            modifier = Modifier.fillMaxWidth(),
        )
        Surface(color = colors.selectedSurface, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, colors.primaryText)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("BCP Visa Clásica", color = colors.inkPrimary, style = MaterialTheme.typography.titleSmall)
                Text("Producto seleccionado", color = colors.primaryText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Preview(name = "Formulario de instrumento · Claro", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
private fun InstrumentFormLightPreview() {
    KipuTheme(darkTheme = false) { InstrumentFormThemePreviewContent() }
}

@Preview(name = "Formulario de instrumento · Oscuro", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun InstrumentFormDarkPreview() {
    KipuTheme(darkTheme = true) { InstrumentFormThemePreviewContent() }
}
