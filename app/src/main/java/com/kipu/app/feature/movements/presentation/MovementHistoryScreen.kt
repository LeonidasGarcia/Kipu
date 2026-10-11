package com.kipu.app.feature.movements.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipu.app.R
import com.kipu.app.feature.categories.presentation.resolveCategoryIcon
import com.kipu.app.feature.movements.domain.model.MovementSyncStatus
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.TransactionItem
import com.kipu.app.feature.movements.domain.model.TransactionStatus
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MovementHistoryRoute(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToNewAccount: () -> Unit = {},
    onNavigateToPlans: () -> Unit = {},
    onNavigateToEditor: (String) -> Unit = {},
    viewModel: MovementHistoryViewModel = hiltViewModel(),
    prewarmQuickMovement: Boolean = false,
    modifier: Modifier = Modifier,
    savedMessage: Boolean = false,
    onSavedMessageConsumed: () -> Unit = {},
    openRegisterMovement: Boolean = false,
    onConsumeRegisterMovement: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val voidBalances by viewModel.voidBalanceState.collectAsStateWithLifecycle()
    val quickMovementViewModel = if (prewarmQuickMovement) hiltViewModel<QuickMovementViewModel>() else null
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedText = stringResource(R.string.history_saved_pending)
    val voidedText = stringResource(R.string.history_voided_pending)
    val emeraldColors = rememberCalmEmeraldColors()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val receiptPreferences = remember(context) {
        context.getSharedPreferences("movement_receipts_v1", Context.MODE_PRIVATE)
    }
    val detailItem = uiState.selectedDetail
    var receiptUriText by remember(detailItem?.transaction?.userId, detailItem?.transaction?.id) {
        mutableStateOf(detailItem?.let { row ->
            receiptPreferences.getString(movementReceiptPreferenceKey(row), null)
        })
    }
    val receiptPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val row = detailItem
        if (uri != null && row != null) {
            val isPdf = runCatching { context.contentResolver.getType(uri)?.substringBefore(';') }
                .getOrNull()
                ?.let { it == "application/pdf" } ?: true
            val persisted = isPdf && runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.isSuccess
            if (persisted) {
                receiptPreferences.edit()
                    .putString(movementReceiptPreferenceKey(row), uri.toString())
                    .apply()
                receiptUriText = uri.toString()
                scope.launch { snackbarHostState.showSnackbar("Comprobante PDF guardado en este dispositivo") }
            } else {
                scope.launch { snackbarHostState.showSnackbar("No se pudo guardar el acceso al PDF. Elige otro archivo.") }
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= layout.totalItemsCount - 6
        }.collect { nearEnd ->
            if (nearEnd) viewModel.onLoadMore()
        }
    }

    LaunchedEffect(savedMessage) {
        if (savedMessage) {
            onSavedMessageConsumed()
            snackbarHostState.showSnackbar(savedText)
        }
    }

    // Respond to root navigation register movement trigger
    LaunchedEffect(openRegisterMovement) {
        if (openRegisterMovement) {
            viewModel.onOpenRegisterSheet()
            onConsumeRegisterMovement()
        }
    }

    val mostUsedAccountId = uiState.mostUsedAccountId

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.movements_title),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 24.sp,
                            ),
                            color = emeraldColors.primaryText,
                        )

                        // Period context pill
                        val periodLabel = remember(uiState.fromDate, uiState.toDate) {
                            if (uiState.fromDate != null && uiState.toDate != null) {
                                val fmt = SimpleDateFormat("MMM", Locale("es", "PE"))
                                val fromStr = fmt.format(Date(uiState.fromDate!!)).replaceFirstChar { it.uppercase() }
                                val toStr = fmt.format(Date(uiState.toDate!!)).replaceFirstChar { it.uppercase() }
                                if (fromStr.equals(toStr, ignoreCase = true)) fromStr else "$fromStr - $toStr"
                            } else if (uiState.fromDate != null) {
                                SimpleDateFormat("MMM yyyy", Locale("es", "PE")).format(Date(uiState.fromDate!!)).replaceFirstChar { it.uppercase() }
                            } else {
                                "Todos"
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = emeraldColors.surfaceCard,
                            border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp)),
                        ) {
                            Row(
                                modifier = Modifier
                                    .heightIn(min = 36.dp)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = periodLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = emeraldColors.secondaryMuted,
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = "Ajustes" }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = emeraldColors.secondaryMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = emeraldColors.background),
            )
        },
        containerColor = emeraldColors.background,
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("list_movements"),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item(key = "history_controls") {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Search Bar
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::onSearchQueryChanged,
                        placeholder = {
                            Text(
                                stringResource(R.string.movements_search_placeholder),
                                color = emeraldColors.secondaryMuted,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = emeraldColors.secondaryMuted,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.onSearchQueryChanged("") },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("btn_clear_search")
                                            .semantics { contentDescription = "Limpiar búsqueda" }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = null,
                                            tint = emeraldColors.secondaryMuted,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { viewModel.onToggleAdvancedFilterPanel(true) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("btn_open_filters")
                                        .semantics { contentDescription = "Abrir filtros" }
                                ) {
                                    Icon(
                                        Icons.Rounded.FilterList,
                                        contentDescription = null,
                                        tint = if (uiState.hasActiveAdvancedFilters) emeraldColors.primaryDeep else emeraldColors.secondaryMuted,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = emeraldColors.surfaceCard,
                            unfocusedContainerColor = emeraldColors.surfaceCard,
                            focusedBorderColor = emeraldColors.primaryDeep,
                            unfocusedBorderColor = emeraldColors.borderSubtle,
                            cursorColor = emeraldColors.primaryDeep,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("input_search_movements"),
                    )

                    // Type Filter Chips: Todos, Gastos, Ingresos, Transf.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CalmEmeraldTypeChip(
                            label = "Todos",
                            selected = uiState.selectedFilterType == null,
                            onClick = { viewModel.onFilterTypeSelected(null) },
                            hasCheck = true,
                            modifier = Modifier.testTag("chip_filter_all"),
                        )
                        CalmEmeraldTypeChip(
                            label = "Gastos",
                            dotColor = emeraldColors.expenseCoral,
                            selected = uiState.selectedFilterType == MovementType.EXPENSE,
                            onClick = { viewModel.onFilterTypeSelected(MovementType.EXPENSE) },
                            modifier = Modifier.testTag("chip_filter_expense"),
                        )
                        CalmEmeraldTypeChip(
                            label = "Ingresos",
                            dotColor = emeraldColors.incomeEmerald,
                            selected = uiState.selectedFilterType == MovementType.INCOME,
                            onClick = { viewModel.onFilterTypeSelected(MovementType.INCOME) },
                            modifier = Modifier.testTag("chip_filter_income"),
                        )
                        CalmEmeraldTypeChip(
                            label = "Transf.",
                            dotColor = emeraldColors.transferBlue,
                            selected = uiState.selectedFilterType == MovementType.TRANSFER,
                            onClick = { viewModel.onFilterTypeSelected(MovementType.TRANSFER) },
                            modifier = Modifier.testTag("chip_filter_transfer"),
                        )

                        if (uiState.hasActiveAdvancedFilters || uiState.selectedFilterType != null || uiState.searchQuery.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = emeraldColors.surfaceCard,
                                border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(onClick = viewModel::onClearFilters)
                                    .testTag("btn_clear_filters")
                                    .semantics { contentDescription = "Limpiar todos los filtros" },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(R.string.movements_filter_clear),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = emeraldColors.expenseCoral,
                                    )
                                }
                            }
                        }
                    }

                    // Summary Card for Filtered Query (pre-computed in ViewModel)
                    val totalMovCount = uiState.totalFilteredCount
                    val netByCurrency = uiState.netByCurrency

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = emeraldColors.surfaceCard),
                        border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = "BALANCE NETO",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.6.sp,
                                            fontSize = 11.sp,
                                        ),
                                        color = emeraldColors.secondaryMuted,
                                    )
                                    Text(
                                        text = "Resultados filtrados",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                        ),
                                        color = emeraldColors.secondaryMuted.copy(alpha = 0.8f),
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = emeraldColors.pillTrack,
                                ) {
                                    Text(
                                        text = if (totalMovCount == 1L) "1 mov" else "$totalMovCount movs",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                        ),
                                        color = emeraldColors.secondaryMuted,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val currenciesToShow = remember(netByCurrency) {
                                if (netByCurrency.isEmpty()) listOf("PEN")
                                else {
                                    val list = mutableListOf<String>()
                                    if (netByCurrency.containsKey("PEN")) list.add("PEN")
                                    if (netByCurrency.containsKey("USD")) list.add("USD")
                                    netByCurrency.keys.filter { it != "PEN" && it != "USD" }.forEach { list.add(it) }
                                    list
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                currenciesToShow.forEach { currency ->
                                    val netMinor = netByCurrency[currency] ?: BigInteger.ZERO
                                    val (prefix, formattedAmount) = formatSignedBigIntegerMinor(netMinor)
                                    val symbol = when (currency) {
                                        "PEN" -> "S/"
                                        "USD" -> "$"
                                        else -> currency
                                    }
                                    val netColor = when {
                                        netMinor > BigInteger.ZERO -> emeraldColors.incomeEmerald
                                        netMinor < BigInteger.ZERO -> emeraldColors.expenseCoral
                                        else -> emeraldColors.primaryText
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (currenciesToShow.size > 1) {
                                            Text(
                                                text = currency,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                ),
                                                color = emeraldColors.secondaryMuted,
                                            )
                                        }
                                        MoneyText(
                                            amount = "$prefix$formattedAmount",
                                            currencySymbol = symbol,
                                            style = MaterialTheme.typography.headlineMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = if (currenciesToShow.size > 1) 20.sp else 24.sp,
                                                fontFeatureSettings = "tnum",
                                            ),
                                            color = netColor,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // System / Access cards
                    MovementAccessCard(
                        uiState,
                        onNavigateToPlans,
                        viewModel::onRevalidateAccess,
                        viewModel::onDismissRecovery,
                        Modifier.fillMaxWidth(),
                    )
                    MovementAppliedFilters(
                        uiState,
                        viewModel::onRemoveFilter,
                        Modifier.fillMaxWidth(),
                    )
                }
            }

            when {
                uiState.isLoading -> item(key = "loading") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = emeraldColors.primaryDeep)
                    }
                }

                uiState.queryError -> item(key = "read_error") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 240.dp)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            stringResource(R.string.history_query_error),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = emeraldColors.primaryText,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.history_query_error_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = emeraldColors.secondaryMuted,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = viewModel::onRetryHistory,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = emeraldColors.primaryDeep,
                                contentColor = emeraldColors.onPrimaryDeep,
                            ),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.history_retry), color = emeraldColors.onPrimaryDeep)
                        }
                    }
                }

                uiState.filteredTransactions.isEmpty() -> item(key = "empty") {
                    val isSearch = uiState.searchQuery.isNotBlank()
                    val hasFilters = uiState.hasActiveAdvancedFilters || uiState.selectedFilterType != null
                    CalmEmeraldEmptyMovementsState(
                        isSearchActive = isSearch,
                        hasActiveFilters = hasFilters,
                        onClearSearch = { viewModel.onSearchQueryChanged("") },
                        onClearFilters = viewModel::onClearFilters,
                        onRegister = viewModel::onOpenRegisterSheet,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )
                }

                else -> {
                    uiState.filteredTransactions.forEach { (dateHeader, rows) ->
                        item(key = "header_$dateHeader") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = dateHeader,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = emeraldColors.primaryText,
                                )
                                Text(
                                    text = if (uiState.hasMorePages) "${rows.size} cargados"
                                        else if (rows.size == 1) "1 movimiento" else "${rows.size} movimientos",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = emeraldColors.secondaryMuted,
                                )
                            }
                        }

                        itemsIndexed(
                            items = rows,
                            key = { _, item -> "movement_${item.transaction.id}" },
                            contentType = { _, _ -> "movement" },
                        ) { index, item ->
                            val first = index == 0
                            val last = index == rows.lastIndex
                            Card(
                                shape = RoundedCornerShape(
                                    topStart = if (first) 16.dp else 0.dp,
                                    topEnd = if (first) 16.dp else 0.dp,
                                    bottomStart = if (last) 16.dp else 0.dp,
                                    bottomEnd = if (last) 16.dp else 0.dp,
                                ),
                                colors = CardDefaults.cardColors(containerColor = emeraldColors.surfaceCard),
                                border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = if (last) 10.dp else 0.dp),
                            ) {
                                TransactionRow(
                                    item = item,
                                    onClick = { viewModel.onOpenDetail(item) },
                                    onEditClick = { onNavigateToEditor(item.transaction.id) },
                                    onVoidClick = { viewModel.onSelectTransactionForVoid(item) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.isLoadingNextPage) {
                item(key = "loading_next_page") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = emeraldColors.primaryDeep,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            } else if (uiState.loadMoreError != null) {
                item(key = "load_more_error") {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        uiState.loadMoreError?.let { error ->
                            Text(error, color = emeraldColors.secondaryMuted)
                        }
                        OutlinedButton(onClick = viewModel::onLoadMore) {
                            Text(stringResource(R.string.history_retry))
                        }
                    }
                }
            } else if (!uiState.isLoading && uiState.filteredTransactions.isNotEmpty() && !uiState.hasMorePages) {
                item(key = "end_of_history") {
                    Text(
                        text = "Fin del historial",
                        color = emeraldColors.secondaryMuted,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    )
                }
            }

            // Bottom space for floating nav bar
            item(key = "bottom_space") {
                Spacer(Modifier.height(72.dp))
            }
        }
    }

    if (uiState.showRegisterSheet) {
        QuickMovementBottomSheet(
            onDismissRequest = viewModel::onCloseRegisterSheet,
            viewModel = quickMovementViewModel ?: hiltViewModel(),
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
        MovementDetailSheet(
            item,
            uiState.detailRevisions,
            uiState.detailLoading,
            uiState.detailError,
            onDismiss = viewModel::onCloseDetail,
            onEdit = { viewModel.onCloseDetail(); onNavigateToEditor(item.transaction.id) },
            onVoid = { viewModel.onCloseDetail(); viewModel.onSelectTransactionForVoid(item) },
            receiptAttached = !receiptUriText.isNullOrBlank(),
            onAttachReceipt = { receiptPicker.launch(arrayOf("application/pdf")) },
            onOpenReceipt = {
                val uri = receiptUriText?.let(Uri::parse)
                if (uri != null) {
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, "application/pdf")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(intent, "Abrir comprobante"))
                    }.onFailure {
                        scope.launch { snackbarHostState.showSnackbar("No se pudo abrir el comprobante PDF") }
                    }
                }
            },
            onRemoveReceipt = {
                receiptPreferences.edit().remove(movementReceiptPreferenceKey(item)).apply()
                receiptUriText = null
            },
        )
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
            currentSourceBalanceMinor = voidBalances.sourceMinorUnits,
            currentDestinationBalanceMinor = voidBalances.destinationMinorUnits,
        )
    }

    if (uiState.showAdvancedFilterPanel) {
        MovementFiltersSheet(
            uiState,
            onDismiss = { viewModel.onToggleAdvancedFilterPanel(false) },
            onApply = viewModel::onApplyFilterDraft,
            onVerify = viewModel::onRevalidateAccess,
            onViewPlans = onNavigateToPlans,
            onDismissRecovery = viewModel::onDismissRecovery
        )
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
    val emeraldColors = rememberCalmEmeraldColors()
    val isVoided = tx.status == TransactionStatus.VOIDED
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

    val (iconBg, iconTint) = when {
        isVoided -> emeraldColors.pillTrack to emeraldColors.secondaryMuted
        tx.type == MovementType.INCOME -> emeraldColors.incomeBg to emeraldColors.incomeEmerald
        tx.type == MovementType.EXPENSE -> emeraldColors.expenseBg to emeraldColors.expenseCoral
        tx.type == MovementType.TRANSFER -> emeraldColors.transferBg to emeraldColors.transferBlue
        else -> emeraldColors.pillTrack to emeraldColors.primaryDeep
    }

    val amountColor = if (isVoided) {
        emeraldColors.secondaryMuted
    } else when (tx.type) {
        MovementType.INCOME -> emeraldColors.incomeEmerald
        MovementType.EXPENSE -> emeraldColors.expenseCoral
        MovementType.TRANSFER -> emeraldColors.transferBlue
    }

    val amountPrefix = when (tx.type) {
        MovementType.INCOME -> "+"
        MovementType.EXPENSE -> "−"
        MovementType.TRANSFER -> ""
    }

    val currencySymbol = if (tx.currency == "PEN") "S/" else "$"
    val formattedAmount = formatMinorUnits(tx.amountMinor)

    Surface(
        color = Color.Transparent,
        modifier = modifier
            .testTag("tx_row_${tx.id}")
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            // Tinted circle icon
            Surface(
                shape = CircleShape,
                color = iconBg,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (item.categoryIcon != null) {
                        Icon(
                            imageVector = resolveCategoryIcon(item.categoryIcon),
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Text(
                            text = title.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            ),
                            color = iconTint,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Subtitle + Badges
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            textDecoration = if (isVoided) TextDecoration.LineThrough else TextDecoration.None,
                        ),
                        color = if (isVoided) emeraldColors.secondaryMuted else emeraldColors.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isVoided) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = emeraldColors.expenseBg,
                            modifier = Modifier.testTag("tx_voided_badge_${tx.id}"),
                        ) {
                            Text(
                                text = stringResource(R.string.movement_void_status_badge),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                color = emeraldColors.expenseCoral,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    } else if (tx.syncStatus == MovementSyncStatus.CONFLICT) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = emeraldColors.warningBg,
                        ) {
                            Text(
                                text = "Requiere revisión",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                color = emeraldColors.warningAmber,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = emeraldColors.secondaryMuted,
                        maxLines = 1,
                    )
                    SyncStatusIcon(status = tx.syncStatus)
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Amount
            MoneyText(
                amount = "$amountPrefix$formattedAmount",
                currencySymbol = currencySymbol,
                color = amountColor,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    fontFeatureSettings = "tnum",
                    textDecoration = if (isVoided) TextDecoration.LineThrough else TextDecoration.None,
                ),
                modifier = Modifier.testTag("tx_amount_${tx.id}"),
            )

            // Options menu (Editar / Anular)
            if (!isVoided) {
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("tx_menu_${tx.id}")
                            .semantics { contentDescription = "Opciones del movimiento" }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = null,
                            tint = emeraldColors.secondaryMuted,
                            modifier = Modifier.size(18.dp)
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
private fun CalmEmeraldTypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dotColor: Color? = null,
    hasCheck: Boolean = false,
) {
    val emeraldColors = rememberCalmEmeraldColors()

    val container = if (selected) {
        if (hasCheck) emeraldColors.primaryDeep
        else when (dotColor) {
            emeraldColors.expenseCoral -> emeraldColors.expenseBg
            emeraldColors.incomeEmerald -> emeraldColors.incomeBg
            emeraldColors.transferBlue -> emeraldColors.transferBg
            else -> emeraldColors.incomeBg
        }
    } else {
        emeraldColors.surfaceCard
    }

    val contentColor = if (selected) {
        if (hasCheck) emeraldColors.onPrimaryDeep else dotColor ?: emeraldColors.primaryDeep
    } else {
        emeraldColors.secondaryMuted
    }

    val border = if (selected) {
        if (hasCheck) null
        else BorderStroke(1.dp, dotColor?.copy(alpha = 0.5f) ?: emeraldColors.primaryDeep)
    } else {
        BorderStroke(1.dp, emeraldColors.borderSubtle)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = container,
        border = border,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics {
                this.selected = selected
                this.role = Role.Tab
            },
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (hasCheck && selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp),
                )
            } else if (dotColor != null) {
                Surface(
                    shape = CircleShape,
                    color = dotColor,
                    modifier = Modifier.size(7.dp),
                ) {}
            }

            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp,
                ),
                color = contentColor,
            )
        }
    }
}

