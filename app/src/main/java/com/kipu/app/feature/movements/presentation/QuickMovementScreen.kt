package com.kipu.app.feature.movements.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipu.app.R
import com.kipu.app.feature.categories.presentation.components.MerchantPickerBottomSheet
import com.kipu.app.feature.categories.presentation.components.MerchantPickerViewModel
import com.kipu.app.feature.categories.presentation.resolveCategoryIcon
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.KipuMotionTokens
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickMovementBottomSheet(
    onDismissRequest: () -> Unit,
    onMessage: (String) -> Unit = {},
    onSaved: (MovementType) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: QuickMovementViewModel = hiltViewModel(),
    merchantPickerViewModel: MerchantPickerViewModel = hiltViewModel(),
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onNavigateToNewAccount: () -> Unit = {},
    mostUsedAccountId: String? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val merchantPickerState by merchantPickerViewModel.uiState.collectAsStateWithLifecycle()
    var showMerchantPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val emeraldColors = rememberCalmEmeraldColors()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is QuickMovementUiEvent.TransactionSaved -> {
                    onSaved(event.movementType)
                    onDismissRequest()
                    onMessage(event.message)
                }
                is QuickMovementUiEvent.ShowMessage -> onMessage(event.message)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = emeraldColors.surfaceCard,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFCBD5E1))
            )
        },
        modifier = modifier.testTag("quick_movement_sheet"),
    ) {
        QuickMovementContent(
            uiState = uiState,
            onTypeSelected = viewModel::onTypeSelected,
            onAmountChanged = viewModel::onAmountChanged,
            onSourceAccountSelected = viewModel::onSourceAccountSelected,
            onSourceCardSelected = viewModel::onSourceCardSelected,
            onDestinationAccountSelected = viewModel::onDestinationAccountSelected,
            onCategorySelected = viewModel::onCategorySelected,
            onOpenMerchantPicker = { showMerchantPicker = true },
            onClearMerchant = {
                merchantPickerViewModel.clearSelection()
                viewModel.onMerchantCleared()
            },
            onNoteChanged = viewModel::onNoteChanged,
            onOpenDatePicker = { showDatePicker = true },
            onToggleMoreDetails = viewModel::onToggleMoreDetails,
            onSave = viewModel::onSave,
            onClose = onDismissRequest,
            accountBalances = uiState.accountBalances,
            creditCardDebts = uiState.creditCardDebts,
            creditCardAvailableCredits = uiState.creditCardAvailableCredits,
            mostUsedAccountId = mostUsedAccountId,
            onNavigateToNewAccount = onNavigateToNewAccount,
            onCreateSubcategory = viewModel::createCategoryOrSubcategory,
            isCreatingSubcategory = uiState.isCreatingSubcategory,
            categoryCreationError = uiState.categoryCreationError,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = toUtcDateMillis(uiState.occurredAt),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { selectedDateMillis ->
                            viewModel.onOccurredAtChanged(
                                datePickerMillisToLocalInstant(selectedDateMillis, uiState.occurredAt),
                            )
                        }
                        showDatePicker = false
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Listo", color = emeraldColors.primaryDeep)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Cancelar", color = emeraldColors.secondaryMuted)
                }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (showMerchantPicker) {
        MerchantPickerBottomSheet(
            state = merchantPickerState,
            onQueryChange = merchantPickerViewModel::onQueryChanged,
            onSelectMerchant = { merchant ->
                merchantPickerViewModel.selectMerchant(merchant)
                viewModel.onMerchantSelected(merchant)
            },
            onSetProvisionalText = { name ->
                merchantPickerViewModel.setProvisionalText(name)
                viewModel.onMerchantProvisionalText(name)
            },
            onClearSelection = {
                merchantPickerViewModel.clearSelection()
                viewModel.onMerchantCleared()
            },
            onDismiss = { showMerchantPicker = false },
        )
    }

    if (uiState.showDuplicateWarning) {
        DuplicateWarningDialog(
            onConfirm = viewModel::onConfirmDuplicate,
            onDismiss = viewModel::onDismissDuplicate,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickMovementContent(
    uiState: QuickMovementUiState,
    onTypeSelected: (MovementType) -> Unit,
    onAmountChanged: (String) -> Unit,
    onSourceAccountSelected: (String) -> Unit,
    onSourceCardSelected: (String) -> Unit,
    onDestinationAccountSelected: (String) -> Unit,
    onCategorySelected: (CategoryOption) -> Unit,
    onOpenMerchantPicker: () -> Unit,
    onClearMerchant: () -> Unit,
    onNoteChanged: (String) -> Unit,
    onOpenDatePicker: () -> Unit,
    onToggleMoreDetails: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    accountBalances: Map<String, Money>,
    creditCardDebts: Map<String, Money>,
    creditCardAvailableCredits: Map<String, Money>,
    mostUsedAccountId: String?,
    onNavigateToNewAccount: () -> Unit,
    onCreateSubcategory: suspend (String?, String, String, String, Boolean) -> Result<CategoryOption>,
    isCreatingSubcategory: Boolean,
    categoryCreationError: String?,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val emeraldColors = rememberCalmEmeraldColors()
    val reducedMotion = rememberReducedMotionEnabled()
    val animDuration = if (reducedMotion) 0 else KipuMotionTokens.SegmentMillis
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showSourceAccountPicker by remember { mutableStateOf(false) }
    var showDestinationAccountPicker by remember { mutableStateOf(false) }

    // Separate field validation errors from general transient errors
    val fieldErrors = remember(uiState.amountError, uiState.accountError, uiState.categoryError, uiState.destinationAccountError) {
        listOfNotNull(
            uiState.amountError,
            uiState.accountError,
            uiState.categoryError,
            uiState.destinationAccountError,
        ).distinct()
    }
    val hasFieldErrors = fieldErrors.isNotEmpty()
    val fieldErrorCount = fieldErrors.size
    val generalError = uiState.generalError
    val hasGeneralError = !generalError.isNullOrBlank()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 720.dp)
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.movement_register_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    ),
                    color = emeraldColors.primaryText,
                    modifier = Modifier.weight(1f, fill = false),
                )

                if (hasFieldErrors) {
                    Surface(
                        shape = CircleShape,
                        color = emeraldColors.expenseBg,
                        border = BorderStroke(1.dp, emeraldColors.expenseBorder),
                    ) {
                        Text(
                            text = if (fieldErrorCount == 1) "1 error" else "$fieldErrorCount errores",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = emeraldColors.expenseCoral,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "Cerrar formulario" }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = emeraldColors.secondaryMuted,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // Active validation or error banner
        if (hasFieldErrors || hasGeneralError) {
            val bannerMessage = if (hasFieldErrors) {
                fieldErrors.first()
            } else {
                generalError ?: "Corrige los datos requeridos"
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = emeraldColors.expenseBg,
                border = BorderStroke(1.dp, emeraldColors.expenseBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = emeraldColors.expenseCoral,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = bannerMessage,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = emeraldColors.expenseCoral,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 3 Standalone Type Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CalmEmeraldTypeTab(
                    type = MovementType.EXPENSE,
                    label = stringResource(R.string.movement_type_expense),
                    selected = uiState.type == MovementType.EXPENSE,
                    onSelect = { onTypeSelected(MovementType.EXPENSE) },
                    modifier = Modifier.weight(1f),
                )
                CalmEmeraldTypeTab(
                    type = MovementType.INCOME,
                    label = stringResource(R.string.movement_type_income),
                    selected = uiState.type == MovementType.INCOME,
                    onSelect = { onTypeSelected(MovementType.INCOME) },
                    modifier = Modifier.weight(1f),
                )
                CalmEmeraldTypeTab(
                    type = MovementType.TRANSFER,
                    label = stringResource(R.string.movement_type_transfer),
                    selected = uiState.type == MovementType.TRANSFER,
                    onSelect = { onTypeSelected(MovementType.TRANSFER) },
                    modifier = Modifier.weight(1f),
                )
            }

            // Hero Type-Tinted Amount Card
            val (heroBg, heroBorder, heroText) = when (uiState.type) {
                MovementType.EXPENSE -> Triple(emeraldColors.expenseBg, emeraldColors.expenseBorder, emeraldColors.expenseCoral)
                MovementType.INCOME -> Triple(emeraldColors.incomeBg, emeraldColors.incomeBorder, emeraldColors.incomeEmerald)
                MovementType.TRANSFER -> Triple(emeraldColors.transferBg, emeraldColors.transferBorder, emeraldColors.primaryText)
            }
            val effectiveBorder = if (uiState.amountError != null) emeraldColors.expenseCoral else heroBorder

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = heroBg),
                border = BorderStroke(1.dp, effectiveBorder),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = when (uiState.type) {
                                MovementType.EXPENSE -> "MONTO DEL GASTO"
                                MovementType.INCOME -> "MONTO DEL INGRESO"
                                MovementType.TRANSFER -> "MONTO A TRANSFERIR"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp,
                            ),
                            color = when (uiState.type) {
                                MovementType.EXPENSE -> emeraldColors.expenseCoral
                                MovementType.INCOME -> emeraldColors.incomeEmerald
                                MovementType.TRANSFER -> emeraldColors.transferBlue
                            },
                        )

                        // Segmented Currency Selector: PEN / USD
                        val hasPen = remember(uiState.availableAccounts, uiState.availableCreditCards, uiState.type) {
                            uiState.availableAccounts.any { it.currency.name == "PEN" } ||
                                (uiState.type == MovementType.EXPENSE && uiState.availableCreditCards.any { it.currency.name == "PEN" })
                        }
                        val hasUsd = remember(uiState.availableAccounts, uiState.availableCreditCards, uiState.type) {
                            uiState.availableAccounts.any { it.currency.name == "USD" } ||
                                (uiState.type == MovementType.EXPENSE && uiState.availableCreditCards.any { it.currency.name == "USD" })
                        }
                        val isPen = uiState.currency == "PEN"
                        val isUsd = uiState.currency == "USD"

                        FlowRow(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(emeraldColors.pillTrack)
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            CurrencyCapsuleTab(
                                label = "PEN (S/)",
                                selected = isPen,
                                enabled = hasPen,
                                onSelect = { showSourceAccountPicker = true }
                            )

                            CurrencyCapsuleTab(
                                label = "USD ($)",
                                selected = isUsd,
                                enabled = hasUsd,
                                onSelect = { showSourceAccountPicker = true }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val signPrefix = when (uiState.type) {
                        MovementType.EXPENSE -> "− "
                        MovementType.INCOME -> "+ "
                        MovementType.TRANSFER -> ""
                    }
                    val currSymbol = if (uiState.currency == "PEN") "S/ " else "$ "

                    OutlinedTextField(
                        value = uiState.amountText,
                        onValueChange = onAmountChanged,
                        placeholder = {
                            Text(
                                "0.00",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 32.sp,
                                    fontFeatureSettings = "tnum",
                                    fontWeight = FontWeight.Bold,
                                    color = emeraldColors.secondaryMuted,
                                )
                            )
                        },
                        prefix = {
                            Text(
                                "$signPrefix$currSymbol",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = heroText,
                                )
                            )
                        },
                        textStyle = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum",
                            color = heroText,
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = uiState.amountError != null,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            errorBorderColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp)
                            .testTag("input_amount")
                            .semantics { contentDescription = "Monto de la transacción" },
                    )

                    if (uiState.amountError != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = emeraldColors.expenseCoral,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = uiState.amountError,
                                color = emeraldColors.expenseCoral,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            // Selection Rows: Accounts
            val selectedSourceAccount = uiState.availableAccounts.firstOrNull {
                it.id.value == uiState.selectedSourceAccountId
            }
            val selectedSourceCard = uiState.availableCreditCards.firstOrNull {
                it.id.value == uiState.selectedSourceCardId
            }

            val (sourceIcon, sourceIconBg, sourceIconTint) = remember(selectedSourceAccount, selectedSourceCard, emeraldColors.isDark) {
                val icon = when {
                    selectedSourceCard != null -> Icons.Default.CreditCard
                    selectedSourceAccount != null -> when (selectedSourceAccount.type) {
                        com.kipu.app.feature.accounts.domain.model.AccountType.CASH -> Icons.Default.Payments
                        com.kipu.app.feature.accounts.domain.model.AccountType.DIGITAL_WALLET -> Icons.Default.AccountBalanceWallet
                        com.kipu.app.feature.accounts.domain.model.AccountType.SAVINGS,
                        com.kipu.app.feature.accounts.domain.model.AccountType.BANK -> Icons.Default.AccountBalance
                        com.kipu.app.feature.accounts.domain.model.AccountType.CREDIT_LIABILITY -> Icons.Default.CreditCard
                    }
                    else -> Icons.Default.AccountBalanceWallet
                }
                val bg = if (emeraldColors.isDark) Color(0xFF134E4A) else Color(0xFFE6F7F3)
                val tint = if (emeraldColors.isDark) Color(0xFF5EEAD4) else Color(0xFF075E52)
                Triple(icon, bg, tint)
            }

            val sourceTitle = selectedSourceAccount?.alias
                ?: selectedSourceCard?.alias
                ?: selectedSourceCard?.let { "${it.issuer} ${it.network.name}" }
                ?: "Seleccionar cuenta"
            val sourceSubtitle = selectedSourceAccount?.let { account ->
                val balance = accountBalances[account.id.value]?.let { formatMoneyInline(it) } ?: "S/ 0.00"
                if (uiState.accountError != null) "Saldo: $balance · Insuficiente"
                else "${accountTypeLabel(account.type)} ${account.currency.name} · Saldo $balance"
            } ?: selectedSourceCard?.let { card ->
                val debt = creditCardDebts[card.id.value]?.let { " · Deuda ${formatMoneyInline(it)}" }.orEmpty()
                "Crédito · •••• ${card.lastFourDigits}$debt"
            } ?: "Elige dónde registrar este movimiento"

            // Account selection row (or transfer grouped card)
            if (uiState.type == MovementType.TRANSFER) {
                // Grouped Transfer Source & Destination
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = emeraldColors.surfaceCard),
                    border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Source
                        TransferAccountRow(
                            label = "Cuenta origen / Desde",
                            value = sourceTitle,
                            supportingText = sourceSubtitle,
                            icon = sourceIcon,
                            iconBg = sourceIconBg,
                            iconTint = sourceIconTint,
                            onClick = { showSourceAccountPicker = true },
                            modifier = Modifier.testTag("selector_source_account"),
                        )

                        // Divider with Down Arrow Circle
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = emeraldColors.transferBg,
                                border = BorderStroke(1.dp, emeraldColors.transferBorder),
                                modifier = Modifier.size(26.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = null,
                                        tint = emeraldColors.transferBlue,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }

                        // Destination
                        val eligibleDestinationAccounts = remember(uiState.availableAccounts, uiState.selectedSourceAccountId, uiState.currency) {
                            uiState.availableAccounts.filter { account ->
                                account.id.value != uiState.selectedSourceAccountId && account.currency.name == uiState.currency
                            }
                        }
                        val hasDestinations = eligibleDestinationAccounts.isNotEmpty()
                        val selectedDestination = eligibleDestinationAccounts.firstOrNull {
                            it.id.value == uiState.selectedDestinationAccountId
                        }

                        TransferAccountRow(
                            label = "Cuenta destino / Hacia",
                            value = selectedDestination?.alias
                                ?: if (!hasDestinations) "Sin cuenta destino disponible" else "Seleccionar cuenta destino",
                            supportingText = selectedDestination?.let { "${accountTypeLabel(it.type)} · ${it.currency.name}" }
                                ?: if (!hasDestinations) "Se requiere otra cuenta distinta en ${uiState.currency}" else "Elige la cuenta que recibirá el monto",
                            icon = Icons.Default.AccountBalanceWallet,
                            iconBg = if (emeraldColors.isDark) Color(0xFF1E3A8A) else Color(0xFFEFF6FF),
                            iconTint = emeraldColors.transferBlue,
                            onClick = { showDestinationAccountPicker = true },
                            modifier = Modifier.testTag("selector_destination_account"),
                        )

                        if (!hasDestinations) {
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = onNavigateToNewAccount,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("btn_create_destination_account"),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text("+ Crear otra cuenta en ${uiState.currency}")
                            }
                        }
                    }
                }

                // Transfer Commission / Cost row
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = emeraldColors.surfaceCard,
                    border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "Comisión / Costo:",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = emeraldColors.primaryText,
                            )
                            Text(
                                text = "Sin costo (Misma titularidad)",
                                style = MaterialTheme.typography.bodySmall,
                                color = emeraldColors.secondaryMuted,
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = emeraldColors.incomeBg,
                        ) {
                            Text(
                                text = "Gratis",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                ),
                                color = emeraldColors.incomeEmerald,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            } else {
                CalmEmeraldSelectionRow(
                    icon = sourceIcon,
                    iconContainer = sourceIconBg,
                    iconTint = sourceIconTint,
                    label = if (uiState.type == MovementType.INCOME) "Cuenta de destino" else "Cuenta de cargo",
                    value = sourceTitle,
                    supportingText = sourceSubtitle,
                    onClick = { showSourceAccountPicker = true },
                    isError = uiState.accountError != null,
                    errorMessage = uiState.accountError,
                    trailingActionText = if (uiState.accountError != null) "Cambiar cuenta ⇄" else null,
                    onTrailingActionClick = { showSourceAccountPicker = true },
                    modifier = Modifier.testTag("selector_source_account"),
                )
            }

            // Category Row (for Expense and Income)
            AnimatedVisibility(
                visible = uiState.type != MovementType.TRANSFER,
                enter = fadeIn(tween(animDuration)) + expandVertically(tween(animDuration)),
                exit = fadeOut(tween(animDuration)) + shrinkVertically(tween(animDuration)),
            ) {
                val hasCategory = !uiState.selectedCategoryName.isNullOrBlank()
                val (catIcon, catBg, catTint) = if (hasCategory) {
                    val colorsPair = categoryPastelColors(uiState.selectedCategoryName.orEmpty())
                    Triple(
                        resolveCategoryIcon(uiState.selectedCategoryIcon ?: "label"),
                        colorsPair.first,
                        colorsPair.second,
                    )
                } else {
                    val bg = if (emeraldColors.isDark) Color(0xFF2E1065) else Color(0xFFF3E8FF)
                    val tint = if (emeraldColors.isDark) Color(0xFFC4B5FD) else Color(0xFF7E22CE)
                    Triple(Icons.Default.Label, bg, tint)
                }

                CalmEmeraldSelectionRow(
                    icon = catIcon,
                    iconContainer = catBg,
                    iconTint = catTint,
                    label = if (uiState.type == MovementType.EXPENSE) "Categoría *" else "Categoría (opcional)",
                    value = uiState.selectedCategoryName ?: "Sin categoría seleccionada",
                    supportingText = when {
                        uiState.categoryError != null -> "Este campo es obligatorio"
                        uiState.selectedCategoryName != null -> "Toca para cambiar"
                        uiState.type == MovementType.INCOME -> "Opcional"
                        else -> "Selecciona una categoría requerida"
                    },
                    onClick = { showCategoryPicker = true },
                    isError = uiState.categoryError != null,
                    errorMessage = uiState.categoryError,
                    modifier = Modifier.testTag("category_picker_open"),
                )
            }

            // Merchant Row (for Expense)
            AnimatedVisibility(
                visible = uiState.type == MovementType.EXPENSE,
                enter = fadeIn(tween(animDuration)) + expandVertically(tween(animDuration)),
                exit = fadeOut(tween(animDuration)) + shrinkVertically(tween(animDuration)),
            ) {
                val (merchantBg, merchantTint) = if (emeraldColors.isDark) {
                    Color(0xFF451A03) to Color(0xFFFDBA74)
                } else {
                    Color(0xFFFEF3C7) to Color(0xFFD97706)
                }
                val hasMerchant = uiState.merchantName.isNotBlank()

                CalmEmeraldSelectionRow(
                    icon = Icons.Default.Storefront,
                    iconContainer = merchantBg,
                    iconTint = merchantTint,
                    label = "Comercio o Pagador",
                    value = if (hasMerchant) uiState.merchantName else "Agregar comercio (opcional)",
                    supportingText = if (uiState.selectedMerchantId != null) "Comercio del catálogo" else "Puedes escribir un nombre libre",
                    onClick = onOpenMerchantPicker,
                    modifier = Modifier.testTag("input_merchant"),
                    trailingContent = if (hasMerchant) {
                        {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = onClearMerchant,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("btn_clear_merchant")
                                        .semantics { contentDescription = "Limpiar comercio" },
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        tint = emeraldColors.secondaryMuted,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                IconButton(
                                    onClick = onOpenMerchantPicker,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .semantics { contentDescription = "Editar comercio" },
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = emeraldColors.secondaryMuted,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    } else {
                        {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = emeraldColors.secondaryMuted,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
            }

            // Date & Time Row
            val (dateBg, dateTint) = if (emeraldColors.isDark) {
                Color(0xFF082F49) to Color(0xFF7DD3FC)
            } else {
                Color(0xFFE0F2FE) to Color(0xFF0284C7)
            }
            val formattedDate = remember(uiState.occurredAt) { formatTransactionDate(uiState.occurredAt) }

            CalmEmeraldSelectionRow(
                icon = Icons.Default.CalendarToday,
                iconContainer = dateBg,
                iconTint = dateTint,
                label = "Fecha y hora",
                value = formattedDate,
                supportingText = "Toca para cambiar la fecha",
                onClick = onOpenDatePicker,
                modifier = Modifier.testTag("selector_occurred_at"),
            )

            // Collapsible Notes Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = uiState.isMoreDetailsExpanded,
                        role = Role.Button,
                        onValueChange = { onToggleMoreDetails() },
                    )
                    .semantics {
                        stateDescription = if (uiState.isMoreDetailsExpanded) "Expandido" else "Contraído"
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (uiState.type == MovementType.TRANSFER) "+ Nota o motivo (opcional)" else "+ Nota (opcional)",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = emeraldColors.primaryDeep,
                )
                Icon(
                    imageVector = if (uiState.isMoreDetailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = emeraldColors.primaryDeep,
                    modifier = Modifier.size(20.dp),
                )
            }

            AnimatedVisibility(
                visible = uiState.isMoreDetailsExpanded,
                enter = fadeIn(tween(animDuration)) + expandVertically(tween(animDuration)),
                exit = fadeOut(tween(animDuration)) + shrinkVertically(tween(animDuration)),
            ) {
                OutlinedTextField(
                    value = uiState.note,
                    onValueChange = onNoteChanged,
                    placeholder = { Text(stringResource(R.string.movement_note_placeholder), color = emeraldColors.secondaryMuted) },
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = emeraldColors.primaryDeep,
                        unfocusedBorderColor = emeraldColors.borderSubtle,
                        focusedContainerColor = emeraldColors.surfaceCard,
                        unfocusedContainerColor = emeraldColors.surfaceCard,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_note"),
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Sticky Pinned Footer Save Button
        val saveButtonText = when (uiState.type) {
            MovementType.EXPENSE -> "✓ Guardar gasto"
            MovementType.INCOME -> "✓ Guardar ingreso"
            MovementType.TRANSFER -> "✓ Confirmar transferencia"
        }

        if (hasFieldErrors) {
            Button(
                onClick = {},
                enabled = false,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = if (emeraldColors.isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                    disabledContentColor = emeraldColors.secondaryMuted,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("btn_save_transaction"),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Corregir datos para guardar",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            Button(
                onClick = onSave,
                enabled = !uiState.isSaving,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = emeraldColors.primaryDeep,
                    contentColor = emeraldColors.onPrimaryDeep,
                    disabledContainerColor = emeraldColors.primaryDeep.copy(alpha = 0.5f),
                    disabledContentColor = emeraldColors.onPrimaryDeep.copy(alpha = 0.7f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("btn_save_transaction")
                    .semantics {
                        contentDescription = if (uiState.isSaving) "Guardando transacción" else (if (hasGeneralError) "Reintentar guardar" else saveButtonText)
                    },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            color = emeraldColors.onPrimaryDeep,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.movement_saving),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    } else {
                        Text(
                            text = if (hasGeneralError) "Reintentar guardar" else saveButtonText,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }

    if (showCategoryPicker) {
        PastelCategoryPickerBottomSheet(
            categories = uiState.availableCategories,
            selectedCategoryId = uiState.selectedCategoryId,
            onSelect = { category ->
                onCategorySelected(category)
                showCategoryPicker = false
            },
            onCreateCategory = onCreateSubcategory,
            onDismiss = { showCategoryPicker = false },
            isCreating = isCreatingSubcategory,
            errorMessage = categoryCreationError,
            directSelectionMode = true,
        )
    }

    if (showSourceAccountPicker) {
        AccountSelectorBottomSheet(
            onDismissRequest = { showSourceAccountPicker = false },
            accounts = uiState.availableAccounts,
            creditCards = uiState.availableCreditCards,
            selectedAccountId = uiState.selectedSourceAccountId,
            selectedCardId = uiState.selectedSourceCardId,
            allowCreditCards = uiState.type == MovementType.EXPENSE,
            accountBalances = accountBalances,
            cardDebts = creditCardDebts,
            cardAvailableCredits = creditCardAvailableCredits,
            mostUsedAccountId = mostUsedAccountId,
            title = if (uiState.type == MovementType.INCOME) "Seleccionar cuenta destino" else "Seleccionar cuenta",
            subtitle = if (uiState.type == MovementType.INCOME) "Elige dónde recibir el ingreso" else "Elige de dónde sale el movimiento",
            onAccountIdSelected = onSourceAccountSelected,
            onCardIdSelected = onSourceCardSelected,
            onNewAccountClick = {
                showSourceAccountPicker = false
                onNavigateToNewAccount()
            },
        )
    }

    if (showDestinationAccountPicker) {
        val eligibleDestinationAccounts = uiState.availableAccounts.filter { account ->
            account.id.value != uiState.selectedSourceAccountId && account.currency.name == uiState.currency
        }
        AccountSelectorBottomSheet(
            onDismissRequest = { showDestinationAccountPicker = false },
            accounts = eligibleDestinationAccounts,
            selectedAccountId = uiState.selectedDestinationAccountId,
            allowCreditCards = false,
            accountBalances = accountBalances,
            title = stringResource(R.string.movement_destination_account),
            subtitle = if (eligibleDestinationAccounts.isEmpty()) {
                "Para transferir necesitas otra cuenta distinta en ${uiState.currency}"
            } else {
                "Elige la cuenta que recibirá el monto"
            },
            onAccountIdSelected = onDestinationAccountSelected,
            onNewAccountClick = {
                showDestinationAccountPicker = false
                onNavigateToNewAccount()
            },
        )
    }
}

@Composable
private fun CalmEmeraldTypeTab(
    type: MovementType,
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val emeraldColors = rememberCalmEmeraldColors()
    val reducedMotion = rememberReducedMotionEnabled()
    val animDuration = if (reducedMotion) 0 else KipuMotionTokens.SegmentMillis

    val (bg, text, border) = when (type) {
        MovementType.EXPENSE -> Triple(emeraldColors.expenseBg, emeraldColors.expenseCoral, emeraldColors.expenseBorder)
        MovementType.INCOME -> Triple(emeraldColors.incomeBg, emeraldColors.incomeEmerald, emeraldColors.incomeBorder)
        MovementType.TRANSFER -> Triple(emeraldColors.transferBg, emeraldColors.transferBlue, emeraldColors.transferBorder)
    }
    val unselectedBg = emeraldColors.pillTrack
    val unselectedBorder = emeraldColors.borderSubtle
    val unselectedText = emeraldColors.secondaryMuted

    val animatedBg by animateColorAsState(
        targetValue = if (selected) bg else unselectedBg,
        animationSpec = tween(durationMillis = animDuration),
        label = "tabBg",
    )
    val animatedText by animateColorAsState(
        targetValue = if (selected) text else unselectedText,
        animationSpec = tween(durationMillis = animDuration),
        label = "tabText",
    )
    val icon = when (type) {
        MovementType.EXPENSE -> "↗"
        MovementType.INCOME -> "✓"
        MovementType.TRANSFER -> "⇄"
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = animatedBg,
        border = if (selected) BorderStroke(1.5.dp, border) else BorderStroke(1.dp, unselectedBorder),
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                role = Role.Tab,
                onClick = onSelect,
            )
            .semantics { this.selected = selected }
            .testTag("tab_${type.name.lowercase()}"),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "$icon  $label",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                ),
                color = animatedText,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CurrencyCapsuleTab(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onSelect: () -> Unit,
) {
    val emeraldColors = rememberCalmEmeraldColors()
    val bgColor = when {
        selected -> emeraldColors.surfaceCard
        else -> Color.Transparent
    }
    val textColor = when {
        !enabled -> emeraldColors.secondaryMuted.copy(alpha = 0.4f)
        selected -> emeraldColors.primaryText
        else -> emeraldColors.secondaryMuted
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        shadowElevation = if (selected) 1.dp else 0.dp,
        modifier = Modifier
            .then(
                if (enabled) {
                    Modifier.clickable(onClick = onSelect)
                } else {
                    Modifier
                }
            )
            .heightIn(min = 48.dp),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp,
                ),
                color = textColor,
            )
        }
    }
}

@Composable
private fun CalmEmeraldSelectionRow(
    icon: ImageVector,
    iconContainer: Color,
    iconTint: Color,
    label: String,
    value: String,
    supportingText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector = Icons.Default.ChevronRight,
    isError: Boolean = false,
    errorMessage: String? = null,
    trailingActionText: String? = null,
    onTrailingActionClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val emeraldColors = rememberCalmEmeraldColors()
    val borderColor = if (isError) emeraldColors.expenseCoral else emeraldColors.borderSubtle

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics {
                contentDescription = "$label. $value. $supportingText"
                role = Role.Button
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = emeraldColors.surfaceCard),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = if (isError) emeraldColors.expenseCoral else emeraldColors.secondaryMuted,
                        maxLines = 1,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        ),
                        color = emeraldColors.primaryText,
                        maxLines = 1,
                    )
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = if (isError) emeraldColors.expenseCoral else emeraldColors.secondaryMuted,
                        maxLines = 1,
                    )
                }

                if (trailingContent != null) {
                    trailingContent()
                } else if (trailingActionText != null && onTrailingActionClick != null) {
                    TextButton(
                        onClick = onTrailingActionClick,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = trailingActionText,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = emeraldColors.expenseCoral,
                        )
                    }
                } else {
                    Icon(
                        imageVector = trailingIcon,
                        contentDescription = null,
                        tint = emeraldColors.secondaryMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            if (isError && errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "▲ $errorMessage",
                    style = MaterialTheme.typography.labelSmall,
                    color = emeraldColors.expenseCoral,
                )
            }
        }
    }
}

