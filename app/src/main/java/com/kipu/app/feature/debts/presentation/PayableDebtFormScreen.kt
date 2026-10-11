package com.kipu.app.feature.debts.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

@Composable
fun PayableDebtFormScreen(
    state: DebtFormUiState,
    onCounterpartyNameChange: (String) -> Unit,
    onPrincipalAmountChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onOpeningModeChange: (DebtOpeningMode) -> Unit,
    onAccountSelected: (String) -> Unit,
    onDueDateChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onNavigateBack: () -> Unit,
) = DebtOpeningFormScreen(
    obligationType = DebtObligationType.PAYABLE,
    state = state,
    onCounterpartyNameChange = onCounterpartyNameChange,
    onPrincipalAmountChange = onPrincipalAmountChange,
    onCurrencyChange = onCurrencyChange,
    onOpeningModeChange = onOpeningModeChange,
    onAccountSelected = onAccountSelected,
    onDueDateChange = onDueDateChange,
    onNotesChange = onNotesChange,
    onSave = onSave,
    onNavigateBack = onNavigateBack,
)

@Composable
fun ReceivableDebtFormScreen(
    state: DebtFormUiState,
    onCounterpartyNameChange: (String) -> Unit,
    onPrincipalAmountChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onOpeningModeChange: (DebtOpeningMode) -> Unit,
    onAccountSelected: (String) -> Unit,
    onDueDateChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onNavigateBack: () -> Unit,
) = DebtOpeningFormScreen(
    obligationType = DebtObligationType.RECEIVABLE,
    state = state,
    onCounterpartyNameChange = onCounterpartyNameChange,
    onPrincipalAmountChange = onPrincipalAmountChange,
    onCurrencyChange = onCurrencyChange,
    onOpeningModeChange = onOpeningModeChange,
    onAccountSelected = onAccountSelected,
    onDueDateChange = onDueDateChange,
    onNotesChange = onNotesChange,
    onSave = onSave,
    onNavigateBack = onNavigateBack,
)

@Composable
private fun DebtOpeningFormScreen(
    obligationType: DebtObligationType,
    state: DebtFormUiState,
    onCounterpartyNameChange: (String) -> Unit,
    onPrincipalAmountChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onOpeningModeChange: (DebtOpeningMode) -> Unit,
    onAccountSelected: (String) -> Unit,
    onDueDateChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val payable = obligationType == DebtObligationType.PAYABLE
    val title = if (payable) "Registrar deuda" else "Registrar dinero prestado"
    val personLabel = if (payable) "¿A quién le debes?" else "¿A quién le prestaste?"
    val movementTitle = if (payable) "Me prestaron" else "Presté dinero ahora"
    val movementDescription = if (payable) {
        "El saldo de la cuenta aumenta por el principal recibido."
    } else {
        "El saldo de la cuenta disminuye por el dinero entregado."
    }
    val emeraldColors = rememberCalmEmeraldColors()

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onNavigateBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                }
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        },
        bottomBar = {
            Button(
                onClick = onSave,
                enabled = state.isValidForSave(),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .heightIn(min = 52.dp)
                    .testTag("save-debt"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = emeraldColors.primaryAction),
            ) {
                Text(if (state.isSaving) "Guardando…" else title, fontWeight = FontWeight.SemiBold)
            }
        },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                if (payable) "Registra el saldo que debes y cómo se originó en tus cuentas." else "Registra el dinero que esperas recuperar y la cuenta de origen.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = state.counterpartyName,
                onValueChange = onCounterpartyNameChange,
                label = { Text(personLabel) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.principalAmount,
                onValueChange = onPrincipalAmountChange,
                label = { Text("Monto principal") },
                supportingText = { Text("Solo el principal; los intereses se registran al amortizar o cancelar.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("PEN", "USD").forEach { currency ->
                    val selected = state.currencyCode == currency
                    androidx.compose.material3.FilterChip(
                        selected = selected,
                        onClick = { onCurrencyChange(currency) },
                        label = { Text(currency, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = emeraldColors.pillTrack,
                            selectedLabelColor = emeraldColors.primaryDeep,
                        ),
                    )
                }
            }

            Text("Origen del dinero", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OpeningModeOption(
                selected = state.openingMode == DebtOpeningMode.NEW_CASH_FLOW,
                title = movementTitle,
                description = movementDescription,
                tag = "opening-new",
                onClick = { onOpeningModeChange(DebtOpeningMode.NEW_CASH_FLOW) },
            )
            OpeningModeOption(
                selected = state.openingMode == DebtOpeningMode.HISTORICAL,
                title = "Ya estaba registrado previamente",
                description = "El saldo ya lo refleja en tus cuentas; no se registrará un segundo movimiento.",
                tag = "opening-historical",
                onClick = { onOpeningModeChange(DebtOpeningMode.HISTORICAL) },
            )

            AnimatedVisibility(visible = state.openingMode == DebtOpeningMode.NEW_CASH_FLOW) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (payable) "Cuenta que recibió el dinero" else "Cuenta de origen",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (state.availableAccounts.isEmpty()) {
                        Text("No hay cuentas activas disponibles en ${state.currencyCode}.", color = MaterialTheme.colorScheme.error)
                    } else {
                        state.availableAccounts.forEach { account ->
                            val currencyMatches = account.currencyCode == state.currencyCode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .selectable(
                                        selected = state.selectedAccountId == account.id,
                                        enabled = currencyMatches,
                                        role = Role.RadioButton,
                                        onClick = { onAccountSelected(account.id) },
                                    )
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = state.selectedAccountId == account.id,
                                    onClick = { if (currencyMatches) onAccountSelected(account.id) },
                                    enabled = currencyMatches,
                                    colors = RadioButtonDefaults.colors(selectedColor = emeraldColors.primaryAction),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(account.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    Text(
                                        if (currencyMatches) account.currencyCode else "Moneda distinta (${account.currencyCode})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.dueDateInput,
                onValueChange = onDueDateChange,
                label = { Text("Fecha de vencimiento (opcional, AAAA-MM-DD)") },
                isError = state.dueDateInput.length >= 10 && state.dueDate == null,
                supportingText = if (state.dueDateInput.length >= 10 && state.dueDate == null) {
                    { Text("Ingresa una fecha válida con el formato AAAA-MM-DD.") }
                } else null,
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("debt-due-date"),
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = onNotesChange,
                label = { Text("Notas (opcional)") },
                shape = RoundedCornerShape(14.dp),
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            state.errorMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.heightIn(min = 4.dp))
        }
    }
}

@Composable
private fun OpeningModeOption(
    selected: Boolean,
    title: String,
    description: String,
    tag: String,
    onClick: () -> Unit,
) {
    val emeraldColors = rememberCalmEmeraldColors()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(onClick = onClick)
            .testTag(tag),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) emeraldColors.pillTrack else MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) emeraldColors.primaryAction else emeraldColors.borderSubtle,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = emeraldColors.primaryAction),
            )
            Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp, horizontal = 4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
