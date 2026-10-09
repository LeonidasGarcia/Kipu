package com.kipu.app.feature.movements.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import com.kipu.app.ui.component.LocalBalanceMasked
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kipu.app.R
import com.kipu.app.feature.movements.domain.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.Alignment
import com.kipu.app.ui.component.KipuBottomSheet
import com.kipu.app.ui.component.KipuFilterChip

/** A sheet owns its draft. Closing it never writes applied filters. */
@Composable
fun MovementFiltersSheet(state: MovementHistoryUiState, onDismiss: () -> Unit,
    onApply: (MovementFilterDraft) -> FilterDraftValidation,
    onVerify: () -> Unit = {}, onViewPlans: () -> Unit = {}, onDismissRecovery: () -> Unit = {}) {
    key(state.ownerId) {
        MovementFiltersSheetContent(state, onDismiss, onApply, onVerify, onViewPlans, onDismissRecovery)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MovementFiltersSheetContent(state: MovementHistoryUiState, onDismiss: () -> Unit,
    onApply: (MovementFilterDraft) -> FilterDraftValidation,
    onVerify: () -> Unit = {}, onViewPlans: () -> Unit = {}, onDismissRecovery: () -> Unit = {}) {
    var draft by rememberSaveable(state.ownerId, stateSaver = MovementFilterDraftSaver) {
        mutableStateOf(MovementFilterDraft.fromApplied(state.appliedFilters))
    }
    var errors by remember { mutableStateOf(emptyMap<String, FilterDraftError>()) }
    var validationAttempted by remember { mutableStateOf(false) }
    var dates by remember { mutableStateOf(false) }
    var showPremiumOptions by rememberSaveable { mutableStateOf(false) }
    val allowed = state.accessStatus == MovementHistoryAccessDecision.Allowed
    val today = remember { LocalDate.now() }
    val weekStart = today.minusDays(6)
    val monthStart = today.withDayOfMonth(1)
    val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
    val yearStart = today.withDayOfYear(1)
    val yearEnd = today.withMonth(12).withDayOfMonth(31)
    var showOtherPeriods by rememberSaveable(state.ownerId) {
        mutableStateOf(
            (draft.fromDate == weekStart.toString() && draft.toDate == today.toString()) ||
                (draft.fromDate == yearStart.toString() && draft.toDate == yearEnd.toString()),
        )
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun updateDraft(updated: MovementFilterDraft) {
        draft = updated
        if (validationAttempted) errors = updated.validate().errors
    }

    val rows = state.allTransactions
    val accounts = remember(rows) { rows.flatMap { listOfNotNull(
        it.transaction.sourceAccountId?.let { id -> MovementReferenceOption(id, it.sourceAccountAlias ?: "Cuenta histórica") },
        it.transaction.destinationAccountId?.let { id -> MovementReferenceOption(id, it.destinationAccountAlias ?: "Cuenta histórica") }) }.distinctBy { it.id } }
    val categories = remember(rows) { rows.mapNotNull { row -> row.transaction.categoryId?.let { MovementReferenceOption(it, row.categoryName ?: "Categoría histórica") } }.distinctBy { it.id } }
    val cards = remember(rows) { rows.mapNotNull { row -> row.transaction.cardId?.let { MovementReferenceOption(it, row.cardAlias ?: "Tarjeta histórica") } }.distinctBy { it.id } }
    val merchants = remember(rows) { rows.mapNotNull { row -> row.transaction.merchantId?.let { MovementReferenceOption(it, row.merchantName ?: "Comercio histórico") } }.distinctBy { it.id } }

    KipuBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("panel_advanced_filters"),
        header = {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Filtros", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp).testTag("btn_close_filters")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.history_filter_cancel))
                    }
                }
                Text(
                    "Personaliza los movimientos que ves",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                )
            }
        },
        footer = {
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { draft = MovementFilterDraft(); errors = emptyMap(); validationAttempted = false },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("btn_reset_advanced_filters"),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Text("Limpiar")
                    }
                    Button(
                        onClick = {
                            val draftToApply = if (!allowed) {
                                val prev = MovementFilterDraft.fromApplied(state.appliedFilters)
                                MovementFilterDraft(
                                    fromDate = draft.fromDate,
                                    toDate = draft.toDate,
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
                        modifier = Modifier.weight(1.5f).heightIn(min = 48.dp).testTag("btn_apply_filters")
                    ) {
                        val appliedText = stringResource(R.string.history_filter_apply)
                        val pausedCount = if (!allowed) {
                            state.appliedFilters.accountIds.size + state.appliedFilters.categoryIds.size +
                                state.appliedFilters.cardIds.size + state.appliedFilters.merchantIds.size +
                                (if (state.appliedFilters.minAmountMinor != null || state.appliedFilters.maxAmountMinor != null) 1 else 0) +
                                state.appliedFilters.financialStates.size + state.appliedFilters.syncStatuses.size
                        } else 0
                        if (pausedCount > 0) {
                            Text("$appliedText ($pausedCount en pausa)")
                        } else {
                            Text(appliedText)
                        }
                }
            }
            }
        },
    ) {


            // Scrollable body with weight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Keep the four frequent choices visible; infrequent presets stay available
                // behind one compact disclosure, preserving the existing date functionality.
                val allDatesSelected = draft.fromDate.isBlank() && draft.toDate.isBlank()
                val todaySelected = draft.fromDate == today.toString() && draft.toDate == today.toString()
                val weekSelected = draft.fromDate == weekStart.toString() && draft.toDate == today.toString()
                val monthSelected = draft.fromDate == monthStart.toString() && draft.toDate == monthEnd.toString()
                val yearSelected = draft.fromDate == yearStart.toString() && draft.toDate == yearEnd.toString()
                val customSelected = !allDatesSelected && !todaySelected && !weekSelected && !monthSelected && !yearSelected

                Text("Periodo", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    KipuFilterChip(
                        selected = allDatesSelected,
                        onClick = {
                            updateDraft(draft.copy(fromDate = "", toDate = ""))
                            showOtherPeriods = false
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth().semantics { contentDescription = "Todas las fechas" },
                        label = { Text("Todas", maxLines = 1) },
                    )
                    KipuFilterChip(
                        selected = todaySelected,
                        onClick = {
                            updateDraft(draft.copy(fromDate = today.toString(), toDate = today.toString()))
                            showOtherPeriods = false
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        label = { Text(stringResource(R.string.history_today), maxLines = 1) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    KipuFilterChip(
                        selected = monthSelected,
                        onClick = {
                            updateDraft(draft.copy(fromDate = monthStart.toString(), toDate = monthEnd.toString()))
                            showOtherPeriods = false
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        label = { Text("Este mes", maxLines = 1) },
                    )
                    KipuFilterChip(
                        selected = customSelected,
                        onClick = {
                            showOtherPeriods = false
                            dates = true
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth().testTag("chip_filter_custom_dates"),
                        label = { Text("Personalizado", maxLines = 1) },
                    )
                }
                if (showOtherPeriods) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        KipuFilterChip(
                            selected = weekSelected,
                            onClick = {
                                updateDraft(draft.copy(fromDate = weekStart.toString(), toDate = today.toString()))
                                showOtherPeriods = true
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            label = { Text("7 días", maxLines = 1) },
                        )
                        KipuFilterChip(
                            selected = yearSelected,
                            onClick = {
                                updateDraft(draft.copy(fromDate = yearStart.toString(), toDate = yearEnd.toString()))
                                showOtherPeriods = true
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            label = { Text("Año", maxLines = 1) },
                        )
                    }
                    if (!weekSelected && !yearSelected) {
                        TextButton(
                            onClick = { showOtherPeriods = false },
                            modifier = Modifier.heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                        ) {
                            Text("Menos periodos")
                        }
                    }
                } else {
                    TextButton(
                        onClick = { showOtherPeriods = true },
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                    ) {
                        Text("Más periodos")
                    }
                }
                val from = runCatching { LocalDate.parse(draft.fromDate) }.getOrNull()
                val to = runCatching { LocalDate.parse(draft.toDate) }.getOrNull()
                val dateFormat = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val dayCount = if (from != null && to != null && !to.isBefore(from)) {
                        java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1
                    } else null
                    OutlinedButton(
                        onClick = { dates = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("btn_filter_dates"),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(
                                text = if (from == null && to == null) stringResource(R.string.history_custom_dates)
                                else "${from?.format(dateFormat) ?: "…"} — ${to?.format(dateFormat) ?: "…"}",
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            dayCount?.let { Text("${it}d", style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                    FilterFieldError("from", errors["from"])
                    FilterFieldError("to", errors["to"])
                }

                // Advanced / Premium sections
                if (!allowed) {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("card_advanced_filters_info"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(stringResource(R.string.history_filter_advanced), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.history_filter_premium_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!state.fallbackUsed && state.recovery == HistoryAccessRecovery.IDLE &&
                                state.accessStatus !is MovementHistoryAccessDecision.RevalidationRequired) {
                                TextButton(onClick = onViewPlans, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ver Premium") }
                            }
                        }
                    }
                    if (state.fallbackUsed || state.recovery != HistoryAccessRecovery.IDLE ||
                        state.accessStatus is MovementHistoryAccessDecision.RevalidationRequired) {
                        MovementAccessCard(state, onViewPlans, onVerify, onDismissRecovery)
                    }
                    OutlinedButton(
                        onClick = { showPremiumOptions = !showPremiumOptions },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(if (showPremiumOptions) "Ocultar filtros avanzados" else "Mostrar filtros avanzados")
                    }
                    if (showPremiumOptions) {
                        AdvancedFiltersControls(
                            draft = draft,
                            onDraftChange = ::updateDraft,
                            errors = errors,
                            allowed = false,
                            accounts = accounts,
                            categories = categories,
                            cards = cards,
                            merchants = merchants
                        )
                    }
                } else {
                    Text(stringResource(R.string.history_filter_advanced), style = MaterialTheme.typography.titleSmall)
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

                Spacer(Modifier.height(8.dp))
            }
    }
    if (dates) {
        fun String.utc(): Long? = runCatching { LocalDate.parse(this).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        val picker = rememberDateRangePickerState(initialSelectedStartDateMillis = draft.fromDate.utc(), initialSelectedEndDateMillis = draft.toDate.utc())
        MovementDateRangeDialog(onDismissRequest = { dates = false }, confirmButton = {
            TextButton(onClick = {
                fun Long?.date() = this?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }.orEmpty()
                updateDraft(draft.copy(fromDate = picker.selectedStartDateMillis.date(), toDate = picker.selectedEndDateMillis.date())); dates = false
            }) { Text(stringResource(R.string.history_filter_apply)) }
        }, dismissButton = { TextButton(onClick = { dates = false }) { Text(stringResource(R.string.history_filter_cancel)) } }) {
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
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                stringResource(R.string.history_filter_select_dates),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = {
                                picker.displayMode = if (picker.displayMode == DisplayMode.Picker) DisplayMode.Input else DisplayMode.Picker
                            }, modifier = Modifier.size(48.dp)) {
                                Icon(
                                    imageVector = if (picker.displayMode == DisplayMode.Picker) Icons.Default.Edit else Icons.Default.DateRange,
                                    contentDescription = stringResource(if (picker.displayMode == DisplayMode.Picker)
                                        R.string.history_filter_date_input_mode else R.string.history_filter_date_calendar_mode),
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
}

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
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ReferenceSelector(stringResource(R.string.history_accounts), accounts, draft.accountIds, allowed) { onDraftChange(draft.copy(accountIds = it)) }
        ReferenceSelector(stringResource(R.string.history_categories), categories, draft.categoryIds, allowed) { onDraftChange(draft.copy(categoryIds = it)) }
        ReferenceSelector(stringResource(R.string.history_cards), cards, draft.cardIds, allowed) { onDraftChange(draft.copy(cardIds = it)) }
        ReferenceSelector(stringResource(R.string.history_merchants), merchants, draft.merchantIds, allowed) { onDraftChange(draft.copy(merchantIds = it)) }
        Text(stringResource(R.string.history_amount), style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.history_currency), style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("PEN", "USD").forEach { currency -> KipuFilterChip(selected = draft.currency == currency, enabled = allowed,
                    onClick = { onDraftChange(draft.copy(currency = currency)) }, label = { Text(currency) }) }
            }
            FilterFieldError("currency", errors["currency"])
        }
        OutlinedTextField(draft.minAmount, { onDraftChange(draft.copy(minAmount = it)) }, enabled = allowed,
            label = { Text(stringResource(R.string.movements_filter_min_amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            visualTransformation = if (LocalBalanceMasked.current) PasswordVisualTransformation() else VisualTransformation.None,
            supportingText = if (errors.containsKey("min")) { { FilterFieldError("min", errors["min"]) } } else null,
            isError = errors.containsKey("min"), modifier = Modifier.fillMaxWidth().testTag("input_filter_min_amount").privateAmount(LocalBalanceMasked.current, allowed, "Importe mínimo oculto") { onDraftChange(draft.copy(minAmount = it)) })
        OutlinedTextField(draft.maxAmount, { onDraftChange(draft.copy(maxAmount = it)) }, enabled = allowed,
            label = { Text(stringResource(R.string.movements_filter_max_amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            visualTransformation = if (LocalBalanceMasked.current) PasswordVisualTransformation() else VisualTransformation.None,
            supportingText = if (errors.containsKey("max")) { { FilterFieldError("max", errors["max"]) } } else null,
            isError = errors.containsKey("max"), modifier = Modifier.fillMaxWidth().testTag("input_filter_max_amount").privateAmount(LocalBalanceMasked.current, allowed, "Importe máximo oculto") { onDraftChange(draft.copy(maxAmount = it)) })
        Text("Estado del movimiento", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MovementFinancialState.entries.forEach { value -> KipuFilterChip(selected = value in draft.financialStates,
                enabled = allowed, onClick = { onDraftChange(draft.copy(financialStates = draft.financialStates.toggle(value))) },
                label = { Text(value.uiLabel()) }, modifier = Modifier.testTag("chip_filter_${value.name.lowercase()}")) }
        }
        Text(stringResource(R.string.history_sync_state), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MovementSyncStatus.entries.forEach { value -> KipuFilterChip(selected = value in draft.syncStatuses, enabled = allowed,
                onClick = { onDraftChange(draft.copy(syncStatuses = draft.syncStatuses.toggle(value))) }, label = { Text(value.uiLabel()) }) }
        }
    }
}

@Composable
private fun FilterDateEndpoint(label: String, dateMillis: Long?, modifier: Modifier = Modifier) {
    val date = dateMillis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
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
    Text(stringResource(message), color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("error_filter_$field"))
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
        DatePickerDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton,
            dismissButton = dismissButton, content = content)
    } else {
        Dialog(onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp),
                shape = DatePickerDefaults.shape,
                color = DatePickerDefaults.colors().containerColor,
                tonalElevation = DatePickerDefaults.TonalElevation) {
                Column {
                    Box(Modifier.weight(1f, fill = false)) { Column(content = content) }
                    FlowRow(modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        dismissButton()
                        confirmButton()
                    }
                }
            }
        }
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
