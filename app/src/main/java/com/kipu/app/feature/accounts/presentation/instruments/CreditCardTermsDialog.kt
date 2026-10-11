package com.kipu.app.feature.accounts.presentation.instruments

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kipu.app.core.finance.domain.MoneyInputParser
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.component.symbol
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

import androidx.compose.material.icons.rounded.CreditCard

@Composable
fun CreditCardTermsDialog(
    card: CreditCard,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (creditLimitMinorUnits: Long, billingDay: Int, dueDay: Int, lastFourDigits: String, alias: String?) -> Unit,
) {
    var alias by remember(card.id, card.alias) { mutableStateOf(card.alias.orEmpty()) }
    var lastFourDigits by remember(card.id, card.lastFourDigits) { mutableStateOf(card.lastFourDigits) }
    var creditLimit by remember(card.id, card.creditLimitMinorUnits) {
        mutableStateOf(formatMinorUnits(card.creditLimitMinorUnits))
    }
    var billingDay by remember(card.id, card.billingDay) { mutableStateOf(card.billingDay.toString()) }
    var dueDay by remember(card.id, card.dueDay) { mutableStateOf(card.dueDay.toString()) }
    var attemptedSave by remember(card.id) { mutableStateOf(false) }
    val emeraldColors = rememberCalmEmeraldColors()

    val parsedLimit = remember(creditLimit) { MoneyInputParser.parseMinorUnits(creditLimit) }
    val parsedBillingDay = remember(billingDay) { billingDay.toIntOrNull() }
    val parsedDueDay = remember(dueDay) { dueDay.toIntOrNull() }
    val validDigits = lastFourDigits.length == 4 && lastFourDigits.all(Char::isDigit)
    val validLimit = parsedLimit != null && parsedLimit >= 0L
    val validBillingDay = parsedBillingDay != null && parsedBillingDay in 1..31
    val validDueDay = parsedDueDay != null && parsedDueDay in 1..31
    val canSave = validLimit && validBillingDay && validDueDay && validDigits && !isSaving

    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 680.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Drag handle pill at top
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }

                // Header with Flujo R10 tag and title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "FLUJO R10 · EDICIÓN",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = emeraldColors.primaryDeep,
                        )
                        Text(
                            text = "Editar tarjeta de crédito",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    IconButton(
                        onClick = { if (!isSaving) onDismiss() },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Mini preview card of the credit card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F243A)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AccountBalance,
                                    contentDescription = null,
                                    tint = Color(0xFF5EEAD4),
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = card.issuer,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    text = "CRÉDITO",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }

                        Text(
                            text = alias.ifBlank { card.alias ?: "${card.issuer} ${card.network}" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = "•••• ${lastFourDigits.ifBlank { card.lastFourDigits }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = "LÍNEA ASIGNADA",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 9.sp,
                                )
                                Text(
                                    text = "${card.currency.symbol()} ${creditLimit.ifBlank { "0.00" }}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF5EEAD4),
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "CORTE / PAGO",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 9.sp,
                                )
                                Text(
                                    text = "Día ${billingDay.ifBlank { "0" }} · Día ${dueDay.ifBlank { "0" }}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }

                // Input field: Nombre visible o alias
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Nombre visible o alias",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Obligatorio",
                            style = MaterialTheme.typography.labelSmall,
                            color = emeraldColors.secondaryMuted,
                        )
                    }
                    OutlinedTextField(
                        value = alias,
                        onValueChange = { alias = it.take(40) },
                        leadingIcon = {
                            Icon(Icons.Rounded.CreditCard, contentDescription = null, tint = emeraldColors.primaryDeep)
                        },
                        placeholder = { Text(card.alias ?: "${card.issuer} ${card.network}") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Input field: Últimos 4 dígitos
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Últimos 4 dígitos de la tarjeta",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "4 dígitos",
                            style = MaterialTheme.typography.labelSmall,
                            color = emeraldColors.secondaryMuted,
                        )
                    }
                    OutlinedTextField(
                        value = lastFourDigits,
                        onValueChange = { lastFourDigits = it.filter(Char::isDigit).take(4) },
                        prefix = { Text("•••• ", fontWeight = FontWeight.Bold, color = emeraldColors.secondaryMuted) },
                        placeholder = { Text("Ej. 8978") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        isError = attemptedSave && !validDigits,
                        supportingText = if (attemptedSave && !validDigits) {
                            { Text("Ingresa los 4 dígitos numéricos finales.") }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Input field: Línea total de crédito
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Línea total de crédito (${card.currency.name})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${card.currency.name} (${card.currency.symbol()})",
                            style = MaterialTheme.typography.labelSmall,
                            color = emeraldColors.secondaryMuted,
                        )
                    }
                    OutlinedTextField(
                        value = creditLimit,
                        onValueChange = { creditLimit = it.take(18) },
                        prefix = { Text("${card.currency.symbol()} ", fontWeight = FontWeight.SemiBold) },
                        placeholder = { Text("0.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        isError = attemptedSave && !validLimit,
                        supportingText = if (attemptedSave && !validLimit) {
                            { Text("Ingresa un monto válido igual o mayor a cero.") }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Row with Día de corte and Día de pago
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCut,
                                contentDescription = null,
                                tint = emeraldColors.secondaryMuted,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "Día de corte",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        OutlinedTextField(
                            value = billingDay,
                            onValueChange = { billingDay = it.filter(Char::isDigit).take(2) },
                            prefix = { Text("Día ") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            isError = attemptedSave && !validBillingDay,
                            supportingText = if (attemptedSave && !validBillingDay) {
                                { Text("Entre 1 y 31.") }
                            } else null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarToday,
                                contentDescription = null,
                                tint = emeraldColors.secondaryMuted,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "Día de pago",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        OutlinedTextField(
                            value = dueDay,
                            onValueChange = { dueDay = it.filter(Char::isDigit).take(2) },
                            prefix = { Text("Día ") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            isError = attemptedSave && !validDueDay,
                            supportingText = if (attemptedSave && !validDueDay) {
                                { Text("Entre 1 y 31.") }
                            } else null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Actions: Guardar cambios & Cancelar
                Button(
                    onClick = {
                        attemptedSave = true
                        if (canSave) {
                            onSave(
                                requireNotNull(parsedLimit),
                                requireNotNull(parsedBillingDay),
                                requireNotNull(parsedDueDay),
                                lastFourDigits,
                                alias.ifBlank { null },
                            )
                        }
                    },
                    enabled = canSave,
                    colors = ButtonDefaults.buttonColors(containerColor = emeraldColors.primaryDeep),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isSaving) "Guardando…" else "Guardar cambios",
                        fontWeight = FontWeight.Bold,
                    )
                }

                TextButton(
                    onClick = { if (!isSaving) onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
