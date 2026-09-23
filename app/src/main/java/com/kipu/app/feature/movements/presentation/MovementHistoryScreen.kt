package com.kipu.app.feature.movements.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipu.app.R
import com.kipu.app.feature.movements.domain.model.MovementSyncStatus
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.TransactionItem
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.formatMinorUnits
import kotlinx.coroutines.launch

private val PrimaryTeal = Color(0xFF0F766E)
private val IncomeGreen = Color(0xFF16A34A)
private val ExpenseRed = Color(0xFFE85D5D)
private val BackgroundGray = Color(0xFFF8FAFC)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovementHistoryRoute(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    viewModel: MovementHistoryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.movements_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 20.sp,
                        ),
                        color = Color(0xFF0F172A),
                    )
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToNotifications,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = "Notificaciones" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = Color(0xFF475569)
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = "Ajustes" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = Color(0xFF475569)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::onOpenRegisterSheet,
                containerColor = PrimaryTeal,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .size(56.dp)
                    .testTag("fab_add_transaction")
                    .semantics { contentDescription = "Registrar nueva transacción" },
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = BackgroundGray,
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = {
                    Text(
                        stringResource(R.string.movements_search_placeholder),
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF64748B)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = PrimaryTeal,
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
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
                        label = { Text("Todos") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryTeal,
                            selectedLabelColor = Color.White,
                            containerColor = Color.White,
                            labelColor = Color(0xFF475569),
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("chip_filter_all"),
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.selectedFilterType == MovementType.EXPENSE,
                        onClick = { viewModel.onFilterTypeSelected(MovementType.EXPENSE) },
                        label = { Text(stringResource(R.string.movement_type_expense)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryTeal,
                            selectedLabelColor = Color.White,
                            containerColor = Color.White,
                            labelColor = Color(0xFF475569),
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("chip_filter_expense"),
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.selectedFilterType == MovementType.INCOME,
                        onClick = { viewModel.onFilterTypeSelected(MovementType.INCOME) },
                        label = { Text(stringResource(R.string.movement_type_income)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryTeal,
                            selectedLabelColor = Color.White,
                            containerColor = Color.White,
                            labelColor = Color(0xFF475569),
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("chip_filter_income"),
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.selectedFilterType == MovementType.TRANSFER,
                        onClick = { viewModel.onFilterTypeSelected(MovementType.TRANSFER) },
                        label = { Text(stringResource(R.string.movement_type_transfer)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryTeal,
                            selectedLabelColor = Color.White,
                            containerColor = Color.White,
                            labelColor = Color(0xFF475569),
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("chip_filter_transfer"),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Transaction List or Empty State
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryTeal)
                }
            } else if (uiState.filteredTransactions.isEmpty()) {
                EmptyMovementsState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("list_movements")
                ) {
                    uiState.filteredTransactions.forEach { (dateHeader, items) ->
                        item(key = "header_$dateHeader") {
                            Text(
                                text = dateHeader,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BackgroundGray)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        items(items, key = { it.transaction.id }) { item ->
                            TransactionRow(
                                item = item,
                                onActionClicked = { action ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Acción $action diferida a próximos sprints.")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp)) // FAB spacing
                    }
                }
            }
        }
    }

    if (uiState.showRegisterSheet) {
        QuickMovementBottomSheet(
            onDismissRequest = viewModel::onCloseRegisterSheet,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionRow(
    item: TransactionItem,
    onActionClicked: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    val tx = item.transaction

    val legacyTitle = when (tx.legacyKind) {
        "OPENING" -> "Saldo inicial"
        "ADJUSTMENT" -> "Ajuste de saldo"
        "REVERSAL" -> "Reversión de apertura"
        "CARD_PAYMENT_CASH" -> "Pago de tarjeta"
        else -> null
    }
    val title = legacyTitle ?: item.merchantName?.takeIf { it.isNotBlank() }
        ?: item.categoryName?.takeIf { it.isNotBlank() }
        ?: when (tx.type) {
            MovementType.EXPENSE -> "Gasto"
            MovementType.INCOME -> "Ingreso"
            MovementType.TRANSFER -> "Transferencia"
        }

    val subtitle = when (tx.type) {
        MovementType.TRANSFER -> "${item.sourceAccountAlias ?: "Origen"} → ${item.destinationAccountAlias ?: "Destino"}"
        else -> item.sourceAccountAlias ?: "Cuenta"
    }

    val amountColor = when (tx.type) {
        MovementType.INCOME -> IncomeGreen
        MovementType.EXPENSE -> Color(0xFF0F172A)
        MovementType.TRANSFER -> PrimaryTeal
    }

    val amountPrefix = when (tx.type) {
        MovementType.INCOME -> "+"
        MovementType.EXPENSE -> "-"
        MovementType.TRANSFER -> ""
    }

    val currencySymbol = if (tx.currency == "PEN") "S/" else "$"

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 0.5.dp,
        modifier = modifier
            .combinedClickable(
                onClick = {},
                onLongClick = { showMenu = true },
            )
            .testTag("tx_row_${tx.id}"),
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
                        when (tx.type) {
                            MovementType.INCOME -> IncomeGreen.copy(alpha = 0.12f)
                            MovementType.EXPENSE -> ExpenseRed.copy(alpha = 0.12f)
                            MovementType.TRANSFER -> PrimaryTeal.copy(alpha = 0.12f)
                        }
                    )
            ) {
                Text(
                    text = title.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = when (tx.type) {
                            MovementType.INCOME -> IncomeGreen
                            MovementType.EXPENSE -> ExpenseRed
                            MovementType.TRANSFER -> PrimaryTeal
                        }
                    )
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Sync badge
                    SyncStatusIcon(status = tx.syncStatus)
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Amount with tnum
            Column(horizontalAlignment = Alignment.End) {
                val formattedAmount = formatMinorUnits(tx.amountMinor)
                MoneyText(
                    amount = "$amountPrefix$formattedAmount",
                    currencySymbol = currencySymbol,
                    color = amountColor,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                    modifier = Modifier.testTag("tx_amount_${tx.id}"),
                )
            }

            // Context Menu (Deferred S4/S7)
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.movement_action_edit)) },
                    onClick = {
                        showMenu = false
                        onActionClicked("Editar")
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.movement_action_void)) },
                    onClick = {
                        showMenu = false
                        onActionClicked("Anular")
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.movement_action_delete)) },
                    onClick = {
                        showMenu = false
                        onActionClicked("Eliminar")
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.movement_action_refund)) },
                    onClick = {
                        showMenu = false
                        onActionClicked("Reembolso")
                    }
                )
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
                tint = Color(0xFF94A3B8),
                modifier = modifier.size(14.dp)
            )
        }
        MovementSyncStatus.PENDING, MovementSyncStatus.IN_FLIGHT -> {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = stringResource(R.string.movements_sync_pending),
                tint = Color(0xFF38BDF8),
                modifier = modifier.size(14.dp)
            )
        }
        MovementSyncStatus.CONFLICT, MovementSyncStatus.FAILED_PERMANENT -> {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = stringResource(R.string.movements_sync_error),
                tint = ExpenseRed,
                modifier = modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun EmptyMovementsState(
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
                .background(Color(0xFFE2E8F0))
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.movements_empty_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF0F172A),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.movements_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center
        )
    }
}
