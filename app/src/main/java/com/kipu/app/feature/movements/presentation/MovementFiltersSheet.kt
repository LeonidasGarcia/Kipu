package com.kipu.app.feature.movements.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kipu.app.R
import com.kipu.app.core.finance.domain.MoneyInputParser
import com.kipu.app.feature.movements.domain.model.*
import com.kipu.app.ui.component.KipuBottomSheet
import com.kipu.app.ui.component.KipuFilterChip
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*

/** A sheet owns its draft. Closing it never writes applied filters. */
@Composable
fun MovementFiltersSheet(
    state: MovementHistoryUiState,
    onDismiss: () -> Unit,
    onApply: (MovementFilterDraft) -> FilterDraftValidation,
    onVerify: () -> Unit = {},
    onViewPlans: () -> Unit = {},
    onDismissRecovery: () -> Unit = {},
) {
    key(state.ownerId) {
        MovementFiltersSheetContent(state, onDismiss, onApply, onVerify, onViewPlans, onDismissRecovery)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MovementFiltersSheetContent(
    state: MovementHistoryUiState,
    onDismiss: () -> Unit,
    onApply: (MovementFilterDraft) -> FilterDraftValidation,
    onVerify: () -> Unit = {},
    onViewPlans: () -> Unit = {},
    onDismissRecovery: () -> Unit = {},
) {
    var draft by rememberSaveable(state.ownerId, stateSaver = MovementFilterDraftSaver) {
        mutableStateOf(
            MovementFilterDraft.fromApplied(
                filters = state.appliedFilters,
                movementType = state.selectedFilterType,
            )
        )
    }
    var errors by remember { mutableStateOf(emptyMap<String, FilterDraftError>()) }
    var validationAttempted by remember { mutableStateOf(false) }
    var dates by remember { mutableStateOf(false) }
    var showPeriodOptions by rememberSaveable { mutableStateOf(true) }
    var showPremiumOptions by rememberSaveable { mutableStateOf(false) }
    var showUpsell by remember { mutableStateOf(false) }
    val allowed = state.accessStatus == MovementHistoryAccessDecision.Allowed
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun updateDraft(updated: MovementFilterDraft) {
        draft = updated
        if (validationAttempted) errors = updated.validate().errors
    }

    val rows = state.allTransactions
    val accounts = remember(rows) {
        rows.flatMap {
            listOfNotNull(
                it.transaction.sourceAccountId?.let { id -> MovementReferenceOption(id, it.sourceAccountAlias ?: "Cuenta histórica") },
                it.transaction.destinationAccountId?.let { id -> MovementReferenceOption(id, it.destinationAccountAlias ?: "Cuenta histórica") }
            )
        }.distinctBy { it.id }
    }
    val categories = remember(rows) {
        rows.mapNotNull { row ->
            row.transaction.categoryId?.let { MovementReferenceOption(it, row.categoryName ?: "Categoría histórica") }
        }.distinctBy { it.id }
    }
    val cards = remember(rows) {
        rows.mapNotNull { row ->
            row.transaction.cardId?.let { MovementReferenceOption(it, row.cardAlias ?: "Tarjeta histórica") }
        }.distinctBy { it.id }
    }
    val merchants = remember(rows) {
        rows.mapNotNull { row ->
            row.transaction.merchantId?.let { MovementReferenceOption(it, row.merchantName ?: "Comercio histórico") }
        }.distinctBy { it.id }
    }

    val emeraldColors = rememberCalmEmeraldColors()

    // Count of active filter criteria
    val activeFilterCount = remember(draft) {
        var count = 0
        if (draft.fromDate.isNotBlank() || draft.toDate.isNotBlank()) count++
        if (draft.movementType != null) count++
        if (draft.comparePreviousMonth) count++
        if (draft.accountIds.isNotEmpty()) count++
        if (draft.categoryIds.isNotEmpty()) count++
        if (draft.cardIds.isNotEmpty()) count++
        if (draft.merchantIds.isNotEmpty()) count++
        if (draft.minAmount.isNotBlank() || draft.maxAmount.isNotBlank()) count++
        if (draft.financialStates.isNotEmpty()) count++
        if (draft.syncStatuses.isNotEmpty()) count++
        count
    }

    // Live calculation of matching movements in local history
    val matchingMovementsCount = remember(rows, draft) {
        val fromMillis = runCatching { LocalDate.parse(draft.fromDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        val toMillis = runCatching { LocalDate.parse(draft.toDate).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        val minMinor = draft.minAmount.takeIf { it.isNotBlank() }?.let { MoneyInputParser.parseMinorUnits(it) }
        val maxMinor = draft.maxAmount.takeIf { it.isNotBlank() }?.let { MoneyInputParser.parseMinorUnits(it) }

        rows.count { item ->
            val tx = item.transaction
            if (draft.movementType != null && tx.type != draft.movementType) return@count false
            if (fromMillis != null && tx.occurredAt < fromMillis) return@count false
            if (toMillis != null && tx.occurredAt >= toMillis) return@count false
            if (draft.accountIds.isNotEmpty()) {
                val hasAcc = (tx.sourceAccountId != null && tx.sourceAccountId in draft.accountIds) ||
                    (tx.destinationAccountId != null && tx.destinationAccountId in draft.accountIds)
                if (!hasAcc) return@count false
            }
            if (draft.categoryIds.isNotEmpty() && (tx.categoryId == null || tx.categoryId !in draft.categoryIds)) return@count false
            if (draft.cardIds.isNotEmpty() && (tx.cardId == null || tx.cardId !in draft.cardIds)) return@count false
            if (draft.merchantIds.isNotEmpty() && (tx.merchantId == null || tx.merchantId !in draft.merchantIds)) return@count false
            if (minMinor != null && tx.amountMinor < minMinor) return@count false
            if (maxMinor != null && tx.amountMinor > maxMinor) return@count false
            if (draft.financialStates.isNotEmpty()) {
                val stateMatch = when (tx.status) {
                    TransactionStatus.ACTIVE, TransactionStatus.CONFIRMED -> MovementFinancialState.CONFIRMED in draft.financialStates
                    TransactionStatus.REVISED -> MovementFinancialState.REVISED in draft.financialStates
                    TransactionStatus.VOIDED -> MovementFinancialState.VOIDED in draft.financialStates
                    TransactionStatus.FAILED -> MovementFinancialState.LEGACY_FAILED in draft.financialStates
                }
                if (!stateMatch) return@count false
            }
            if (draft.syncStatuses.isNotEmpty() && tx.syncStatus !in draft.syncStatuses) return@count false
            true
        }
    }

    // Date calculations
    val today = remember { LocalDate.now() }
    val fromDateObj = remember(draft.fromDate) { runCatching { LocalDate.parse(draft.fromDate) }.getOrNull() }
    val toDateObj = remember(draft.toDate) { runCatching { LocalDate.parse(draft.toDate) }.getOrNull() }
    val isPeriodActive = draft.fromDate.isNotBlank() || draft.toDate.isNotBlank()
    val isTodayPreset = isPeriodActive && draft.fromDate == today.toString() && draft.toDate == today.toString()
    val is7DaysPreset = isPeriodActive && draft.fromDate == today.minusDays(6).toString() && draft.toDate == today.toString()
    val isMonthPreset = isPeriodActive && draft.fromDate == today.withDayOfMonth(1).toString() && draft.toDate == today.withDayOfMonth(today.lengthOfMonth()).toString()
    val isYearPreset = isPeriodActive && draft.fromDate == today.withDayOfYear(1).toString() && draft.toDate == today.withMonth(12).withDayOfMonth(31).toString()
    val isCustomRange = isPeriodActive && !isTodayPreset && !is7DaysPreset && !isMonthPreset && !isYearPreset

    val periodDurationDays = remember(fromDateObj, toDateObj) {
        if (fromDateObj != null && toDateObj != null && !toDateObj.isBefore(fromDateObj)) {
            ChronoUnit.DAYS.between(fromDateObj, toDateObj) + 1
        } else null
    }

    val displayDateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    val shortDateFormatter = remember { DateTimeFormatter.ofPattern("dd MMM", Locale("es")) }

    val periodSubtitle = when {
        !isPeriodActive -> "Cualquier fecha (Historial completo)"
        isMonthPreset -> "Este mes: ${fromDateObj?.format(shortDateFormatter)} — ${toDateObj?.format(shortDateFormatter)}"
        isTodayPreset -> "Hoy: ${fromDateObj?.format(shortDateFormatter)}"
        is7DaysPreset -> "Últimos 7 días: ${fromDateObj?.format(shortDateFormatter)} — ${toDateObj?.format(shortDateFormatter)}"
        isYearPreset -> "Este año: ${fromDateObj?.format(shortDateFormatter)} — ${toDateObj?.format(shortDateFormatter)}"
        else -> "Personalizado: ${fromDateObj?.format(shortDateFormatter) ?: "…"} — ${toDateObj?.format(shortDateFormatter) ?: "…"}"
    }

    KipuBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("panel_advanced_filters"),
        header = {
            Column(Modifier.fillMaxWidth()) {
                // Header Top Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Filtros de movimientos",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            ),
                            color = Color(0xFF0F172A)
                        )
                        if (activeFilterCount > 0) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFDCFCE7),
                            ) {
                                Text(
                                    text = "$activeFilterCount activos",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    color = Color(0xFF15803D),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp).testTag("btn_close_filters")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.history_filter_cancel),
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Active Filter Chips Bar (Dismissible)
                AnimatedVisibility(visible = activeFilterCount > 0) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Date chip
                        if (isPeriodActive) {
                            val chipLabel = when {
                                isTodayPreset -> "Hoy"
                                is7DaysPreset -> "7 días"
                                isMonthPreset -> "Este mes"
                                isYearPreset -> "Este año"
                                else -> "${fromDateObj?.format(shortDateFormatter) ?: "…"} — ${toDateObj?.format(shortDateFormatter) ?: "…"}"
                            }
                            ActiveFilterDismissChip(
                                text = chipLabel,
                                bgColor = Color(0xFFE6F4EA),
                                borderColor = Color(0xFFA7F3D0),
                                textColor = Color(0xFF0F766E),
                                onDismiss = { updateDraft(draft.copy(fromDate = "", toDate = "")) }
                            )
                        }

                        // Movement Type chip
                        draft.movementType?.let { type ->
                            val (typeLabel, bgCol, borderCol, textCol) = when (type) {
                                MovementType.EXPENSE -> listOf("Gastos", Color(0xFFFEE2E2), Color(0xFFFECACA), Color(0xFFDC2626))
                                MovementType.INCOME -> listOf("Ingresos", Color(0xFFDCFCE7), Color(0xFFA7F3D0), Color(0xFF16A34A))
                                MovementType.TRANSFER -> listOf("Transferencias", Color(0xFFEFF6FF), Color(0xFFBFDBFE), Color(0xFF2563EB))
                            }
                            ActiveFilterDismissChip(
                                text = typeLabel as String,
                                bgColor = bgCol as Color,
                                borderColor = borderCol as Color,
                                textColor = textCol as Color,
                                onDismiss = { updateDraft(draft.copy(movementType = null)) }
                            )
                        }

                        // Compare month chip
                        if (draft.comparePreviousMonth) {
                            ActiveFilterDismissChip(
                                text = "Comparar mes",
                                bgColor = Color(0xFFF1F5F9),
                                borderColor = Color(0xFFCBD5E1),
                                textColor = Color(0xFF475569),
                                onDismiss = { updateDraft(draft.copy(comparePreviousMonth = false)) }
                            )
                        }

                        // Account chips
                        draft.accountIds.forEach { accId ->
                            val accName = accounts.firstOrNull { it.id == accId }?.label ?: "Cuenta"
                            ActiveFilterDismissChip(
                                text = accName,
                                bgColor = Color(0xFFE6F4EA),
                                borderColor = Color(0xFFA7F3D0),
                                textColor = Color(0xFF0F766E),
                                onDismiss = { updateDraft(draft.copy(accountIds = draft.accountIds - accId)) }
                            )
                        }

                        // Category chips
                        draft.categoryIds.forEach { catId ->
                            val catName = categories.firstOrNull { it.id == catId }?.label ?: "Categoría"
                            ActiveFilterDismissChip(
                                text = catName,
                                bgColor = Color(0xFFE6F4EA),
                                borderColor = Color(0xFFA7F3D0),
                                textColor = Color(0xFF0F766E),
                                onDismiss = { updateDraft(draft.copy(categoryIds = draft.categoryIds - catId)) }
                            )
                        }

                        // Amount chip
                        if (draft.minAmount.isNotBlank() || draft.maxAmount.isNotBlank()) {
                            val amountLabel = "S/ ${draft.minAmount.ifBlank { "0" }} – S/ ${draft.maxAmount.ifBlank { "∞" }}"
                            ActiveFilterDismissChip(
                                text = amountLabel,
                                bgColor = Color(0xFFE6F4EA),
                                borderColor = Color(0xFFA7F3D0),
                                textColor = Color(0xFF0F766E),
                                onDismiss = { updateDraft(draft.copy(minAmount = "", maxAmount = "", currency = null)) }
                            )
                        }
                    }
                }
            }
        }
    ) {
        // Scrollable content body
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // CARD 1: Periodo y Fecha
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF2FBF7),
                border = BorderStroke(1.dp, Color(0xFFD1FAE5)),
                modifier = Modifier.fillMaxWidth().animateContentSize()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header of Card 1 (Clickable to collapse/expand)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPeriodOptions = !showPeriodOptions }
                            .testTag("btn_toggle_filter_period"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isPeriodActive) Color(0xFF0F766E) else Color(0xFFCCFBF1),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.CalendarMonth,
                                        contentDescription = null,
                                        tint = if (isPeriodActive) Color.White else Color(0xFF0F766E),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Periodo y Fecha",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF0F172A)
                                    )
                                    if (isPeriodActive) {
                                        Spacer(Modifier.width(6.dp))
                                        Box(Modifier.size(6.dp).background(Color(0xFF0F766E), CircleShape))
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = periodSubtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF64748B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!showPeriodOptions && periodDurationDays != null) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFDCFCE7)
                                        ) {
                                            Text(
                                                text = "${periodDurationDays}d",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                ),
                                                color = Color(0xFF15803D),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Icon(
                            imageVector = if (showPeriodOptions) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Expanded Content for Card 1
                    AnimatedVisibility(visible = showPeriodOptions) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Preset buttons row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterPresetChip(
                                    label = "Hoy",
                                    selected = isTodayPreset,
                                    modifier = Modifier.weight(1f),
                                    onClick = { updateDraft(draft.copy(fromDate = today.toString(), toDate = today.toString())) }
                                )
                                FilterPresetChip(
                                    label = "7 días",
                                    selected = is7DaysPreset,
                                    modifier = Modifier.weight(1f),
                                    onClick = { updateDraft(draft.copy(fromDate = today.minusDays(6).toString(), toDate = today.toString())) }
                                )
                                FilterPresetChip(
                                    label = "Este mes",
                                    selected = isMonthPreset,
                                    modifier = Modifier.weight(1.2f),
                                    onClick = {
                                        val firstDay = today.withDayOfMonth(1)
                                        val lastDay = today.withDayOfMonth(today.lengthOfMonth())
                                        updateDraft(draft.copy(fromDate = firstDay.toString(), toDate = lastDay.toString()))
                                    }
                                )
                                if (isCustomRange) {
                                    FilterPresetChip(
                                        label = "Personalizado",
                                        selected = true,
                                        modifier = Modifier.weight(1.5f),
                                        onClick = { dates = true }
                                    )
                                } else {
                                    FilterPresetChip(
                                        label = if (isYearPreset) "Año" else "Todos",
                                        selected = !isPeriodActive || isYearPreset,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            if (isPeriodActive && !isYearPreset) {
                                                // Toggle to Year
                                                updateDraft(draft.copy(fromDate = today.withDayOfYear(1).toString(), toDate = today.withMonth(12).withDayOfMonth(31).toString()))
                                            } else {
                                                // Clean
                                                updateDraft(draft.copy(fromDate = "", toDate = ""))
                                            }
                                        }
                                    )
                                }
                            }

                            // Date Range Display Box / Custom Date Cards
                            if (isCustomRange) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // DESDE Card
                                        Surface(
                                            onClick = { dates = true },
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color.White,
                                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                            modifier = Modifier.weight(1f).testTag("btn_filter_dates")
                                        ) {
                                            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                                Text(
                                                    text = "DESDE",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp
                                                    ),
                                                    color = Color(0xFF64748B)
                                                )
                                                Spacer(Modifier.height(4.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.CalendarMonth,
                                                        contentDescription = null,
                                                        tint = Color(0xFF0F766E),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = fromDateObj?.format(displayDateFormatter) ?: "Sin elegir",
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                        color = Color(0xFF0F172A)
                                                    )
                                                }
                                            }
                                        }

                                        // HASTA Card
                                        Surface(
                                            onClick = { dates = true },
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color.White,
                                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                                Text(
                                                    text = "HASTA",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp
                                                    ),
                                                    color = Color(0xFF64748B)
                                                )
                                                Spacer(Modifier.height(4.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.CalendarMonth,
                                                        contentDescription = null,
                                                        tint = Color(0xFF0F766E),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = toDateObj?.format(displayDateFormatter) ?: "Sin elegir",
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                        color = Color(0xFF0F172A)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Interval summary
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Intervalo seleccionado",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF64748B)
                                        )
                                        if (periodDurationDays != null) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFFDCFCE7)
                                            ) {
                                                Text(
                                                    text = "$periodDurationDays días",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color(0xFF15803D),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Standard / Todos Capsule Box
                                Surface(
                                    onClick = { dates = true },
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("btn_filter_dates")
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.CalendarMonth,
                                                contentDescription = null,
                                                tint = if (isPeriodActive) Color(0xFF0F766E) else Color(0xFF64748B),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = if (!isPeriodActive) "Historial completo (sin restricciones)"
                                                else "${fromDateObj?.format(displayDateFormatter) ?: "…"} — ${toDateObj?.format(displayDateFormatter) ?: "…"}",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                color = Color(0xFF0F172A)
                                            )
                                        }
                                        if (periodDurationDays != null) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFFDCFCE7)
                                            ) {
                                                Text(
                                                    text = "${periodDurationDays}d",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color(0xFF15803D),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Comparison Switch
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SwapHoriz,
                                        contentDescription = null,
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Comparar con mes anterior",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF1E293B)
                                    )
                                }
                                Switch(
                                    checked = draft.comparePreviousMonth,
                                    onCheckedChange = { updateDraft(draft.copy(comparePreviousMonth = it)) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF0F766E),
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = Color(0xFFCBD5E1),
                                        uncheckedBorderColor = Color.Transparent
                                    )
                                )
                            }

                            // Errors
                            FilterFieldError("from", errors["from"])
                            FilterFieldError("to", errors["to"])
                        }
                    }
                }
            }

            // SECTION 2: TIPO DE MOVIMIENTO
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "TIPO DE MOVIMIENTO",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(start = 4.dp)
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        MovementTypeSegment(
                            title = "Todos",
                            isSelected = draft.movementType == null,
                            activeColor = Color(0xFF0F766E),
                            modifier = Modifier.weight(1f),
                            onClick = { updateDraft(draft.copy(movementType = null)) }
                        )
                        MovementTypeSegment(
                            title = "Gastos",
                            isSelected = draft.movementType == MovementType.EXPENSE,
                            bulletColor = Color(0xFFDC2626),
                            activeColor = Color(0xFFDC2626),
                            modifier = Modifier.weight(1f),
                            onClick = { updateDraft(draft.copy(movementType = MovementType.EXPENSE)) }
                        )
                        MovementTypeSegment(
                            title = "Ingresos",
                            isSelected = draft.movementType == MovementType.INCOME,
                            bulletColor = Color(0xFF16A34A),
                            activeColor = Color(0xFF16A34A),
                            modifier = Modifier.weight(1f),
                            onClick = { updateDraft(draft.copy(movementType = MovementType.INCOME)) }
                        )
                        MovementTypeSegment(
                            title = "Transf.",
                            isSelected = draft.movementType == MovementType.TRANSFER,
                            bulletColor = Color(0xFF2563EB),
                            activeColor = Color(0xFF2563EB),
                            modifier = Modifier.weight(1f),
                            onClick = { updateDraft(draft.copy(movementType = MovementType.TRANSFER)) }
                        )
                    }
                }
            }

            // SECTION 3: Filtros avanzados
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF2FBF7),
                border = BorderStroke(1.dp, Color(0xFFD1FAE5)),
                modifier = Modifier.fillMaxWidth().animateContentSize()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header of Advanced Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Filtros avanzados",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF0F172A)
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFDCFCE7)
                            ) {
                                if (allowed) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Verified,
                                            contentDescription = null,
                                            tint = Color(0xFF0F766E),
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(Modifier.width(3.dp))
                                        Text(
                                            text = "PRO ACTIVO",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            ),
                                            color = Color(0xFF0F766E)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "PREMIUM",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        ),
                                        color = Color(0xFF0F766E),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        if (!allowed) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.MilitaryTech,
                                contentDescription = null,
                                tint = Color(0xFF0F766E),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Text(
                        text = "Filtra por cuenta, categoría, comercio o rango de importe.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B)
                    )

                    // 4 Rows for Advanced Filters
                    if (!allowed) {
                        // LOCKED / FREE STATE ROWS
                        LockedAdvancedRow(
                            icon = Icons.Rounded.AccountBalanceWallet,
                            title = "Cuenta",
                            summary = "Todas las cuentas",
                            onClick = { showUpsell = true }
                        )
                        LockedAdvancedRow(
                            icon = Icons.Rounded.Category,
                            title = "Categoría",
                            summary = "Todas las categorías",
                            onClick = { showUpsell = true }
                        )
                        LockedAdvancedRow(
                            icon = Icons.Rounded.Payments,
                            title = "Importe / Rango",
                            summary = "Monto mín. y máx.",
                            onClick = { showUpsell = true }
                        )
                        LockedAdvancedRow(
                            icon = Icons.Rounded.Storefront,
                            title = "Comercio",
                            summary = "Por comercio o nota",
                            onClick = { showUpsell = true }
                        )

                        // Ver Premium CTA button in card
                        Surface(
                            onClick = { showUpsell = true },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFE8F7F0),
                            border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MilitaryTech,
                                    contentDescription = null,
                                    tint = Color(0xFF0F766E),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Ver Premium",
                                    color = Color(0xFF0F766E),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        // Fallback & recovery banner support
                        if (state.fallbackUsed || state.recovery != HistoryAccessRecovery.IDLE ||
                            state.accessStatus is MovementHistoryAccessDecision.RevalidationRequired) {
                            MovementAccessCard(state, onViewPlans, onVerify, onDismissRecovery)
                        }
                    } else {
                        // UNLOCKED / PRO PLAN CONTROLS
                        AdvancedFiltersControls(
                            draft = draft,
                            onDraftChange = ::updateDraft,
                            errors = errors,
                            allowed = true,
                            accounts = accounts,
                            categories = categories,
                            cards = cards,
                            merchants = merchants
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        // STICKY BOTTOM BAR
        Surface(
            tonalElevation = 4.dp,
            shadowElevation = 8.dp,
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        draft = MovementFilterDraft()
                        errors = emptyMap()
                        validationAttempted = false
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF475569)),
                    modifier = Modifier.weight(0.32f).height(48.dp).testTag("btn_reset_advanced_filters")
                ) {
                    Text("Limpiar", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }

                val hasZeroMatches = matchingMovementsCount == 0
                val ctaText = when {
                    activeFilterCount == 0 -> "Ver todos ($matchingMovementsCount movimientos)"
                    matchingMovementsCount > 0 -> "Ver $matchingMovementsCount movimientos"
                    else -> "Ver 0 movimientos"
                }

                Button(
                    onClick = {
                        val draftToApply = if (!allowed) {
                            val prev = MovementFilterDraft.fromApplied(state.appliedFilters)
                            MovementFilterDraft(
                                fromDate = draft.fromDate,
                                toDate = draft.toDate,
                                movementType = draft.movementType,
                                comparePreviousMonth = draft.comparePreviousMonth,
                                accountIds = prev.accountIds,
                                categoryIds = prev.categoryIds,
                                cardIds = prev.cardIds,
                                merchantIds = prev.merchantIds,
                                financialStates = prev.financialStates,
                                syncStatuses = prev.syncStatuses,
                                minAmount = prev.minAmount,
                                maxAmount = prev.maxAmount,
                                currency = prev.currency
                            )
                        } else {
                            draft
                        }
                        val validation = onApply(draftToApply)
                        validationAttempted = true
                        errors = validation.errors
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasZeroMatches) Color(0xFFE2E8F0) else Color(0xFF0F766E),
                        contentColor = if (hasZeroMatches) Color(0xFF94A3B8) else Color.White,
                        disabledContainerColor = Color(0xFFE2E8F0),
                        disabledContentColor = Color(0xFF94A3B8),
                    ),
                    modifier = Modifier.weight(0.68f).height(48.dp).testTag("btn_apply_filters")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(ctaText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    // DATE RANGE PICKER DIALOG
    if (dates) {
        fun String.utc(): Long? = runCatching { LocalDate.parse(this).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        val picker = rememberDateRangePickerState(
            initialSelectedStartDateMillis = draft.fromDate.utc(),
            initialSelectedEndDateMillis = draft.toDate.utc()
        )
        MovementDateRangeDialog(
            onDismissRequest = { dates = false },
            confirmButton = {
                TextButton(onClick = {
                    fun Long?.date() = this?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }.orEmpty()
                    updateDraft(draft.copy(fromDate = picker.selectedStartDateMillis.date(), toDate = picker.selectedEndDateMillis.date()))
                    dates = false
                }) {
                    Text(
                        stringResource(R.string.history_filter_apply),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F766E)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { dates = false }) {
                    Text(
                        stringResource(R.string.history_filter_cancel),
                        color = Color(0xFF64748B)
                    )
                }
            }
        ) {
            DateRangePicker(
                state = picker,
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                title = {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                stringResource(R.string.history_filter_select_dates),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = {
                                picker.displayMode = if (picker.displayMode == DisplayMode.Picker) DisplayMode.Input else DisplayMode.Picker
                            }, modifier = Modifier.size(48.dp)) {
                                Icon(
                                    imageVector = if (picker.displayMode == DisplayMode.Picker) Icons.Rounded.Edit else Icons.Rounded.DateRange,
                                    contentDescription = stringResource(
                                        if (picker.displayMode == DisplayMode.Picker)
                                            R.string.history_filter_date_input_mode else R.string.history_filter_date_calendar_mode
                                    ),
                                    tint = Color(0xFF0F766E)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            FilterDateEndpoint(stringResource(R.string.history_filter_date_start), picker.selectedStartDateMillis, Modifier.weight(1f))
                            FilterDateEndpoint(stringResource(R.string.history_filter_date_end), picker.selectedEndDateMillis, Modifier.weight(1f))
                        }
                    }
                },
                headline = null,
                showModeToggle = false,
            )
        }
    }

    // MODAL UPSELL KIPU PRO
    if (showUpsell) {
        KipuProUpsellDialog(
            onDismiss = { showUpsell = false },
            onGetPro = {
                showUpsell = false
                onViewPlans()
            }
        )
    }
}

