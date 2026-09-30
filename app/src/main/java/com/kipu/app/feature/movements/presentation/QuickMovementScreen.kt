package com.kipu.app.feature.movements.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
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
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.movements.domain.model.MovementType
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale
import com.kipu.app.ui.theme.KipuExpense
import com.kipu.app.ui.theme.KipuIncome
import com.kipu.app.ui.theme.KipuMotionTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickMovementBottomSheet(
    onDismissRequest: () -> Unit,
    onMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: QuickMovementViewModel = hiltViewModel(),
    merchantPickerViewModel: MerchantPickerViewModel = hiltViewModel(),
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val merchantPickerState by merchantPickerViewModel.uiState.collectAsStateWithLifecycle()
    var showMerchantPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is QuickMovementUiEvent.TransactionSaved -> {
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
        containerColor = colors.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colors.outlineVariant)
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
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
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
                    Text("Listo")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Cancelar")
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.movement_register_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "Cerrar formulario" }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tabs: Gasto / Ingreso / Transferencia
        val tabs = listOf(
            MovementType.EXPENSE to stringResource(R.string.movement_type_expense),
            MovementType.INCOME to stringResource(R.string.movement_type_income),
            MovementType.TRANSFER to stringResource(R.string.movement_type_transfer),
        )
        val selectedTabIndex = tabs.indexOfFirst { it.first == uiState.type }.coerceAtLeast(0)

        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = colors.surfaceContainerLow,
            contentColor = colors.primary,
            indicator = { tabPositions ->
                if (selectedTabIndex < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = when (uiState.type) {
                            MovementType.EXPENSE -> KipuExpense
                            MovementType.INCOME -> KipuIncome
                            MovementType.TRANSFER -> colors.primary
                        }
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
        ) {
            tabs.forEachIndexed { index, (type, label) ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { onTypeSelected(type) },
                    text = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (selectedTabIndex == index) colors.onSurface else colors.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("tab_${type.name.lowercase()}"),
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Amount Input with tabular numbers (tnum)
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.movement_amount_label),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = uiState.amountText,
                onValueChange = onAmountChanged,
                placeholder = {
                    Text(
                        "0.00",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFeatureSettings = "tnum",
                            color = colors.outline
                        )
                    )
                },
                prefix = {
                    Text(
                        if (uiState.currency == "PEN") "S/ " else "$ ",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = when (uiState.type) {
                                MovementType.EXPENSE -> KipuExpense
                                MovementType.INCOME -> KipuIncome
                                MovementType.TRANSFER -> colors.primary
                            }
                        )
                    )
                },
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum",
                    color = colors.onSurface
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = uiState.amountError != null,
                supportingText = uiState.amountError?.let { { Text(it, color = colors.error) } },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.outlineVariant,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_amount")
                    .semantics { contentDescription = "Monto de la transacción" },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Source Account Selector
        SourceInstrumentDropdownSelector(
            label = if (uiState.type == MovementType.INCOME) "Cuenta destino" else stringResource(R.string.movement_source_account),
            accounts = uiState.availableAccounts,
            creditCards = if (uiState.type == MovementType.EXPENSE) uiState.availableCreditCards else emptyList(),
            selectedAccountId = uiState.selectedSourceAccountId,
            selectedCardId = uiState.selectedSourceCardId,
            onAccountSelected = onSourceAccountSelected,
            onCardSelected = onSourceCardSelected,
            error = uiState.accountError,
            modifier = Modifier.testTag("selector_source_account"),
        )

        // Destination Account Selector (Transfer only)
        AnimatedVisibility(
            visible = uiState.type == MovementType.TRANSFER,
            enter = fadeIn(tween(KipuMotionTokens.FastMillis)) + expandVertically(tween(KipuMotionTokens.FastMillis)),
            exit = fadeOut(tween(KipuMotionTokens.FastMillis)) + shrinkVertically(tween(KipuMotionTokens.FastMillis)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(16.dp))
                SourceInstrumentDropdownSelector(
                    label = stringResource(R.string.movement_destination_account),
                    accounts = uiState.availableAccounts.filter {
                        it.id.value != uiState.selectedSourceAccountId && it.currency.name == uiState.currency
                    },
                    selectedAccountId = uiState.selectedDestinationAccountId,
                    onAccountSelected = onDestinationAccountSelected,
                    error = uiState.destinationAccountError,
                    modifier = Modifier.testTag("selector_destination_account"),
                )
            }
        }

        // Category Selector (Expense: mandatory, Income: optional, Transfer: none)
        AnimatedVisibility(
            visible = uiState.type != MovementType.TRANSFER,
            enter = fadeIn(tween(KipuMotionTokens.FastMillis)) + expandVertically(tween(KipuMotionTokens.FastMillis)),
            exit = fadeOut(tween(KipuMotionTokens.FastMillis)) + shrinkVertically(tween(KipuMotionTokens.FastMillis)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (uiState.type == MovementType.EXPENSE) "Categoría *" else "Categoría (opcional)",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant
                    )
                    if (uiState.selectedCategoryName != null) {
                        Text(
                            text = uiState.selectedCategoryName,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (uiState.availableCategories.isEmpty()) {
                    Text(
                        "Cargando categorías. Conéctate para sincronizarlas si aún no aparecen.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    uiState.availableCategories.forEach { category ->
                        val isSelected = uiState.selectedCategoryId == category.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { onCategorySelected(category) },
                            label = { Text(category.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primaryContainer,
                                selectedLabelColor = colors.onPrimaryContainer,
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("chip_cat_${category.id}"),
                        )
                    }
                }
                if (uiState.categoryError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(uiState.categoryError, color = colors.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onOpenMerchantPicker,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("input_merchant"),
                    ) {
                        Text(
                            uiState.merchantName.ifBlank { stringResource(R.string.movement_select_merchant) },
                            maxLines = 1,
                        )
                    }
                    if (uiState.merchantName.isNotBlank()) {
                        IconButton(onClick = onClearMerchant, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Quitar comercio")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Collapsible "Más detalles"
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
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.movement_more_details),
                style = MaterialTheme.typography.titleSmall,
                color = colors.primary
            )
            Icon(
                imageVector = if (uiState.isMoreDetailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = colors.primary
            )
        }

        AnimatedVisibility(
            visible = uiState.isMoreDetailsExpanded,
            enter = fadeIn(tween(KipuMotionTokens.FastMillis)) + expandVertically(tween(KipuMotionTokens.FastMillis)),
            exit = fadeOut(tween(KipuMotionTokens.FastMillis)) + shrinkVertically(tween(KipuMotionTokens.FastMillis)),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                // Date Display
                val formattedDate = remember(uiState.occurredAt) {
                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(uiState.occurredAt))
                }
                OutlinedButton(
                    onClick = onOpenDatePicker,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Fecha: $formattedDate", color = colors.onSurface)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Notes input
                OutlinedTextField(
                    value = uiState.note,
                    onValueChange = onNoteChanged,
                    label = { Text(stringResource(R.string.movement_note)) },
                    placeholder = { Text(stringResource(R.string.movement_note_placeholder)) },
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.outlineVariant,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_note"),
                )
            }
        }

        if (uiState.generalError != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = uiState.generalError,
                color = colors.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Save Button (Primary CTA)
        Button(
            onClick = onSave,
            enabled = !uiState.isSaving,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("btn_save_transaction")
                .semantics {
                    contentDescription = if (uiState.isSaving) "Guardando transacción" else "Guardar transacción"
                },
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(
                    color = colors.onPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.movement_saving))
            } else {
                Text(
                    text = stringResource(R.string.movement_save),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceInstrumentDropdownSelector(
    label: String,
    accounts: List<com.kipu.app.feature.accounts.domain.model.Account>,
    creditCards: List<CreditCard> = emptyList(),
    selectedAccountId: String?,
    selectedCardId: String? = null,
    onAccountSelected: (String) -> Unit,
    onCardSelected: (String) -> Unit = {},
    error: String?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    val selectedAccount = accounts.find { it.id.value == selectedAccountId }
    val selectedCard = creditCards.find { it.id.value == selectedCardId }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedAccount?.let { "${it.alias} · ${accountTypeLabel(it.type)}" }
                    ?: selectedCard?.let { "${it.alias ?: it.issuer} ${it.network.name} •••• ${it.lastFourDigits} · Tarjeta de crédito" }
                    ?: "Selecciona una cuenta o tarjeta",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                isError = error != null,
                supportingText = error?.let { { Text(it, color = colors.error) } },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.outlineVariant,
                ),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                if (accounts.isEmpty() && creditCards.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No hay cuentas disponibles") },
                        onClick = { expanded = false },
                        enabled = false
                    )
                } else {
                    accounts.forEach { account ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(account.alias, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${account.type.name} • ${account.currency.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onAccountSelected(account.id.value)
                                expanded = false
                            }
                        )
                    }
                    creditCards.forEach { card ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(card.alias ?: "${card.issuer} ${card.network.name}", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "•••• ${card.lastFourDigits} · Tarjeta de crédito · ${card.currency.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = {
                                onCardSelected(card.id.value)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
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
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = colors.tertiary,
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
                color = colors.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_confirm_duplicate"),
            ) {
                Text(stringResource(R.string.movement_duplicate_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_cancel_duplicate"),
            ) {
                Text(stringResource(R.string.movement_duplicate_cancel), color = colors.onSurfaceVariant)
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
