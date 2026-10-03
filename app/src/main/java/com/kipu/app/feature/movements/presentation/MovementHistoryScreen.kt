package com.kipu.app.feature.movements.presentation

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipu.app.R
import com.kipu.app.feature.movements.domain.model.MovementSyncStatus
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.TransactionItem
import com.kipu.app.feature.movements.domain.model.TransactionStatus
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.theme.KipuExpense
import com.kipu.app.ui.theme.KipuIncome

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovementHistoryRoute(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToNewAccount: () -> Unit = {},
    onNavigateToPlans: () -> Unit = {},
    onNavigateToEditor: (String) -> Unit = {},
    viewModel: MovementHistoryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
    savedMessage: Boolean = false,
    onSavedMessageConsumed: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedText = stringResource(R.string.history_saved_pending)
    val voidedText = stringResource(R.string.history_voided_pending)
    LaunchedEffect(savedMessage) {
        if (savedMessage) { onSavedMessageConsumed(); snackbarHostState.showSnackbar(savedText) }
    }
    val colorScheme = MaterialTheme.colorScheme
    val mostUsedAccountId = remember(uiState.allTransactions) {
        uiState.allTransactions.asSequence()
            .filter { it.transaction.status == TransactionStatus.ACTIVE }
            .mapNotNull { it.transaction.sourceAccountId }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
    }
    val searchDescription = stringResource(R.string.movements_search_placeholder)
    val filterChipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = colorScheme.primaryContainer,
        selectedLabelColor = colorScheme.onPrimaryContainer,
        containerColor = colorScheme.surfaceContainerLow,
        labelColor = colorScheme.onSurfaceVariant,
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.movements_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = colorScheme.onSurface,
                    )
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = "Ajustes" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.surface),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::onOpenRegisterSheet,
                containerColor = colorScheme.primaryContainer,
                contentColor = colorScheme.onPrimaryContainer,
                shape = CircleShape,
                modifier = Modifier
                    .size(56.dp)
                    .testTag("fab_add_transaction")
                    .semantics { contentDescription = "Registrar nueva transacción" },
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        containerColor = colorScheme.background,
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).testTag("list_movements"),
        ) {
            item(key = "history_controls") {
                Column {
                    // Search Bar
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::onSearchQueryChanged,
                        placeholder = {
                            Text(
                                stringResource(R.string.movements_search_placeholder),
                                color = colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { viewModel.onToggleAdvancedFilterPanel(true) },
                                modifier = Modifier.size(48.dp).testTag("btn_open_filters")
                                    .semantics { contentDescription = "Abrir filtros" }) {
                                Icon(Icons.Default.FilterList, contentDescription = null,
                                    tint = if (uiState.hasActiveAdvancedFilters) colorScheme.primary else colorScheme.onSurfaceVariant)
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colorScheme.surfaceContainerLowest,
                            unfocusedContainerColor = colorScheme.surfaceContainerLowest,
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.outlineVariant,
                            cursorColor = colorScheme.primary,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .semantics { contentDescription = searchDescription }
                            .testTag("input_search_movements"),
                    )

                    // Filter Chips (Todos, Gasto, Ingreso, Transferencia)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = uiState.selectedFilterType == null,
                                onClick = { viewModel.onFilterTypeSelected(null) },
                                label = { Text("Todos", color = if (uiState.selectedFilterType == null) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant) },
                                colors = filterChipColors,
                                shape = CircleShape,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("chip_filter_all"),
                            )
                        }
                        item {
                            FilterChip(
                                selected = uiState.selectedFilterType == MovementType.EXPENSE,
                                onClick = { viewModel.onFilterTypeSelected(MovementType.EXPENSE) },
                                label = { Text(stringResource(R.string.movement_type_expense)) },
                                colors = filterChipColors,
                                shape = CircleShape,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("chip_filter_expense"),
                            )
                        }
                        item {
                            FilterChip(
                                selected = uiState.selectedFilterType == MovementType.INCOME,
                                onClick = { viewModel.onFilterTypeSelected(MovementType.INCOME) },
                                label = { Text(stringResource(R.string.movement_type_income)) },
                                colors = filterChipColors,
                                shape = CircleShape,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("chip_filter_income"),
                            )
                        }
                        item {
                            FilterChip(
                                selected = uiState.selectedFilterType == MovementType.TRANSFER,
                                onClick = { viewModel.onFilterTypeSelected(MovementType.TRANSFER) },
                                label = { Text(stringResource(R.string.movement_type_transfer)) },
                                colors = filterChipColors,
                                shape = CircleShape,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("chip_filter_transfer"),
                            )
                        }
                        if (uiState.hasActiveAdvancedFilters || uiState.selectedFilterType != null || uiState.searchQuery.isNotBlank()) {
                            item {
                                FilterChip(
                                    selected = false,
                                    onClick = viewModel::onClearFilters,
                                    label = { Text(stringResource(R.string.movements_filter_clear)) },
                                    colors = filterChipColors,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("btn_clear_filters")
                                        .semantics { contentDescription = "Limpiar todos los filtros" },
                                )
                            }
                        }
                    }

                    MovementAccessCard(uiState, onNavigateToPlans, viewModel::onRevalidateAccess,
                        viewModel::onDismissRecovery, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    MovementAppliedFilters(uiState, viewModel::onRemoveFilter,
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp))

                    Spacer(modifier = Modifier.height(8.dp))

                }
            }
            when {
                uiState.isLoading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().heightIn(min = 240.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colorScheme.primary)
                    }
                }
                uiState.queryError -> item(key = "read_error") {
                    Column(Modifier.fillMaxWidth().heightIn(min = 240.dp).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(stringResource(R.string.history_query_error), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.history_query_error_body), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = viewModel::onRetryHistory, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.history_retry))
                        }
                    }
                }
                uiState.filteredTransactions.isEmpty() -> item(key = "empty") {
                    EmptyMovementsState(hasActiveFilters = uiState.hasActiveAdvancedFilters ||
                        uiState.selectedFilterType != null || uiState.searchQuery.isNotBlank(),
                        onClearFilters = viewModel::onClearFilters, onRegister = viewModel::onOpenRegisterSheet,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp).padding(horizontal = 32.dp))
                }
                else -> uiState.filteredTransactions.forEach { (dateHeader, rows) ->
                    item(key = "header_$dateHeader") {
                        Text(dateHeader, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp))
                    }
                    items(rows, key = { it.transaction.id }) { item ->
                        TransactionRow(item, onClick = { viewModel.onOpenDetail(item) },
                            onEditClick = { onNavigateToEditor(item.transaction.id) },
                            onVoidClick = { viewModel.onSelectTransactionForVoid(item) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                }
            }
            item(key = "fab_space") { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (uiState.showRegisterSheet) {
        QuickMovementBottomSheet(
            onDismissRequest = viewModel::onCloseRegisterSheet,
            onNavigateToNewAccount = onNavigateToNewAccount,
            mostUsedAccountId = mostUsedAccountId,
            onSaved = { movementType ->
                if (movementType == MovementType.TRANSFER) viewModel.showAllTransactionsAfterTransfer()
            },
            onMessage = { message ->
                scope.launch { snackbarHostState.showSnackbar(message) }
            },
        )
    }

    uiState.selectedDetail?.let { item ->
        MovementDetailSheet(item, uiState.detailRevisions, uiState.detailLoading, uiState.detailError,
            onDismiss = viewModel::onCloseDetail,
            onEdit = { viewModel.onCloseDetail(); onNavigateToEditor(item.transaction.id) },
            onVoid = { viewModel.onCloseDetail(); viewModel.onSelectTransactionForVoid(item) })
    }

    if (uiState.selectedTransactionForVoid != null) {
        VoidMovementDialog(
            item = uiState.selectedTransactionForVoid!!,
            onDismissRequest = viewModel::onDismissVoidDialog,
            onConfirmVoid = { reason ->
                viewModel.onConfirmVoid(reason) { success, errorMsg ->
                    if (success) {
                        val msg = errorMsg ?: voidedText
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    } else if (errorMsg != null) {
                        scope.launch { snackbarHostState.showSnackbar(errorMsg) }
                    }
                }
            },
            isVoiding = uiState.isVoiding,
            errorMessage = uiState.voidErrorMessage,
        )
    }

    if (uiState.showAdvancedFilterPanel) {
        MovementFiltersSheet(uiState,
            onDismiss = { viewModel.onToggleAdvancedFilterPanel(false) },
            onApply = viewModel::onApplyFilterDraft, onVerify = viewModel::onRevalidateAccess,
            onViewPlans = onNavigateToPlans, onDismissRecovery = viewModel::onDismissRecovery)

    }
}

@Composable
fun TransactionRow(
    item: TransactionItem,
    onClick: () -> Unit = {},
    onVoidClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    onEditClick: () -> Unit = onClick,
) {
    val tx = item.transaction
    val colorScheme = MaterialTheme.colorScheme
    val isVoided = tx.status == TransactionStatus.VOIDED
    val stackedAmount = LocalDensity.current.fontScale > 1.3f
    var showMenu by remember { mutableStateOf(false) }

    val legacyTitle = when (tx.legacyKind) {
        "OPENING" -> "Saldo inicial"
        "ADJUSTMENT" -> "Ajuste de saldo"
        "REVERSAL" -> "Reversión de apertura"
        "CARD_PAYMENT_CASH" -> "Pago de tarjeta"
        else -> null
    }
    val title = when {
        tx.operationKind.equals("CARD_PAYMENT", ignoreCase = true) -> "Pago de tarjeta"
        legacyTitle != null -> legacyTitle
        else -> item.merchantName?.takeIf { it.isNotBlank() }
            ?: item.categoryName?.takeIf { it.isNotBlank() }
            ?: when (tx.type) {
                MovementType.EXPENSE -> "Gasto"
                MovementType.INCOME -> "Ingreso"
                MovementType.TRANSFER -> "Transferencia"
            }
    }

    val cardLabel = item.cardAlias ?: item.cardLastFourDigits?.let { "•••• $it" } ?: "Tarjeta"
    val subtitle = when {
        tx.operationKind.equals("CARD_PURCHASE", ignoreCase = true) -> cardLabel
        tx.operationKind.equals("CARD_PAYMENT", ignoreCase = true) -> "${item.sourceAccountAlias ?: "Cuenta"} → $cardLabel"
        tx.type == MovementType.TRANSFER -> "${item.sourceAccountAlias ?: "Origen"} → ${item.destinationAccountAlias ?: "Destino"}"
        else -> item.sourceAccountAlias ?: "Cuenta"
    }

    val amountColor = if (isVoided) {
        colorScheme.onSurfaceVariant
    } else when (tx.type) {
        MovementType.INCOME -> KipuIncome
        MovementType.EXPENSE -> colorScheme.onSurface
        MovementType.TRANSFER -> colorScheme.primary
    }

    val amountPrefix = when (tx.type) {
        MovementType.INCOME -> "+"
        MovementType.EXPENSE -> "-"
        MovementType.TRANSFER -> ""
    }

    val currencySymbol = if (tx.currency == "PEN") "S/" else "$"

    Surface(
        shape = MaterialTheme.shapes.large,
        color = colorScheme.surface,

        modifier = modifier
            .testTag("tx_row_${tx.id}")
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            // Category / Avatar Icon
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isVoided) {
                            colorScheme.surfaceContainerHigh
                        } else when (tx.type) {
                            MovementType.INCOME -> KipuIncome.copy(alpha = 0.12f)
                            MovementType.EXPENSE -> KipuExpense.copy(alpha = 0.12f)
                            MovementType.TRANSFER -> colorScheme.primary.copy(alpha = 0.12f)
                        }
                    )
            ) {
                Text(
                    text = title.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isVoided) {
                            colorScheme.onSurfaceVariant
                        } else when (tx.type) {
                            MovementType.INCOME -> KipuIncome
                            MovementType.EXPENSE -> KipuExpense
                            MovementType.TRANSFER -> colorScheme.primary
                        }
                    )
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Subtitle
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.None,
                        ),
                        color = if (isVoided) colorScheme.onSurfaceVariant else colorScheme.onSurface,
                        maxLines = 2,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isVoided) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.testTag("tx_voided_badge_${tx.id}"),
                        ) {
                            Text(
                                text = stringResource(R.string.movement_void_status_badge),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = colorScheme.error,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Sync badge
                    SyncStatusIcon(status = tx.syncStatus)
                }
                Text(tx.syncStatus.uiLabel(), style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant)
                if (tx.status == TransactionStatus.REVISED) Text("Corregido", style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.primary)
                if (stackedAmount) MoneyText("$amountPrefix${formatMinorUnits(tx.amountMinor)}", currencySymbol = currencySymbol,
                    color = amountColor, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("tx_amount_${tx.id}"))
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Amount with tnum
            if (!stackedAmount) Column(horizontalAlignment = Alignment.End) {
                val formattedAmount = formatMinorUnits(tx.amountMinor)
                MoneyText(
                    amount = "$amountPrefix$formattedAmount",
                    currencySymbol = currencySymbol,
                    color = amountColor,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                        textDecoration = TextDecoration.None,
                    ),
                    modifier = Modifier.testTag("tx_amount_${tx.id}"),
                )
            }

            // Options menu (Editar / Anular) - only for active / non-voided transactions
            if (!isVoided) {
                Spacer(modifier = Modifier.width(4.dp))
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("tx_menu_${tx.id}")
                            .semantics { contentDescription = "Opciones del movimiento" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.testTag("tx_dropdown_menu_${tx.id}")
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.movement_action_edit)) },
                            onClick = {
                                showMenu = false
                                onEditClick()
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("action_edit_${tx.id}")
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.movement_action_void)) },
                            onClick = {
                                showMenu = false
                                onVoidClick()
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("action_void_${tx.id}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SyncStatusIcon(
    status: MovementSyncStatus,
    modifier: Modifier = Modifier,
) {
    when (status) {
        MovementSyncStatus.SYNCED, MovementSyncStatus.MIGRATED_LOCAL -> {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = if (status == MovementSyncStatus.MIGRATED_LOCAL) {
                    "Movimiento histórico conservado localmente"
                } else {
                    stringResource(R.string.movements_sync_synced)
                },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier.size(14.dp)
            )
        }
        MovementSyncStatus.PENDING, MovementSyncStatus.IN_FLIGHT -> {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = stringResource(R.string.movements_sync_pending),
                tint = MaterialTheme.colorScheme.primary,
                modifier = modifier.size(14.dp)
            )
        }
        MovementSyncStatus.CONFLICT, MovementSyncStatus.FAILED_PERMANENT -> {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = stringResource(R.string.movements_sync_error),
                tint = KipuExpense,
                modifier = modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun EmptyMovementsState(
    hasActiveFilters: Boolean = false,
    onClearFilters: (() -> Unit)? = null,
    onRegister: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (hasActiveFilters) {
                stringResource(R.string.movements_filter_empty_title)
            } else {
                stringResource(R.string.movements_empty_title)
            },
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (hasActiveFilters) {
                stringResource(R.string.movements_filter_empty_desc)
            } else {
                stringResource(R.string.movements_empty_desc)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (!hasActiveFilters && onRegister != null) {
            Button(onClick = onRegister, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.history_register))
            }
        }
        if (hasActiveFilters && onClearFilters != null) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onClearFilters,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("btn_empty_clear_filters")
                    .semantics { contentDescription = "Limpiar filtros de búsqueda" },
            ) {
                Text(stringResource(R.string.movements_filter_clear))
            }
        }
    }
}
