package com.kipu.app.feature.debts.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import java.time.LocalDate

@Composable
fun PayableDebtFormScreen(
    state: DebtFormUiState,
    onCounterpartyNameChange: (String) -> Unit,
    onPrincipalAmountChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onOpeningModeChange: (DebtOpeningMode) -> Unit,
    onAccountSelected: (String) -> Unit,
    onDueDateChange: (LocalDate?) -> Unit,
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
    onDueDateChange: (LocalDate?) -> Unit,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebtOpeningFormScreen(
    obligationType: DebtObligationType,
    state: DebtFormUiState,
    onCounterpartyNameChange: (String) -> Unit,
    onPrincipalAmountChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onOpeningModeChange: (DebtOpeningMode) -> Unit,
    onAccountSelected: (String) -> Unit,
    onDueDateChange: (LocalDate?) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val payable = obligationType == DebtObligationType.PAYABLE
    val title = if (payable) "Registrar deuda" else "Registrar dinero prestado"
    val personLabel = if (payable) "¿A quién le debes?" else "¿A quién le prestaste?"
    val movementTitle = if (payable) "Me prestaron" else "Presté dinero ahora"
    val movementDescription = if (payable) {
        "El saldo de la cuenta aumenta por el principal."
    } else {
        "El saldo de la cuenta disminuye por el principal."
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets,
            )
        },
        bottomBar = {
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
            ) {
                Button(
                    onClick = onSave,
                    enabled = state.isValidForSave(),
                    modifier = Modifier.fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .heightIn(min = 52.dp)
                        .testTag("save-debt"),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(if (state.isSaving) "Guardando…" else title)
                }
            }
        },
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                if (payable) "Registra el saldo que debes y cómo se originó." else "Registra el dinero que esperas recuperar.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = state.counterpartyName,
                onValueChange = onCounterpartyNameChange,
                label = { Text(personLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.principalAmount,
                onValueChange = onPrincipalAmountChange,
                label = { Text("Monto principal") },
                supportingText = { Text("Solo el principal; los intereses se registran al pagar o cobrar.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("PEN", "USD").forEach { currency ->
                    val selected = state.currencyCode == currency
                    androidx.compose.material3.FilterChip(
                        selected = selected,
                        onClick = { onCurrencyChange(currency) },
                        label = { Text(currency) },
                        modifier = Modifier.heightIn(min = 48.dp),
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
                title = "Ya estaba registrado",
                description = "El saldo ya lo refleja; no habrá un segundo movimiento.",
                tag = "opening-historical",
                onClick = { onOpeningModeChange(DebtOpeningMode.HISTORICAL) },
            )

            AnimatedVisibility(visible = state.openingMode == DebtOpeningMode.NEW_CASH_FLOW) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (payable) "Cuenta que recibió el dinero" else "Cuenta de origen",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (state.availableAccounts.isEmpty()) {
                        Text("No hay cuentas activas disponibles en ${state.currencyCode}.", color = MaterialTheme.colorScheme.error)
                    } else {
                        state.availableAccounts.forEach { account ->
                            val currencyMatches = account.currencyCode == state.currencyCode
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                    .selectable(
                                        selected = state.selectedAccountId == account.id,
                                        enabled = currencyMatches,
                                        role = Role.RadioButton,
                                        onClick = { onAccountSelected(account.id) },
                                    ).padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = state.selectedAccountId == account.id,
                                    onClick = { if (currencyMatches) onAccountSelected(account.id) },
                                    enabled = currencyMatches,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(account.name, style = MaterialTheme.typography.bodyLarge)
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

            DebtDatePickerField(
                label = "Vencimiento (opcional)",
                date = state.dueDate,
                onDateChange = onDueDateChange,
                pickerTag = "debt_due_date_picker",
                calendarTag = "due_date_calendar",
                confirmTag = "confirm_due_date",
                clearTag = "clear_due_date",
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = onNotesChange,
                label = { Text("Notas (opcional)") },
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
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize().clickable(onClick = onClick).testTag(tag),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp, vertical = 4.dp)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