@Composable
private fun TransferAccountRow(
    label: String,
    value: String,
    supportingText: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val emeraldColors = rememberCalmEmeraldColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = emeraldColors.secondaryMuted,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = emeraldColors.primaryText,
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = emeraldColors.secondaryMuted,
            )
        }
        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = null,
            tint = emeraldColors.secondaryMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun categoryPastelColors(categoryName: String): Pair<Color, Color> {
    val name = categoryName.lowercase(Locale.ROOT)
    val dark = rememberCalmEmeraldColors().isDark
    return when {
        "aliment" in name || "mercado" in name || "comida" in name || "restauran" in name ->
            if (dark) Color(0xFF7C2D12) to Color(0xFFFDBA74) else Color(0xFFFFEDD5) to Color(0xFF9A3412)
        "suscrip" in name || "stream" in name || "entreten" in name ->
            if (dark) Color(0xFF4C1D95) to Color(0xFFC4B5FD) else Color(0xFFEDE9FE) to Color(0xFF5B21B6)
        "transp" in name || "movil" in name || "auto" in name || "gas" in name ->
            if (dark) Color(0xFF0C4A6E) to Color(0xFF7DD3FC) else Color(0xFFE0F2FE) to Color(0xFF075985)
        "salud" in name || "cuidado" in name || "medic" in name || "farmac" in name ->
            if (dark) Color(0xFF881337) to Color(0xFFFDA4AF) else Color(0xFFFFE4E6) to Color(0xFF9F1239)
        "hogar" in name || "servicio" in name || "casa" in name || "luz" in name || "agua" in name ->
            if (dark) Color(0xFF78350F) to Color(0xFFFCD34D) else Color(0xFFFEF3C7) to Color(0xFF92400E)
        "educ" in name || "libro" in name || "curso" in name ->
            if (dark) Color(0xFF064E3B) to Color(0xFF6EE7B7) else Color(0xFFD1FAE5) to Color(0xFF065F46)
        "compr" in name || "ropa" in name || "tienda" in name ->
            if (dark) Color(0xFF831843) to Color(0xFFF472B6) else Color(0xFFFCE7F3) to Color(0xFF9D174D)
        else ->
            if (dark) Color(0xFF1E293B) to Color(0xFF94A3B8) else Color(0xFFF1F5F9) to Color(0xFF475569)
    }
}

