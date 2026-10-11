package com.kipu.app.feature.accounts.presentation.detail

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.KipuMotionTokens
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipu.app.core.finance.domain.CreditCalculations
import com.kipu.app.core.finance.domain.MoneyInputParser
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.FinancialMovement
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.MovementKind
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.Card as FinancialCard
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditCardWithSummary
import com.kipu.app.feature.accounts.domain.model.CreditUtilizationNotification
import com.kipu.app.feature.accounts.domain.model.PurchaseCandidate
import com.kipu.app.feature.accounts.domain.usecase.SimulateInstallments
import com.kipu.app.feature.accounts.presentation.AccountUiEvent
import com.kipu.app.feature.accounts.presentation.AccountsViewModel
import com.kipu.app.feature.accounts.presentation.components.CardStylePresets
import com.kipu.app.feature.accounts.presentation.components.CardStyleType
import com.kipu.app.feature.accounts.presentation.instruments.InstallmentSimulatorScreen
import com.kipu.app.feature.accounts.presentation.instruments.CreditCardTermsDialog
import com.kipu.app.feature.accounts.presentation.instruments.PayCardDialog
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.KipuBottomSheet
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.component.symbol
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
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
    val observedCardMovements by remember(card?.id) {
        (card as? CreditCard)?.let { target ->
            viewModel.observeCardMovements(target.id)
        } ?: flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val effectiveCardMovements = cardMovements ?: observedCardMovements

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var isSubmitting by remember(instrumentId) { mutableStateOf(false) }
    var showCardPayment by remember(instrumentId) { mutableStateOf(false) }
    var showPurchaseDraft by remember(instrumentId, startPurchaseOnOpen) { mutableStateOf(startPurchaseOnOpen) }
    var purchaseMerchant by remember(instrumentId) { mutableStateOf("") }
    var purchaseAmount by remember(instrumentId) { mutableStateOf("") }
    var purchaseCategoryId by remember(instrumentId) { mutableStateOf<String?>(null) }
    var purchaseCategoryMenuExpanded by remember(instrumentId) { mutableStateOf(false) }
    var purchaseCandidate by remember(instrumentId) { mutableStateOf<PurchaseCandidate?>(null) }
    var purchasePending by remember(instrumentId) { mutableStateOf(false) }
    var cardTermsPending by remember(instrumentId) { mutableStateOf(false) }
    var showCardTermsEditor by remember(instrumentId) { mutableStateOf(false) }
    var lastConfirmedPurchase by remember(instrumentId) { mutableStateOf<PurchaseCandidate?>(null) }
    var purchaseAvailableBefore by remember(instrumentId) { mutableStateOf<Long?>(null) }
    var showPurchaseSuccess by remember(instrumentId) { mutableStateOf(false) }
    val selectedPurchaseCategory = purchaseCategories.firstOrNull { it.id == purchaseCategoryId }
    val draftPurchasePreview = remember(instrumentId, purchaseMerchant, purchaseAmount, purchaseCategoryId, selectedPurchaseCategory, card) {
        val creditCard = card as? CreditCard
        val amountMinor = MoneyInputParser.parseMinorUnits(purchaseAmount)
        if (creditCard != null && isValidCreditPurchaseDraft(amountMinor, purchaseMerchant, selectedPurchaseCategory?.id)) {
            PurchaseCandidate(
                id = "preview-${creditCard.id.value}",
                cardId = creditCard.id,
                amount = Money(requireNotNull(amountMinor), creditCard.currency),
                merchant = purchaseMerchant.trim(),
                occurredAt = Instant.now(),
                suggestedInstallments = 3,
                categoryId = requireNotNull(selectedPurchaseCategory).id,
            )
        } else null
    }

    LaunchedEffect(purchaseCategories, purchaseCategoryId) {
        if (purchaseCategoryId != null && purchaseCategories.none { it.id == purchaseCategoryId }) {
            purchaseCategoryId = null
        }
    }

    LaunchedEffect(viewModel, instrumentId, isCard) {
        viewModel.refreshCreditUtilizationNotifications(instrumentId.takeIf { isCard })
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AccountUiEvent.ShowMessage -> {
                    if (purchasePending) {
                        purchasePending = false
                        isSubmitting = false
                        showPurchaseSuccess = true
                        snackbarHostState.showSnackbar(event.message)
                    } else if (cardTermsPending) {
                        cardTermsPending = false
                        isSubmitting = false
                        snackbarHostState.showSnackbar(event.message)
                    } else if (isSubmitting) {
                        isSubmitting = false
                        onReturnToDashboard(event.message)
                    } else {
                        snackbarHostState.showSnackbar(event.message)
                    }
                }
                is AccountUiEvent.Error -> {
                    if (purchasePending) {
                        purchasePending = false
                        isSubmitting = false
                        lastConfirmedPurchase?.let { purchaseCandidate = it }
                    }
                    if (cardTermsPending) cardTermsPending = false
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
        creditNotifications = creditNotifications.filter { it.cardId == instrumentId },
        cardMovements = effectiveCardMovements,
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
        onNavigateToRateCatalog = onNavigateToRateCatalog,
        onStartCreditPurchase = { showPurchaseDraft = true },
        onEditCreditCardTerms = { showCardTermsEditor = true },
        isSubmitting = isSubmitting,
    )

    if (showCardTermsEditor && card is CreditCard) {
        CreditCardTermsDialog(
            card = card,
            isSaving = isSubmitting,
            onDismiss = { if (!isSubmitting) showCardTermsEditor = false },
            onSave = { limit, billingDay, dueDay, lastFourDigits, alias ->
                cardTermsPending = true
                isSubmitting = true
                showCardTermsEditor = false
                viewModel.updateCreditCardTerms(card.id, limit, billingDay, dueDay, lastFourDigits, alias)
            },
        )
    }

    if (showCardPayment && creditCardSummary != null) {
        PayCardDialog(
            creditCardWithSummary = creditCardSummary,
            eligibleAccounts = dashboard.dashboardData?.liquidAccounts.orEmpty(),
            onPayCreditCard = viewModel::payCreditCard,
            onDismiss = { showCardPayment = false },
        )
    }

    if (showPurchaseDraft && card is CreditCard) {
        val availableMinor = creditCardSummary?.availableCredit?.minorUnits ?: card.creditLimitMinorUnits
        val availableMoneyStr = "${card.currency.symbol()} ${formatMinorUnits(availableMinor)}"
        val emeraldColors = rememberCalmEmeraldColors()
        val parsedMinor = remember(purchaseAmount) { MoneyInputParser.parseMinorUnits(purchaseAmount) }
        val canSimulate = isValidCreditPurchaseDraft(parsedMinor, purchaseMerchant, selectedPurchaseCategory?.id)

        KipuBottomSheet(
            onDismissRequest = { showPurchaseDraft = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Header: (X) Nueva compra   [• Simulador]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(
                            onClick = { showPurchaseDraft = false },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cerrar")
                        }
                        Text(
                            text = "Nueva compra",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = emeraldColors.incomeBg,
                        border = BorderStroke(1.dp, emeraldColors.incomeBorder),
                    ) {
                        Text(
                            text = "• Simulador",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = emeraldColors.incomeEmerald,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }

                // Banner: Simulación de consumo
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF0FDF4),
                    border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(20.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Simulación de consumo",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF166534),
                            )
                            Text(
                                text = "Este cálculo es referencial y no registra ningún gasto real en tu tarjeta.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF15803D),
                            )
                        }
                    }
                }

                // Dark Card Preview
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F243A)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CreditCard,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = card.alias ?: "${card.issuer} ${card.network}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                                Text(
                                    text = "•••• ${card.lastFourDigits} · ${card.currency.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Línea de crédito",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF064E3B),
                            ) {
                                Text(
                                    text = "• $availableMoneyStr libre",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF34D399),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }

                // Amount input field
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Importe a simular (${card.currency.name})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value = purchaseAmount,
                        onValueChange = { purchaseAmount = it },
                        placeholder = { Text("0.00", style = MaterialTheme.typography.titleLarge) },
                        prefix = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${card.currency.symbol()} ",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "${card.currency.name} ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = emeraldColors.secondaryMuted,
                                )
                            }
                        },
                        trailingIcon = {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = emeraldColors.pillTrack,
                                modifier = Modifier.padding(end = 8.dp),
                            ) {
                                Text(
                                    text = if (card.currency == Currency.PEN) "Soles" else "Dólares",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = emeraldColors.primaryDeep,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Ingresa el monto que planeas consumir para calcular cuotas o interés.",
                        style = MaterialTheme.typography.bodySmall,
                        color = emeraldColors.secondaryMuted,
                    )
                }

                // Selector: COMERCIO
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(emeraldColors.pillTrack, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Storefront,
                                contentDescription = null,
                                tint = emeraldColors.primaryDeep,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "COMERCIO",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = emeraldColors.secondaryMuted,
                            )
                            OutlinedTextField(
                                value = purchaseMerchant,
                                onValueChange = { purchaseMerchant = it.take(100) },
                                placeholder = { Text("Ej. Bembos, Saga, etc.") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                // Selector: CATEGORÍA
                Box(modifier = Modifier.fillMaxWidth()) {
                    Card(
                        onClick = { purchaseCategoryMenuExpanded = true },
                        enabled = purchaseCategories.isNotEmpty(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(emeraldColors.pillTrack, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Category,
                                    contentDescription = null,
                                    tint = emeraldColors.primaryDeep,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "CATEGORÍA",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = emeraldColors.secondaryMuted,
                                )
                                Text(
                                    text = selectedPurchaseCategory?.name ?: "Seleccionar categoría de gasto",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selectedPurchaseCategory != null) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selectedPurchaseCategory != null) MaterialTheme.colorScheme.onSurface else emeraldColors.secondaryMuted,
                                )
                            }
                            Text("›", fontSize = 20.sp, color = emeraldColors.secondaryMuted)
                        }
                    }
                    DropdownMenu(
                        expanded = purchaseCategoryMenuExpanded,
                        onDismissRequest = { purchaseCategoryMenuExpanded = false },
                    ) {
                        purchaseCategories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    purchaseCategoryId = category.id
                                    purchaseCategoryMenuExpanded = false
                                },
                            )
                        }
                    }
                }

                if (draftPurchasePreview != null) {
                    val preview = remember(draftPurchasePreview, card) {
                        SimulateInstallments()(draftPurchasePreview, card, installmentsCount = 3)
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Simulación en tiempo real · 3 cuotas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Cuota estimada")
                                MoneyText(money = preview.schedule.first().amount, fontWeight = FontWeight.SemiBold)
                            }
                            Text(preview.disclaimer, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // CTA Button: Ver simulación
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Button(
                        onClick = {
                            val amountMinor = MoneyInputParser.parseMinorUnits(purchaseAmount)
                            if (isValidCreditPurchaseDraft(amountMinor, purchaseMerchant, selectedPurchaseCategory?.id)) {
                                purchaseCandidate = PurchaseCandidate(
                                    id = UUID.randomUUID().toString(),
                                    cardId = card.id,
                                    amount = Money(requireNotNull(amountMinor), card.currency),
                                    merchant = purchaseMerchant.trim(),
                                    occurredAt = Instant.now(),
                                    suggestedInstallments = 3,
                                    categoryId = requireNotNull(selectedPurchaseCategory).id,
                                )
                                showPurchaseDraft = false
                            } else {
                                coroutineScope.launch {
                                    val message = if (selectedPurchaseCategory == null) {
                                        "Selecciona una categoría para clasificar la compra."
                                    } else {
                                        "Indica un comercio y un importe válido."
                                    }
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        },
                        enabled = canSimulate,
                        colors = ButtonDefaults.buttonColors(containerColor = emeraldColors.primaryDeep),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    ) {
                        Text("Ver simulación", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Text(
                        text = if (canSimulate) "Calcula cuotas e intereses de esta compra" else "Ingresa un importe para calcular las cuotas",
                        style = MaterialTheme.typography.bodySmall,
                        color = emeraldColors.secondaryMuted,
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    purchaseCandidate?.let { candidate ->
        val creditCard = card as? CreditCard
        if (creditCard != null) {
            Dialog(onDismissRequest = { purchaseCandidate = null }) {
                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    InstallmentSimulatorScreen(
                        candidate = candidate,
                        card = creditCard,
                        teaBps = null,
                        availableCredit = creditCardSummary?.availableCredit,
                        onConfirmPurchase = { installments ->
                            if (!isSubmitting) {
                                isSubmitting = true
                                purchasePending = true
                                lastConfirmedPurchase = candidate.copy(suggestedInstallments = installments)
                                purchaseAvailableBefore = creditCardSummary?.availableCredit?.minorUnits
                                viewModel.confirmCreditPurchase(
                                    cardId = creditCard.id,
                                    amount = candidate.amount,
                                    merchant = candidate.merchant,
                                    effectiveAt = candidate.occurredAt,
                                    installments = installments,
                                    categoryId = requireNotNull(candidate.categoryId),
                                )
                                purchaseCandidate = null
                            }
                        },
                        onRejectPurchase = { purchaseCandidate = null },
                    )
                }
            }
        }
    }
    if (showPurchaseSuccess && lastConfirmedPurchase != null && card is CreditCard) {
        val savedPurchase = requireNotNull(lastConfirmedPurchase)
        val availableBefore = purchaseAvailableBefore
        val purchaseExcess = availableBefore?.let { available ->
            (savedPurchase.amount.minorUnits - available).takeIf { it > 0L }
        }
        AlertDialog(
            onDismissRequest = {
                showPurchaseSuccess = false
                lastConfirmedPurchase = null
                purchaseAvailableBefore = null
            },
            title = { Text("Compra registrada") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(savedPurchase.merchant, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Principal registrado")
                        MoneyText(money = savedPurchase.amount, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        if (savedPurchase.suggestedInstallments == 1) "Pago directo"
                        else "Plan seleccionado: ${savedPurchase.suggestedInstallments} cuotas",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (availableBefore != null) {
                        val availableAfter = (availableBefore - savedPurchase.amount.minorUnits).coerceAtLeast(0L)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Crédito disponible")
                            MoneyText(money = Money(availableAfter, card.currency))
                        }
                    } else {
                        Text("El crédito disponible se actualizará cuando cargue la proyección de la tarjeta.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (purchaseExcess != null) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Sobreutilización registrada", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Exceso sobre el disponible")
                                    MoneyText(money = Money(purchaseExcess, card.currency))
                                }
                                Text("El principal completo se conserva; el disponible no se muestra en negativo.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPurchaseSuccess = false
                        lastConfirmedPurchase = null
                        purchaseAvailableBefore = null
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Listo") }
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
    onCorrectOpeningBalance: (Long) -> Unit,
    onArchive: () -> Unit,
    onReactivate: () -> Unit,
    modifier: Modifier = Modifier,
    creditNotifications: List<CreditUtilizationNotification> = emptyList(),
    onPayCreditCard: () -> Unit = {},
    onNavigateToRateCatalog: (String) -> Unit = {},
    onStartCreditPurchase: () -> Unit = {},
    onEditCreditCardTerms: () -> Unit = {},
    cardMovements: List<FinancialMovement> = emptyList(),
    isSubmitting: Boolean = false,
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
    val archived = account?.isArchived ?: card?.isArchived ?: false
    val emeraldColors = rememberCalmEmeraldColors()

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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (card.isArchived) MaterialTheme.colorScheme.outline else emeraldColors.incomeEmerald),
                                )
                                Text(
                                    text = if (card.isArchived) "Tarjeta archivada · Solo lectura" else "Tarjeta de crédito activa",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else if (card != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (card.isArchived) MaterialTheme.colorScheme.outline else emeraldColors.incomeEmerald),
                                )
                                Text(
                                    text = if (card.isArchived) "Tarjeta de Débito archivada" else "Tarjeta de Débito activa",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else if (account != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (account.isArchived) MaterialTheme.colorScheme.outline else emeraldColors.incomeEmerald),
                                )
                                Text(
                                    text = if (account.isArchived) "Cuenta archivada" else "Cuenta activa",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    if (card is CreditCard && !archived) {
                        IconButton(onClick = onEditCreditCardTerms) {
                            Icon(Icons.Rounded.Tune, contentDescription = "Editar tarjeta")
                        }
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

                    // R10 CTAs and Actions
                    CreditCardActionsSection(
                        card = card,
                        summary = creditCardSummary,
                        isArchived = archived,
                        isSubmitting = isSubmitting,
                        onStartCreditPurchase = onStartCreditPurchase,
                        onPayCreditCard = onPayCreditCard,
                        onNavigateToRateCatalog = onNavigateToRateCatalog,
                        onEditCreditTerms = onEditCreditCardTerms,
                        onArchiveClick = { showArchiveConfirmation = true },
                        onReactivateClick = { showReactivateConfirmation = true },
                    )

                    // R10 Movements Section
                    CreditCardMovementsSection(
                        movements = cardMovements,
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
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            tween(durationMillis = KipuMotionTokens.SlowMillis, easing = FastOutSlowInEasing)
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
                    this.rotationY = rotation
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

                if (rotation <= 90f) {
                    CreditCardFrontContent(
                        card = card,
                        cardForeground = cardForeground,
                        accentColor = cardAccent,
                        networkDisplay = networkDisplay,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationY = 180f },
                    ) {
                        CreditCardBackContent(
                            card = card,
                            cardForeground = cardForeground,
                            accentColor = cardAccent,
                        )
                    }
                }
            }
        }

        // Toca la tarjeta para voltear
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { isFlipped = !isFlipped }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
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
    val emeraldColors = rememberCalmEmeraldColors()
    val debt = summary?.debt ?: Money(0L, card.currency)
    val available = summary?.availableCredit ?: Money(card.creditLimitMinorUnits, card.currency)
    val utilization = summary?.utilizationPercentage ?: 0.0

    if (card.isArchived) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Header: ESTADO FINAL AL ARCHIVAR + Badge Solo lectura
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
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outline),
                        )
                        Text(
                            text = "ESTADO FINAL AL ARCHIVAR",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Text(
                            text = "Solo lectura",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                // Two columns: Deuda pendiente + Línea liberada
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "Deuda pendiente",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MoneyText(
                            money = debt,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (debt.minorUnits > 0L) emeraldColors.expenseCoral else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (debt.minorUnits > 0L) "Pendiente de regularización" else "Sin deuda pendiente",
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
                            text = "Línea liberada",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MoneyText(
                            money = available,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Línea total",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }

                // Subcard: Ciclos congelados
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Ciclos congelados",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "No hay pagos pendientes ni cortes activos programados.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Info banner
                Text(
                    text = "Esta tarjeta fue archivada. Su historial de movimientos se conserva intacto, pero no se pueden registrar nuevos consumos ni simulaciones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    val nextBilling = remember(card.billingDay) { CreditCalculations.calculateNextDate(card.billingDay) }
    val nextDue = remember(card.dueDay) { CreditCalculations.calculateNextDate(card.dueDay) }
    val now = remember { LocalDate.now() }
    val daysUntilBilling = remember(nextBilling, now) {
        ChronoUnit.DAYS.between(now, nextBilling).coerceAtLeast(0)
    }
    val daysUntilDue = remember(nextDue, now) {
        ChronoUnit.DAYS.between(now, nextDue).coerceAtLeast(0)
    }

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

    val progressColorTarget = when {
        utilization >= 80.0 -> emeraldColors.expenseCoral
        utilization >= 50.0 -> emeraldColors.warningAmber
        else -> emeraldColors.incomeEmerald
    }
    val progressColor = animateColorAsState(
        targetValue = progressColorTarget,
        animationSpec = if (reducedMotion) snap() else tween(
            durationMillis = KipuMotionTokens.MediumMillis,
            easing = FastOutSlowInEasing,
        ),
        label = "creditDetailUtilizationColor",
    ).value

    val utilizationStatusLabel = when {
        utilization > 100.0 -> "Sobreutilizada"
        utilization >= 80.0 -> "⚠ Utilización muy alta (> 80%)"
        utilization >= 50.0 -> "Atención: > 50% utilizado"
        utilization >= 30.0 -> "Utilización moderada (30-50%)"
        else -> "✓ Utilización moderada (< 30%) · Óptimo"
    }
    val utilizationStatusBackgroundTarget = when {
        utilization >= 80.0 -> emeraldColors.expenseBg
        utilization >= 50.0 -> emeraldColors.warningBg
        else -> emeraldColors.incomeBg
    }
    val utilizationStatusBorderTarget = when {
        utilization >= 80.0 -> emeraldColors.expenseBorder
        utilization >= 50.0 -> emeraldColors.warningBorder
        else -> emeraldColors.incomeBorder
    }
    val utilizationStatusTextTarget = when {
        utilization >= 80.0 -> emeraldColors.expenseCoral
        utilization >= 50.0 -> emeraldColors.warningText
        else -> emeraldColors.incomeEmerald
    }
    val utilizationStatusBackground = animateColorAsState(
        utilizationStatusBackgroundTarget,
        animationSpec = if (reducedMotion) snap() else tween(KipuMotionTokens.MediumMillis, easing = FastOutSlowInEasing),
        label = "creditDetailStatusBackground",
    ).value
    val utilizationStatusBorder = animateColorAsState(
        utilizationStatusBorderTarget,
        animationSpec = if (reducedMotion) snap() else tween(KipuMotionTokens.MediumMillis, easing = FastOutSlowInEasing),
        label = "creditDetailStatusBorder",
    ).value
    val utilizationStatusText = animateColorAsState(
        utilizationStatusTextTarget,
        animationSpec = if (reducedMotion) snap() else tween(KipuMotionTokens.MediumMillis, easing = FastOutSlowInEasing),
        label = "creditDetailStatusText",
    ).value

    val dueFormatter = remember {
        DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("es-PE"))
    }

    Card(
        shape = RoundedCornerShape(16.dp),
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
                            .clip(CircleShape)
                            .background(emeraldColors.incomeEmerald),
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
                    color = emeraldColors.incomeBg,
                    border = BorderStroke(1.dp, emeraldColors.incomeBorder),
                ) {
                    Text(
                        text = "Cierre en $daysUntilBilling días",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = emeraldColors.incomeEmerald,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            // Dual Column Metrics: Deuda actual vs Disponible de crédito
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
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (debt.minorUnits > 0L) emeraldColors.expenseCoral else MaterialTheme.colorScheme.onSurface,
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
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = emeraldColors.primaryDeep,
                    )
                    Text(
                        text = "Línea libre",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }

            // Subcard: Fecha de pago configurada
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(emeraldColors.pillTrack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarToday,
                            contentDescription = null,
                            tint = emeraldColors.primaryDeep,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
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
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (daysUntilDue <= 2) emeraldColors.expenseBg else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (daysUntilDue <= 2) emeraldColors.expenseBorder else MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Text(
                            text = if (daysUntilDue <= 2) "¡Vence en $daysUntilDue días!" else "En $daysUntilDue días",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (daysUntilDue <= 2) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (daysUntilDue <= 2) emeraldColors.expenseCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
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
                        text = if (card.creditLimitMinorUnits == 0L) "No disponible" else "%.1f%% / 100%%".format(Locale.US, utilization),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = progressColor,
                    )
                }

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    color = progressColor,
                    trackColor = emeraldColors.pillTrack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
            }

            // Status chip on left, Línea total on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = utilizationStatusBackground,
                    border = BorderStroke(1.dp, utilizationStatusBorder),
                ) {
                    Text(
                        text = utilizationStatusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = utilizationStatusText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                Text(
                    text = "Línea: ${card.currency.name} ${formatMinorUnits(card.creditLimitMinorUnits)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // High utilization financial risk banner (>= 80%)
            if (utilization >= 80.0) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = emeraldColors.expenseBg),
                    border = BorderStroke(1.dp, emeraldColors.expenseBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = emeraldColors.expenseCoral,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Alerta de riesgo financiero: Consumo crítico",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = emeraldColors.expenseCoral,
                            )
                            Text(
                                text = "Estás usando el ${"%.1f".format(Locale.US, utilization)}% de tu línea autorizada. Superar el 50% puede afectar tu score crediticio y generar comisiones por sobregiro.",
                                style = MaterialTheme.typography.bodySmall,
                                color = emeraldColors.expenseCoral,
                            )
                        }
                    }
                }
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
    onEditCreditTerms: () -> Unit,
    onArchiveClick: () -> Unit,
    onReactivateClick: () -> Unit,
) {
    val emeraldColors = rememberCalmEmeraldColors()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!isArchived) {
            val hasDebt = (summary?.debt?.minorUnits ?: 0L) > 0L
            if (hasDebt) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onPayCreditCard,
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(containerColor = emeraldColors.primaryDeep),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Payments,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Pagar tarjeta", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onStartCreditPurchase,
                        enabled = !isSubmitting,
                        border = BorderStroke(1.5.dp, emeraldColors.primaryDeep),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = emeraldColors.primaryDeep,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Registrar consumo", color = emeraldColors.primaryDeep, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Button(
                    onClick = onStartCreditPurchase,
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = emeraldColors.primaryDeep),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Registrar consumo", fontWeight = FontWeight.Bold)
                }
            }

            // Tasas referenciales y TEA personal with badge
            OutlinedButton(
                onClick = { onNavigateToRateCatalog(card.id.value) },
                enabled = !isSubmitting,
                border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
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
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(emeraldColors.pillTrack),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Percent,
                                contentDescription = null,
                                tint = emeraldColors.primaryDeep,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Text(
                            text = "Tasas referenciales y TEA personal",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    if (card.personalTeaBps != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = emeraldColors.incomeBg,
                            border = BorderStroke(1.dp, emeraldColors.incomeBorder),
                        ) {
                            Text(
                                text = "${"%.1f".format(card.personalTeaBps / 100.0)}% TEA >",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = emeraldColors.incomeEmerald,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    } else {
                        Text(
                            text = ">",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onEditCreditTerms,
                enabled = !isSubmitting,
                border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("Editar línea, ciclo y fechas", fontWeight = FontWeight.Medium)
                }
            }

            OutlinedButton(
                onClick = onArchiveClick,
                enabled = !isSubmitting,
                border = BorderStroke(1.dp, emeraldColors.expenseBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Archive,
                        contentDescription = null,
                        tint = emeraldColors.expenseCoral,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("Archivar tarjeta", color = emeraldColors.expenseCoral, fontWeight = FontWeight.Medium)
                }
            }
        } else {
            Button(
                onClick = onReactivateClick,
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = emeraldColors.primaryDeep),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Reactivar tarjeta", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onArchiveClick,
                enabled = !isSubmitting,
                border = BorderStroke(1.dp, emeraldColors.expenseBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = emeraldColors.expenseCoral,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Eliminar tarjeta permanentemente", color = emeraldColors.expenseCoral, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CreditCardMovementsSection(
    movements: List<FinancialMovement>,
    modifier: Modifier = Modifier,
) {
    val emeraldColors = rememberCalmEmeraldColors()
    var showAllMovements by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var selectedTypeFilter by remember { mutableStateOf("ALL") } // "ALL", "PURCHASES", "PAYMENTS"
    var selectedPeriodFilter by remember { mutableStateOf("ALL") } // "ALL", "CYCLE"

    val activeFilterCount = (if (selectedTypeFilter != "ALL") 1 else 0) + (if (selectedPeriodFilter != "ALL") 1 else 0)

    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.forLanguageTag("es-PE"))
            .withZone(ZoneId.systemDefault())
    }

    val filteredMovements = remember(movements, selectedTypeFilter, selectedPeriodFilter) {
        val now = java.time.Instant.now()
        val thirtyDaysAgo = now.minus(30, java.time.temporal.ChronoUnit.DAYS)
        movements.filter { m ->
            val matchesType = when (selectedTypeFilter) {
                "PURCHASES" -> m.kind == MovementKind.CREDIT_PURCHASE
                "PAYMENTS" -> m.kind == MovementKind.CARD_PAYMENT_LIABILITY || m.kind == MovementKind.CARD_PAYMENT_CASH
                else -> true
            }
            val matchesPeriod = when (selectedPeriodFilter) {
                "CYCLE" -> m.effectiveAt >= thirtyDaysAgo
                else -> true
            }
            matchesType && matchesPeriod
        }.sortedByDescending { it.effectiveAt }
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Movimientos de la tarjeta",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(emeraldColors.pillTrack),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${filteredMovements.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = emeraldColors.primaryDeep,
                    )
                }
            }

            Surface(
                onClick = { showFilterSheet = true },
                shape = RoundedCornerShape(8.dp),
                color = if (activeFilterCount > 0) emeraldColors.pillTrack else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (activeFilterCount > 0) emeraldColors.primaryDeep else MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Filtrar",
                        tint = if (activeFilterCount > 0) emeraldColors.primaryDeep else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = if (activeFilterCount > 0) "Filtros ($activeFilterCount)" else "Filtrar",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (activeFilterCount > 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (activeFilterCount > 0) emeraldColors.primaryDeep else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Active filter chips row if filters are active
        if (activeFilterCount > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectedTypeFilter != "ALL") {
                    AssistChip(
                        onClick = { selectedTypeFilter = "ALL" },
                        label = {
                            Text(
                                if (selectedTypeFilter == "PURCHASES") "Compras" else "Pagos",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        trailingIcon = {
                            Icon(Icons.Rounded.Close, contentDescription = "Remover", modifier = Modifier.size(14.dp))
                        },
                        colors = AssistChipDefaults.assistChipColors(containerColor = emeraldColors.pillTrack),
                    )
                }
                if (selectedPeriodFilter != "ALL") {
                    AssistChip(
                        onClick = { selectedPeriodFilter = "ALL" },
                        label = {
                            Text(
                                "Ciclo actual",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        trailingIcon = {
                            Icon(Icons.Rounded.Close, contentDescription = "Remover", modifier = Modifier.size(14.dp))
                        },
                        colors = AssistChipDefaults.assistChipColors(containerColor = emeraldColors.pillTrack),
                    )
                }
                TextButton(
                    onClick = {
                        selectedTypeFilter = "ALL"
                        selectedPeriodFilter = "ALL"
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Text("Limpiar", style = MaterialTheme.typography.labelSmall, color = emeraldColors.primaryDeep)
                }
            }
        }

        if (filteredMovements.isEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(emeraldColors.pillTrack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
                            contentDescription = null,
                            tint = emeraldColors.primaryDeep,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Text(
                        text = if (activeFilterCount > 0) "Sin movimientos con estos filtros" else "Sin consumos en el ciclo",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (activeFilterCount > 0) "Intenta cambiar o limpiar los filtros seleccionados para ver más movimientos." else "No has registrado compras ni pagos con esta tarjeta este mes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    if (activeFilterCount > 0) {
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = {
                                selectedTypeFilter = "ALL"
                                selectedPeriodFilter = "ALL"
                            },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Restablecer filtros")
                        }
                    }
                }
            }
        } else {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    filteredMovements.take(4).forEachIndexed { index, movement ->
                        CreditCardMovementRow(movement, dateFormatter, emeraldColors)
                        if (index < minOf(filteredMovements.size, 4) - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                    TextButton(
                        onClick = { showAllMovements = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("card_movements_view_all"),
                    ) {
                        Text("Ver todos los movimientos de la tarjeta")
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        KipuBottomSheet(
            onDismissRequest = { showFilterSheet = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Filtros de movimientos",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = { showFilterSheet = false }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cerrar")
                    }
                }

                // Section: Tipo de movimiento
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Tipo de movimiento",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = selectedTypeFilter == "ALL",
                            onClick = { selectedTypeFilter = "ALL" },
                            label = { Text("Todos") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = emeraldColors.pillTrack,
                                selectedLabelColor = emeraldColors.primaryDeep,
                            ),
                        )
                        FilterChip(
                            selected = selectedTypeFilter == "PURCHASES",
                            onClick = { selectedTypeFilter = "PURCHASES" },
                            label = { Text("Compras") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = emeraldColors.pillTrack,
                                selectedLabelColor = emeraldColors.primaryDeep,
                            ),
                        )
                        FilterChip(
                            selected = selectedTypeFilter == "PAYMENTS",
                            onClick = { selectedTypeFilter = "PAYMENTS" },
                            label = { Text("Pagos") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = emeraldColors.pillTrack,
                                selectedLabelColor = emeraldColors.primaryDeep,
                            ),
                        )
                    }
                }

                // Section: Período
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Período",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = selectedPeriodFilter == "ALL",
                            onClick = { selectedPeriodFilter = "ALL" },
                            label = { Text("Histórico completo") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = emeraldColors.pillTrack,
                                selectedLabelColor = emeraldColors.primaryDeep,
                            ),
                        )
                        FilterChip(
                            selected = selectedPeriodFilter == "CYCLE",
                            onClick = { selectedPeriodFilter = "CYCLE" },
                            label = { Text("Ciclo actual (30 días)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = emeraldColors.pillTrack,
                                selectedLabelColor = emeraldColors.primaryDeep,
                            ),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            selectedTypeFilter = "ALL"
                            selectedPeriodFilter = "ALL"
                            showFilterSheet = false
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Restablecer")
                    }
                    Button(
                        onClick = { showFilterSheet = false },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = emeraldColors.primaryDeep),
                    ) {
                        Text("Aplicar filtros", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(4.dp))
            }
        }
    }

    if (showAllMovements) {
        KipuBottomSheet(
            onDismissRequest = { showAllMovements = false },
            header = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Movimientos de la tarjeta (${filteredMovements.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { showAllMovements = false },
                        modifier = Modifier.size(48.dp).testTag("card_movements_close"),
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cerrar movimientos")
                    }
                }
            },
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
            ) {
                items(items = filteredMovements, key = { it.id.value }) { movement ->
                    CreditCardMovementRow(movement, dateFormatter, emeraldColors)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun CreditCardMovementRow(
    movement: FinancialMovement,
    dateFormatter: DateTimeFormatter,
    emeraldColors: com.kipu.app.ui.theme.CalmEmeraldColors,
) {
    val isPayment = movement.kind == MovementKind.CARD_PAYMENT_LIABILITY || movement.kind == MovementKind.CARD_PAYMENT_CASH
    val isNegative = movement.kind == MovementKind.CREDIT_PURCHASE

    val (icon, iconTint, boxBg) = when (movement.kind) {
        MovementKind.CARD_PAYMENT_LIABILITY, MovementKind.CARD_PAYMENT_CASH ->
            Triple(Icons.Rounded.Payments, emeraldColors.incomeEmerald, emeraldColors.incomeBg)
        MovementKind.CREDIT_PURCHASE ->
            Triple(Icons.Rounded.CreditCard, emeraldColors.expenseCoral, emeraldColors.expenseBg)
        MovementKind.ADJUSTMENT ->
            Triple(Icons.Rounded.Refresh, emeraldColors.secondaryMuted, emeraldColors.pillTrack)
        MovementKind.REVERSAL ->
            Triple(Icons.Rounded.Refresh, emeraldColors.expenseCoral, emeraldColors.expenseBg)
        MovementKind.OPENING ->
            Triple(Icons.Rounded.AccountBalance, emeraldColors.primaryDeep, emeraldColors.pillTrack)
    }

    val title = when (movement.kind) {
        MovementKind.CARD_PAYMENT_LIABILITY, MovementKind.CARD_PAYMENT_CASH -> "Pago Tarjeta de Crédito"
        MovementKind.CREDIT_PURCHASE -> "Consumo con tarjeta"
        MovementKind.ADJUSTMENT -> "Ajuste de crédito"
        MovementKind.REVERSAL -> "Reverso de movimiento"
        MovementKind.OPENING -> "Apertura"
    }

    val badgeText = when (movement.kind) {
        MovementKind.CARD_PAYMENT_LIABILITY, MovementKind.CARD_PAYMENT_CASH -> "Abono a línea"
        MovementKind.CREDIT_PURCHASE -> "1 cuota"
        else -> null
    }

    val formattedDate = remember(movement.effectiveAt) { dateFormatter.format(movement.effectiveAt) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = boxBg,
            modifier = Modifier.size(42.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = emeraldColors.secondaryMuted,
                )
                if (badgeText != null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }

        MoneyText(
            amount = "${if (isNegative) "-" else if (isPayment) "+" else ""}${formatMinorUnits(kotlin.math.abs(movement.amountMinorUnits))}",
            currencySymbol = movement.currency.symbol(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (isPayment) emeraldColors.incomeEmerald else MaterialTheme.colorScheme.onSurface,
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
    AccountType.CREDIT_LIABILITY -> "Pasivo de tarjeta"
}
