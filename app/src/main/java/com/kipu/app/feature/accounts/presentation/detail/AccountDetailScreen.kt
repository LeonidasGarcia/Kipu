package com.kipu.app.feature.accounts.presentation.detail

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.KipuMotionTokens
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipu.app.core.finance.domain.CreditCalculations
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.FinancialMovement
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.MovementKind
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.Card as FinancialCard
import com.kipu.app.feature.accounts.domain.model.CardPaymentSuggestion
import com.kipu.app.feature.accounts.domain.model.CardPreset
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditCardWithSummary
import com.kipu.app.feature.accounts.domain.model.CreditUtilizationNotification
import com.kipu.app.feature.accounts.presentation.AccountUiEvent
import com.kipu.app.feature.accounts.presentation.AccountsViewModel
import com.kipu.app.core.finance.domain.MoneyInputParser
import com.kipu.app.feature.accounts.presentation.components.CardStylePresets
import com.kipu.app.feature.accounts.presentation.components.CardStyleType
import com.kipu.app.feature.accounts.presentation.instruments.PayCardDialog
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.formatMinorUnits
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    instrumentId: String,
    isCard: Boolean,
    viewModel: AccountsViewModel,
    onNavigateBack: () -> Unit,
    onReturnToDashboard: (String) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToRateCatalog: (String) -> Unit = {},
    cardMovements: List<FinancialMovement>? = null,
    startPurchaseOnOpen: Boolean = false,
) {
    val instruments by viewModel.instrumentsUiState.collectAsStateWithLifecycle()
    val dashboard by viewModel.dashboardUiState.collectAsStateWithLifecycle()
    val creditNotifications by viewModel.creditNotifications.collectAsStateWithLifecycle()
    val purchaseCategories by viewModel.purchaseCategories.collectAsStateWithLifecycle()
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
    val observedCardMovementsFlow = remember(card?.id) {
        val movementFlow = (card as? CreditCard)?.let { viewModel.observeCardMovements(it.id) }
        if (movementFlow == null) {
            flowOf<List<FinancialMovement>?>(null)
        } else {
            movementFlow.map { movements -> movements as List<FinancialMovement>? }
        }
    }
    val observedCardMovements by observedCardMovementsFlow.collectAsStateWithLifecycle(initialValue = null)
    val effectiveCardMovements = cardMovements ?: observedCardMovements.orEmpty()
    val isCardMovementsLoading = card is CreditCard && cardMovements == null && observedCardMovements == null
    val nextInstallmentPaymentFlow = remember(card?.id) {
        (card as? CreditCard)?.let { viewModel.observeNextInstallmentPayment(it.id) }
            ?: flowOf<CardPaymentSuggestion?>(null)
    }
    val nextInstallmentPayment by nextInstallmentPaymentFlow.collectAsStateWithLifecycle(initialValue = null)

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var isSubmitting by remember(instrumentId) { mutableStateOf(false) }
    var keepDetailOpenAfterSave by remember(instrumentId) { mutableStateOf(false) }
    var showCardPayment by remember(instrumentId) { mutableStateOf(false) }
    var showPurchaseDraft by remember(instrumentId, startPurchaseOnOpen) { mutableStateOf(startPurchaseOnOpen) }

    LaunchedEffect(viewModel, instrumentId, isCard) {
        viewModel.refreshCreditUtilizationNotifications(instrumentId.takeIf { isCard })
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AccountUiEvent.ShowMessage -> {
                    if (isSubmitting) {
                        isSubmitting = false
                        if (keepDetailOpenAfterSave) {
                            keepDetailOpenAfterSave = false
                            snackbarHostState.showSnackbar(event.message)
                        } else {
                            onReturnToDashboard(event.message)
                        }
                    } else {
                        snackbarHostState.showSnackbar(event.message)
                    }
                }
                is AccountUiEvent.Error -> {
                    isSubmitting = false
                    keepDetailOpenAfterSave = false
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
        creditNotifications = creditNotifications.filter { it.cardId == instrumentId },
        cardMovements = effectiveCardMovements,
        isCardMovementsLoading = isCardMovementsLoading,
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
        onSaveCardAppearance = { alias, preset ->
            (card as? CreditCard)?.let { target ->
                isSubmitting = true
                keepDetailOpenAfterSave = true
                val colorToken = if (preset == target.preset) target.colorToken else preset?.defaultColorToken
                val iconToken = if (preset == target.preset) target.iconToken else preset?.defaultIconToken
                viewModel.updateCardAppearance(
                    cardId = target.id,
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
        onNavigateToRateCatalog = onNavigateToRateCatalog,
        onStartCreditPurchase = { showPurchaseDraft = true },
        isSubmitting = isSubmitting,
    )

    if (showCardPayment && creditCardSummary != null) {
        PayCardDialog(
            creditCardWithSummary = creditCardSummary,
            nextInstallmentDue = nextInstallmentPayment,
            eligibleAccounts = dashboard.dashboardData?.liquidAccounts.orEmpty(),
            onPayCreditCard = viewModel::payCreditCard,
            onDismiss = { showCardPayment = false },
        )
    }

    if (showPurchaseDraft && card is CreditCard) {
        CreditPurchaseDraftSheet(
            card = card,
            categories = purchaseCategories,
            onDismiss = { showPurchaseDraft = false },
            onConfirmPurchase = { candidate, installments ->
                if (!isSubmitting) {
                    isSubmitting = true
                    viewModel.confirmCreditPurchase(
                        cardId = card.id,
                        amount = candidate.amount,
                        merchant = candidate.merchant,
                        effectiveAt = candidate.occurredAt,
                        installments = installments,
                        categoryId = requireNotNull(candidate.categoryId),
                        merchantId = candidate.merchantId,
                    )
                    showPurchaseDraft = false
                }
            },
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
    onSaveCardAppearance: (alias: String?, preset: CardPreset?) -> Unit = { _, _ -> },
    onCorrectOpeningBalance: (Long) -> Unit,
    onArchive: () -> Unit,
    onReactivate: () -> Unit,
    modifier: Modifier = Modifier,
    creditNotifications: List<CreditUtilizationNotification> = emptyList(),
    onPayCreditCard: () -> Unit = {},
    onNavigateToRateCatalog: (String) -> Unit = {},
    onStartCreditPurchase: () -> Unit = {},
    cardMovements: List<FinancialMovement> = emptyList(),
    isCardMovementsLoading: Boolean = false,
    isSubmitting: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val instrumentKey = account?.id?.value ?: card?.id?.value
    val title = account?.alias ?: card?.alias ?: card?.let { "${it.issuer} ${it.network}" } ?: "Detalle"
    var alias by remember(instrumentKey, account?.alias) { mutableStateOf(account?.alias.orEmpty()) }
    var selectedPreset by remember(instrumentKey, account?.preset) { mutableStateOf(account?.preset) }
    var presetMenuExpanded by remember(instrumentKey) { mutableStateOf(false) }
    var cardAlias by remember(instrumentKey, card?.alias) { mutableStateOf(card?.alias.orEmpty()) }
    var selectedCardPreset by remember(instrumentKey, card?.preset) { mutableStateOf(card?.preset) }
    var cardPresetMenuExpanded by remember(instrumentKey) { mutableStateOf(false) }
    var showArchiveConfirmation by remember(instrumentKey) { mutableStateOf(false) }
    var showReactivateConfirmation by remember(instrumentKey) { mutableStateOf(false) }
    var showOpeningCorrection by remember(instrumentKey) { mutableStateOf(false) }
    var openingAmount by remember(instrumentKey, account?.initialBalance?.minorUnits) {
        mutableStateOf(account?.initialBalance?.minorUnits?.let(::formatMinorUnits).orEmpty())
    }
    var aliasError by remember(instrumentKey) { mutableStateOf<String?>(null) }
    var openingAmountError by remember(instrumentKey) { mutableStateOf<String?>(null) }
    val archived = account?.isArchived ?: card?.isArchived ?: false

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (card is CreditCard) {
                            Text(
                                text = if (card.isArchived) "Tarjeta de Crédito archivada" else "Tarjeta de Crédito activa",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (card != null) {
                            Text(
                                text = if (card.isArchived) "Tarjeta de Débito archivada" else "Tarjeta de Débito activa",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (account != null && account.isArchived) {
                            Text(
                                text = "Cuenta archivada",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
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
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (card is CreditCard) {
                    // R10 3D Kibo Credit Card
                    Kibo3DCreditCard(card = card)

                    creditNotifications.forEach { notification ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(notification.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(notification.body, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

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

                    // R10 Estado del Periodo Actual Panel
                    CreditCardPeriodPanel(
                        card = card,
                        summary = creditCardSummary,
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("card_presentation_card"),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("Identificación de la tarjeta", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = cardAlias,
                                onValueChange = { cardAlias = it.take(80) },
                                label = { Text("Nombre visible") },
                                placeholder = { Text("${card.issuer} · ${card.network.name}") },
                                singleLine = true,
                                enabled = !isSubmitting,
                                modifier = Modifier.fillMaxWidth().testTag("card_alias_field"),
                            )
                            Box {
                                OutlinedButton(
                                    onClick = { cardPresetMenuExpanded = true },
                                    enabled = !isSubmitting,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        .testTag("card_preset_selector"),
                                ) {
                                    Icon(Icons.Default.CreditCard, contentDescription = null)
                                    Text(
                                        selectedCardPreset?.defaultName ?: "Diseño original",
                                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Elegir diseño")
                                }
                                DropdownMenu(
                                    expanded = cardPresetMenuExpanded,
                                    onDismissRequest = { cardPresetMenuExpanded = false },
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                                ) {
                                    CardPreset.entries.forEach { preset ->
                                        DropdownMenuItem(
                                            text = { Text(preset.defaultName) },
                                            modifier = Modifier.testTag("card_preset_" + preset.name.lowercase()),
                                            onClick = {
                                                selectedCardPreset = preset
                                                cardPresetMenuExpanded = false
                                            },
                                            trailingIcon = if (selectedCardPreset == preset) {
                                                { Icon(Icons.Default.CheckCircle, contentDescription = "Seleccionado") }
                                            } else {
                                                null
                                            },
                                        )
                                    }
                                }
                            }
                            Text(
                                "Los consumos, el límite y las fechas de pago no cambian.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = {
                                    onSaveCardAppearance(cardAlias.trim().takeIf(String::isNotBlank), selectedCardPreset)
                                },
                                enabled = !isSubmitting,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .testTag("save_card_presentation"),
                            ) {
                                Text(if (isSubmitting) "Guardando..." else "Guardar cambios")
                            }
                        }
                    }

                    // R10 CTAs and Actions
                    CreditCardActionsSection(
                        card = card,
                        summary = creditCardSummary,
                        isArchived = archived,
                        isSubmitting = isSubmitting,
                        onStartCreditPurchase = onStartCreditPurchase,
                        onPayCreditCard = onPayCreditCard,
                        onNavigateToRateCatalog = onNavigateToRateCatalog,
                        onArchiveClick = { showArchiveConfirmation = true },
                        onReactivateClick = { showReactivateConfirmation = true },
                    )

                    // R10 Movements Section
                    CreditCardMovementsSection(
                        movements = cardMovements,
                        currency = card.currency,
                        isLoading = isCardMovementsLoading,
                    )
                } else {
                    // Liquid Account or Debit Card view (preserved in full)
                    InstrumentSummary(
                        account = account,
                        card = card,
                        currentBalance = currentBalance,
                        creditCardSummary = creditCardSummary,
                    )

                    creditNotifications.forEach { notification ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(notification.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(notification.body, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

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

                    account?.let {
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("account_presentation_card"),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    "Presentación",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    "Personaliza cómo identificas esta cuenta.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedTextField(
                                    value = alias,
                                    onValueChange = { value ->
                                        alias = value.take(80)
                                        aliasError = if (value.isBlank()) "Escribe un alias para identificar la cuenta." else null
                                    },
                                    label = { Text("Nombre de la cuenta") },
                                    singleLine = true,
                                    isError = aliasError != null,
                                    supportingText = aliasError?.let { { Text(it) } },
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth().testTag("account_alias_field"),
                                )
                                if (account.type != AccountType.CASH) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Institución", style = MaterialTheme.typography.labelLarge)
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        OutlinedButton(
                                            onClick = { presetMenuExpanded = true },
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                                .testTag("account_preset_selector"),
                                            shape = MaterialTheme.shapes.medium,
                                            ) {
                                                Icon(
                                                    imageVector = selectedPreset?.let(::accountPresetIcon)
                                                        ?: Icons.Default.AccountBalance,
                                                    contentDescription = null,
                                                )
                                                Text(
                                                    selectedPreset?.defaultName ?: "Elegir institución",
                                                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Elegir institución")
                                        }
                                        DropdownMenu(
                                            expanded = presetMenuExpanded,
                                            onDismissRequest = { presetMenuExpanded = false },
                                            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                                        ) {
                                            AccountPreset.entries.filter { preset ->
                                                when (account.type) {
                                                    AccountType.DIGITAL_WALLET -> preset == AccountPreset.YAPE ||
                                                        preset == AccountPreset.PLIN || preset == AccountPreset.GENERIC
                                                    AccountType.SAVINGS, AccountType.BANK -> preset != AccountPreset.CASH &&
                                                        preset != AccountPreset.YAPE && preset != AccountPreset.PLIN
                                                    AccountType.CASH -> false
                                                    AccountType.CREDIT_LIABILITY -> preset != AccountPreset.CASH
                                                }
                                            }.forEach { preset ->
                                                DropdownMenuItem(
                                                    text = { Text(preset.defaultName) },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = accountPresetIcon(preset),
                                                            contentDescription = null,
                                                        )
                                                    },
                                                    onClick = {
                                                        selectedPreset = preset
                                                        presetMenuExpanded = false
                                                    },
                                                    trailingIcon = if (selectedPreset == preset) {
                                                        { Icon(Icons.Default.CheckCircle, contentDescription = "Seleccionada") }
                                                    } else {
                                                        null
                                                    },
                                                    modifier = Modifier.testTag("account_preset_" + preset.name.lowercase()),
                                                )
                                            }
                                        }
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
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        .testTag("save_account_presentation"),
                                ) {
                                    Text(if (isSubmitting) "Guardando..." else "Guardar presentación")
                                }
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    "Saldo inicial",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "Corregir solo si el saldo con el que abriste la cuenta fue registrado incorrectamente. La corrección conserva el historial original mediante un reverso y un nuevo ajuste auditado.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedButton(
                                    onClick = { showOpeningCorrection = true },
                                    enabled = !isSubmitting,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                ) {
                                    Text("Corregir saldo inicial")
                                }
                            }
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
                    if (card != null && card !is CreditCard) {
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
private fun Kibo3DCreditCard(
    card: CreditCard,
    modifier: Modifier = Modifier,
) {
    var isFlipped by remember { mutableStateOf(false) }
    val reducedMotion = rememberReducedMotionEnabled()
    val rotation = animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            tween(durationMillis = KipuMotionTokens.CardFlipMillis, easing = FastOutSlowInEasing)
        },
        label = "kibo3DCardRotationY",
    )

    val stylePreset = remember(card.stylePresetId, card.issuer, card.alias) {
        CardStylePresets.byId(card.stylePresetId)
            ?: CardStylePresets.forProduct(card.issuer, card.alias.orEmpty())
            ?: CardStylePresets.forInstitution(card.issuer, CardStyleType.CREDIT)
                .firstOrNull { it.network.equals(card.network.name, ignoreCase = true) }
            ?: CardStylePresets.forInstitution(card.issuer, CardStyleType.CREDIT).firstOrNull()
            ?: CardStylePresets.genericCreditStyle
    }

    val isBcpVisa = card.issuer.contains("BCP", ignoreCase = true) && card.network.name == "VISA"
    val cardBrush = if (isBcpVisa) {
        Brush.linearGradient(listOf(Color(0xFF0B1F33), Color(0xFF081826)))
    } else {
        stylePreset.gradientBrush
    }
    val cardForeground = if (isBcpVisa) Color.White else stylePreset.textColor
    val cardAccent = if (isBcpVisa) Color(0xFFD4AF37) else stylePreset.accentColor
    val networkDisplay = if (card.network.name == "VISA") {
        "VISA ${stylePreset.tierLabel.uppercase(Locale.forLanguageTag("es-PE"))}"
    } else {
        card.network.name
    }
    val shape = RoundedCornerShape(16.dp)

    val accessibleDescription = if (!isFlipped) {
        "Tarjeta de crédito ${card.alias ?: card.issuer} ${card.network}, últimos dígitos ${card.lastFourDigits}, línea autorizada ${card.currency.name} ${formatMinorUnits(card.creditLimitMinorUnits)}. Toca para ver el reverso financiero."
    } else {
        "Reverso de tarjeta de crédito, día de corte ${card.billingDay}, día de pago ${card.dueDay}. Toca para volver al frente."
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Card(
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(204.dp)
                .graphicsLayer {
                    this.rotationY = rotation.value
                    this.cameraDistance = 12f * density
                }
                .clickable { isFlipped = !isFlipped }
                .semantics {
                    role = Role.Button
                    contentDescription = accessibleDescription
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(cardBrush),
            ) {
                // Background subtle metallic decorative canvas
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        color = stylePreset.accentColor.copy(alpha = 0.15f),
                        radius = size.minDimension * 0.45f,
                        center = Offset(size.width * 0.85f, size.height * 0.15f),
                    )
                    drawCircle(
                        color = stylePreset.accentColor.copy(alpha = 0.08f),
                        radius = size.minDimension * 0.70f,
                        center = Offset(size.width * 0.90f, size.height * 0.10f),
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (rotation.value <= 90f) 1f else 0f },
                ) {
                    CreditCardFrontContent(
                        card = card,
                        cardForeground = cardForeground,
                        accentColor = cardAccent,
                        networkDisplay = networkDisplay,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationY = 180f
                            alpha = if (rotation.value > 90f) 1f else 0f
                        },
                ) {
                    CreditCardBackContent(
                        card = card,
                        cardForeground = cardForeground,
                        accentColor = cardAccent,
                    )
                }
            }
        }

        // Toca la tarjeta para voltear
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .heightIn(min = 48.dp)
                .clickable { isFlipped = !isFlipped }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = if (isFlipped) "Toca la tarjeta para ver el frente" else "Toca la tarjeta para voltear",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CreditCardFrontContent(
    card: CreditCard,
    cardForeground: Color,
    accentColor: Color,
    networkDisplay: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Top row: Chip EMV + Contactless on left, Issuer badge on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Gold EMV Chip
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFE5C07B), Color(0xFFD19A66), Color(0xFFE5C07B)),
                            ),
                        )
                        .padding(3.dp),
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawLine(
                            color = Color(0xFF7A5813),
                            start = Offset(size.width * 0.5f, 0f),
                            end = Offset(size.width * 0.5f, size.height),
                            strokeWidth = 1.dp.toPx(),
                        )
                        drawLine(
                            color = Color(0xFF7A5813),
                            start = Offset(0f, size.height * 0.5f),
                            end = Offset(size.width, size.height * 0.5f),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                }
                // Contactless wave symbol
                Text(
                    text = ")))",
                    color = cardForeground.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Issuer Badge / Pill
            Surface(
                color = Color.Black.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, cardForeground.copy(alpha = 0.2f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = card.issuer.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = cardForeground,
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(accentColor),
                    )
                }
            }
        }

        // Middle: Alias / Product Name + Total Limit
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = (card.alias ?: "${card.issuer} ${card.network}").uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = cardForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "LÍNEA TOTAL: ${card.currency.name} ${formatMinorUnits(card.creditLimitMinorUnits)}",
                style = MaterialTheme.typography.labelSmall,
                color = cardForeground.copy(alpha = 0.75f),
            )
        }

        // Bottom row: Masked Digits + Network
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "•••• •••• •••• ${card.lastFourDigits}",
                    style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 2.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = cardForeground,
                )
                Text(
                    text = "CORTE: Día %02d   PAGO: Día %02d".format(card.billingDay, card.dueDay),
                    style = MaterialTheme.typography.labelSmall,
                    color = cardForeground.copy(alpha = 0.70f),
                )
            }

            Text(
                text = networkDisplay,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = cardForeground,
            )
        }
    }
}

@Composable
private fun CreditCardBackContent(
    card: CreditCard,
    cardForeground: Color,
    accentColor: Color,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Magnetic band at top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .height(38.dp)
                .background(Color(0xFF111111)),
        )

        // Middle: Signature panel + Cycle info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // White signature band with masked digits (NO CVV!)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.90f))
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "•••• ${card.lastFourDigits}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                )
            }

            // Billing Cycle details (Reverso con ciclo)
            Surface(
                color = Color.Black.copy(alpha = 0.35f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, cardForeground.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "CICLO DE FACTURACIÓN",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Día de corte: Día ${card.billingDay} de cada mes",
                            style = MaterialTheme.typography.bodySmall,
                            color = cardForeground.copy(alpha = 0.90f),
                        )
                        Text(
                            text = "Pago: Día ${card.dueDay}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = cardForeground.copy(alpha = 0.90f),
                        )
                    }
                    Text(
                        text = "Ajuste automático al último día en meses cortos.",
                        style = MaterialTheme.typography.labelSmall,
                        color = cardForeground.copy(alpha = 0.65f),
                    )
                }
            }
        }

        // Bottom: Issuer & Network
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${card.issuer} • ${card.network}",
                style = MaterialTheme.typography.labelSmall,
                color = cardForeground.copy(alpha = 0.70f),
            )
            Text(
                text = "Línea: ${card.currency.name} ${formatMinorUnits(card.creditLimitMinorUnits)}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = cardForeground.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun CreditCardPeriodPanel(
    card: CreditCard,
    summary: CreditCardWithSummary?,
    modifier: Modifier = Modifier,
) {
    val nextBilling = remember(card.billingDay) { CreditCalculations.calculateNextDate(card.billingDay) }
    val nextDue = remember(card.dueDay) { CreditCalculations.calculateNextDate(card.dueDay) }
    val now = remember { LocalDate.now() }
    val daysUntilBilling = remember(nextBilling, now) {
        ChronoUnit.DAYS.between(now, nextBilling).coerceAtLeast(0)
    }
    val daysUntilDue = remember(nextDue, now) {
        ChronoUnit.DAYS.between(now, nextDue).coerceAtLeast(0)
    }

    val debt = summary?.debt ?: Money(0L, card.currency)
    val available = summary?.availableCredit ?: Money(card.creditLimitMinorUnits, card.currency)
    val utilization = summary?.utilizationPercentage ?: 0.0

    val targetProgress = (utilization / 100.0).toFloat().coerceIn(0f, 1f)
    val reducedMotion = rememberReducedMotionEnabled()
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            tween(
                durationMillis = KipuMotionTokens.MediumMillis,
                easing = FastOutSlowInEasing,
            )
        },
        label = "creditUtilizationProgress",
    )

    val progressColor = when {
        utilization >= 80.0 -> MaterialTheme.colorScheme.error
        utilization >= 50.0 -> MaterialTheme.colorScheme.tertiary
        else -> Color(0xFF00B34D)
    }

    val dueFormatter = remember {
        DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("es-PE"))
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header: ESTADO DEL PERIODO ACTUAL + Badge Cierre en X días
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF0F766E)),
                    )
                    Text(
                        text = "ESTADO DEL PERIODO ACTUAL",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE8F5E9),
                    border = BorderStroke(1.dp, Color(0xFFA5D6A7)),
                ) {
                    Text(
                        text = "Cierre en $daysUntilBilling días",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            // Adaptive metrics layout: 3 Columns on standard width/normal text, stacked on compact width or fontScale > 1.25f
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val fontScale = LocalDensity.current.fontScale
                val isStacked = maxWidth < 400.dp || fontScale > 1.25f

                if (isStacked) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "Deuda actual",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                MoneyText(
                                    money = debt,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (debt.minorUnits > 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Facturado",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "Disponible de crédito",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                MoneyText(
                                    money = available,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Línea libre",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Fecha de pago configurada",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = nextDue.format(dueFormatter),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Text(
                                text = "En $daysUntilDue días",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Column 1: Deuda actual
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "Deuda actual",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            MoneyText(
                                money = debt,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (debt.minorUnits > 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Facturado",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }

                        // Column 2: Disponible de crédito
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "Disponible de crédito",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            MoneyText(
                                money = available,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Línea libre",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }

                        // Column 3: Fecha de pago configurada
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "Fecha de pago configurada",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = nextDue.format(dueFormatter),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "En $daysUntilDue días",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }

            // Utilization progress bar
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Utilización de línea total:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (card.creditLimitMinorUnits == 0L) "No disponible" else "%.1f%% / 100%%".format(utilization),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = progressColor,
                    )
                }

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
            }

            // Bottom: Factual utilization status on left, Línea total on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (utilization < 50.0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFE8F5E9),
                        border = BorderStroke(1.dp, Color(0xFFA5D6A7)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "Utilización baja (< 50%)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF2E7D32),
                            )
                        }
                    }
                } else if (utilization < 80.0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFF3E0),
                        border = BorderStroke(1.dp, Color(0xFFFFB74D)),
                    ) {
                        Text(
                            text = "Atención: > 50% utilizado",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE65100),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFEBEE),
                        border = BorderStroke(1.dp, Color(0xFFEF9A9A)),
                    ) {
                        Text(
                            text = "Alerta: alta utilización (≥ 80%)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                Text(
                    text = "Línea: ${card.currency.name} ${formatMinorUnits(card.creditLimitMinorUnits)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CreditCardActionsSection(
    card: CreditCard,
    summary: CreditCardWithSummary?,
    isArchived: Boolean,
    isSubmitting: Boolean,
    onStartCreditPurchase: () -> Unit,
    onPayCreditCard: () -> Unit,
    onNavigateToRateCatalog: (String) -> Unit,
    onArchiveClick: () -> Unit,
    onReactivateClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!isArchived) {
            val hasDebt = (summary?.debt?.minorUnits ?: 0L) > 0L
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onStartCreditPurchase,
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Registrar consumo")
                }

                if (hasDebt) {
                    Button(
                        onClick = onPayCreditCard,
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Pagar tarjeta")
                    }
                }
            }

            OutlinedButton(
                onClick = { onNavigateToRateCatalog(card.id.value) },
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text("Tasas referenciales y TEA personal")
            }

            OutlinedButton(
                onClick = onArchiveClick,
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text("Archivar tarjeta", color = MaterialTheme.colorScheme.error)
            }
        } else {
            Button(
                onClick = onReactivateClick,
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text("Reactivar tarjeta")
            }
        }
    }
}

