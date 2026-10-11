package com.kipu.app.feature.movements.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.R
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.TransactionItem
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.theme.KipuExpense
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

fun isSpecializedMovement(item: TransactionItem): Boolean {
    val tx = item.transaction
    return (!tx.operationKind.isNullOrBlank() && !tx.operationKind.equals("STANDARD", ignoreCase = true)) ||
        tx.cardId != null ||
        tx.installmentCount != null ||
        tx.operationKind.equals("CARD_PAYMENT", ignoreCase = true) ||
        tx.operationKind.equals("CARD_PURCHASE", ignoreCase = true) ||
        tx.legacyKind in setOf("OPENING", "ADJUSTMENT", "REVERSAL", "CARD_PAYMENT_CASH")
}

@Composable
fun VoidMovementDialog(
    item: TransactionItem,
    onDismissRequest: () -> Unit,
    onConfirmVoid: (reason: String?) -> Unit,
    modifier: Modifier = Modifier,
    isVoiding: Boolean = false,
    errorMessage: String? = null,
    currentSourceBalanceMinor: Long? = null,
    currentDestinationBalanceMinor: Long? = null,
) {
    var reasonText by remember { mutableStateOf("") }
    val isSpecialized = remember(item) { isSpecializedMovement(item) }
    val emeraldColors = rememberCalmEmeraldColors()
    val tx = item.transaction

    val title = when {
        item.merchantName?.isNotBlank() == true -> item.merchantName
        item.categoryName?.isNotBlank() == true -> item.categoryName
        tx.type == MovementType.TRANSFER -> stringResource(R.string.movement_type_transfer)
        tx.type == MovementType.INCOME -> stringResource(R.string.movement_type_income)
        else -> stringResource(R.string.movement_type_expense)
    }

    val currencySymbol = if (tx.currency == "PEN") "S/" else "$"

    AlertDialog(
        onDismissRequest = {
            if (!isVoiding) onDismissRequest()
        },
        modifier = modifier.testTag("dialog_void_movement"),
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        icon = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(emeraldColors.expenseBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = emeraldColors.expenseCoral,
                    modifier = Modifier.size(28.dp),
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.movement_void_dialog_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Movement Hero Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = emeraldColors.surfaceCard,
                    border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(emeraldColors.pillTrack),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
                                    contentDescription = null,
                                    tint = emeraldColors.primaryDeep,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = if (tx.type == MovementType.TRANSFER) {
                                        "${item.sourceAccountAlias ?: "Origen"} → ${item.destinationAccountAlias ?: "Destino"}"
                                    } else {
                                        item.sourceAccountAlias ?: "Cuenta"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = emeraldColors.secondaryMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        MoneyText(
                            amount = formatMinorUnits(tx.amountMinor),
                            currencySymbol = currencySymbol,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = when (tx.type) {
                                MovementType.EXPENSE -> emeraldColors.expenseCoral
                                MovementType.INCOME -> emeraldColors.incomeEmerald
                                MovementType.TRANSFER -> emeraldColors.primaryDeep
                            },
                        )
                    }
                }

                if (isSpecialized) {
                    // Specialized warning
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = emeraldColors.expenseBg,
                        border = BorderStroke(1.dp, emeraldColors.expenseBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = emeraldColors.expenseCoral,
                                modifier = Modifier.size(24.dp),
                            )
                            Text(
                                text = stringResource(R.string.movement_void_specialized_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = emeraldColors.expenseCoral,
                                modifier = Modifier.testTag("text_void_specialized_warning"),
                            )
                        }
                    }
                } else {
                    // Concrete consequences description
                    val consequences = when (tx.type) {
                        MovementType.TRANSFER -> stringResource(
                            R.string.movement_void_dialog_desc_transfer,
                            item.sourceAccountAlias ?: "Origen",
                            item.destinationAccountAlias ?: "Destino",
                        )
                        MovementType.EXPENSE -> stringResource(R.string.movement_void_dialog_desc_expense)
                        MovementType.INCOME -> stringResource(R.string.movement_void_dialog_desc_income)
                    }

                    Text(
                        text = consequences,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("text_void_consequences"),
                    )

                    // Balance impact preview
                    MovementVoidBalancePreview(
                        item = item,
                        sourceBalanceMinor = currentSourceBalanceMinor,
                        destinationBalanceMinor = currentDestinationBalanceMinor,
                        currencySymbol = currencySymbol,
                    )

                    // Audit notice
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = emeraldColors.secondaryMuted,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.movement_void_dialog_audit_notice),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = emeraldColors.secondaryMuted,
                        )
                    }

                    // Optional reason input
                    OutlinedTextField(
                        value = reasonText,
                        onValueChange = { reasonText = it },
                        label = { Text("Motivo de anulación (opcional)") },
                        placeholder = { Text("Ej. Registro duplicado, error de monto") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = emeraldColors.primaryDeep,
                            unfocusedBorderColor = emeraldColors.borderSubtle,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_void_reason"),
                    )
                }

                AnimatedVisibility(visible = !errorMessage.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = emeraldColors.expenseBg,
                        border = BorderStroke(1.dp, emeraldColors.expenseBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = emeraldColors.expenseCoral,
                            modifier = Modifier
                                .padding(10.dp)
                                .testTag("text_void_error"),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!isSpecialized) {
                Button(
                    onClick = {
                        onConfirmVoid(reasonText.trim().ifEmpty { null })
                    },
                    enabled = !isVoiding,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KipuExpense,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_confirm_void")
                        .semantics { contentDescription = "Confirmar anulación" },
                ) {
                    if (isVoiding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(R.string.movement_void_dialog_confirm),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismissRequest,
                enabled = !isVoiding,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("btn_cancel_void")
                    .semantics { contentDescription = "Cancelar anulación" },
            ) {
                Text(
                    text = stringResource(R.string.movement_void_dialog_cancel),
                    fontWeight = FontWeight.Medium,
                )
            }
        },
    )
}

