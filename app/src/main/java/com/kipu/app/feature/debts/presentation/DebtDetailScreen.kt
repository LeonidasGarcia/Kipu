package com.kipu.app.feature.debts.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtSummary
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

data class DebtSettlementActivity(
    val eventId: String,
    val principalMinor: Long,
    val interestMinor: Long,
    val occurredAt: Long,
    val isVoided: Boolean,
)

@Composable
fun DebtDetailScreen(
    debt: DebtSummary,
    hasFinancialHistory: Boolean,
    onNavigateBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSettle: () -> Unit = {},
    onSchedule: () -> Unit = {},
    errorMessage: String? = null,
    activities: List<DebtSettlementActivity> = emptyList(),
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val currency = runCatching { Currency.getInstance(debt.currencyCode) }.getOrNull()
    val formatter = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("es").setRegion("PE").build())
    if (currency != null) formatter.currency = currency
    Scaffold(
        topBar = {
            Row(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp)) {
                TextButton(onClick = onNavigateBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Volver") }
                Text("Detalle de deuda", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
            }
        },
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(debt.counterpartyName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Card(
                modifier = Modifier.fillMaxWidth().animateContentSize(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Saldo pendiente", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatter.format(debt.remainingPrincipalMinor / 100.0), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        when (debt.status) {
                            DebtLifecycleStatus.ACTIVE -> "Obligaci\u00f3n activa"
                            DebtLifecycleStatus.SETTLED -> "Liquidada \u00b7 saldo cero"
                            DebtLifecycleStatus.CANCELLED -> if (debt.remainingPrincipalMinor > 0L) {
                                "Cancelada \u00b7 saldo pendiente no pagado"
                            } else "Cancelada"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (debt.status == DebtLifecycleStatus.CANCELLED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Principal original: ${formatter.format(debt.principalMinor / 100.0)}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (debt.openingMode == DebtOpeningMode.HISTORICAL) "Apertura histórica" else "Dinero recibido o entregado ahora",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text("${debt.currencyCode} · ${debt.openedOn}", style = MaterialTheme.typography.bodySmall)
                    debt.dueDate?.let { Text("Vence el $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
            debt.notes?.takeIf(String::isNotBlank)?.let { notes ->
                Text(notes, style = MaterialTheme.typography.bodyLarge)
            }
            if (activities.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().animateContentSize().testTag("debt-settlement-activities"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Historial de pagos y cobros", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        activities.forEach { activity ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Principal · reduce el saldo", style = MaterialTheme.typography.bodyMedium)
                                Text(formatter.format(activity.principalMinor / 100.0), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                if (activity.interestMinor > 0L) {
                                    Text(
                                        if (debt.obligationType.name == "PAYABLE") "Interés · gasto operativo" else "Interés · ingreso operativo",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(formatter.format(activity.interestMinor / 100.0), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                }
                                if (activity.isVoided) {
                                    Text("Anulado · saldo restaurado", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
            Button(onClick = onSettle, enabled = debt.remainingPrincipalMinor > 0, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (debt.obligationType.name == "PAYABLE") "Registrar pago" else "Registrar cobro")
            }
            OutlinedButton(onClick = onSchedule, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("Cuotas y cierre")
            }
            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("Editar datos")
            }
            OutlinedButton(
                onClick = { showDeleteConfirmation = true },
                enabled = !hasFinancialHistory,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text("Eliminar deuda")
            }
            AnimatedVisibility(visible = hasFinancialHistory) {
                Text(
                    "Con historial financiero no se puede borrar.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
    }
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("¿Eliminar esta deuda?") },
            text = { Text("La deuda y su apertura histórica se quitarán de tu lista. Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                    modifier = Modifier.testTag("confirm-delete-debt"),
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancelar") }
            },
        )
    }
}
