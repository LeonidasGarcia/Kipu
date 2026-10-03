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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.Alignment
import com.kipu.app.ui.component.KipuBottomSheet

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
    val esPeLocale = Locale.forLanguageTag("es-PE")
    val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", esPeLocale).withZone(ZoneId.systemDefault())
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val amountPrefix = when (tx.type) {
        MovementType.EXPENSE -> "-"
        MovementType.INCOME -> "+"
        MovementType.TRANSFER -> ""
    }
    val currencySymbol = if (tx.currency == "PEN") "S/" else if (tx.currency == "USD") "$" else tx.currency
    val amountColor = if (voided) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else when (tx.type) {
        MovementType.INCOME -> MaterialTheme.colorScheme.primary
        MovementType.EXPENSE -> MaterialTheme.colorScheme.onSurface
        MovementType.TRANSFER -> MaterialTheme.colorScheme.primary
    }

    KipuBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("movement_detail_sheet"),
        header = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.history_detail),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).semantics { heading() }
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp).testTag("btn_close_movement_detail")
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.history_detail_close))
                }
            }
        }
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            Text(item.merchantName ?: item.categoryName ?: when (tx.type) { MovementType.EXPENSE -> "Gasto"; MovementType.INCOME -> "Ingreso"; MovementType.TRANSFER -> "Transferencia" }, style = MaterialTheme.typography.titleMedium)
            MoneyText(
                amount = "$amountPrefix${formatMinorUnits(tx.amountMinor)}",
                currencySymbol = currencySymbol,
                color = amountColor,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 12.dp).testTag("detail_amount"),
            )
            if (voided) {
                Text(
                    text = "Movimiento anulado · Sin efecto en saldos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Text(
                    text = when(tx.status) {
                        TransactionStatus.REVISED -> "Corregido"
                        TransactionStatus.FAILED -> "Fallo histórico"
                        else -> "Confirmado"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(tx.syncStatus.uiLabel(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            if (tx.type == MovementType.TRANSFER) {
                DetailField("Cuenta de origen", item.sourceAccountAlias ?: "Origen")
                DetailField("Cuenta de destino", item.destinationAccountAlias ?: "Destino")
            } else if (item.cardAlias != null) {
                DetailField("Tarjeta", item.cardAlias)
            } else {
                DetailField(
                    if (tx.type == MovementType.INCOME) "Cuenta de destino" else "Cuenta de origen",
                    item.sourceAccountAlias ?: item.destinationAccountAlias ?: "Cuenta"
                )
            }
            item.categoryName?.let { DetailField(stringResource(R.string.history_categories), it) }
            DetailField(stringResource(R.string.history_date), dateFormat.format(Instant.ofEpochMilli(tx.occurredAt)).lowercase(esPeLocale))
            tx.note?.takeIf(String::isNotBlank)?.let { DetailField(stringResource(R.string.history_note), it) }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.history_revisions), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            when {
                loading -> CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp))
                error -> Text(stringResource(R.string.history_revisions_error), style = MaterialTheme.typography.bodyMedium)
                revisions.isEmpty() -> Text(stringResource(R.string.history_revisions_empty), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
                else -> revisions.forEach { revision ->
                    val action = mapRevisionOperation(revision.operation)
                    DetailField("Revisión ${revision.revision} · $action", dateFormat.format(Instant.ofEpochMilli(revision.createdAt)).lowercase(esPeLocale))
                    humanizeRevisionReason(revision.reason)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
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

private fun mapRevisionOperation(operation: String): String = when (operation.uppercase(Locale.ROOT)) {
    "VOID", "VOID_TRANSACTION", "LOCAL_REJECTION_VOID" -> "Anulación"
    "REVISE", "REVISE_TRANSACTION" -> "Corrección"
    "REGISTER", "REGISTER_TRANSACTION", "CREATE", "MIGRATION_BASELINE" -> "Registro inicial"
    else -> "Registro conservado"
}

private fun humanizeRevisionReason(reason: String?): String? {
    if (reason.isNullOrBlank()) return null
    val trimmed = reason.trim()
    return when (trimmed.uppercase(Locale.ROOT)) {
        "CATEGORY_UNAVAILABLE" -> "La categoría original ya no está disponible"
        "CATEGORY_DELETED" -> "La categoría fue eliminada"
        "ACCOUNT_UNAVAILABLE" -> "La cuenta original ya no está disponible"
        "MERCHANT_UNAVAILABLE" -> "El comercio original ya no está disponible"
        "VOID_TRANSACTION" -> "Anulación solicitada por el usuario"
        "REVISE_TRANSACTION" -> "Corrección solicitada por el usuario"
        "DUPLICATE_TRANSACTION" -> "Transacción duplicada"
        "USER_REQUEST" -> "Solicitud del usuario"
        "IMPORT_ADJUSTMENT" -> "Ajuste de importación"
        else -> {
            if (trimmed.matches(Regex("^[A-Z0-9_]{3,}$"))) {
                "Actualización de registro"
            } else {
                trimmed
            }
        }
    }
}

@Composable private fun DetailField(label: String, value: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
