package com.kipu.app.feature.accounts.presentation.instruments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.AccountWithBalance
import com.kipu.app.feature.accounts.domain.model.CreditCardWithSummary
import com.kipu.app.feature.accounts.domain.model.CardPaymentSuggestion
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.ui.component.formatMinorUnits
import java.time.format.DateTimeFormatter
import java.util.Locale

typealias CreditCardPaymentHandler = (
    cardId: CardId,
    sourceAccountId: AccountId,
    paymentAmount: Money,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit,
) -> Unit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayCardDialog(
    creditCardWithSummary: CreditCardWithSummary,
    nextInstallmentDue: CardPaymentSuggestion? = null,
    eligibleAccounts: List<AccountWithBalance>,
    onPayCreditCard: CreditCardPaymentHandler,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val card = creditCardWithSummary.card
    val debt = creditCardWithSummary.debt

    val matchingAccounts = remember(eligibleAccounts, card.currency) {
        eligibleCardPaymentAccounts(eligibleAccounts, card.currency)
    }
    val nextDueSuggestion = nextInstallmentDue?.takeIf {
        it.amount.minorUnits in 1L..debt.minorUnits
    }
    val nextDueAmount = nextDueSuggestion?.amount?.minorUnits
    val initialPaymentAmount = nextDueAmount ?: debt.minorUnits
    val dueDateFormatter = remember {
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-PE"))
    }

    var selectedAccount by remember { mutableStateOf(matchingAccounts.firstOrNull()) }
    var isAccountDropdownExpanded by remember { mutableStateOf(false) }
    var amountInput by remember(card.id, initialPaymentAmount) {
        mutableStateOf(formatMinorUnits(initialPaymentAmount))
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Pagar Tarjeta de Crédito",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "${card.alias ?: card.issuer} (•••• ${card.lastFourDigits})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Deuda total: ${card.currency.name} ${formatMinorUnits(debt.minorUnits)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                if (nextDueSuggestion != null && nextDueAmount != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth().testTag("card_next_installment_suggestion"),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Próxima cuota", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "Vence ${nextDueSuggestion.dueDate.format(dueDateFormatter)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                            Text(
                                "${card.currency.name} ${formatMinorUnits(nextDueAmount)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                if (matchingAccounts.isEmpty()) {
                    Text(
                        text = "No tienes cuentas activas en ${card.currency.name} con saldo suficiente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = isAccountDropdownExpanded,
                        onExpandedChange = { isAccountDropdownExpanded = it },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = selectedAccount?.let { "${it.account.alias} (Saldo: ${formatMinorUnits(it.balance.minorUnits)})" } ?: "Seleccionar",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Pagar desde") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isAccountDropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                        )
                        ExposedDropdownMenu(
                            expanded = isAccountDropdownExpanded,
                            onDismissRequest = { isAccountDropdownExpanded = false },
                        ) {
                            matchingAccounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text("${acc.account.alias} • Saldo: ${formatMinorUnits(acc.balance.minorUnits)}") },
                                    onClick = {
                                        selectedAccount = acc
                                        isAccountDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = {
                        amountInput = it
                        errorMessage = null
                    },
                    label = { Text("Monto (${card.currency.name})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = {
                        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("credit_card_payment_amount"),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (nextDueAmount != null) {
                        FilterChip(
                            selected = amountInput == formatMinorUnits(nextDueAmount),
                            onClick = {
                                amountInput = formatMinorUnits(nextDueAmount)
                                errorMessage = null
                            },
                            label = { Text("Próxima cuota") },
                        )
                    }
                    FilterChip(
                        selected = amountInput == formatMinorUnits(debt.minorUnits),
                        onClick = {
                            amountInput = formatMinorUnits(debt.minorUnits)
                            errorMessage = null
                        },
                        label = { Text("Total") },
                    )
                }

                Text(
                    text = "El pago reduce tu deuda y el saldo de la cuenta; no se registra como otro gasto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val minorUnits = parseAmountToMinorUnits(amountInput)
                    if (minorUnits == null || minorUnits <= 0L) {
                        errorMessage = "Ingresa un monto mayor a cero"
                        return@Button
                    }
                    if (minorUnits > debt.minorUnits) {
                        errorMessage = "El monto no puede exceder la deuda actual (${formatMinorUnits(debt.minorUnits)})"
                        return@Button
                    }
                    val sourceAcc = selectedAccount
                    if (sourceAcc == null) {
                        errorMessage = "Selecciona una cuenta de origen"
                        return@Button
                    }
                    if (minorUnits > sourceAcc.balance.minorUnits) {
                        errorMessage = "Fondos insuficientes en la cuenta origen (${formatMinorUnits(sourceAcc.balance.minorUnits)})"
                        return@Button
                    }

                    isSubmitting = true
                    onPayCreditCard(
                        card.id,
                        sourceAcc.account.id,
                        Money(minorUnits, card.currency),
                        onDismiss,
                        { message ->
                            isSubmitting = false
                            errorMessage = message
                        },
                    )
                },
                enabled = !isSubmitting && selectedAccount != null && debt.minorUnits > 0L,
            ) {
                Text(if (isSubmitting) "Procesando..." else "Confirmar Pago")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
        modifier = modifier,
    )
}

internal fun eligibleCardPaymentAccounts(
    accounts: List<AccountWithBalance>,
    cardCurrency: com.kipu.app.core.finance.domain.model.Currency,
): List<AccountWithBalance> = accounts.filter {
    it.account.type in setOf(AccountType.BANK, AccountType.SAVINGS) &&
        it.account.currency == cardCurrency && !it.account.isArchived && it.balance.minorUnits > 0L
}