/** Dismissible active filter chip matching Stitch Calm Emerald mockups */
@Composable
private fun ActiveFilterDismissChip(
    text: String,
    bgColor: Color,
    borderColor: Color,
    textColor: Color,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.height(30.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, end = 6.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = textColor
            )
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Quitar filtro",
                    tint = textColor,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

/** Preset chip inside Periodo y Fecha card */
@Composable
private fun FilterPresetChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color(0xFF0F766E) else Color.White,
        border = BorderStroke(1.dp, if (selected) Color(0xFF0F766E) else Color(0xFFE2E8F0)),
        modifier = modifier.height(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (selected) Color.White else Color(0xFF1E293B)
            )
        }
    }
}

/** Segmented item for TIPO DE MOVIMIENTO */
@Composable
private fun MovementTypeSegment(
    title: String,
    isSelected: Boolean,
    activeColor: Color,
    bulletColor: Color? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (isSelected) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color.White,
            shadowElevation = 1.dp,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = modifier.height(34.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)
            ) {
                if (bulletColor != null) {
                    Box(Modifier.size(6.dp).background(bulletColor, CircleShape))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = activeColor
                )
            }
        }
    } else {
        Box(
            modifier = modifier
                .height(34.dp)
                .clickable(role = Role.RadioButton, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = Color(0xFF64748B)
            )
        }
    }
}

