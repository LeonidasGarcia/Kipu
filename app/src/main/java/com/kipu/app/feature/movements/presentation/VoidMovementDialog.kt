package com.kipu.app.feature.movements.presentation

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.R
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.TransactionItem
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.theme.KipuExpense

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
) {
    var reasonText by remember { mutableStateOf("") }
    val isSpecialized = remember(item) { isSpecializedMovement(item) }
    val colorScheme = MaterialTheme.colorScheme
    val tx = item.transaction

    val title = when {
        item.merchantName?.isNotBlank() == true -> item.merchantName
        item.categoryName?.isNotBlank() == true -> item.categoryName
        tx.type == MovementType.TRANSFER -> stringResource(R.string.movement_type_transfer)
        tx.type == MovementType.INCOME -> stringResource(R.string.movement_type_income)
        else -> stringResource(R.string.movement_type_expense)
    }

    val currencySymbol = if (tx.currency == "PEN") "S/" else "$"
    val formattedAmount = "$currencySymbol ${formatMinorUnits(tx.amountMinor)}"

    AlertDialog(
        onDismissRequest = {
            if (!isVoiding) onDismissRequest()
        },
        modifier = modifier.testTag("dialog_void_movement"),
        title = {
            Text(
                text = stringResource(R.string.movement_void_dialog_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Movement identification
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = colorScheme.onSurface,
                            )
                            if (tx.type == MovementType.TRANSFER) {
                                Text(
                                    text = "${item.sourceAccountAlias ?: "Origen"} → ${item.destinationAccountAlias ?: "Destino"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    text = item.sourceAccountAlias ?: "Cuenta",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            text = formattedAmount,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colorScheme.onSurface,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isSpecialized) {
                    // Specialized warning
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.movement_void_specialized_warning),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onErrorContainer,
                                modifier = Modifier.testTag("text_void_specialized_warning")
                            )
                        }
                    }
                } else {
                    // Concrete consequences
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
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("text_void_consequences")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.movement_void_dialog_audit_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.outline,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Optional reason input
                    OutlinedTextField(
                        value = reasonText,
                        onValueChange = { reasonText = it },
                        label = { Text("Motivo (opcional)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_void_reason"),
                    )
                }

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.error,
                        modifier = Modifier.testTag("text_void_error")
                    )
                }
            }
        },
        confirmButton = {
            if (!isSpecialized) {
                TextButton(
                    onClick = {
                        onConfirmVoid(reasonText.trim().ifEmpty { null })
                    },
                    enabled = !isVoiding,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = KipuExpense,
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
                            color = KipuExpense
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.movement_void_dialog_confirm),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                enabled = !isVoiding,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("btn_cancel_void")
                    .semantics { contentDescription = "Cancelar anulación" },
            ) {
                Text(text = stringResource(R.string.movement_void_dialog_cancel))
            }
        },
    )
}
