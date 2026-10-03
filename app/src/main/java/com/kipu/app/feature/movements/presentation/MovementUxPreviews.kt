package com.kipu.app.feature.movements.presentation

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.movements.domain.model.MovementHistoryAccessDecision
import com.kipu.app.feature.movements.domain.model.MovementHistoryQuery
import com.kipu.app.ui.theme.KipuTheme

@Preview(name = "Acceso · Light", showBackground = true, widthDp = 360)
@Preview(name = "Acceso · Dark", showBackground = true, widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Acceso · Texto ampliado", showBackground = true, widthDp = 360, fontScale = 1.6f)
@Composable
private fun HistoryAccessPreview() = KipuTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MovementAccessCard(MovementHistoryUiState(fallbackUsed = true,
                accessStatus = MovementHistoryAccessDecision.PremiumRequired(MovementHistoryQuery())), {}, {}, {}, reducedMotion = true)
            MovementAccessCard(MovementHistoryUiState(fallbackUsed = true,
                accessStatus = MovementHistoryAccessDecision.RevalidationRequired(MovementHistoryQuery())), {}, {}, {}, reducedMotion = true)
            MovementAccessCard(MovementHistoryUiState(recovery = HistoryAccessRecovery.VERIFYING), {}, {}, {}, reducedMotion = true)
        }
    }
}

@Preview(name = "Comparación de cambios", showBackground = true, widthDp = 360)
@Composable
private fun ChangesPreview() = KipuTheme {
    MovementChangeSummary(MovementEditorUiState(isLoading = false,
        initialAmountMinor = 1550, amountText = "20.00", initialNote = "Compra semanal", note = "Compra corregida"), reducedMotion = true)
}