/** Locked row in Free state with padlock */
@Composable
private fun LockedAdvancedRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B)
                )
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** Controls for Unlocked / Pro Plan filters */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedFiltersControls(
    draft: MovementFilterDraft,
    onDraftChange: (MovementFilterDraft) -> Unit,
    errors: Map<String, FilterDraftError>,
    allowed: Boolean,
    accounts: List<MovementReferenceOption>,
    categories: List<MovementReferenceOption>,
    cards: List<MovementReferenceOption>,
    merchants: List<MovementReferenceOption>,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Cuentas
        ReferenceSelector(
            title = stringResource(R.string.history_accounts),
            displayTitle = "Cuenta",
            icon = Icons.Rounded.AccountBalanceWallet,
            emptySummary = "Todas las cuentas",
            options = accounts,
            selected = draft.accountIds,
            enabled = allowed,
            onChange = { onDraftChange(draft.copy(accountIds = it)) }
        )

        // Categorías
        ReferenceSelector(
            title = stringResource(R.string.history_categories),
            displayTitle = "Categoría",
            icon = Icons.Rounded.Category,
            emptySummary = "Todas las categorías",
            options = categories,
            selected = draft.categoryIds,
            enabled = allowed,
            onChange = { onDraftChange(draft.copy(categoryIds = it)) }
        )

        // Importe / Rango Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Payments,
                            contentDescription = null,
                            tint = Color(0xFF0F766E),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Importe / Rango",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    val hasCustomAmount = draft.minAmount.isNotBlank() || draft.maxAmount.isNotBlank()
                    Text(
                        text = if (hasCustomAmount) "S/ ${draft.minAmount.ifBlank { "0" }} – S/ ${draft.maxAmount.ifBlank { "∞" }}" else "Monto mín. y máx.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = if (hasCustomAmount) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (hasCustomAmount) Color(0xFF0F766E) else Color(0xFF64748B)
                    )
                }

                // Currency selector
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Moneda:", style = MaterialTheme.typography.labelMedium, color = Color(0xFF64748B))
                    listOf("PEN", "USD").forEach { currency ->
                        KipuFilterChip(
                            selected = draft.currency == currency,
                            enabled = allowed,
                            onClick = { onDraftChange(draft.copy(currency = currency)) },
                            label = { Text(currency) }
                        )
                    }
                }
                FilterFieldError("currency", errors["currency"])

                // Min and Max Amount Input Fields
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = draft.minAmount,
                        onValueChange = { onDraftChange(draft.copy(minAmount = it)) },
                        enabled = allowed,
                        label = { Text(stringResource(R.string.movements_filter_min_amount)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        visualTransformation = if (LocalBalanceMasked.current) PasswordVisualTransformation() else VisualTransformation.None,
                        supportingText = if (errors.containsKey("min")) { { FilterFieldError("min", errors["min"]) } } else null,
                        isError = errors.containsKey("min"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF0F766E),
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_filter_min_amount")
                            .privateAmount(LocalBalanceMasked.current, allowed, "Importe mínimo oculto") { onDraftChange(draft.copy(minAmount = it)) }
                    )

                    OutlinedTextField(
                        value = draft.maxAmount,
                        onValueChange = { onDraftChange(draft.copy(maxAmount = it)) },
                        enabled = allowed,
                        label = { Text(stringResource(R.string.movements_filter_max_amount)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        visualTransformation = if (LocalBalanceMasked.current) PasswordVisualTransformation() else VisualTransformation.None,
                        supportingText = if (errors.containsKey("max")) { { FilterFieldError("max", errors["max"]) } } else null,
                        isError = errors.containsKey("max"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF0F766E),
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_filter_max_amount")
                            .privateAmount(LocalBalanceMasked.current, allowed, "Importe máximo oculto") { onDraftChange(draft.copy(maxAmount = it)) }
                    )
                }
            }
        }

        // Comercios
        ReferenceSelector(
            title = stringResource(R.string.history_merchants),
            displayTitle = "Comercio",
            icon = Icons.Rounded.Storefront,
            emptySummary = "Por comercio o nota",
            options = merchants,
            selected = draft.merchantIds,
            enabled = allowed,
            onChange = { onDraftChange(draft.copy(merchantIds = it)) }
        )

        // Tarjetas
        if (cards.isNotEmpty()) {
            ReferenceSelector(
                title = stringResource(R.string.history_cards),
                displayTitle = "Tarjeta",
                icon = Icons.Rounded.CreditCard,
                emptySummary = "Todas las tarjetas",
                options = cards,
                selected = draft.cardIds,
                enabled = allowed,
                onChange = { onDraftChange(draft.copy(cardIds = it)) }
            )
        }

        // Estado financiero
        Text("Estado del movimiento", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MovementFinancialState.entries.forEach { value ->
                KipuFilterChip(
                    selected = value in draft.financialStates,
                    enabled = allowed,
                    onClick = { onDraftChange(draft.copy(financialStates = draft.financialStates.toggle(value))) },
                    label = { Text(value.uiLabel()) },
                    modifier = Modifier.testTag("chip_filter_${value.name.lowercase()}")
                )
            }
        }

        // Estado de sincronización
        Text(stringResource(R.string.history_sync_state), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MovementSyncStatus.entries.forEach { value ->
                KipuFilterChip(
                    selected = value in draft.syncStatuses,
                    enabled = allowed,
                    onClick = { onDraftChange(draft.copy(syncStatuses = draft.syncStatuses.toggle(value))) },
                    label = { Text(value.uiLabel()) }
                )
            }
        }
    }
}

