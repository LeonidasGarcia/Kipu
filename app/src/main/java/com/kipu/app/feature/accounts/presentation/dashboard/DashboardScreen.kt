package com.kipu.app.feature.accounts.presentation.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.AccountWithBalance
import com.kipu.app.feature.accounts.domain.model.CreditCardWithSummary
import com.kipu.app.feature.accounts.presentation.AccountsViewModel
import com.kipu.app.ui.component.MaskedCardReference
import com.kipu.app.ui.component.MoneyText

import androidx.compose.material.icons.filled.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: AccountsViewModel,
    onNavigateToNewAccount: () -> Unit,
    onNavigateToNewCard: () -> Unit,
    onNavigateToMovements: () -> Unit = {},
    onAccountClick: (String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.dashboardUiState.collectAsState()
    val instruments by viewModel.instrumentsUiState.collectAsState()
    var showAddInstrumentSheet by remember { mutableStateOf(false) }
    var showQuotaSelection by remember { mutableStateOf(false) }

    Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Mi Dinero Real",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    actions = {
                        IconButton(onClick = onNavigateToMovements) {
                            Icon(
                                imageVector = Icons.Default.Payments,
                                contentDescription = "Historial de Movimientos",
                            )
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Ajustes",
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showAddInstrumentSheet = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Añadir instrumento")
                }
            },
            modifier = modifier,
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (state.errorMessage != null) {
                    Text(
                        text = state.errorMessage ?: "Error desconocido",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                    )
                } else {
                    val data = state.dashboardData
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            // Quota Indicator Card
                            QuotaCard(
                                currentCount = data?.activeComputableCount ?: 0,
                                maxQuota = data?.maxFreeQuota ?: 4,
                            )
                            if ((data?.activeComputableCount ?: 0) > (data?.maxFreeQuota ?: 4)) {
                                TextButton(onClick = { showQuotaSelection = true }) {
                                    Text("Elegir instrumentos disponibles")
                                }
                            }
                        }

                        item {
                            // Total Real Money Card
                            RealMoneyTotalsCard(
                                totalPen = data?.totalPen,
                                totalUsd = data?.totalUsd,
                            )
                        }

                        if (state.activeAlerts.isNotEmpty()) {
                            items(state.activeAlerts, key = { "${it.cardId.value}_${it.threshold.name}" }) { alert ->
                                com.kipu.app.feature.accounts.presentation.components.UtilizationAlertBanner(alert = alert)
                            }
                        }

                        item {
                            Text(
                                text = "Cuentas y Efectivo",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        if (data?.liquidAccounts.isNullOrEmpty()) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            text = "No tienes cuentas activas registradas",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                            }
                        } else {
                            items(data.liquidAccounts, key = { it.account.id.value }) { item ->
                                LiquidAccountCard(
                                    accountWithBalance = item,
                                    onClick = { onAccountClick(item.account.id.value) }
                                )
                            }
                        }

                        if (!data?.creditCards.isNullOrEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Tarjetas de Crédito",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                ) {
                                    Text(
                                        text = "El crédito representa capacidad de endeudamiento y pasivos; no forma parte de tu dinero real disponible.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(8.dp),
                                    )
                                }
                            }

                            items(data.creditCards, key = { it.card.id.value }) { creditItem ->
                                com.kipu.app.feature.accounts.presentation.components.CreditCardSummaryCard(
                                    creditCardWithSummary = creditItem,
                                    onClick = { onAccountClick(creditItem.card.id.value) },
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }
    if (showAddInstrumentSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddInstrumentSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Añadir instrumento", style = MaterialTheme.typography.titleLarge)
                Button(
                    onClick = {
                        showAddInstrumentSheet = false
                        onNavigateToNewAccount()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Default.AccountBalance, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Nueva cuenta")
                }
                Button(
                    onClick = {
                        showAddInstrumentSheet = false
                        onNavigateToNewCard()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Default.CreditCard, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Nueva tarjeta")
                }
            }
        }
    }
    if (showQuotaSelection) {
        val accounts = instruments.activeAccounts.filter { it.type != AccountType.CASH }
        InstrumentQuotaSelectionDialog(
            accounts = accounts,
            cards = instruments.activeCards,
            selectedIds = instruments.selectedFreeInstrumentIds,
            maxQuota = instruments.maxFreeQuota,
            onDismiss = { showQuotaSelection = false },
            onSave = { ids ->
                viewModel.saveFreeInstrumentSelection(ids)
                showQuotaSelection = false
            },
        )
    }
}

