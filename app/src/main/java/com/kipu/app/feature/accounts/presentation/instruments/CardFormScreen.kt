package com.kipu.app.feature.accounts.presentation.instruments

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.MoneyInputParser
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.Card as DomainCard
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CardPreset
import com.kipu.app.feature.accounts.presentation.AccountUiEvent
import com.kipu.app.feature.accounts.presentation.AccountsViewModel
import com.kipu.app.feature.accounts.presentation.components.InstrumentCardPreview
import com.kipu.app.ui.theme.KipuMotionTokens
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardFormScreen(
    viewModel: AccountsViewModel,
    onNavigateBack: () -> Unit,
    onSaveSuccess: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val instrumentsState by viewModel.instrumentsUiState.collectAsState()

    var isDebit by remember { mutableStateOf(true) }
    var issuer by remember { mutableStateOf("BCP") }
    var selectedNetwork by remember { mutableStateOf(CardNetwork.VISA) }
    var lastFourDigits by remember { mutableStateOf("") }
    var lastFourDigitsError by remember { mutableStateOf<String?>(null) }
    var alias by remember { mutableStateOf("") }
    var selectedPreset by remember { mutableStateOf<CardPreset?>(CardPreset.BCP_VISA) }

    // Debit specifics:
    val eligibleAccounts = instrumentsState.activeAccounts.filter {
        it.type in listOf(AccountType.SAVINGS, AccountType.BANK)
    }
    var selectedAccount by remember { mutableStateOf<Account?>(eligibleAccounts.firstOrNull()) }
    var isAccountDropdownExpanded by remember { mutableStateOf(false) }

    // Credit specifics:
    var selectedCurrency by remember { mutableStateOf(Currency.PEN) }
    var creditLimitInput by remember { mutableStateOf("1000.00") }
    var billingDayInput by remember { mutableStateOf("15") }
    var dueDayInput by remember { mutableStateOf("5") }
    val creditLimitMinorUnits = MoneyInputParser.parseMinorUnits(creditLimitInput)
    val creditLimitError = when {
        creditLimitInput.isBlank() -> "Ingresa una línea de crédito autorizada."
        creditLimitMinorUnits == null -> "Ingresa un importe válido con hasta dos decimales."
        else -> null
    }
    val billingDay = billingDayInput.toIntOrNull()
    val billingDayError = when {
        billingDayInput.isBlank() -> "Ingresa el día de corte."
        billingDay == null || billingDay !in 1..31 -> "Ingresa el día de corte entre 1 y 31."
        else -> null
    }
    val dueDay = dueDayInput.toIntOrNull()
    val dueDayError = when {
        dueDayInput.isBlank() -> "Ingresa el día de pago."
        dueDay == null || dueDay !in 1..31 -> "Ingresa el día de pago entre 1 y 31."
        else -> null
    }
    val areCreditTermsValid = creditLimitError == null && billingDayError == null && dueDayError == null

    var isSubmitting by remember { mutableStateOf(false) }
    var showDuplicateWarning by remember { mutableStateOf(false) }

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

    LaunchedEffect(eligibleAccounts) {
        if (selectedAccount == null && eligibleAccounts.isNotEmpty()) {
            selectedAccount = eligibleAccounts.first()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isDebit) "Nueva Tarjeta de Débito" else "Nueva Tarjeta de Crédito") },
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
                text = "Tipo de Tarjeta",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = isDebit,
                    onClick = { isDebit = true },
                    label = { Text("Tarjeta de Débito") },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = !isDebit,
                    onClick = { isDebit = false },
                    label = { Text("Tarjeta de Crédito") },
                    modifier = Modifier.weight(1f),
                )
            }

            InstrumentCardPreview(
                title = alias.ifBlank { issuer },
                instrumentType = if (isDebit) "Tarjeta de débito" else "Tarjeta de crédito",
                subtitle = if (isDebit) {
                    "${selectedAccount?.alias ?: issuer} · ${selectedNetwork.name}"
                } else {
                    "${selectedNetwork.name} · ${selectedCurrency.name}"
                },
                lastFourDigits = lastFourDigits.takeIf { it.length == 4 },
            )

            Text(
                text = "Identificación de la Tarjeta",
                style = MaterialTheme.typography.titleSmall,
            )

            val isIssuerForbidden = remember(issuer) { DomainCard.isForbiddenSensitiveCardInput(issuer) }
            val isAliasForbidden = remember(alias) { alias.isNotBlank() && DomainCard.isForbiddenSensitiveCardInput(alias) }

            OutlinedTextField(
                value = issuer,
                onValueChange = { if (it.length <= 80) issuer = it },
                label = { Text("Entidad Emisora") },
                placeholder = { Text("Ej. BCP, BBVA, Interbank") },
                isError = isIssuerForbidden,
                supportingText = {
                    if (isIssuerForbidden) {
                        Text("No ingrese números de tarjeta completos (PAN) ni códigos CVV.", color = MaterialTheme.colorScheme.error)
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(text = "Red de la Tarjeta", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CardNetwork.entries.forEach { net ->
                    FilterChip(
                        selected = selectedNetwork == net,
                        onClick = { selectedNetwork = net },
                        label = { Text(net.name) },
                    )
                }
            }

            OutlinedTextField(
                value = lastFourDigits,
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }
                    if (digits.length > 4) {
                        lastFourDigitsError = "Solo se guardan los últimos 4 dígitos. La entrada se rechazó y no se guardó."
                    } else {
                        lastFourDigits = digits
                        lastFourDigitsError = null
                    }
                },
                label = { Text("Últimos 4 dígitos del plástico") },
                placeholder = { Text("1234") },
                supportingText = {
                    lastFourDigitsError?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error)
                    } ?: Text("Solo los 4 dígitos finales. Nunca solicitamos tu número completo ni CVV.")
                },
                isError = lastFourDigitsError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_last_four_digits"),
            )

            OutlinedTextField(
                value = alias,
                onValueChange = { if (it.length <= 80) alias = it },
                label = { Text("Alias personalizado (opcional)") },
                placeholder = { Text("Ej. Mi Visa Oro") },
                isError = isAliasForbidden,
                supportingText = {
                    if (isAliasForbidden) {
                        Text("No ingrese números de tarjeta completos (PAN) ni códigos CVV.", color = MaterialTheme.colorScheme.error)
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = tween(KipuMotionTokens.FastMillis)),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (isDebit) {
                    Text(
                        text = "Cuenta Vinculada",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    if (eligibleAccounts.isEmpty()) {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "No tienes cuentas de ahorro o corriente activas para vincular esta tarjeta.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = isAccountDropdownExpanded,
                            onExpandedChange = { isAccountDropdownExpanded = it },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedTextField(
                                value = selectedAccount?.let { "${it.alias} (${it.currency.name})" } ?: "Seleccionar cuenta",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Cuenta a la que pertenece la tarjeta") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isAccountDropdownExpanded) },
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                            )
                            ExposedDropdownMenu(
                                expanded = isAccountDropdownExpanded,
                                onDismissRequest = { isAccountDropdownExpanded = false },
                            ) {
                                eligibleAccounts.forEach { account ->
                                    DropdownMenuItem(
                                        text = { Text("${account.alias} • ${account.currency.name}") },
                                        onClick = {
                                            selectedAccount = account
                                            isAccountDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Esta tarjeta reflejará los fondos de la cuenta seleccionada. No añadirá saldo por separado.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Condiciones de Crédito",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
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

                    OutlinedTextField(
                        value = creditLimitInput,
                        onValueChange = { creditLimitInput = it },
                        label = { Text("Línea de crédito autorizada (${selectedCurrency.name})") },
                        isError = creditLimitError != null,
                        supportingText = creditLimitError?.let { error ->
                            { Text(error, color = MaterialTheme.colorScheme.error) }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = billingDayInput,
                            onValueChange = { billingDayInput = it },
                            label = { Text("Día de corte (1-31)") },
                            isError = billingDayError != null,
                            supportingText = billingDayError?.let { error ->
                                { Text(error, color = MaterialTheme.colorScheme.error) }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = dueDayInput,
                            onValueChange = { dueDayInput = it },
                            label = { Text("Día de pago (1-31)") },
                            isError = dueDayError != null,
                            supportingText = dueDayError?.let { error ->
                                { Text(error, color = MaterialTheme.colorScheme.error) }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (showDuplicateWarning) {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = "Ya existe una tarjeta con este emisor, red y 4 dígitos. Por favor ingresa un alias distintivo para continuar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Esta tarjeta consumirá 1 cupo de los 4 permitidos en el plan Free.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (lastFourDigits.length != 4) return@Button
                    if (issuer.isBlank() || isIssuerForbidden) return@Button
                    if (isAliasForbidden) return@Button
                    if (isDebit && selectedAccount == null) return@Button
                    if (!isDebit && !areCreditTermsValid) {
                        return@Button
                    }

                    scope.launch {
                        val isDuplicate = viewModel.checkDuplicateCard(
                            issuer = issuer.trim(),
                            network = selectedNetwork,
                            lastFourDigits = lastFourDigits,
                        )
                        if (isDuplicate && alias.isBlank()) {
                            showDuplicateWarning = true
                            return@launch
                        }

                        isSubmitting = true
                        if (isDebit) {
                            val account = selectedAccount ?: return@launch
                            viewModel.registerDebitCard(
                                alias = alias,
                                issuer = issuer,
                                network = selectedNetwork,
                                lastFourDigits = lastFourDigits,
                                linkedAccount = account,
                                preset = selectedPreset,
                                colorToken = selectedPreset?.defaultColorToken,
                                iconToken = selectedPreset?.defaultIconToken,
                            )
                        } else {
                            val limitMinor = creditLimitMinorUnits ?: return@launch
                            val bDay = billingDay?.takeIf { it in 1..31 } ?: return@launch
                            val dDay = dueDay?.takeIf { it in 1..31 } ?: return@launch

                            viewModel.registerCreditCard(
                                alias = alias,
                                issuer = issuer,
                                network = selectedNetwork,
                                lastFourDigits = lastFourDigits,
                                currency = selectedCurrency,
                                creditLimitMinorUnits = limitMinor,
                                billingDay = bDay,
                                dueDay = dDay,
                                preset = selectedPreset,
                                colorToken = selectedPreset?.defaultColorToken,
                                iconToken = selectedPreset?.defaultIconToken,
                            )
                        }
                    }
                },
                enabled = !isSubmitting && lastFourDigits.length == 4 && issuer.isNotBlank() && !isIssuerForbidden && !isAliasForbidden &&
                    (if (isDebit) selectedAccount != null else areCreditTermsValid),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(if (isSubmitting) "Guardando..." else "Registrar Tarjeta")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
