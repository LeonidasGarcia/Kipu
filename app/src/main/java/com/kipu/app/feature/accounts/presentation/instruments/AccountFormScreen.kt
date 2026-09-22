package com.kipu.app.feature.accounts.presentation.instruments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.presentation.AccountUiEvent
import com.kipu.app.feature.accounts.presentation.AccountsViewModel

fun parseAmountToMinorUnits(input: String): Long? {
    val clean = input.trim().replace(",", ".")
    if (clean.isBlank()) return 0L
    val parts = clean.split(".")
    if (parts.size > 2) return null
    val major = parts[0].toLongOrNull() ?: return null
    if (major < 0) return null
    val minor = if (parts.size == 2) {
        val centsStr = parts[1].take(2).padEnd(2, '0')
        centsStr.toLongOrNull() ?: return null
    } else {
        0L
    }
    return Math.addExact(Math.multiplyExact(major, 100L), minor)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountFormScreen(
    viewModel: AccountsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostStateState() }

    var alias by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(AccountType.SAVINGS) }
    var selectedCurrency by remember { mutableStateOf(Currency.PEN) }
    var selectedPreset by remember { mutableStateOf<AccountPreset?>(AccountPreset.BCP) }
    var initialBalanceInput by remember { mutableStateOf("0.00") }
    var isSubmitting by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AccountUiEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                    onNavigateBack()
                }
                is AccountUiEvent.Error -> {
                    isSubmitting = false
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nueva Cuenta") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Información General",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            OutlinedTextField(
                value = alias,
                onValueChange = { if (it.length <= 80) alias = it },
                label = { Text("Nombre o Alias de la cuenta") },
                placeholder = { Text("Ej. Sueldo BCP, Billetera") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "Tipo de Cuenta",
                style = MaterialTheme.typography.titleSmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AccountType.entries.forEach { type ->
                    val label = when (type) {
                        AccountType.CASH -> "Efectivo"
                        AccountType.SAVINGS -> "Ahorros"
                        AccountType.BANK -> "Corriente"
                        AccountType.DIGITAL_WALLET -> "Billetera"
                    }
                    FilterChip(
                        selected = selectedType == type,
                        onClick = {
                            selectedType = type
                            if (type == AccountType.CASH) {
                                selectedPreset = AccountPreset.CASH
                                if (alias.isBlank()) alias = "Efectivo"
                            }
                        },
                        label = { Text(label) },
                    )
                }
            }

            Text(
                text = "Moneda",
                style = MaterialTheme.typography.titleSmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Currency.entries.forEach { curr ->
                    FilterChip(
                        selected = selectedCurrency == curr,
                        onClick = { selectedCurrency = curr },
                        label = { Text(curr.name) },
                    )
                }
            }

            Text(
                text = "Institución / Preset",
                style = MaterialTheme.typography.titleSmall,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AccountPreset.entries.chunked(3).forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowPresets.forEach { preset ->
                            FilterChip(
                                selected = selectedPreset == preset,
                                onClick = {
                                    selectedPreset = preset
                                    if (alias.isBlank()) alias = preset.defaultName
                                },
                                label = { Text(preset.defaultName) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            Text(
                text = "Saldo Inicial",
                style = MaterialTheme.typography.titleSmall,
            )
            OutlinedTextField(
                value = initialBalanceInput,
                onValueChange = { initialBalanceInput = it },
                label = { Text("Importe inicial (${selectedCurrency.name})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (selectedType != AccountType.CASH) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Esta cuenta consumirá 1 cupo de los 4 permitidos en el plan Free.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val minorUnits = parseAmountToMinorUnits(initialBalanceInput)
                    if (alias.isBlank()) {
                        return@Button
                    }
                    if (minorUnits == null) {
                        return@Button
                    }
                    isSubmitting = true
                    viewModel.createAccount(
                        alias = alias.trim(),
                        type = selectedType,
                        currency = selectedCurrency,
                        preset = selectedPreset,
                        initialBalanceMinorUnits = minorUnits,
                        colorToken = selectedPreset?.defaultColorToken,
                        iconToken = selectedPreset?.defaultIconToken,
                    )
                },
                enabled = !isSubmitting && alias.isNotBlank() && parseAmountToMinorUnits(initialBalanceInput) != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(if (isSubmitting) "Guardando..." else "Crear Cuenta")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun SnackbarHostStateState() = SnackbarHostState()