@Composable
fun SyncStatusIcon(
    status: MovementSyncStatus,
    modifier: Modifier = Modifier,
) {
    val emeraldColors = rememberCalmEmeraldColors()
    when (status) {
        MovementSyncStatus.SYNCED, MovementSyncStatus.MIGRATED_LOCAL -> {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = if (status == MovementSyncStatus.MIGRATED_LOCAL) {
                    "Movimiento histórico conservado localmente"
                } else {
                    stringResource(R.string.movements_sync_synced)
                },
                tint = emeraldColors.secondaryMuted,
                modifier = modifier.size(13.dp)
            )
        }
        MovementSyncStatus.PENDING, MovementSyncStatus.IN_FLIGHT -> {
            Icon(
                imageVector = Icons.Rounded.Sync,
                contentDescription = stringResource(R.string.movements_sync_pending),
                tint = emeraldColors.primaryDeep,
                modifier = modifier.size(13.dp)
            )
        }
        MovementSyncStatus.CONFLICT, MovementSyncStatus.FAILED_PERMANENT -> {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = stringResource(R.string.movements_sync_error),
                tint = emeraldColors.expenseCoral,
                modifier = modifier.size(13.dp)
            )
        }
    }
}

@Composable
fun CalmEmeraldEmptyMovementsState(
    isSearchActive: Boolean = false,
    hasActiveFilters: Boolean = false,
    onClearSearch: (() -> Unit)? = null,
    onClearFilters: (() -> Unit)? = null,
    onRegister: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val emeraldColors = rememberCalmEmeraldColors()

    val title = when {
        isSearchActive -> stringResource(R.string.movements_search_empty_title)
        hasActiveFilters -> stringResource(R.string.movements_filter_empty_title)
        else -> stringResource(R.string.movements_empty_title)
    }
    val message = when {
        isSearchActive -> stringResource(R.string.movements_search_empty_desc)
        hasActiveFilters -> stringResource(R.string.movements_filter_empty_desc)
        else -> stringResource(R.string.movements_empty_desc)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = emeraldColors.surfaceCard),
        border = BorderStroke(1.dp, emeraldColors.borderSubtle),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = emeraldColors.incomeBg,
                border = BorderStroke(1.dp, emeraldColors.incomeBorder),
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
                        contentDescription = null,
                        tint = emeraldColors.incomeEmerald,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = emeraldColors.primaryText,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = emeraldColors.secondaryMuted,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (!isSearchActive && !hasActiveFilters && onRegister != null) {
                Button(
                    onClick = onRegister,
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = emeraldColors.primaryDeep,
                        contentColor = emeraldColors.onPrimaryDeep,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = "+ Registrar primer movimiento",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = emeraldColors.onPrimaryDeep,
                    )
                }
            } else if (isSearchActive && onClearSearch != null) {
                OutlinedButton(
                    onClick = onClearSearch,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_empty_clear_search")
                        .semantics { contentDescription = "Limpiar búsqueda" },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(R.string.movements_search_clear), color = emeraldColors.primaryDeep)
                }
            } else if (hasActiveFilters && onClearFilters != null) {
                OutlinedButton(
                    onClick = onClearFilters,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_empty_clear_filters")
                        .semantics { contentDescription = "Limpiar filtros" },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(R.string.movements_filter_clear), color = emeraldColors.primaryDeep)
                }
            }
        }
    }
}

private fun formatSignedBigIntegerMinor(minor: BigInteger): Pair<String, String> {
    val isNegative = minor < BigInteger.ZERO
    val absMinor = if (isNegative) minor.negate() else minor
    val prefix = if (isNegative) "− " else if (minor > BigInteger.ZERO) "+ " else ""
    val bd = BigDecimal(absMinor).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
    val formatted = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(bd)
    return prefix to formatted
}

private fun movementReceiptPreferenceKey(item: TransactionItem): String =
    "${item.transaction.userId}:${item.transaction.id}"
