package com.kipu.app.feature.movements.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.kipu.app.ui.component.LocalBalanceMasked
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kipu.app.R
import com.kipu.app.feature.movements.domain.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
    var dates by remember { mutableStateOf(false) }
    var showPremiumOptions by rememberSaveable { mutableStateOf(false) }
    val allowed = state.accessStatus == MovementHistoryAccessDecision.Allowed
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val rows = state.allTransactions
    val accounts = rows.flatMap { listOfNotNull(
        it.transaction.sourceAccountId?.let { id -> MovementReferenceOption(id, it.sourceAccountAlias ?: "Cuenta histórica") },
        it.transaction.destinationAccountId?.let { id -> MovementReferenceOption(id, it.destinationAccountAlias ?: "Cuenta histórica") }) }.distinctBy { it.id }
    val categories = rows.mapNotNull { row -> row.transaction.categoryId?.let { MovementReferenceOption(it, row.categoryName ?: "Categoría histórica") } }.distinctBy { it.id }
    val cards = rows.mapNotNull { row -> row.transaction.cardId?.let { MovementReferenceOption(it, row.cardAlias ?: "Tarjeta histórica") } }.distinctBy { it.id }
    val merchants = rows.mapNotNull { row -> row.transaction.merchantId?.let { MovementReferenceOption(it, row.merchantName ?: "Comercio histórico") } }.distinctBy { it.id }

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
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                )
            }
        }
    ) {

            // Scrollable body with weight
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Free basics first: Dates
                Text("Periodo y Fecha", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KipuFilterChip(selected = draft.fromDate.isBlank() && draft.toDate.isBlank(), onClick = {
                        draft = draft.copy(fromDate = "", toDate = "")
                    }, label = { Text(stringResource(R.string.history_all_dates)) })
                    KipuFilterChip(selected = draft.fromDate == LocalDate.now().toString() && draft.toDate == draft.fromDate, onClick = {
                        draft = draft.copy(fromDate = LocalDate.now().toString(), toDate = LocalDate.now().toString())
                    }, label = { Text(stringResource(R.string.history_today)) })
                    val today = LocalDate.now()
                    listOf(
                        Triple("7 días", today.minusDays(6), today),
                        Triple("Este mes", today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth())),
                        Triple("Año", today.withDayOfYear(1), today.withMonth(12).withDayOfMonth(31)),
                    ).forEach { (label, from, to) ->
                        KipuFilterChip(selected = draft.fromDate == from.toString() && draft.toDate == to.toString(),
                            onClick = { draft = draft.copy(fromDate = from.toString(), toDate = to.toString()) },
                            label = { Text(label) })
                    }
                }
                val from = runCatching { LocalDate.parse(draft.fromDate) }.getOrNull()
                val to = runCatching { LocalDate.parse(draft.toDate) }.getOrNull()
                val dateFormat = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
                OutlinedButton(onClick = { dates = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (from == null && to == null) stringResource(R.string.history_custom_dates)
                        else "${from?.format(dateFormat) ?: "…"} — ${to?.format(dateFormat) ?: "…"}")
                }
                if (from != null && to != null && !to.isBefore(from)) {
                    Text("${java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1} días",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Advanced / Premium sections
                if (!allowed) {
                    Text("Filtros avanzados · Premium", style = MaterialTheme.typography.titleSmall)
                    Text("Cuenta · Tarjeta · Categoría · Importe · Comercio", style = MaterialTheme.typography.bodyMedium)
                    if (state.fallbackUsed || state.recovery != HistoryAccessRecovery.IDLE ||
                        state.accessStatus is MovementHistoryAccessDecision.RevalidationRequired) {
                        MovementAccessCard(state, onViewPlans, onVerify, onDismissRecovery)
                    } else {
                        TextButton(onClick = onViewPlans, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ver Premium") }
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
                            onDraftChange = { draft = it },
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
                        onDraftChange = { draft = it },
                        errors = errors,
                        allowed = true,
                        accounts = accounts,
                        categories = categories,
                        cards = cards,
                        merchants = merchants
                    )
                }

                // Errors
                errors.values.distinct().forEach { error ->
                    Text(stringResource(when (error) {
                        FilterDraftError.AMOUNT -> R.string.history_filter_invalid_amount
                        FilterDraftError.CURRENCY -> R.string.history_filter_missing_currency
                        FilterDraftError.DATE -> R.string.history_filter_invalid_date
                        FilterDraftError.RANGE -> R.string.history_filter_invalid_range
                    }), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(8.dp))
            }

            // Sticky footer outside scroll
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TextButton(
                        onClick = { draft = MovementFilterDraft(); errors = emptyMap() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("btn_reset_advanced_filters")
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
                            errors = validation.errors
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("btn_apply_filters")
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
    }
    if (dates) {
        fun String.utc(): Long? = runCatching { LocalDate.parse(this).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        val picker = rememberDateRangePickerState(initialSelectedStartDateMillis = draft.fromDate.utc(), initialSelectedEndDateMillis = draft.toDate.utc())
        DatePickerDialog(onDismissRequest = { dates = false }, confirmButton = {
            TextButton(onClick = {
                fun Long?.date() = this?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }.orEmpty()
                draft = draft.copy(fromDate = picker.selectedStartDateMillis.date(), toDate = picker.selectedEndDateMillis.date()); dates = false
            }) { Text(stringResource(R.string.history_filter_apply)) }
        }, dismissButton = { TextButton(onClick = { dates = false }) { Text(stringResource(R.string.history_filter_cancel)) } }) {
            DateRangePicker(state = picker, modifier = Modifier.heightIn(max = 500.dp))
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
    ReferenceSelector(stringResource(R.string.history_accounts), accounts, draft.accountIds, allowed) { onDraftChange(draft.copy(accountIds = it)) }
    ReferenceSelector(stringResource(R.string.history_categories), categories, draft.categoryIds, allowed) { onDraftChange(draft.copy(categoryIds = it)) }
    ReferenceSelector(stringResource(R.string.history_cards), cards, draft.cardIds, allowed) { onDraftChange(draft.copy(cardIds = it)) }
    ReferenceSelector(stringResource(R.string.history_merchants), merchants, draft.merchantIds, allowed) { onDraftChange(draft.copy(merchantIds = it)) }
    Text(stringResource(R.string.history_amount), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("PEN", "USD").forEach { currency -> KipuFilterChip(selected = draft.currency == currency, enabled = allowed,
            onClick = { onDraftChange(draft.copy(currency = currency)) }, label = { Text(currency) }) }
    }
    OutlinedTextField(draft.minAmount, { onDraftChange(draft.copy(minAmount = it)) }, enabled = allowed,
        label = { Text(stringResource(R.string.movements_filter_min_amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        visualTransformation = if (LocalBalanceMasked.current) PasswordVisualTransformation() else VisualTransformation.None,
        isError = errors.containsKey("min"), modifier = Modifier.fillMaxWidth().testTag("input_filter_min_amount").privateAmount(LocalBalanceMasked.current, allowed, "Importe mínimo oculto") { onDraftChange(draft.copy(minAmount = it)) })
    OutlinedTextField(draft.maxAmount, { onDraftChange(draft.copy(maxAmount = it)) }, enabled = allowed,
        label = { Text(stringResource(R.string.movements_filter_max_amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        visualTransformation = if (LocalBalanceMasked.current) PasswordVisualTransformation() else VisualTransformation.None,
        isError = errors.containsKey("max"), modifier = Modifier.fillMaxWidth().testTag("input_filter_max_amount").privateAmount(LocalBalanceMasked.current, allowed, "Importe máximo oculto") { onDraftChange(draft.copy(maxAmount = it)) })
    Text("Estado del movimiento", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MovementFinancialState.entries.forEach { value -> KipuFilterChip(selected = value in draft.financialStates,
            enabled = allowed, onClick = { onDraftChange(draft.copy(financialStates = draft.financialStates.toggle(value))) },
            label = { Text(value.uiLabel()) }, modifier = Modifier.testTag("chip_filter_${value.name.lowercase()}")) }
    }
    Text(stringResource(R.string.history_sync_state), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MovementSyncStatus.entries.forEach { value -> KipuFilterChip(selected = value in draft.syncStatuses, enabled = allowed,
            onClick = { onDraftChange(draft.copy(syncStatuses = draft.syncStatuses.toggle(value))) }, label = { Text(value.uiLabel()) }) }
    }
}

@Composable
private fun ReferenceSelector(title: String, options: List<MovementReferenceOption>, selected: Set<String>, enabled: Boolean, onChange: (Set<String>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text("$title · ${selected.size}")
    }
    LaunchedEffect(enabled) { if (!enabled) open = false }
    if (open && enabled) AlertDialog(onDismissRequest = { open = false }, title = { Text(title) }, text = {
        Column {
            OutlinedTextField(search, { search = it }, label = { Text(stringResource(R.string.history_filter_search)) }, modifier = Modifier.fillMaxWidth())
            Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                val visible = options.filter { it.label.contains(search, ignoreCase = true) }.sortedBy { it.label }
                if (visible.isEmpty()) Text(stringResource(R.string.history_filter_none), modifier = Modifier.padding(vertical = 12.dp))
                visible.forEach { option -> Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(value = option.id in selected, role = Role.Checkbox, onValueChange = { onChange(selected.toggle(option.id)) }), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = option.id in selected, onCheckedChange = null)
                    Text(option.label, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
                } }
            }
        }
    }, confirmButton = { TextButton(onClick = { open = false }) { Text("Listo") } })
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
