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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.debts.domain.model.CloseDebtAction
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import com.kipu.app.feature.debts.domain.model.DebtSummary
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

@Composable
fun DebtScheduleScreen(
    state: DebtScheduleUiState,
    onInstallmentCountChange: (String) -> Unit,
    onFirstDueDateChange: (String) -> Unit,
    onReminderLeadDaysChange: (Int?) -> Unit,
    onSchedule: () -> Unit,
    onCancelSchedule: () -> Unit,
    onCloseDebt: (CloseDebtAction, String) -> Unit,
    onNavigateBack: () -> Unit,
    onAdjustmentAmountChange: (String) -> Unit = {},
    onForgivenessAmountChange: (String) -> Unit = {},
    onClosureReasonChange: (String) -> Unit = {},
) {
    val debt = state.debt
    var attemptedAction by remember(debt?.debtId) { mutableStateOf<CloseDebtAction?>(null) }
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onNavigateBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Volver") }
                Text("Cuotas y cierre", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
            }
        },
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (debt == null) {
                Text(state.errorMessage ?: "Cargando deuda…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                DebtScheduleBalanceCard(debt)
                if (debt.status == DebtLifecycleStatus.ACTIVE && debt.remainingPrincipalMinor > 0L) {
                    Card(
                        modifier = Modifier.fillMaxWidth().animateContentSize(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Planificar cuotas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Cuotas planificadas \u00b7 no son pagos",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = state.installmentCount,
                                    onValueChange = onInstallmentCountChange,
                                    modifier = Modifier.weight(1f),
                                    label = { Text("Cantidad de cuotas") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                )
                                OutlinedTextField(
                                    value = state.reminderLeadDays?.toString().orEmpty(),
                                    onValueChange = { value ->
                                        onReminderLeadDaysChange(value.filter(Char::isDigit).take(3).toIntOrNull())
                                    },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("Avisar d\u00edas antes") },
                                    supportingText = { Text("Vac\u00edo desactiva el aviso") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                )
                            }
                            OutlinedTextField(
                                value = state.firstDueDate,
                                onValueChange = onFirstDueDateChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Primer vencimiento") },
                                supportingText = { Text("Formato AAAA-MM-DD; las siguientes cuotas son mensuales") },
                                singleLine = true,
                            )
                            Button(
                                onClick = onSchedule,
                                enabled = !state.isSaving,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            ) { Text(if (state.isSaving) "Guardando…" else "Guardar cronograma") }
                        }
                    }
                }

                AnimatedVisibility(visible = state.installments.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().animateContentSize(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Cronograma actual", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            state.installments.forEach { installment ->
                                DebtInstallmentRow(installment, debt.currencyCode)
                            }
                            if (state.installments.any { it.status == "PENDING" || it.status == "PARTIAL" }) {
                                OutlinedButton(
                                    onClick = onCancelSchedule,
                                    enabled = !state.isSaving,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("debt-schedule-cancel"),
                                ) { Text("Cancelar cuotas y avisos") }
                            }
                        }
                    }
                }

                if (debt.status == DebtLifecycleStatus.ACTIVE) {
                    Card(
                        modifier = Modifier.fillMaxWidth().animateContentSize(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Cerrar obligaci\u00f3n", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Una deuda solo se marca liquidada con saldo cero. Ajustes y condonaciones guardan motivo e historial.", style = MaterialTheme.typography.bodyMedium)
                            OutlinedTextField(
                                value = state.adjustmentAmount,
                                onValueChange = onAdjustmentAmountChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Ajuste en ${currencyLabel(debt.currencyCode)} (+/-)") },
                                supportingText = { Text("Positivo aumenta; negativo reduce el saldo") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = state.forgivenessAmount,
                                onValueChange = onForgivenessAmountChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Monto a condonar en ${currencyLabel(debt.currencyCode)}") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = state.closureReason,
                                onValueChange = onClosureReasonChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Motivo para el historial") },
                                minLines = 2,
                            )
                            OutlinedButton(
                                onClick = { attemptedAction = CloseDebtAction.ADJUST },
                                enabled = !state.isSaving,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) { Text("Aplicar ajuste") }
                            OutlinedButton(
                                onClick = { attemptedAction = CloseDebtAction.FORGIVE },
                                enabled = !state.isSaving,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) { Text("Registrar condonaci\u00f3n") }
                            OutlinedButton(
                                onClick = { attemptedAction = CloseDebtAction.CANCEL },
                                enabled = !state.isSaving,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) { Text("Cancelar obligaci\u00f3n") }
                            if (debt.remainingPrincipalMinor == 0L) {
                                Button(
                                    onClick = { onCloseDebt(CloseDebtAction.SETTLE, "") },
                                    enabled = !state.isSaving,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                ) { Text("Marcar como liquidada") }
                            }
                        }
                    }
                }
                state.successMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
                state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
    attemptedAction?.let { action ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { attemptedAction = null },
            title = { Text(closureTitle(action)) },
            text = { Text("Confirma esta acci\u00f3n. El motivo y el saldo actualizado quedar\u00e1n en el historial.") },
            confirmButton = {
                Button(onClick = {
                    attemptedAction = null
                    onCloseDebt(action, state.closureReason)
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { attemptedAction = null }) { Text("Volver") } },
        )
    }
}

@Composable
private fun DebtScheduleBalanceCard(debt: DebtSummary) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(debt.counterpartyName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Saldo pendiente", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatDebtAmount(debt.remainingPrincipalMinor, debt.currencyCode), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
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
            Text("Las cuotas son un plan; los pagos se registran por separado.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DebtInstallmentRow(installment: DebtScheduleItem, currencyCode: String) {
    Row(
        modifier = Modifier.fillMaxWidth().animateContentSize().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Cuota ${installment.installmentNumber} · ${installment.dueDate}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(installmentStatus(installment.status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(formatDebtAmount(installment.principalMinor, currencyCode), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

private fun installmentStatus(status: String): String = when (status) {
    "PENDING", "PLANNED" -> "Pendiente de pago"
    "PARTIAL" -> "Pago parcial"
    "PAID" -> "Pago registrado"
    "CANCELLED" -> "Cuota cancelada"
    else -> "Estado: $status"
}

private fun formatDebtAmount(minor: Long, currencyCode: String): String {
    val value = DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US)).format(minor / 100.0)
    return if (currencyCode == "PEN") "S/ $value" else "$currencyCode $value"
}

private fun currencyLabel(currencyCode: String): String = if (currencyCode == "PEN") "S/" else currencyCode

private fun closureTitle(action: CloseDebtAction): String = when (action) {
    CloseDebtAction.SETTLE -> "Marcar como liquidada"
    CloseDebtAction.CANCEL -> "Cancelar obligaci\u00f3n"
    CloseDebtAction.ADJUST -> "Aplicar ajuste de saldo"
    CloseDebtAction.FORGIVE -> "Registrar condonaci\u00f3n"
}
