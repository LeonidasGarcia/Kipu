package com.kipu.app.feature.movements.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.R
import com.kipu.app.feature.movements.domain.model.*
import com.kipu.app.ui.component.KipuBottomSheet
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
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
    receiptAttached: Boolean = false,
    onAttachReceipt: () -> Unit = {},
    onOpenReceipt: () -> Unit = {},
    onRemoveReceipt: () -> Unit = {},
) {
    val emeraldColors = rememberCalmEmeraldColors()
    val tx = item.transaction
    val voided = tx.status == TransactionStatus.VOIDED
    val specialized = isSpecializedMovement(item)
    val esPeLocale = remember { Locale.forLanguageTag("es-PE") }
    val dateFormat = remember(esPeLocale) {
        DateTimeFormatter.ofPattern("d 'de' MMM. 'de' yyyy, hh:mm a", esPeLocale).withZone(ZoneId.systemDefault())
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showRevisions by remember { mutableStateOf(false) }

    val amountPrefix = when (tx.type) {
        MovementType.EXPENSE -> "− "
        MovementType.INCOME -> "+ "
        MovementType.TRANSFER -> ""
    }
    val currencySymbol = if (tx.currency == "PEN") "S/" else if (tx.currency == "USD") "$" else tx.currency
    val amountColor = if (voided) {
        emeraldColors.secondaryMuted
    } else when (tx.type) {
        MovementType.INCOME -> emeraldColors.incomeEmerald
        MovementType.EXPENSE -> emeraldColors.expenseCoral
        MovementType.TRANSFER -> emeraldColors.primaryDeep
    }

    val typeLabel = when (tx.type) {
        MovementType.EXPENSE -> "Gasto"
        MovementType.INCOME -> "Ingreso"
        MovementType.TRANSFER -> "Transferencia"
    }

    val typeIcon: ImageVector = when (tx.type) {
        MovementType.EXPENSE -> Icons.Rounded.ShoppingBag
        MovementType.INCOME -> Icons.Rounded.Work
        MovementType.TRANSFER -> Icons.Rounded.SwapHoriz
    }

    val merchantDisplay = item.merchantName?.takeIf { it.isNotBlank() }
        ?: item.categoryName?.takeIf { it.isNotBlank() }
        ?: if (tx.type == MovementType.TRANSFER) "Entre cuentas propias" else typeLabel

    KipuBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("movement_detail_sheet"),
        header = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Detalle del movimiento",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    ),
                    modifier = Modifier.semantics { heading() },
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("btn_close_movement_detail"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.history_detail_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // === 1. HERO CARD PRINCIPAL ===
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = emeraldColors.surfaceCard),
                border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Merchant Header with badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        when (tx.type) {
                                            MovementType.EXPENSE -> emeraldColors.expenseBg
                                            MovementType.INCOME -> emeraldColors.incomeBg
                                            MovementType.TRANSFER -> emeraldColors.transferBg
                                        }
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = typeIcon,
                                    contentDescription = null,
                                    tint = when (tx.type) {
                                        MovementType.EXPENSE -> emeraldColors.expenseCoral
                                        MovementType.INCOME -> emeraldColors.incomeEmerald
                                        MovementType.TRANSFER -> emeraldColors.primaryDeep
                                    },
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = if (tx.type == MovementType.EXPENSE) "COMERCIO" else if (tx.type == MovementType.INCOME) "ORIGEN" else "OPERACIÓN",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = emeraldColors.secondaryMuted,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = merchantDisplay,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        // Badges: Type & Verification
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Type pill
                            Surface(
                                shape = CircleShape,
                                color = if (voided) emeraldColors.pillTrack else when (tx.type) {
                                    MovementType.EXPENSE -> emeraldColors.expenseBg
                                    MovementType.INCOME -> emeraldColors.incomeBg
                                    MovementType.TRANSFER -> emeraldColors.transferBg
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (voided) emeraldColors.borderSubtle else when (tx.type) {
                                        MovementType.EXPENSE -> emeraldColors.expenseBorder
                                        MovementType.INCOME -> emeraldColors.incomeBorder
                                        MovementType.TRANSFER -> emeraldColors.transferBorder
                                    }
                                ),
                            ) {
                                Text(
                                    text = if (voided) "Anulado" else typeLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (voided) emeraldColors.secondaryMuted else when (tx.type) {
                                        MovementType.EXPENSE -> emeraldColors.expenseCoral
                                        MovementType.INCOME -> emeraldColors.incomeEmerald
                                        MovementType.TRANSFER -> emeraldColors.primaryDeep
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }

                            // Verified badge
                            if (!voided) {
                                Surface(
                                    shape = CircleShape,
                                    color = emeraldColors.incomeBg,
                                    border = BorderStroke(1.dp, emeraldColors.incomeBorder),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = emeraldColors.incomeEmerald,
                                            modifier = Modifier.size(12.dp),
                                        )
                                        Text(
                                            text = "Verificado",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = emeraldColors.incomeEmerald,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Monto Hero
                    Column {
                        MoneyText(
                            amount = "$amountPrefix${formatMinorUnits(tx.amountMinor)}",
                            currencySymbol = currencySymbol,
                            color = amountColor,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp,
                            ),
                            modifier = Modifier.testTag("detail_amount"),
                        )
                        if (tx.type == MovementType.TRANSFER) {
                            Text(
                                text = "Movimiento interno · Sin impacto en balance neto",
                                style = MaterialTheme.typography.bodySmall,
                                color = emeraldColors.secondaryMuted,
                            )
                        }
                    }

                    // Comprobante banner si es Gasto o si tiene archivo
                    if (tx.type == MovementType.EXPENSE || receiptAttached) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Description,
                                        contentDescription = null,
                                        tint = if (receiptAttached) emeraldColors.primaryDeep else emeraldColors.secondaryMuted,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Column {
                                        Text(
                                            text = if (receiptAttached) "Comprobante_Adjunto.pdf" else "Comprobante de pago",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = if (receiptAttached) "Archivo PDF · Guardado en este dispositivo" else "Pendiente de adjuntar",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = emeraldColors.secondaryMuted,
                                        )
                                    }
                                }

                                if (receiptAttached) {
                                    TextButton(
                                        onClick = onOpenReceipt,
                                        modifier = Modifier.testTag("btn_open_movement_receipt"),
                                    ) {
                                        Text("Abrir", color = emeraldColors.primaryDeep, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    TextButton(
                                        onClick = onAttachReceipt,
                                        modifier = Modifier.testTag("btn_attach_movement_receipt"),
                                    ) {
                                        Text("Adjuntar", color = emeraldColors.primaryDeep, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Notice if VOIDED
                    if (voided) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
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
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = emeraldColors.expenseCoral,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = "Este movimiento fue anulado. El saldo fue restituido a la cuenta de origen y no afecta el balance contable actual.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = emeraldColors.expenseCoral,
                                )
                            }
                        }
                    }
                }
            }

            // === 2. DETALLES FINANCIEROS AGRUPADOS (M3 CONTAINER) ===
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = emeraldColors.surfaceCard),
                border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    if (tx.type == MovementType.TRANSFER) {
                        DetailRow(
                            icon = Icons.Rounded.AccountBalance,
                            label = "Cuenta de origen",
                            value = item.sourceAccountAlias ?: "Origen",
                            iconColor = emeraldColors.secondaryMuted,
                        )
                        HorizontalDivider(color = emeraldColors.borderSubtle, thickness = 0.8.dp)
                        DetailRow(
                            icon = Icons.Rounded.AccountBalance,
                            label = "Cuenta de destino",
                            value = item.destinationAccountAlias ?: "Destino",
                            iconColor = emeraldColors.secondaryMuted,
                        )
                    } else if (item.cardAlias != null) {
                        DetailRow(
                            icon = Icons.Rounded.CreditCard,
                            label = "Tarjeta utilizada",
                            value = item.cardAlias,
                            iconColor = emeraldColors.secondaryMuted,
                        )
                    } else {
                        DetailRow(
                            icon = Icons.Rounded.AccountBalance,
                            label = if (tx.type == MovementType.INCOME) "Cuenta de destino" else "Cuenta de origen",
                            value = item.sourceAccountAlias ?: item.destinationAccountAlias ?: "Cuenta registrada",
                            iconColor = emeraldColors.secondaryMuted,
                        )
                    }

                    item.categoryName?.let { cat ->
                        HorizontalDivider(color = emeraldColors.borderSubtle, thickness = 0.8.dp)
                        DetailRow(
                            icon = Icons.Rounded.LocalOffer,
                            label = "Categoría",
                            value = cat,
                            iconColor = emeraldColors.primaryDeep,
                        )
                    }

                    HorizontalDivider(color = emeraldColors.borderSubtle, thickness = 0.8.dp)
                    DetailRow(
                        icon = Icons.Rounded.Schedule,
                        label = "Fecha y hora",
                        value = dateFormat.format(Instant.ofEpochMilli(tx.occurredAt)).lowercase(esPeLocale),
                        iconColor = emeraldColors.secondaryMuted,
                    )

                    HorizontalDivider(color = emeraldColors.borderSubtle, thickness = 0.8.dp)
                    DetailRow(
                        icon = Icons.Rounded.Payment,
                        label = "Método de registro",
                        value = when {
                            item.cardAlias != null -> "Tarjeta · ${item.cardAlias}"
                            tx.type == MovementType.TRANSFER -> "Transferencia de fondos"
                            else -> "Cuenta bancaria directa"
                        },
                        iconColor = emeraldColors.secondaryMuted,
                    )

                    tx.note?.takeIf(String::isNotBlank)?.let { noteText ->
                        HorizontalDivider(color = emeraldColors.borderSubtle, thickness = 0.8.dp)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Notes,
                                    contentDescription = null,
                                    tint = emeraldColors.secondaryMuted,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = "Notas",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = emeraldColors.secondaryMuted,
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 30.dp),
                            ) {
                                Text(
                                    text = noteText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                        }
                    }
                }
            }

            // === 3. TRAZABILIDAD Y AUDITORÍA (EXPANDIBLE) ===
            TextButton(
                onClick = { showRevisions = !showRevisions },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (showRevisions) "Ocultar historial de revisiones" else "Ver historial de revisiones (${revisions.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = emeraldColors.primaryDeep,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = if (showRevisions) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = emeraldColors.primaryDeep,
                    modifier = Modifier.size(18.dp),
                )
            }

            AnimatedVisibility(visible = showRevisions) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when {
                        loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).size(24.dp))
                        error -> Text("Error cargando revisiones", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        revisions.isEmpty() -> Text("Sin modificaciones posteriores.", style = MaterialTheme.typography.bodySmall, color = emeraldColors.secondaryMuted)
                        else -> revisions.forEach { rev ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Revisión ${rev.revision} · ${mapRevisionOperation(rev.operation)}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    )
                                    Text(
                                        text = dateFormat.format(Instant.ofEpochMilli(rev.createdAt)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = emeraldColors.secondaryMuted,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // === 4. BOTONES DE ACCIÓN INFERIORES ===
            if (!voided && !specialized) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onVoid,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("detail_void"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = emeraldColors.expenseCoral,
                        ),
                        border = BorderStroke(1.dp, emeraldColors.expenseCoral),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Anular",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        )
                    }

                    Button(
                        onClick = onEdit,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("detail_edit"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = emeraldColors.primaryDeep,
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Editar",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
            } else if (voided) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = emeraldColors.primaryDeep),
                ) {
                    Text("Cerrar detalle", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    iconColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun mapRevisionOperation(operation: String): String = when (operation.uppercase(Locale.ROOT)) {
    "VOID", "VOID_TRANSACTION", "LOCAL_REJECTION_VOID" -> "Anulación"
    "REVISE", "REVISE_TRANSACTION" -> "Corrección"
    "REGISTER", "REGISTER_TRANSACTION", "CREATE", "MIGRATION_BASELINE" -> "Registro inicial"
    else -> "Registro conservado"
}
