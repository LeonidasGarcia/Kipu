package com.kipu.app.feature.debts.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class DebtEditUiState(
    val counterpartyName: String = "",
    val principalAmount: String = "",
    val currencyCode: String = "PEN",
    val dueDate: String = "",
    val notes: String = "",
    val revision: Long = 0L,
    val isSaving: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

@Composable
fun DebtEditDetailsScreen(
    state: DebtEditUiState,
    onCounterpartyNameChange: (String) -> Unit,
    onDueDateChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onNavigateBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Volver") }
                Text("Editar deuda", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
        },
        bottomBar = {
            Button(
                onClick = onSave,
                enabled = state.counterpartyName.isNotBlank() && state.revision > 0 && !state.isSaving && !state.isLoading,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
                    .heightIn(min = 52.dp).testTag("edit-debt-save"),
                shape = RoundedCornerShape(16.dp),
            ) { Text(if (state.isSaving) "Guardando…" else "Guardar cambios") }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Actualiza los datos de referencia; los movimientos y el saldo se conservan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = state.counterpartyName,
                onValueChange = onCounterpartyNameChange,
                label = { Text("Persona o entidad") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Principal · no editable", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(state.principalAmount, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            OutlinedTextField(
                value = state.dueDate,
                onValueChange = onDueDateChange,
                label = { Text("Vencimiento (opcional, AAAA-MM-DD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = onNotesChange,
                label = { Text("Notas") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