private fun formatMoneyInline(money: Money): String {
    val currency = when (money.currency.name) {
        "PEN" -> "S/"
        "USD" -> "$"
        else -> money.currency.name
    }
    return "$currency ${formatMinorUnits(money.minorUnits)}"
}

private fun formatTransactionDate(timestamp: Long): String {
    val date = Date(timestamp)
    val locale = Locale("es", "PE")
    val dateCalendar = java.util.Calendar.getInstance().apply { time = date }
    val nowCalendar = java.util.Calendar.getInstance()
    val isToday = dateCalendar.get(java.util.Calendar.YEAR) == nowCalendar.get(java.util.Calendar.YEAR) &&
        dateCalendar.get(java.util.Calendar.DAY_OF_YEAR) == nowCalendar.get(java.util.Calendar.DAY_OF_YEAR)
    val timeText = SimpleDateFormat("hh:mm a", Locale.US).format(date)
    return if (isToday) {
        "Hoy · $timeText"
    } else {
        val dateText = SimpleDateFormat("d MMM yyyy", locale).format(date)
        "$dateText · $timeText"
    }
}

private fun accountTypeLabel(type: com.kipu.app.feature.accounts.domain.model.AccountType): String = when (type) {
    com.kipu.app.feature.accounts.domain.model.AccountType.SAVINGS -> "Ahorros"
    com.kipu.app.feature.accounts.domain.model.AccountType.BANK -> "Cuenta bancaria"
    com.kipu.app.feature.accounts.domain.model.AccountType.DIGITAL_WALLET -> "Billetera"
    com.kipu.app.feature.accounts.domain.model.AccountType.CASH -> "Efectivo"
    com.kipu.app.feature.accounts.domain.model.AccountType.CREDIT_LIABILITY -> "Pasivo de tarjeta"
}

