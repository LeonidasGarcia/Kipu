package com.kipu.app.feature.movements.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kipu.app.R
import com.kipu.app.feature.movements.domain.model.*
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.formatMinorUnits
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovementDetailSheet(
    item: TransactionItem,
    revisions: List<MovementRevisionAudit>,
    loading: Boolean,
    error: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onVoid: () -> Unit,
) {
    val tx = item.transaction
    val voided = tx.status == TransactionStatus.VOIDED
    val specialized = isSpecializedMovement(item)
    val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.forLanguageTag("es-PE")).withZone(ZoneId.systemDefault())
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("movement_detail_sheet")) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.history_detail), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(16.dp))
            Text(item.merchantName ?: item.categoryName ?: when (tx.type) { MovementType.EXPENSE -> "Gasto"; MovementType.INCOME -> "Ingreso"; MovementType.TRANSFER -> "Transferencia" }, style = MaterialTheme.typography.titleMedium)
            MoneyText(formatMinorUnits(tx.amountMinor), currencySymbol = if (tx.currency == "PEN") "S/" else if (tx.currency == "USD") "$" else tx.currency,
                style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 12.dp).testTag("detail_amount"))
            if (voided) Text(stringResource(R.string.history_detail_readonly), style = MaterialTheme.typography.bodyMedium)
            else Text(when(tx.status) { TransactionStatus.REVISED -> "Corregido"; TransactionStatus.FAILED -> "Fallo histórico"; else -> "Confirmado" }, style = MaterialTheme.typography.bodyMedium)
            Text(tx.syncStatus.uiLabel(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            DetailField(stringResource(R.string.history_accounts), listOfNotNull(item.sourceAccountAlias, item.destinationAccountAlias).joinToString(" → ").ifBlank { item.cardAlias ?: "Cuenta histórica" })
            item.categoryName?.let { DetailField(stringResource(R.string.history_categories), it) }
            DetailField(stringResource(R.string.history_date), dateFormat.format(Instant.ofEpochMilli(tx.occurredAt)))
            tx.note?.takeIf(String::isNotBlank)?.let { DetailField(stringResource(R.string.history_note), it) }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.history_revisions), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            when {
                loading -> CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp))
                error -> Text(stringResource(R.string.history_revisions_error), style = MaterialTheme.typography.bodyMedium)
                revisions.isEmpty() -> Text(stringResource(R.string.history_revisions_empty), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
                else -> revisions.forEach { revision ->
                    val action = when (revision.operation) { "VOID" -> "Anulación"; "REVISE" -> "Corrección"; else -> "Registro conservado" }
                    DetailField("Revisión ${revision.revision} · $action", dateFormat.format(Instant.ofEpochMilli(revision.createdAt)))
                    revision.reason?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (specialized && !voided) Text(stringResource(R.string.movement_void_specialized_warning), style = MaterialTheme.typography.bodyMedium)
            if (!voided && !specialized) {
                Button(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("detail_edit")) { Text(stringResource(R.string.movement_action_edit)) }
                OutlinedButton(onClick = onVoid, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("detail_void")) { Text(stringResource(R.string.movement_action_void)) }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.history_detail_close)) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable private fun DetailField(label: String, value: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
