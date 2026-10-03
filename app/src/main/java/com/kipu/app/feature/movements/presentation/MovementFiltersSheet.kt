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
    val allowed = state.accessStatus == MovementHistoryAccessDecision.Allowed
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("panel_advanced_filters")) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.history_filter_apply), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.history_filter_draft_notice), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.history_filter_basic), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = draft.fromDate.isBlank() && draft.toDate.isBlank(), onClick = {
                    draft = draft.copy(fromDate = "", toDate = "")
                }, label = { Text(stringResource(R.string.history_all_dates)) })
                FilterChip(selected = draft.fromDate == LocalDate.now().toString() && draft.toDate == draft.fromDate, onClick = {
                    draft = draft.copy(fromDate = LocalDate.now().toString(), toDate = LocalDate.now().toString())
                }, label = { Text(stringResource(R.string.history_today)) })
                FilterChip(selected = draft.fromDate == LocalDate.now().withDayOfMonth(1).toString() && draft.toDate == LocalDate.now().toString(), onClick = {
                    draft = draft.copy(fromDate = LocalDate.now().withDayOfMonth(1).toString(), toDate = LocalDate.now().toString())
                }, label = { Text(stringResource(R.string.history_month)) })
            }
            OutlinedButton(onClick = { dates = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (draft.fromDate.isBlank() && draft.toDate.isBlank()) stringResource(R.string.history_custom_dates)
                    else "${draft.fromDate.ifBlank { "…" }} — ${draft.toDate.ifBlank { "…" }}")
            }
            Text(stringResource(R.string.history_filter_advanced), style = MaterialTheme.typography.titleSmall)
            if (!allowed) Text(stringResource(R.string.history_filter_parked), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            MovementAccessCard(state, onViewPlans, onVerify, onDismissRecovery)
            val rows = state.allTransactions
            val accounts = rows.flatMap { listOfNotNull(
                it.transaction.sourceAccountId?.let { id -> MovementReferenceOption(id, it.sourceAccountAlias ?: "Cuenta histórica") },
                it.transaction.destinationAccountId?.let { id -> MovementReferenceOption(id, it.destinationAccountAlias ?: "Cuenta histórica") }) }.distinctBy { it.id }
            val categories = rows.mapNotNull { row -> row.transaction.categoryId?.let { MovementReferenceOption(it, row.categoryName ?: "Categoría histórica") } }.distinctBy { it.id }
            val cards = rows.mapNotNull { row -> row.transaction.cardId?.let { MovementReferenceOption(it, row.cardAlias ?: "Tarjeta histórica") } }.distinctBy { it.id }
            val merchants = rows.mapNotNull { row -> row.transaction.merchantId?.let { MovementReferenceOption(it, row.merchantName ?: "Comercio histórico") } }.distinctBy { it.id }
            ReferenceSelector(stringResource(R.string.history_accounts), accounts, draft.accountIds, allowed) { draft = draft.copy(accountIds = it) }
            ReferenceSelector(stringResource(R.string.history_categories), categories, draft.categoryIds, allowed) { draft = draft.copy(categoryIds = it) }
            ReferenceSelector(stringResource(R.string.history_cards), cards, draft.cardIds, allowed) { draft = draft.copy(cardIds = it) }
            ReferenceSelector(stringResource(R.string.history_merchants), merchants, draft.merchantIds, allowed) { draft = draft.copy(merchantIds = it) }
            Text(stringResource(R.string.history_amount), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("PEN", "USD").forEach { currency -> FilterChip(selected = draft.currency == currency, enabled = allowed,
                    onClick = { draft = draft.copy(currency = currency) }, label = { Text(currency) }) }
            }
            OutlinedTextField(draft.minAmount, { draft = draft.copy(minAmount = it) }, enabled = allowed,
                label = { Text(stringResource(R.string.movements_filter_min_amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                visualTransformation = if (LocalBalanceMasked.current) PasswordVisualTransformation() else VisualTransformation.None,
                isError = errors.containsKey("min"), modifier = Modifier.fillMaxWidth().testTag("input_filter_min_amount").privateAmount(LocalBalanceMasked.current, allowed, "Importe mínimo oculto") { draft = draft.copy(minAmount = it) })
            OutlinedTextField(draft.maxAmount, { draft = draft.copy(maxAmount = it) }, enabled = allowed,
                label = { Text(stringResource(R.string.movements_filter_max_amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                visualTransformation = if (LocalBalanceMasked.current) PasswordVisualTransformation() else VisualTransformation.None,
                isError = errors.containsKey("max"), modifier = Modifier.fillMaxWidth().testTag("input_filter_max_amount").privateAmount(LocalBalanceMasked.current, allowed, "Importe máximo oculto") { draft = draft.copy(maxAmount = it) })
            Text("Estado del movimiento", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MovementFinancialState.entries.forEach { value -> FilterChip(selected = value in draft.financialStates,
                    enabled = allowed, onClick = { draft = draft.copy(financialStates = draft.financialStates.toggle(value)) },
                    label = { Text(value.uiLabel()) }, modifier = Modifier.testTag("chip_filter_${value.name.lowercase()}")) }
            }
            Text(stringResource(R.string.history_sync_state), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MovementSyncStatus.entries.forEach { value -> FilterChip(selected = value in draft.syncStatuses, enabled = allowed,
                    onClick = { draft = draft.copy(syncStatuses = draft.syncStatuses.toggle(value)) }, label = { Text(value.uiLabel()) }) }
            }
            errors.values.distinct().forEach { error -> Text(stringResource(when (error) {
                FilterDraftError.AMOUNT -> R.string.history_filter_invalid_amount
                FilterDraftError.CURRENCY -> R.string.history_filter_missing_currency
                FilterDraftError.DATE -> R.string.history_filter_invalid_date
                FilterDraftError.RANGE -> R.string.history_filter_invalid_range
            }), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            TextButton(onClick = { draft = MovementFilterDraft(); errors = emptyMap() }, modifier = Modifier.heightIn(min = 48.dp).testTag("btn_reset_advanced_filters")) {
                Text(stringResource(R.string.movements_filter_reset))
            }
            Button(onClick = { errors = onApply(draft).errors }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("btn_apply_filters")) {
                Text(stringResource(R.string.history_filter_apply))
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.history_filter_cancel)) }
            Spacer(Modifier.height(16.dp))
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
