package com.kipu.app.feature.debts.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.feature.debts.domain.DebtAmountParser
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtSettlementSheet(
    debt: DebtSummary,
    state: DebtSettlementUiState,
    onPrincipalAmountChange: (String) -> Unit,
    onInterestAmountChange: (String) -> Unit,
    onAccountSelected: (String) -> Unit,
    onInterestCategorySelected: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val emeraldColors = rememberCalmEmeraldColors()
    val principalMinor = DebtAmountParser.toMinorUnits(state.principalAmount)
    val interestMinor = DebtAmountParser.toNonNegativeMinorUnits(state.interestAmount)
    val principalTooHigh = principalMinor != null && principalMinor > debt.remainingPrincipalMinor
    val payable = debt.obligationType == DebtObligationType.PAYABLE
    val needsInterestCategory = payable && (interestMinor ?: 0L) > 0L
    val selectedAccount = state.availableAccounts.firstOrNull { it.id == state.selectedAccountId }
    val accountValid = selectedAccount != null && selectedAccount.currencyCode == debt.currencyCode
    val categoryValid = !needsInterestCategory || state.availableInterestCategories.any {
        it.id == state.selectedInterestCategoryId && it.categoryType in setOf("GENERAL", "EXPENSE")
    }
    val canSave = principalMinor != null && !principalTooHigh && interestMinor != null && accountValid &&
        categoryValid && !state.isSaving && state.conflictMessage == null
    val formatter = settlementCurrencyFormatter(debt.currencyCode)
    val cashTotal = if (principalMinor != null && interestMinor != null) principalMinor + interestMinor else null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color(0xFFCBD5E1), RoundedCornerShape(999.dp)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFCCFBF1), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Payments,
                            contentDescription = null,
                            tint = emeraldColors.primaryDeep,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column {
                        Text(
                            if (payable) "Registrar pago" else "Registrar cobro",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            debt.counterpartyName,
                            style = MaterialTheme.typography.bodySmall,
                            color = emeraldColors.secondaryMuted,
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = Color(0xFF64748B))
                }
            }

            // Saldo pendiente banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Saldo pendiente exigible", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                        Text(
                            formatter.format(debt.remainingPrincipalMinor / 100.0),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFCCFBF1),
                    ) {
                        Text(
                            "Moneda: ${debt.currencyCode}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = emeraldColors.primaryDeep,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Text(
                "El principal reduce el saldo exigible; el interés se contabiliza por separado como gasto o ingreso operativo.",
                style = MaterialTheme.typography.bodySmall,
                color = emeraldColors.secondaryMuted,
                lineHeight = 18.sp,
            )

            state.conflictMessage?.let { message ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                        Text(message, color = Color(0xFFDC2626), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Input: Principal
            OutlinedTextField(
                value = state.principalAmount,
                onValueChange = onPrincipalAmountChange,
                label = { Text("Principal a amortizar") },
                placeholder = { Text("0.00") },
                supportingText = { Text("Máximo ${formatter.format(debt.remainingPrincipalMinor / 100.0)}") },
                prefix = { Text(if (debt.currencyCode == "PEN") "S/ " else "${debt.currencyCode} ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(14.dp),
                isError = principalTooHigh,
                modifier = Modifier.fillMaxWidth().testTag("settlement-principal"),
            )

            // Input: Interés
            OutlinedTextField(
                value = state.interestAmount,
                onValueChange = onInterestAmountChange,
                label = { Text("Interés adicional (opcional)") },
                placeholder = { Text("0.00") },
                supportingText = {
                    Text(if (payable) "El interés sí forma parte de tus gastos." else "El interés sí forma parte de tus ingresos.")
                },
                prefix = { Text(if (debt.currencyCode == "PEN") "S/ " else "${debt.currencyCode} ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("settlement-interest"),
            )

            AnimatedVisibility(visible = principalTooHigh) {
                Text(
                    "El principal supera el saldo pendiente.",
                    color = Color(0xFFDC2626),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            AnimatedVisibility(visible = interestMinor == null) {
                Text(
                    "Revisa el interés; usa hasta dos decimales y no un monto negativo.",
                    color = Color(0xFFDC2626),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // Cuenta de cargo / abono
            Text(
                if (payable) "Cuenta de origen (${debt.currencyCode})" else "Cuenta de destino (${debt.currencyCode})",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )

            if (state.availableAccounts.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEF2F2),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "No hay cuentas activas en ${debt.currencyCode}.",
                        color = Color(0xFFDC2626),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.availableAccounts.forEach { account ->
                        val matchesCurrency = account.currencyCode == debt.currencyCode
                        val isSelected = state.selectedAccountId == account.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    enabled = matchesCurrency,
                                    role = Role.RadioButton,
                                    onClick = { if (matchesCurrency) onAccountSelected(account.id) },
                                ),
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) Color(0xFFF0FDF4) else Color(0xFFF8FAFC),
                            border = BorderStroke(
                                if (isSelected) 1.5.dp else 1.dp,
                                if (isSelected) emeraldColors.primaryDeep else Color(0xFFE2E8F0),
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { if (matchesCurrency) onAccountSelected(account.id) },
                                    enabled = matchesCurrency,
                                    colors = RadioButtonDefaults.colors(selectedColor = emeraldColors.primaryDeep),
                                )
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(if (matchesCurrency) Color(0xFFCCFBF1) else Color(0xFFF1F5F9), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Rounded.AccountBalance,
                                        contentDescription = null,
                                        tint = if (matchesCurrency) emeraldColors.primaryDeep else Color(0xFF94A3B8),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(account.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                    Text(
                                        if (matchesCurrency) "Moneda: ${account.currencyCode}" else "Moneda distinta (${account.currencyCode})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (matchesCurrency) Color(0xFF64748B) else Color(0xFFDC2626),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Categoría del interés (si aplica)
            AnimatedVisibility(visible = needsInterestCategory) {
                Surface(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.Category, contentDescription = null, tint = emeraldColors.primaryDeep, modifier = Modifier.size(18.dp))
                            Text("Categoría del interés", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        }
                        if (state.availableInterestCategories.isEmpty()) {
                            Text("Crea o sincroniza una categoría de gasto para el interés.", color = Color(0xFFDC2626), style = MaterialTheme.typography.bodySmall)
                        } else {
                            state.availableInterestCategories
                                .filter { it.categoryType in setOf("GENERAL", "EXPENSE") }
                                .forEach { category ->
                                    val isSelected = state.selectedInterestCategoryId == category.id
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .selectable(
                                                selected = isSelected,
                                                role = Role.RadioButton,
                                                onClick = { onInterestCategorySelected(category.id) },
                                            )
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { onInterestCategorySelected(category.id) },
                                            colors = RadioButtonDefaults.colors(selectedColor = emeraldColors.primaryDeep),
                                        )
                                        Text(category.name, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                        }
                    }
                }
            }

            // Impact Card
            cashTotal?.let { total ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF0FDF4),
                    border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (payable) "Saldrá de tu cuenta:" else "Entrará a tu cuenta:",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF15803D),
                        )
                        Text(
                            formatter.format(total / 100.0),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF15803D),
                        )
                    }
                }
            }

            state.errorMessage?.let { message ->
                if (message != "El principal supera el saldo pendiente.") {
                    Text(message, color = Color(0xFFDC2626), style = MaterialTheme.typography.bodySmall)
                }
            }

            // Confirm Button
            Button(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("settlement-save"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = emeraldColors.primaryDeep),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        when {
                            state.isSaving -> "Guardando…"
                            payable -> "Confirmar pago"
                            else -> "Confirmar cobro"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

private fun settlementCurrencyFormatter(currencyCode: String): NumberFormat =
    NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("es").setRegion("PE").build()).apply {
        runCatching { Currency.getInstance(currencyCode) }.getOrNull()?.let { currency = it }
    }