@Composable
private fun CreditCardMovementsSection(
    movements: List<FinancialMovement>,
    currency: Currency,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val sortedMovements = remember(movements) {
        movements.sortedByDescending { it.effectiveAt }
    }

    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.forLanguageTag("es-PE"))
            .withZone(ZoneId.systemDefault())
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Movimientos de la tarjeta",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (sortedMovements.isNotEmpty()) {
                Text(
                    text = "${sortedMovements.size} movimiento${if (sortedMovements.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (isLoading) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().testTag("card_movements_loading"),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        } else if (sortedMovements.isEmpty()) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp),
                    )
                    Text(
                        text = "Sin movimientos registrados",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Los consumos y pagos con esta tarjeta aparecerán aquí.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    sortedMovements.forEachIndexed { index, movement ->
                        val isNegative = movement.kind == MovementKind.CREDIT_PURCHASE
                        val icon = when (movement.kind) {
                            MovementKind.CREDIT_PURCHASE -> Icons.Default.CreditCard
                            MovementKind.CARD_PAYMENT_LIABILITY, MovementKind.CARD_PAYMENT_CASH -> Icons.Default.Payments
                            MovementKind.ADJUSTMENT -> Icons.Default.Refresh
                            MovementKind.REVERSAL -> Icons.Default.Refresh
                            MovementKind.OPENING -> Icons.Default.AccountBalance
                        }
                        val title = when (movement.kind) {
                            MovementKind.CREDIT_PURCHASE -> movement.merchantName?.takeIf(String::isNotBlank) ?: "Consumo con tarjeta"
                            MovementKind.CARD_PAYMENT_LIABILITY, MovementKind.CARD_PAYMENT_CASH -> "Pago amortizador"
                            MovementKind.ADJUSTMENT -> "Ajuste de crédito"
                            MovementKind.REVERSAL -> "Reverso de movimiento"
                            MovementKind.OPENING -> "Apertura"
                        }
                        val formattedDate = remember(movement.effectiveAt) {
                            dateFormatter.format(movement.effectiveAt)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isNegative) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else Color(0xFFE8F5E9),
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = if (isNegative) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = formattedDate,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (movement.status == com.kipu.app.core.finance.domain.model.MovementStatus.PENDING) {
                                        Text(
                                            text = "Pendiente de sincronizar",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                        )
                                    }
                                }
                            }

                            val absMoney = Money(kotlin.math.abs(movement.amountMinorUnits), movement.currency)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isNegative) "- " else "+ ",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isNegative) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                                )
                                MoneyText(
                                    money = absMoney,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isNegative) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                                )
                            }
                        }

                        if (index < sortedMovements.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }
        }
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
    AccountType.CREDIT_LIABILITY -> "Pasivo de tarjeta"
}

private fun accountPresetIcon(preset: AccountPreset) =
    if (preset.defaultIconToken == "wallet") Icons.Default.AccountBalanceWallet else Icons.Default.AccountBalance
