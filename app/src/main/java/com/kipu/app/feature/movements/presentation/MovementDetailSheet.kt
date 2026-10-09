package com.kipu.app.feature.movements.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.R
import com.kipu.app.feature.movements.domain.model.*
import com.kipu.app.ui.component.KipuBottomSheet
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.KipuEasingTokens
import com.kipu.app.ui.theme.KipuMotionTokens
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
    val colors = com.kipu.app.ui.theme.rememberCalmEmeraldColors()
    val tx = item.transaction
    val voided = tx.status == TransactionStatus.VOIDED
    val specialized = isSpecializedMovement(item)
    val esPeLocale = Locale.forLanguageTag("es-PE")
    val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", esPeLocale).withZone(ZoneId.systemDefault())
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var historyExpanded by rememberSaveable(tx.id) { mutableStateOf(false) }
    val reducedMotion = rememberReducedMotionEnabled()

    val amountPrefix = when (tx.type) {
        MovementType.EXPENSE -> "-"
        MovementType.INCOME -> "+"
        MovementType.TRANSFER -> ""
    }
    val currencySymbol = if (tx.currency == "PEN") "S/" else if (tx.currency == "USD") "$" else tx.currency
    val amountColor = if (voided) MaterialTheme.colorScheme.onSurfaceVariant else when (tx.type) {
        MovementType.INCOME -> colors.incomeEmerald
        MovementType.EXPENSE -> colors.expenseCoral
        MovementType.TRANSFER -> colors.transferBlue
    }
    val title = item.merchantName ?: item.categoryName ?: when (tx.type) {
        MovementType.EXPENSE -> "Gasto"
        MovementType.INCOME -> "Ingreso"
        MovementType.TRANSFER -> "Transferencia"
    }
    val statusLabel = if (voided) "Anulado" else when (tx.status) {
        TransactionStatus.REVISED -> "Corregido"
        TransactionStatus.FAILED -> "Fallido"
        else -> "Confirmado"
    }

    KipuBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("movement_detail_sheet"),
        header = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.history_detail),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp).testTag("btn_close_movement_detail")) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.history_detail_close))
                }
            }
        },
        footer = {
            if (!voided && !specialized) {
                Surface(tonalElevation = 2.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = onVoid,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("detail_void"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text(stringResource(R.string.movement_action_void)) }
                        Button(
                            onClick = onEdit,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("detail_edit"),
                        ) { Text(stringResource(R.string.movement_action_edit)) }
                    }
                }
            }
        },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            when (tx.type) {
                                MovementType.EXPENSE -> "Gasto"
                                MovementType.INCOME -> "Ingreso"
                                MovementType.TRANSFER -> "Transferencia"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = amountColor,
                        )
                        StatusPill(statusLabel, isError = voided)
                    }
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    MoneyText(
                        amount = "$amountPrefix${formatMinorUnits(tx.amountMinor)}",
                        currencySymbol = currencySymbol,
                        color = amountColor,
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp).testTag("detail_amount"),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(tx.syncStatus.uiLabel(), isError = false)
                        if (voided) {
                            Text("Movimiento anulado · Solo lectura", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    if (tx.type == MovementType.TRANSFER) {
                        DetailField("Cuenta de origen", item.sourceAccountAlias ?: "Origen")
                        HorizontalDivider()
                        DetailField("Cuenta de destino", item.destinationAccountAlias ?: "Destino")
                    } else if (item.cardAlias != null) {
                        DetailField("Tarjeta", item.cardAlias)
                    } else {
                        DetailField(
                            if (tx.type == MovementType.INCOME) "Cuenta de destino" else "Cuenta de origen",
                            item.sourceAccountAlias ?: item.destinationAccountAlias ?: "Cuenta",
                        )
                    }
                    item.categoryName?.let { HorizontalDivider(); DetailField(stringResource(R.string.history_categories), it) }
                    HorizontalDivider()
                    DetailField(stringResource(R.string.history_date), dateFormat.format(Instant.ofEpochMilli(tx.occurredAt)).lowercase(esPeLocale))
                    tx.note?.takeIf(String::isNotBlank)?.let { HorizontalDivider(); DetailField(stringResource(R.string.history_note), it) }
                }
            }

            if (loading || error || revisions.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .clickable { historyExpanded = !historyExpanded }
                                .padding(start = 16.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (revisions.isNotEmpty()) "${stringResource(R.string.history_revisions)} ? ${revisions.size}"
                                else stringResource(R.string.history_revisions),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f).semantics { heading() },
                            )
                            Icon(
                                if (historyExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (historyExpanded) "Ocultar historial" else "Ver historial",
                            )
                        }
                        AnimatedVisibility(
                            visible = historyExpanded,
                            enter = if (reducedMotion) EnterTransition.None else
                                expandVertically(tween(KipuMotionTokens.SubtreeEnterMillis, easing = KipuEasingTokens.Decelerate)) +
                                    fadeIn(tween(KipuMotionTokens.SubtreeEnterMillis)),
                            exit = if (reducedMotion) ExitTransition.None else
                                shrinkVertically(tween(KipuMotionTokens.SubtreeExitMillis, easing = KipuEasingTokens.Accelerate)) +
                                    fadeOut(tween(KipuMotionTokens.SubtreeExitMillis)),
                        ) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                when {
                                    loading -> CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp))
                                    error -> Text(stringResource(R.string.history_revisions_error), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
                                    else -> revisions.forEachIndexed { index, revision ->
                                        if (index > 0) HorizontalDivider()
                                        val action = mapRevisionOperation(revision.operation)
                                        DetailField("${revision.revision} ? $action", dateFormat.format(Instant.ofEpochMilli(revision.createdAt)).lowercase(esPeLocale))
                                        humanizeRevisionReason(revision.reason)?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (specialized && !voided) {
                Text(
                    stringResource(R.string.movement_void_specialized_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun mapRevisionOperation(operation: String): String = when (operation.uppercase(Locale.ROOT)) {
    "VOID", "VOID_TRANSACTION", "LOCAL_REJECTION_VOID" -> "Anulaci?n"
    "REVISE", "REVISE_TRANSACTION" -> "Correcci?n"
    "REGISTER", "REGISTER_TRANSACTION", "CREATE", "MIGRATION_BASELINE" -> "Registro inicial"
    else -> "Registro conservado"
}

private fun humanizeRevisionReason(reason: String?): String? {
    if (reason.isNullOrBlank()) return null
    val trimmed = reason.trim()
    return when (trimmed.uppercase(Locale.ROOT)) {
        "CATEGORY_UNAVAILABLE" -> "La categor?a original ya no est? disponible"
        "CATEGORY_DELETED" -> "La categor?a fue eliminada"
        "ACCOUNT_UNAVAILABLE" -> "La cuenta original ya no est? disponible"
        "MERCHANT_UNAVAILABLE" -> "El comercio original ya no est? disponible"
        "VOID_TRANSACTION" -> "Anulaci?n solicitada por el usuario"
        "REVISE_TRANSACTION" -> "Correcci?n solicitada por el usuario"
        "DUPLICATE_TRANSACTION" -> "Transacci?n duplicada"
        "USER_REQUEST" -> "Solicitud del usuario"
        "IMPORT_ADJUSTMENT" -> "Ajuste de importaci?n"
        else -> if (trimmed.matches(Regex("^[A-Z0-9_]{3,}$"))) "Actualizaci?n de registro" else trimmed
    }
}

@Composable
private fun DetailField(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.9f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.1f))
    }
}

@Composable
private fun StatusPill(label: String, isError: Boolean) {
    val background = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val foreground = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(color = background, shape = RoundedCornerShape(50)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = foreground, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}
