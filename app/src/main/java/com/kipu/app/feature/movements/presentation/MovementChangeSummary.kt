package com.kipu.app.feature.movements.presentation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kipu.app.R
import com.kipu.app.core.finance.domain.MoneyInputParser
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.KipuMotionTokens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MovementChangeSummary(state: MovementEditorUiState, reducedMotion: Boolean = rememberReducedMotionEnabled()) {
    AnimatedVisibility(state.hasUnsavedChanges,
        enter = if (reducedMotion) EnterTransition.None else fadeIn(tween(KipuMotionTokens.FastMillis)) + expandVertically(tween(KipuMotionTokens.FastMillis)),
        exit = if (reducedMotion) ExitTransition.None else fadeOut(tween(KipuMotionTokens.QuickMillis)) + shrinkVertically(tween(KipuMotionTokens.QuickMillis))) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("editor_change_summary")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.history_changes), style = MaterialTheme.typography.titleSmall)
                if (state.isOnlyNoteChanged) Text(stringResource(R.string.movement_edit_summary_only_note),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("summary_only_note"))
                val amount = MoneyInputParser.parseMinorUnits(state.amountText)
                if (amount != null && amount != state.initialAmountMinor) {
                    Column(Modifier.testTag("summary_amount"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.history_amount), style = MaterialTheme.typography.labelLarge)
                        Text(stringResource(R.string.history_before), style = MaterialTheme.typography.labelMedium)
                        MoneyText(formatMinorUnits(state.initialAmountMinor), currencySymbol = state.currency)
                        Text(stringResource(R.string.history_after), style = MaterialTheme.typography.labelMedium)
                        MoneyText(formatMinorUnits(amount), currencySymbol = state.currency)
                    }
                }
                fun account(id: String?): String = if (id == null) "Sin dato" else state.availableAccounts.firstOrNull { it.id.value == id }?.alias ?: "Cuenta histórica"
                if (state.initialSourceAccountId != state.selectedSourceAccountId)
                    ChangeField("Cuenta de origen", account(state.initialSourceAccountId), account(state.selectedSourceAccountId))
                if (state.initialDestinationAccountId != state.selectedDestinationAccountId)
                    ChangeField("Cuenta de destino", account(state.initialDestinationAccountId), account(state.selectedDestinationAccountId))
                if (state.initialCategoryId != state.selectedCategoryId)
                    ChangeField("Categoría", state.initialCategoryName.orEmpty(), state.selectedCategoryName.orEmpty())
                if (state.initialMerchantName.orEmpty().trim() != state.merchantName.trim())
                    ChangeField("Comercio", state.initialMerchantName.orEmpty(), state.merchantName.trim())
                if (state.initialOccurredAt != state.occurredAt) {
                    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault())
                    ChangeField(stringResource(R.string.history_date), formatter.format(Instant.ofEpochMilli(state.initialOccurredAt)),
                        formatter.format(Instant.ofEpochMilli(state.occurredAt)), Modifier.testTag("summary_date"))
                }
                if (state.initialNote.orEmpty().trim() != state.note.trim())
                    ChangeField(stringResource(R.string.history_note), state.initialNote.orEmpty(), state.note.trim())
            }
        }
    }
}

@Composable
private fun ChangeField(label: String, before: String, after: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text("${stringResource(R.string.history_before)}: ${before.ifBlank { stringResource(R.string.history_none) }}", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${stringResource(R.string.history_after)}: ${after.ifBlank { stringResource(R.string.history_none) }}", style = MaterialTheme.typography.bodyMedium)
    }
}