@Composable
private fun MovementVoidBalancePreview(
    item: TransactionItem,
    sourceBalanceMinor: Long?,
    destinationBalanceMinor: Long?,
    currencySymbol: String,
) {
    val tx = item.transaction
    val emeraldColors = rememberCalmEmeraldColors()
    val sourceProjected = sourceBalanceMinor?.let { current ->
        runCatching {
            when (tx.type) {
                MovementType.EXPENSE, MovementType.TRANSFER -> Math.addExact(current, tx.amountMinor)
                MovementType.INCOME -> Math.subtractExact(current, tx.amountMinor)
            }
        }.getOrNull()
    }
    val destinationProjected = if (tx.type == MovementType.TRANSFER) {
        destinationBalanceMinor?.let { current -> runCatching { Math.subtractExact(current, tx.amountMinor) }.getOrNull() }
    } else null

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = emeraldColors.surfaceCard,
        border = BorderStroke(1.dp, emeraldColors.borderSubtle),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = emeraldColors.primaryDeep,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Impacto en saldos",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = emeraldColors.primaryDeep,
                )
            }

            if (!tx.sourceAccountId.isNullOrBlank()) {
                BalanceImpactRow(
                    accountLabel = if (tx.type == MovementType.INCOME) "${item.destinationAccountAlias ?: "Cuenta"} · ingreso" else item.sourceAccountAlias ?: "Cuenta de origen",
                    current = sourceBalanceMinor,
                    projected = sourceProjected,
                    currencySymbol = currencySymbol,
                )
            }
            if (tx.type == MovementType.TRANSFER && !tx.destinationAccountId.isNullOrBlank()) {
                HorizontalDivider(color = emeraldColors.borderSubtle)
                BalanceImpactRow(
                    accountLabel = item.destinationAccountAlias ?: "Cuenta de destino",
                    current = destinationBalanceMinor,
                    projected = destinationProjected,
                    currencySymbol = currencySymbol,
                )
            }
            if (tx.sourceAccountId.isNullOrBlank() && tx.destinationAccountId.isNullOrBlank()) {
                Text(
                    text = "No hay una cuenta disponible para proyectar este movimiento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = emeraldColors.secondaryMuted,
                )
            }
        }
    }
}

@Composable
private fun BalanceImpactRow(
    accountLabel: String,
    current: Long?,
    projected: Long?,
    currencySymbol: String,
) {
    val emeraldColors = rememberCalmEmeraldColors()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = accountLabel,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Saldo actual",
                style = MaterialTheme.typography.bodySmall,
                color = emeraldColors.secondaryMuted,
            )
            if (current == null) {
                Text("No disponible", style = MaterialTheme.typography.bodySmall)
            } else {
                MoneyText(
                    amount = formatMinorUnits(current),
                    currencySymbol = currencySymbol,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Después de anular",
                style = MaterialTheme.typography.bodySmall,
                color = emeraldColors.secondaryMuted,
            )
            if (projected == null) {
                Text("No disponible", style = MaterialTheme.typography.bodySmall)
            } else {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = emeraldColors.incomeBg,
                    border = BorderStroke(1.dp, emeraldColors.incomeBorder),
                ) {
                    MoneyText(
                        amount = formatMinorUnits(projected),
                        currencySymbol = currencySymbol,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = emeraldColors.incomeEmerald,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}