/** Upsell Modal matching Figma / Stitch Calm Emerald mockup */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KipuProUpsellDialog(
    onDismiss: () -> Unit,
    onGetPro: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        modifier = Modifier.testTag("dialog_movement_filter_pro_upsell")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Close button on top-right
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = Color(0xFF64748B))
                }
            }

            // Top Badge Icon
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFDCFCE7),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.MilitaryTech,
                        contentDescription = null,
                        tint = Color(0xFF0F766E),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // KIPU PRO Pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFDCFCE7)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.Star, contentDescription = null, tint = Color(0xFF0F766E), modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "KIPU PRO",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF0F766E)
                    )
                }
            }

            // Title & Description
            Text(
                text = "Filtra sin límites con Kipu Pro",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center
            )
            Text(
                text = "Encuentra cualquier transacción filtrando por cuentas específicas, múltiples categorías, comercios frecuentes y rango de importes exactos.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF475569),
                textAlign = TextAlign.Center
            )

            // Benefits Container
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF0FDF4),
                border = BorderStroke(1.dp, Color(0xFFDCFCE7)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    UpsellBenefitItem("Filtro por cuentas bancarias y billeteras")
                    UpsellBenefitItem("Múltiples categorías y subcategorías simultáneas")
                    UpsellBenefitItem("Búsqueda por comercio y notas personalizadas")
                    UpsellBenefitItem("Rango de importes mínimo y máximo sin tope")
                }
            }

            // Pricing Option Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF0FDF4),
                border = BorderStroke(1.5.dp, Color(0xFF0F766E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF0F766E)
                        ) {
                            Text(
                                text = "AHORRA 40%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = true,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF0F766E))
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Plan Anual",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "Equivale a S/ 4.99 /mes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "S/ 59.90",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF0F766E)
                            )
                            Text(
                                text = "/año",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            }

            // CTA Button
            Button(
                onClick = onGetPro,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Obtener Kipu Pro",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
            }

            // Dismiss link
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Continuar con Plan Free",
                    color = Color(0xFF475569),
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun UpsellBenefitItem(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF0F766E),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = Color(0xFF1E293B)
        )
    }
}

