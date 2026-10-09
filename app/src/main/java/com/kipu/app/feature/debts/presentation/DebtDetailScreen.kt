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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import com.kipu.app.feature.debts.domain.model.DebtSummary
import kotlin.math.absoluteValue
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

data class DebtSettlementActivity(
    val eventId: String,
    val principalMinor: Long,
    val interestMinor: Long,
    val occurredAt: Long,
    val isVoided: Boolean,
    val eventType: String = "PAYMENT",
    val principalDeltaMinor: Long? = null,
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
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
    installments: List<DebtScheduleItem> = emptyList(),
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val currency = runCatching { Currency.getInstance(debt.currencyCode) }.getOrNull()
    val formatter = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("es").setRegion("PE").build())
    if (currency != null) formatter.currency = currency
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Detalle de deuda",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets,
            )
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
                    debt.dueDate?.let { Text("Vence el ${formatDebtDate(it)}", style = MaterialTheme.typography.bodyMedium) }
                }
            }
            debt.notes?.takeIf(String::isNotBlank)?.let { notes ->
                Text(notes, style = MaterialTheme.typography.bodyLarge)
            }
            if (installments.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().animateContentSize().testTag("debt-installment-plan"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Plan de cuotas (no son pagos)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        installments.sortedBy(DebtScheduleItem::installmentNumber).forEach { installment ->
                            Row(
                                modifier = Modifier.fillMaxWidth().animateContentSize(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Cuota ${installment.installmentNumber} · ${formatDebtDate(installment.dueDate)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    Text(installmentStatusLabel(installment.status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(formatter.format(installment.principalMinor / 100.0), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            if (activities.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().animateContentSize().testTag("debt-settlement-activities"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Historial de pagos, ajustes y condonaciones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        activities.forEach { activity ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                when (activity.eventType) {
                                    "PAYMENT" -> Text("Principal · reduce el saldo", style = MaterialTheme.typography.bodyMedium)
                                    "ADJUSTMENT" -> {
                                        Text("Ajuste de principal", style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            if ((activity.principalDeltaMinor ?: 0L) >= 0L) "Aumenta el saldo" else "Reduce el saldo",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    "FORGIVENESS" -> {
                                        Text("Condonación de principal", style = MaterialTheme.typography.bodyMedium)
                                        Text("Reduce el saldo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    else -> Text("Movimiento de deuda", style = MaterialTheme.typography.bodyMedium)
                                }
                                val displayedPrincipal = if (activity.eventType == "PAYMENT") {
                                    activity.principalMinor
                                } else {
                                    (activity.principalDeltaMinor ?: activity.principalMinor).absoluteValue
                                }
                                Text(formatter.format(displayedPrincipal / 100.0), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                if (activity.eventType == "PAYMENT" && activity.interestMinor > 0L) {
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
            if (debt.status == DebtLifecycleStatus.SETTLED) {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("debt-settled-state"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        "Deuda liquidada. El saldo quedó en cero y el historial se conserva.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else if (debt.status == DebtLifecycleStatus.CANCELLED) {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("debt-cancelled-state"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        if (debt.remainingPrincipalMinor > 0L) {
                            "Deuda cancelada. El saldo pendiente no se registró como pago."
                        } else {
                            "Deuda cancelada. El historial se conserva."
                        },
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (debt.status == DebtLifecycleStatus.ACTIVE) {
                Button(onClick = onSettle, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(if (debt.obligationType.name == "PAYABLE") "Registrar pago" else "Registrar cobro")
                }
                OutlinedButton(onClick = onSchedule, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("Gestionar cuotas")
                }
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
                    "El saldo pendiente, la apertura y cualquier movimiento se conservan como historial.",
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
            text = { Text("Se elimina la obligacion y sus cuotas sin pagos asociados. Esta accion no se puede deshacer.") },
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

private fun installmentStatusLabel(status: String): String = when (status) {
    "PENDING", "PLANNED" -> "Pendiente de pago"
    "PARTIAL" -> "Pago parcial"
    "PAID" -> "Pago registrado"
    "CANCELLED" -> "Cuota cancelada"
    else -> "Estado: $status"
}
