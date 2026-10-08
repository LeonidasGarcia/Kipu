package com.kipu.app.feature.debts.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.debts.domain.DebtAmountParser
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtSummary
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
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (payable) "Registrar pago" else "Registrar cobro",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cerrar") }
            }
            Text(
                "Saldo pendiente: ${formatter.format(debt.remainingPrincipalMinor / 100.0)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "El principal reduce el saldo; el interés se registra por separado.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.conflictMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedTextField(
                value = state.principalAmount,
                onValueChange = onPrincipalAmountChange,
                label = { Text("Principal") },
                supportingText = { Text("Máximo ${formatter.format(debt.remainingPrincipalMinor / 100.0)}") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("settlement-principal"),
            )
            OutlinedTextField(
                value = state.interestAmount,
                onValueChange = onInterestAmountChange,
                label = { Text("Interés") },
                supportingText = {
                    Text(if (payable) "El interés sí forma parte de tus gastos." else "El interés sí forma parte de tus ingresos.")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("settlement-interest"),
            )
            AnimatedVisibility(visible = principalTooHigh) {
                Text(
                    "El principal supera el saldo pendiente.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            AnimatedVisibility(visible = interestMinor == null) {
                Text(
                    "Revisa el interés; usa hasta dos decimales y no un monto negativo.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                "Cuenta en ${debt.currencyCode}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (state.availableAccounts.isEmpty()) {
                Text("No hay cuentas activas en ${debt.currencyCode}.", color = MaterialTheme.colorScheme.error)
            } else {
                state.availableAccounts.forEach { account ->
                    val matchesCurrency = account.currencyCode == debt.currencyCode
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            .selectable(
                                selected = state.selectedAccountId == account.id,
                                enabled = matchesCurrency,
                                role = Role.RadioButton,
                                onClick = { if (matchesCurrency) onAccountSelected(account.id) },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = state.selectedAccountId == account.id,
                            onClick = { if (matchesCurrency) onAccountSelected(account.id) },
                            enabled = matchesCurrency,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(account.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (matchesCurrency) account.currencyCode else "Moneda distinta (${account.currencyCode})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(visible = needsInterestCategory) {
                Card(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Categoría del interés", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        if (state.availableInterestCategories.isEmpty()) {
                            Text("Crea o sincroniza una categoría de gasto para el interés.", color = MaterialTheme.colorScheme.error)
                        } else {
                            state.availableInterestCategories
                                .filter { it.categoryType in setOf("GENERAL", "EXPENSE") }
                                .forEach { category ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                            .selectable(
                                                selected = state.selectedInterestCategoryId == category.id,
                                                role = Role.RadioButton,
                                                onClick = { onInterestCategorySelected(category.id) },
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected = state.selectedInterestCategoryId == category.id,
                                            onClick = { onInterestCategorySelected(category.id) },
                                        )
                                        Text(category.name, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                        }
                    }
                }
            }
            cashTotal?.let { total ->
                Text(
                    "${if (payable) "Saldrá de tu cuenta" else "Entrará a tu cuenta"}: ${formatter.format(total / 100.0)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
            state.errorMessage?.let { message ->
                if (message != "El principal supera el saldo pendiente.") {
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Button(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("settlement-save"),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    when {
                        state.isSaving -> "Guardando…"
                        payable -> "Confirmar pago"
                        else -> "Confirmar cobro"
                    },
                )
            }
        }
    }
}

private fun settlementCurrencyFormatter(currencyCode: String): NumberFormat =
    NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("es").setRegion("PE").build()).apply {
        runCatching { Currency.getInstance(currencyCode) }.getOrNull()?.let { currency = it }
    }