@Composable
private fun FilterDateEndpoint(label: String, dateMillis: Long?, modifier: Modifier = Modifier) {
    val date = dateMillis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(date ?: stringResource(R.string.history_filter_date_unselected), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun FilterFieldError(field: String, error: FilterDraftError?) {
    if (error == null) return
    val message = when (field) {
        "from" -> R.string.history_filter_invalid_start_date
        "to" -> if (error == FilterDraftError.RANGE) R.string.history_filter_invalid_date_range else R.string.history_filter_invalid_end_date
        "max" -> if (error == FilterDraftError.RANGE) R.string.history_filter_invalid_amount_range else R.string.history_filter_invalid_amount
        "currency" -> R.string.history_filter_missing_currency
        else -> R.string.history_filter_invalid_amount
    }
    Text(
        text = stringResource(message),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.testTag("error_filter_$field")
    )
}

/** Material's date dialog requires 360 dp; bound only the compact variant to its window. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MovementDateRangeDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalConfiguration.current.screenWidthDp >= 360) {
        DatePickerDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            dismissButton = dismissButton,
            content = content
        )
    } else {
        Dialog(onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp),
                shape = DatePickerDefaults.shape,
                color = DatePickerDefaults.colors().containerColor,
                tonalElevation = DatePickerDefaults.TonalElevation
            ) {
                Column {
                    Box(Modifier.weight(1f, fill = false)) { Column(content = content) }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dismissButton()
                        confirmButton()
                    }
                }
            }
        }
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
