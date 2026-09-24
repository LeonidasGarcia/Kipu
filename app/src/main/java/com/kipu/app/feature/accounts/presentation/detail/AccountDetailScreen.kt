package com.kipu.app.feature.accounts.presentation.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import com.kipu.app.core.finance.domain.MoneyInputParser
import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.Card as FinancialCard
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditCardWithSummary
import com.kipu.app.feature.accounts.presentation.AccountUiEvent
import com.kipu.app.feature.accounts.presentation.AccountsViewModel
import com.kipu.app.feature.accounts.presentation.instruments.PayCardDialog
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.formatMinorUnits

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    instrumentId: String,
    isCard: Boolean,
    viewModel: AccountsViewModel,
    onNavigateBack: () -> Unit,
    onReturnToDashboard: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val instruments by viewModel.instrumentsUiState.collectAsStateWithLifecycle()
    val dashboard by viewModel.dashboardUiState.collectAsStateWithLifecycle()
    val account = if (isCard) {
        null
    } else {
        (instruments.activeAccounts + instruments.archivedAccounts).firstOrNull { it.id.value == instrumentId }
    }
    val card = if (isCard) {
        (instruments.activeCards + instruments.archivedCards).firstOrNull { it.id.value == instrumentId }
    } else {
        null
    }
    val accountBalanceFlow = remember(account?.id) {
        account?.let { target ->
            viewModel.observeAccountBalance(target.id).map { it as Money? }
        } ?: flowOf(null)
    }
    val currentBalance by accountBalanceFlow.collectAsStateWithLifecycle(initialValue = null)
    val creditCardSummary = (card as? CreditCard)?.let { target ->
        dashboard.dashboardData?.creditCards?.firstOrNull { it.card.id == target.id }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var isSubmitting by remember(instrumentId) { mutableStateOf(false) }
    var showCardPayment by remember(instrumentId) { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AccountUiEvent.ShowMessage -> {
                    if (isSubmitting) {
                        isSubmitting = false
                        onReturnToDashboard(event.message)
                    } else {
                        snackbarHostState.showSnackbar(event.message)
                    }
                }
                is AccountUiEvent.Error -> {
                    isSubmitting = false
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    AccountDetailContent(
        account = account,
        card = card,
        currentBalance = currentBalance,
        creditCardSummary = creditCardSummary,
        isLoading = instruments.isLoading,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onSaveAppearance = { alias, preset ->
            account?.let { target ->
                isSubmitting = true
                val colorToken = if (preset == target.preset) target.colorToken else preset?.defaultColorToken
                val iconToken = if (preset == target.preset) target.iconToken else preset?.defaultIconToken
                viewModel.updateAppearance(
                    accountId = target.id,
                    alias = alias,
                    preset = preset,
                    colorToken = colorToken,
                    iconToken = iconToken,
                )
            }
        },
        onCorrectOpeningBalance = { correctedAmount ->
            account?.let { target ->
                isSubmitting = true
                viewModel.recordOpeningAdjustment(
                    accountId = target.id,
                    correctedAmount = Money(correctedAmount, target.currency),
                )
            }
        },
        onArchive = {
            isSubmitting = true
            viewModel.archiveInstrument(instrumentId, isCard)
        },
        onReactivate = {
            isSubmitting = true
            val isComputable = account?.isComputableForQuota ?: card?.isComputableForQuota ?: true
            viewModel.reactivateInstrument(instrumentId, isCard, isComputable)
        },
        onPayCreditCard = { showCardPayment = true },
        isSubmitting = isSubmitting,
    )

    if (showCardPayment && creditCardSummary != null) {
        PayCardDialog(
            creditCardWithSummary = creditCardSummary,
            eligibleAccounts = dashboard.dashboardData?.liquidAccounts.orEmpty(),
            viewModel = viewModel,
            onDismiss = { showCardPayment = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailContent(
    account: Account?,
    card: FinancialCard?,
    currentBalance: Money?,
    creditCardSummary: CreditCardWithSummary?,
    isLoading: Boolean,
    onNavigateBack: () -> Unit,
    onSaveAppearance: (alias: String, preset: AccountPreset?) -> Unit,
    onCorrectOpeningBalance: (Long) -> Unit,
    onArchive: () -> Unit,
    onReactivate: () -> Unit,
    onPayCreditCard: () -> Unit = {},
    isSubmitting: Boolean = false,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val instrumentKey = account?.id?.value ?: card?.id?.value
    val title = account?.alias ?: card?.alias ?: card?.let { "${it.issuer} ${it.network}" } ?: "Detalle"
    var alias by remember(instrumentKey, account?.alias) { mutableStateOf(account?.alias.orEmpty()) }
    var selectedPreset by remember(instrumentKey, account?.preset) { mutableStateOf(account?.preset) }
    var showArchiveConfirmation by remember(instrumentKey) { mutableStateOf(false) }
    var showReactivateConfirmation by remember(instrumentKey) { mutableStateOf(false) }
    var showOpeningCorrection by remember(instrumentKey) { mutableStateOf(false) }
    var openingAmount by remember(instrumentKey, account?.initialBalance?.minorUnits) {
        mutableStateOf(account?.initialBalance?.minorUnits?.let(::formatMinorUnits).orEmpty())
    }
    var aliasError by remember(instrumentKey) { mutableStateOf<String?>(null) }
    var openingAmountError by remember(instrumentKey) { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            account == null && card == null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("No encontramos este instrumento.", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onNavigateBack) { Text("Volver") }
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val archived = account?.isArchived ?: card?.isArchived ?: false
                InstrumentSummary(
                    account = account,
                    card = card,
                    currentBalance = currentBalance,
                    creditCardSummary = creditCardSummary,
                )

                if (archived) {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Este instrumento está archivado. Su historial y los saldos pendientes se conservan; reactívalo para volver a usarlo o liquidar deuda.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }

                account?.let { target ->
                    Text("Presentación", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(
                        value = alias,
                        onValueChange = { value ->
                            alias = value.take(80)
                            aliasError = if (value.isBlank()) "Escribe un alias para identificar la cuenta." else null
                        },
                        label = { Text("Alias de la cuenta") },
                        singleLine = true,
                        isError = aliasError != null,
                        supportingText = aliasError?.let { { Text(it) } },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Institución / presentación", style = MaterialTheme.typography.titleSmall)
                    AccountPreset.entries.chunked(3).forEach { presetRow ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            presetRow.forEach { preset ->
                                FilterChip(
                                    selected = selectedPreset == preset,
                                    onClick = { selectedPreset = preset },
                                    label = { Text(preset.defaultName) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    Button(
                        onClick = {
                            if (alias.isBlank()) {
                                aliasError = "Escribe un alias para identificar la cuenta."
                            } else {
                                onSaveAppearance(alias.trim(), selectedPreset)
                            }
                        },
                        enabled = !isSubmitting && alias.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text(if (isSubmitting) "Guardando..." else "Guardar presentación")
                    }

                    Text("Saldo inicial", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Corregir solo si el saldo con el que abriste la cuenta fue registrado incorrectamente. La corrección conserva el historial original mediante un reverso y un nuevo ajuste auditado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { showOpeningCorrection = true },
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text("Corregir saldo inicial")
                    }

                    if (!archived) {
                        OutlinedButton(
                            onClick = { showArchiveConfirmation = true },
                            enabled = !isSubmitting,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text("Archivar cuenta", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Button(
                            onClick = { showReactivateConfirmation = true },
                            enabled = !isSubmitting,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text("Reactivar cuenta")
                        }
                    }
                }

                if (card != null) {
                    if (card is CreditCard && !archived && (creditCardSummary?.debt?.minorUnits ?: 0L) > 0L) {
                        Button(
                            onClick = onPayCreditCard,
                            enabled = !isSubmitting,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text("Pagar tarjeta")
                        }
                    }
                    if (!archived) {
                        OutlinedButton(
                            onClick = { showArchiveConfirmation = true },
                            enabled = !isSubmitting,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text("Archivar tarjeta", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Button(
                            onClick = { showReactivateConfirmation = true },
                            enabled = !isSubmitting,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text("Reactivar tarjeta")
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showArchiveConfirmation) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirmation = false },
            title = { Text("¿Archivar ${if (card == null) "la cuenta" else "la tarjeta"}?") },
            text = {
                Text("No se eliminarán los movimientos ni el historial. Podrás encontrar el instrumento en Archivados y reactivarlo después.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showArchiveConfirmation = false
                    onArchive()
                }) { Text("Archivar") }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveConfirmation = false }) { Text("Cancelar") }
            },
        )
    }

    if (showReactivateConfirmation) {
        AlertDialog(
            onDismissRequest = { showReactivateConfirmation = false },
            title = { Text("¿Reactivar ${if (card == null) "la cuenta" else "la tarjeta"}?") },
            text = {
                Text("El instrumento volverá a estar activo. La reactivación puede estar sujeta al cupo de tu plan; su historial se mantiene.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showReactivateConfirmation = false
                    onReactivate()
                }) { Text("Reactivar") }
            },
            dismissButton = {
                TextButton(onClick = { showReactivateConfirmation = false }) { Text("Cancelar") }
            },
        )
    }

    if (showOpeningCorrection && account != null) {
        AlertDialog(
            onDismissRequest = { showOpeningCorrection = false },
            title = { Text("Corrección auditada del saldo inicial") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Se conserva el movimiento de apertura original y se registra su reverso junto con el importe corregido.")
                    OutlinedTextField(
                        value = openingAmount,
                        onValueChange = { value ->
                            openingAmount = value
                            openingAmountError = null
                        },
                        label = { Text("Nuevo saldo inicial (${account.currency.name})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = openingAmountError != null,
                        supportingText = openingAmountError?.let { { Text(it) } },
                        shape = MaterialTheme.shapes.medium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amount = MoneyInputParser.parseMinorUnits(openingAmount)
                    if (amount == null) {
                        openingAmountError = "Ingresa un importe válido con hasta dos decimales."
                    } else {
                        showOpeningCorrection = false
                        onCorrectOpeningBalance(amount)
                    }
                }) { Text("Registrar corrección") }
            },
            dismissButton = {
                TextButton(onClick = { showOpeningCorrection = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun InstrumentSummary(
    account: Account?,
    card: FinancialCard?,
    currentBalance: Money?,
    creditCardSummary: CreditCardWithSummary?,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = account?.let { it.type.toDetailLabel() } ?: card?.let {
                    if (it is CreditCard) "Tarjeta de crédito" else "Tarjeta de débito"
                }.orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = account?.currency?.name ?: card?.currency?.name.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            if (account != null) {
                if (account.isArchived) {
                    Text("Saldo inicial registrado", style = MaterialTheme.typography.labelMedium)
                    MoneyText(money = account.initialBalance, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                } else if (currentBalance != null) {
                    Text("Saldo actual", style = MaterialTheme.typography.labelMedium)
                    MoneyText(money = currentBalance, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            creditCardSummary?.let { summary ->
                Text("Deuda actual", style = MaterialTheme.typography.labelMedium)
                MoneyText(
                    money = summary.debt,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (summary.debt.minorUnits > 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text("Crédito disponible", style = MaterialTheme.typography.labelMedium)
                MoneyText(money = summary.availableCredit, style = MaterialTheme.typography.titleMedium)
            }
            card?.let { target ->
                Text("${target.issuer} • ${target.network} •••• ${target.lastFourDigits}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun AccountType.toDetailLabel(): String = when (this) {
    AccountType.CASH -> "Efectivo"
    AccountType.SAVINGS -> "Cuenta de ahorros"
    AccountType.BANK -> "Cuenta corriente"
    AccountType.DIGITAL_WALLET -> "Billetera digital"
}
