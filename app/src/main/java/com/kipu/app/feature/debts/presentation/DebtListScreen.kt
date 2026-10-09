package com.kipu.app.feature.debts.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import com.kipu.app.feature.debts.domain.model.DebtSummary
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtListScreen(
    debts: List<DebtSummary>,
    selectedType: DebtObligationType?,
    onTypeSelected: (DebtObligationType?) -> Unit,
    onDebtSelected: (String) -> Unit,
    scheduledInstallments: Map<String, DebtScheduleItem> = emptyMap(),
    errorMessage: String? = null,
    isLoading: Boolean = false,
) {
    val filtered = remember(debts, selectedType) {
        debts.filter { selectedType == null || it.obligationType == selectedType }
    }
    val filters = listOf(
        "Todas" to null,
        "Debo" to DebtObligationType.PAYABLE,
        "Me deben" to DebtObligationType.RECEIVABLE,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Deudas y préstamos",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                windowInsets = TopAppBarDefaults.windowInsets,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            errorMessage?.takeIf { debts.isNotEmpty() }?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                filters.forEachIndexed { index, (label, type) ->
                    SegmentedButton(
                        selected = selectedType == type,
                        onClick = { onTypeSelected(type) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = filters.size),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(
                                when (type) {
                                    null -> "debt_filter_all"
                                    DebtObligationType.PAYABLE -> "debt_filter_payable"
                                    DebtObligationType.RECEIVABLE -> "debt_filter_receivable"
                                },
                            ),
                    ) {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            when {
                isLoading -> DebtListLoading(Modifier.weight(1f))
                errorMessage != null && debts.isEmpty() -> DebtListError(
                    message = errorMessage,
                    modifier = Modifier.weight(1f),
                )
                filtered.isEmpty() -> DebtListEmptyState(
                    selectedType = selectedType,
                    modifier = Modifier.weight(1f),
                )
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("debt_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filtered, key = DebtSummary::debtId) { debt ->
                        DebtListItem(
                            debt = debt,
                            scheduledInstallment = scheduledInstallments[debt.debtId],
                            onClick = { onDebtSelected(debt.debtId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DebtListLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("debt_list_loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        Text(
            text = "Cargando deudas",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun DebtListError(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag("debt_list_error"),
        )
    }
}

@Composable
private fun DebtListEmptyState(
    selectedType: DebtObligationType?,
    modifier: Modifier = Modifier,
) {
    val title = when (selectedType) {
        DebtObligationType.PAYABLE -> "No tienes deudas por pagar"
        DebtObligationType.RECEIVABLE -> "Aún no tienes préstamos por cobrar"
        null -> "Aún no tienes deudas ni préstamos"
    }
    val description = when (selectedType) {
        DebtObligationType.PAYABLE -> "Registra lo que debes para consultar su saldo y vencimiento."
        DebtObligationType.RECEIVABLE -> "Registra lo que prestaste para seguir cuánto te deben."
        null -> "Registra lo que debes o prestaste para tener tus saldos en un solo lugar."
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("debt_empty_state"),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            androidx.compose.material3.Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalance,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

    }
}

@Composable
private fun DebtListItem(
    debt: DebtSummary,
    scheduledInstallment: DebtScheduleItem?,
    onClick: () -> Unit,
) {
    val formatter = remember(debt.currencyCode) {
        NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("es").setRegion("PE").build()).apply {
            runCatching { Currency.getInstance(debt.currencyCode) }.getOrNull()?.let { currency = it }
        }
    }
    val obligationLabel = if (debt.obligationType == DebtObligationType.PAYABLE) "Debes" else "Te deben"

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("debt_item_${debt.debtId}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = debt.counterpartyName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$obligationLabel · ${debtLifecycleLabel(debt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (debt.status == DebtLifecycleStatus.CANCELLED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                debt.dueDate?.let {
                    Text("Vence $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                scheduledInstallment?.let { installment ->
                    val (highlight, statusLabel) = when (installment.status) {
                        "PENDING" -> "Cuota planificada · ${installment.dueDate}" to "Pendiente · no es pago"
                        "PARTIAL" -> "Cuota con pago parcial · ${installment.dueDate}" to "Pago parcial registrado"
                        "PAID" -> "Última cuota pagada · ${installment.dueDate}" to "Pago completado"
                        else -> "Plan de cuotas · ${installment.dueDate}" to "Plan de cuotas · no es pago"
                    }
                    Text(
                        text = highlight,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Saldo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = formatter.format(debt.remainingPrincipalMinor / 100.0),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun debtLifecycleLabel(debt: DebtSummary): String = when (debt.status) {
    DebtLifecycleStatus.ACTIVE -> "Activa"
    DebtLifecycleStatus.SETTLED -> "Liquidada"
    DebtLifecycleStatus.CANCELLED -> if (debt.remainingPrincipalMinor > 0L) {
        "Cancelada · saldo pendiente no pagado"
    } else "Cancelada"
}
