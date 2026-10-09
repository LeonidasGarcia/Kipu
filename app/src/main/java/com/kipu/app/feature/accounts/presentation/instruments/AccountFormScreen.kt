package com.kipu.app.feature.accounts.presentation.instruments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.MoneyInputParser
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.presentation.AccountUiEvent
import com.kipu.app.feature.accounts.presentation.AccountsViewModel
import com.kipu.app.feature.accounts.presentation.components.InstrumentCardPreview
import com.kipu.app.ui.theme.KipuMotionTokens

fun parseAmountToMinorUnits(input: String): Long? {
    if (input.isBlank()) return 0L
    return MoneyInputParser.parseMinorUnits(input)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountFormScreen(
    viewModel: AccountsViewModel,
    onNavigateBack: () -> Unit,
    onSaveSuccess: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostStateState() }

    var alias by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(AccountType.SAVINGS) }
    var selectedCurrency by remember { mutableStateOf(Currency.PEN) }
    var selectedPreset by remember { mutableStateOf<AccountPreset?>(AccountPreset.BCP) }
    var presetMenuExpanded by remember { mutableStateOf(false) }
    var initialBalanceInput by remember { mutableStateOf("0.00") }
    var isSubmitting by remember { mutableStateOf(false) }
    var aliasError by remember { mutableStateOf<String?>(null) }
    var initialBalanceError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AccountUiEvent.ShowMessage -> {
                    if (onSaveSuccess != null) {
                        onSaveSuccess(event.message)
                    } else {
                        snackbarHostState.showSnackbar(event.message)
                        onNavigateBack()
                    }
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
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Información General",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            InstrumentCardPreview(
                title = alias.ifBlank { selectedPreset?.defaultName ?: "Mi cuenta" },
                instrumentType = when (selectedType) {
                    AccountType.CASH -> "Efectivo"
                    AccountType.SAVINGS -> "Ahorros"
                    AccountType.BANK -> "Corriente"
                    AccountType.DIGITAL_WALLET -> "Billetera"
                    AccountType.CREDIT_LIABILITY -> "Pasivo de tarjeta"
                },
                subtitle = "${selectedPreset?.defaultName ?: "Cuenta genérica"} · ${selectedCurrency.name}",
            )

            OutlinedTextField(
                value = alias,
                onValueChange = {
                    if (it.length <= 80) alias = it
                    aliasError = null
                },
                label = { Text("Nombre o Alias de la cuenta") },
                placeholder = { Text("Ej. Sueldo BCP, Billetera") },
                isError = aliasError != null,
                supportingText = aliasError?.let { { Text(it) } },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
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
                AccountType.entries.filter { it != AccountType.CREDIT_LIABILITY }.forEach { type ->
                    val label = when (type) {
                        AccountType.CASH -> "Efectivo"
                        AccountType.SAVINGS -> "Ahorros"
                        AccountType.BANK -> "Corriente"
                        AccountType.DIGITAL_WALLET -> "Billetera"
                        AccountType.CREDIT_LIABILITY -> error("Credit liability accounts are internal")
                    }
                    FilterChip(
                        selected = selectedType == type,
                        onClick = {
                            selectedType = type
                            presetMenuExpanded = false
                            selectedPreset = when (type) {
                                AccountType.CASH -> AccountPreset.CASH
                                AccountType.DIGITAL_WALLET -> selectedPreset?.takeIf {
                                    it == AccountPreset.YAPE || it == AccountPreset.PLIN || it == AccountPreset.GENERIC
                                } ?: AccountPreset.YAPE
                                AccountType.SAVINGS, AccountType.BANK -> selectedPreset?.takeIf {
                                    it != AccountPreset.CASH && it != AccountPreset.YAPE && it != AccountPreset.PLIN
                                } ?: AccountPreset.BCP
                                AccountType.CREDIT_LIABILITY -> null
                            }
                            if (type == AccountType.CASH && alias.isBlank()) {
                                alias = "Efectivo"
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

            AnimatedVisibility(
                visible = selectedType != AccountType.CASH,
                enter = fadeIn(tween(KipuMotionTokens.FastMillis)) + expandVertically(tween(KipuMotionTokens.FastMillis)),
                exit = fadeOut(tween(KipuMotionTokens.FastMillis)) + shrinkVertically(tween(KipuMotionTokens.FastMillis)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (selectedType == AccountType.DIGITAL_WALLET) "Billetera" else "Institución",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Box {
                        OutlinedButton(
                            onClick = { presetMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("account_form_preset_selector"),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Icon(
                                imageVector = selectedPreset?.let(::accountPresetIcon) ?: Icons.Default.AccountBalance,
                                contentDescription = null,
                            )
                            Text(
                                text = selectedPreset?.defaultName ?: "Elegir institución",
                                modifier = Modifier.weight(1f).padding(start = 8.dp),
                                maxLines = 1,
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Elegir institución")
                        }
                        DropdownMenu(
                            expanded = presetMenuExpanded,
                            onDismissRequest = { presetMenuExpanded = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            AccountPreset.entries.filter { preset ->
                                when (selectedType) {
                                    AccountType.DIGITAL_WALLET -> preset == AccountPreset.YAPE ||
                                        preset == AccountPreset.PLIN || preset == AccountPreset.GENERIC
                                    AccountType.SAVINGS, AccountType.BANK -> preset != AccountPreset.CASH &&
                                        preset != AccountPreset.YAPE && preset != AccountPreset.PLIN
                                    AccountType.CASH, AccountType.CREDIT_LIABILITY -> false
                                }
                            }.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.defaultName) },
                                    leadingIcon = { Icon(accountPresetIcon(preset), contentDescription = null) },
                                    trailingIcon = if (selectedPreset == preset) {
                                        { Icon(Icons.Default.Check, contentDescription = "Seleccionada") }
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        selectedPreset = preset
                                        presetMenuExpanded = false
                                        if (alias.isBlank()) alias = preset.defaultName
                                    },
                                )
                            }
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
                onValueChange = {
                    initialBalanceInput = it
                    initialBalanceError = null
                },
                label = { Text("Importe inicial (${selectedCurrency.name})") },
                isError = initialBalanceError != null,
                supportingText = initialBalanceError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            AnimatedVisibility(
                visible = selectedType != AccountType.CASH,
                enter = fadeIn(tween(KipuMotionTokens.FastMillis)) + expandVertically(tween(KipuMotionTokens.FastMillis)),
                exit = fadeOut(tween(KipuMotionTokens.FastMillis)) + shrinkVertically(tween(KipuMotionTokens.FastMillis)),
            ) {
                Card(
                    shape = MaterialTheme.shapes.large,
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
                        aliasError = "Escribe un alias para identificar la cuenta."
                        return@Button
                    }
                    if (minorUnits == null) {
                        initialBalanceError = "Ingresa un importe válido con hasta dos decimales."
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
                enabled = !isSubmitting,
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

private fun accountPresetIcon(preset: AccountPreset) =
    if (preset.defaultIconToken == "wallet") Icons.Default.AccountBalanceWallet else Icons.Default.AccountBalance