@Composable
fun DuplicateWarningDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val emeraldColors = rememberCalmEmeraldColors()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = emeraldColors.warningAmber,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.movement_duplicate_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = stringResource(R.string.movement_duplicate_message),
                style = MaterialTheme.typography.bodyMedium,
                color = emeraldColors.secondaryMuted,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = emeraldColors.primaryDeep,
                    contentColor = emeraldColors.onPrimaryDeep,
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_confirm_duplicate"),
            ) {
                Text(stringResource(R.string.movement_duplicate_confirm), color = emeraldColors.onPrimaryDeep)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_cancel_duplicate"),
            ) {
                Text(stringResource(R.string.movement_duplicate_cancel), color = emeraldColors.secondaryMuted)
            }
        },
        modifier = modifier.testTag("dialog_duplicate_warning"),
    )
}

private fun toUtcDateMillis(occurredAt: Long): Long {
    val zone = ZoneId.systemDefault()
    val localDate = Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalDate()
    return localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

private fun datePickerMillisToLocalInstant(selectedDateMillis: Long, occurredAt: Long): Long {
    val zone = ZoneId.systemDefault()
    val selectedDate = Instant.ofEpochMilli(selectedDateMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val existingLocalTime = Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalTime()
    return selectedDate.atTime(existingLocalTime).atZone(zone).toInstant().toEpochMilli()
}
