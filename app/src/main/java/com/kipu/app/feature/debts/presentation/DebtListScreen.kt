package com.kipu.app.feature.debts.presentation

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@Composable
fun DebtListScreen(
    debts: List<DebtSummary>,
    selectedType: DebtObligationType?,
    onTypeSelected: (DebtObligationType?) -> Unit,
    onDebtSelected: (String) -> Unit,
    onAddPayable: () -> Unit,
    onAddReceivable: () -> Unit,
    scheduledInstallments: Map<String, DebtScheduleItem> = emptyMap(),
    errorMessage: String? = null,
    openRegisterDebt: Boolean = false,
    onConsumeRegisterDebt: () -> Unit = {},
) {
    val filtered = debts.filter { selectedType == null || it.obligationType == selectedType }
    val emeraldColors = rememberCalmEmeraldColors()
    var showRegisterTypeSheet by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(openRegisterDebt) {
        if (openRegisterDebt) {
            showRegisterTypeSheet = true
            onConsumeRegisterDebt()
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Deudas y préstamos",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            errorMessage?.let {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEE2E2),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    Text(it, color = Color(0xFFDC2626), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedType == null,
                    onClick = { onTypeSelected(null) },
                    label = { Text("Todas") },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = emeraldColors.pillTrack,
                        selectedLabelColor = emeraldColors.primaryDeep,
                    ),
                )
                FilterChip(
                    selected = selectedType == DebtObligationType.PAYABLE,
                    onClick = { onTypeSelected(DebtObligationType.PAYABLE) },
                    label = { Text("Debo") },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = emeraldColors.pillTrack,
                        selectedLabelColor = emeraldColors.primaryDeep,
                    ),
                )
                FilterChip(
                    selected = selectedType == DebtObligationType.RECEIVABLE,
                    onClick = { onTypeSelected(DebtObligationType.RECEIVABLE) },
                    label = { Text("Me deben") },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = emeraldColors.pillTrack,
                        selectedLabelColor = emeraldColors.primaryDeep,
                    ),
                )
            }
            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(emeraldColors.pillTrack, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Handshake, contentDescription = null, tint = emeraldColors.primaryDeep, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.heightIn(min = 16.dp))
                    Text("Aquí aparecerán tus deudas y préstamos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Registra lo que debes o lo que prestaste para seguir cada saldo sin perder el control.", color = emeraldColors.secondaryMuted, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
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

    if (showRegisterTypeSheet) {
        DebtRegisterTypeBottomSheet(
            onDismiss = { showRegisterTypeSheet = false },
            onSelectPayable = {
                showRegisterTypeSheet = false
                onAddPayable()
            },
            onSelectReceivable = {
                showRegisterTypeSheet = false
                onAddReceivable()
            },
        )
    }
}

@Composable
private fun DebtListItem(debt: DebtSummary, scheduledInstallment: DebtScheduleItem?, onClick: () -> Unit) {
    val formatter = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("es").setRegion("PE").build())
    runCatching { Currency.getInstance(debt.currencyCode) }.getOrNull()?.let { formatter.currency = it }
    val emeraldColors = rememberCalmEmeraldColors()

    val initial = debt.counterpartyName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val isSettled = debt.status == DebtLifecycleStatus.SETTLED

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .animateContentSize()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, emeraldColors.borderSubtle),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (isSettled) emeraldColors.pillTrack else if (debt.obligationType == DebtObligationType.PAYABLE) Color(0xFFFEE2E2) else Color(0xFFDCFCE7),
                            RoundedCornerShape(12.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        initial,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = if (isSettled) emeraldColors.primaryDeep else if (debt.obligationType == DebtObligationType.PAYABLE) Color(0xFFDC2626) else emeraldColors.incomeAccent,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(debt.counterpartyName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (debt.obligationType == DebtObligationType.PAYABLE) "Debes a esta persona" else "Te debe esta persona",
                        style = MaterialTheme.typography.bodySmall,
                        color = emeraldColors.secondaryMuted,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSettled) emeraldColors.pillTrack else if (debt.status == DebtLifecycleStatus.CANCELLED) Color(0xFFFEE2E2) else emeraldColors.surfaceCard,
                        ) {
                            Text(
                                debtLifecycleLabel(debt),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isSettled) emeraldColors.incomeAccent else if (debt.status == DebtLifecycleStatus.CANCELLED) Color(0xFFDC2626) else emeraldColors.primaryDeep,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        debt.dueDate?.let {
                            Text("Vence $it", style = MaterialTheme.typography.bodySmall, color = emeraldColors.secondaryMuted)
                        }
                    }
                    scheduledInstallment?.let { installment ->
                        Text(
                            if (installment.status == "PAID") "Cuota · ${installment.dueDate}"
                            else "Cuota planificada · ${installment.dueDate}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = emeraldColors.primaryDeep,
                        )
                    }
                }
            }
            Text(
                formatter.format(debt.remainingPrincipalMinor / 100.0),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = if (isSettled) emeraldColors.incomeAccent else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun debtLifecycleLabel(debt: DebtSummary): String = when (debt.status) {
    DebtLifecycleStatus.ACTIVE -> "Activa"
    DebtLifecycleStatus.SETTLED -> "Liquidada"
    DebtLifecycleStatus.CANCELLED -> if (debt.remainingPrincipalMinor > 0L) {
        "Cancelada · pendiente"
    } else "Cancelada"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtRegisterTypeBottomSheet(
    onDismiss: () -> Unit,
    onSelectPayable: () -> Unit,
    onSelectReceivable: () -> Unit,
) {
    val emeraldColors = rememberCalmEmeraldColors()
    com.kipu.app.ui.component.KipuBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Registrar en Deudas y Préstamos",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Selecciona si recibiste un préstamo o si prestaste dinero.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = emeraldColors.secondaryMuted,
                )
            }

            Card(
                onClick = onSelectPayable,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, emeraldColors.borderSubtle),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(emeraldColors.pillTrack, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowDownward,
                            contentDescription = null,
                            tint = emeraldColors.primaryDeep,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Me prestaron dinero",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Registra una deuda por pagar a un acreedor o persona.",
                            style = MaterialTheme.typography.bodySmall,
                            color = emeraldColors.secondaryMuted,
                        )
                    }
                }
            }

            Card(
                onClick = onSelectReceivable,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, emeraldColors.borderSubtle),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(emeraldColors.incomeBg, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowUpward,
                            contentDescription = null,
                            tint = emeraldColors.incomeEmerald,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Presté dinero",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Registra un préstamo por cobrar que diste a alguien.",
                            style = MaterialTheme.typography.bodySmall,
                            color = emeraldColors.secondaryMuted,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