@Composable
private fun InstrumentQuotaSelectionDialog(
    accounts: List<com.kipu.app.feature.accounts.domain.model.Account>,
    cards: List<com.kipu.app.feature.accounts.domain.model.Card>,
    selectedIds: Set<String>,
    maxQuota: Int,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    val allIds = remember(accounts, cards) {
        (accounts.map { it.id.value } + cards.map { it.id.value }).toSet()
    }
    val selected = remember(allIds, selectedIds) {
        mutableStateListOf<String>().apply {
            val existing = selectedIds.intersect(allIds)
            addAll(if (existing.isNotEmpty()) existing else allIds.take(maxQuota))
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Instrumentos disponibles") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Elige hasta $maxQuota. Los demás conservarán su historial y saldos, pero no podrán usarse mientras superes el límite Free.")
                accounts.forEach { account ->
                    val id = account.id.value
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = id in selected, onCheckedChange = { checked ->
                            if (checked && selected.size < maxQuota) selected.add(id)
                            else if (!checked) selected.remove(id)
                        })
                        Text(account.alias, modifier = Modifier.weight(1f))
                    }
                }
                cards.forEach { card ->
                    val id = card.id.value
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = id in selected, onCheckedChange = { checked ->
                            if (checked && selected.size < maxQuota) selected.add(id)
                            else if (!checked) selected.remove(id)
                        })
                        Text(card.alias ?: "${card.issuer} •••• ${card.lastFourDigits}", modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selected.toSet()) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun QuotaCard(
    currentCount: Int,
    maxQuota: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Cupo Plan Free",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "$currentCount / $maxQuota activos",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (currentCount.toFloat() / maxQuota.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = if (currentCount >= maxQuota) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun RealMoneyTotalsCard(
    totalPen: com.kipu.app.core.finance.domain.model.Money?,
    totalUsd: com.kipu.app.core.finance.domain.model.Money?,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Total Disponible (Activos Líquidos)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Soles (PEN)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                    MoneyText(
                        money = totalPen ?: com.kipu.app.core.finance.domain.model.Money(0L, Currency.PEN),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                if (totalUsd != null && totalUsd.minorUnits > 0L) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Dólares (USD)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                        MoneyText(
                            money = totalUsd,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LiquidAccountCard(
    accountWithBalance: AccountWithBalance,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val account = accountWithBalance.account
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val iconVector = when (account.type) {
                        AccountType.CASH -> Icons.Default.Payments
                        AccountType.DIGITAL_WALLET -> Icons.Default.Wallet
                        else -> Icons.Default.AccountBalance
                    }
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                    Column {
                        Text(
                            text = account.alias,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val typeLabel = when (account.type) {
                            AccountType.CASH -> "Efectivo"
                            AccountType.SAVINGS -> "Ahorros"
                            AccountType.BANK -> "Corriente"
                            AccountType.DIGITAL_WALLET -> "Billetera"
                        }
                        Text(
                            text = "$typeLabel • ${account.currency.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (account.isPlanLocked) {
                            Text(
                                text = "Bloqueado por el plan Free",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                MoneyText(
                    money = accountWithBalance.balance,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Linked debit cards if any
            if (accountWithBalance.linkedDebitCards.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 40.dp),
                ) {
                    accountWithBalance.linkedDebitCards.forEach { card ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.CreditCard,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.outline,
                            )
                            Text(
                                text = "${card.issuer} ${card.network}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (card.isPlanLocked) {
                                Text(
                                    text = "Bloqueada por el plan Free",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            MaskedCardReference(
                                lastFourDigits = card.lastFourDigits,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
