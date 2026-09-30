package com.kipu.app.feature.accounts.presentation.instruments

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.MoneyInputParser
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CardPreset
import com.kipu.app.feature.accounts.domain.model.Card
import com.kipu.app.feature.accounts.domain.model.CreditProductReference
import com.kipu.app.feature.accounts.presentation.AccountUiEvent
import com.kipu.app.feature.accounts.presentation.AccountsViewModel
import com.kipu.app.feature.accounts.presentation.components.InstrumentCardPreview
import com.kipu.app.feature.accounts.presentation.components.CardStylePreset
import com.kipu.app.feature.accounts.presentation.components.CardStylePresets
import com.kipu.app.feature.accounts.presentation.components.CardStyleType
import com.kipu.app.ui.theme.KipuMotionTokens
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import java.util.Locale
import kotlinx.coroutines.launch

private enum class InstrumentKind(val label: String) {
    SAVINGS_DEBIT("Ahorros / Débito"),
    CREDIT_CARD("Tarjeta de crédito"),
    WALLET("Billetera"),
    CASH("Efectivo"),
}

private data class BankChoice(
    val code: String,
    val name: String,
    val accountPreset: AccountPreset,
    val cardPreset: CardPreset,
    val color: Color,
    val textColor: Color,
)

private data class CatalogProductChoice(
    val name: String,
    val network: CardNetwork,
    val reference: CreditProductReference? = null,
    val stylePreset: CardStylePreset? = null,
) {
    val familyId: String get() = stylePreset?.familyId ?: "verified-products-other"
    val familyLabel: String get() = stylePreset?.familyLabel ?: "Otros productos verificados"
}

private val bankChoices = listOf(
    BankChoice("BCP", "BCP", AccountPreset.BCP, CardPreset.BCP_VISA, Color(0xFFFFC600), Color(0xFF002A8F)),
    BankChoice("INTERBANK", "Interbank", AccountPreset.INTERBANK, CardPreset.INTERBANK_VISA, Color(0xFF009940), Color(0xFF191C1E)),
    BankChoice("BBVA", "BBVA", AccountPreset.BBVA, CardPreset.BBVA_VISA, Color(0xFF004481), Color.White),
    BankChoice("SCOTIABANK", "Scotiabank", AccountPreset.SCOTIABANK, CardPreset.SCOTIABANK_MASTERCARD, Color(0xFFED1C24), Color(0xFF000000)),
    BankChoice("BANCO_FALABELLA", "Banco Falabella", AccountPreset.GENERIC, CardPreset.GENERIC, Color(0xFF00843D), Color.White),
    BankChoice("RIPLEY", "Banco Ripley", AccountPreset.GENERIC, CardPreset.GENERIC, Color(0xFF621275), Color.White),
    BankChoice("BANBIF", "BanBif", AccountPreset.GENERIC, CardPreset.GENERIC, Color(0xFF005A8C), Color.White),
)

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun UnifiedInstrumentFormScreen(
    viewModel: AccountsViewModel,
    onNavigateBack: () -> Unit,
    onSaveSuccess: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotionEnabled()
    val creditProducts by viewModel.creditProductCatalog.collectAsState()
    var kind by remember { mutableStateOf(InstrumentKind.SAVINGS_DEBIT) }
    var bank by remember { mutableStateOf(bankChoices.first()) }
    var walletProvider by remember { mutableStateOf(AccountPreset.YAPE) }
    var selectedProductName by remember { mutableStateOf<String?>(null) }
    var selectedStylePresetId by remember { mutableStateOf<String?>(null) }
    var alias by remember { mutableStateOf("") }
    var lastFourDigits by remember { mutableStateOf("") }
    var network by remember { mutableStateOf(CardNetwork.VISA) }
    var currency by remember { mutableStateOf(Currency.PEN) }
    var balanceInput by remember { mutableStateOf("0.00") }
    var creditLimitInput by remember { mutableStateOf("") }
    var billingDayInput by remember { mutableStateOf("15") }
    var dueDayInput by remember { mutableStateOf("5") }
    var selectedIcon by remember { mutableStateOf("account_balance") }
    var selectedColor by remember { mutableStateOf("institution") }
    var isSubmitting by remember { mutableStateOf(false) }
    var accountCreatedBeforeDebitFailure by remember { mutableStateOf(false) }
    var aliasError by remember { mutableStateOf<String?>(null) }
    var balanceError by remember { mutableStateOf<String?>(null) }
    var cardError by remember { mutableStateOf<String?>(null) }
    var limitError by remember { mutableStateOf<String?>(null) }
    var cycleError by remember { mutableStateOf<String?>(null) }
    var hasAliasBeenEdited by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadCreditProductCatalog() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AccountUiEvent.ShowMessage -> Unit
                is AccountUiEvent.Error -> {
                    val hadPartialAccount = accountCreatedBeforeDebitFailure
                    isSubmitting = false
                    snackbarHostState.showSnackbar(
                        if (hadPartialAccount) "La cuenta quedó creada; la tarjeta física no se pudo vincular. Puedes añadirla después."
                        else event.message,
                    )
                    if (hadPartialAccount) finishInstrumentSave(
                        message = "Cuenta creada; puedes completar el alta de la tarjeta de débito desde tus instrumentos.",
                        onSaveSuccess = onSaveSuccess,
                        onNavigateBack = onNavigateBack,
                        snackbarHostState = snackbarHostState,
                        scope = scope,
                    )
                }
            }
        }
    }

    fun setKind(next: InstrumentKind) {
        kind = next
        selectedProductName = null
        selectedStylePresetId = null
        aliasError = null
        balanceError = null
        cardError = null
        limitError = null
        cycleError = null
        if (next == InstrumentKind.CASH) {
            selectedIcon = "payments"
            bank = bankChoices.first()
            lastFourDigits = ""
        } else if (next == InstrumentKind.WALLET) {
            selectedIcon = "wallet"
            lastFourDigits = ""
        } else if (next == InstrumentKind.CREDIT_CARD) {
            selectedIcon = "credit_card"
        } else {
            selectedIcon = "account_balance"
        }
        if (!hasAliasBeenEdited) {
            alias = when (next) {
                InstrumentKind.SAVINGS_DEBIT -> "Cuenta ${bank.name}"
                InstrumentKind.CREDIT_CARD -> "Tarjeta ${bank.name}"
                InstrumentKind.WALLET -> if (walletProvider == AccountPreset.PLIN) "Billetera Plin" else "Billetera Yape"
                InstrumentKind.CASH -> "Efectivo"
            }
        }
    }

    val productsForBank = remember(kind, bank, creditProducts) {
        when (kind) {
            InstrumentKind.CREDIT_CARD -> {
                val remote = creditProducts
                .filter { it.institutionCode.equals(bank.code, ignoreCase = true) }
                .filter { status ->
                    val value = status.verificationStatus.orEmpty()
                    value.isBlank() || (value.contains("VERIFIC", true) && !value.contains("PENDIENTE", true))
                }
                .mapNotNull { product ->
                    val productNetwork = product.cardNetwork?.let(::networkFromCatalog) ?: CardNetwork.OTHER
                    CatalogProductChoice(
                        name = product.productName,
                        network = productNetwork,
                        reference = product,
                        stylePreset = CardStylePresets.forProduct(bank.code, product.productName),
                    )
                }
                if (remote.isNotEmpty()) remote
                else CardStylePresets.forInstitution(bank.code, CardStyleType.CREDIT).map { preset ->
                    CatalogProductChoice(preset.productName, preset.network.toCardNetwork(), stylePreset = preset)
                }
            }
            InstrumentKind.SAVINGS_DEBIT -> CardStylePresets.forInstitution(bank.code, CardStyleType.DEBIT).map { preset ->
                CatalogProductChoice(preset.productName, preset.network.toCardNetwork(), stylePreset = preset)
            }
            else -> emptyList()
        }
    }
    val selectedProduct = productsForBank.firstOrNull { it.name == selectedProductName }
    val selectedStylePreset = selectedProduct?.stylePreset ?: CardStylePresets.byId(selectedStylePresetId)
    val amountForValidation = if (kind == InstrumentKind.CREDIT_CARD) creditLimitInput else balanceInput
    val parsedAmountForValidation = MoneyInputParser.parseMinorUnits(amountForValidation)
    val cycleIsValid = billingDayInput.toIntOrNull() in 1..31 && dueDayInput.toIntOrNull() in 1..31
    val lastFourIsValid = when (kind) {
        InstrumentKind.CREDIT_CARD -> lastFourDigits.length == 4
        InstrumentKind.SAVINGS_DEBIT -> lastFourDigits.isEmpty() || lastFourDigits.length == 4
        else -> true
    }
    val canSaveInstrument = alias.isNotBlank() && !Card.isForbiddenSensitiveCardInput(alias) &&
        parsedAmountForValidation != null && parsedAmountForValidation >= 0L && lastFourIsValid &&
        (kind != InstrumentKind.CREDIT_CARD || cycleIsValid)
    val cardNetworkLabel = networkLabel(network)
    val cardBackground = when {
        kind == InstrumentKind.CASH -> MaterialTheme.colorScheme.surfaceVariant
        kind == InstrumentKind.WALLET -> Color(0xFF005C55)
        selectedColor == "kipu" -> Color(0xFF0F766E)
        else -> bank.color
    }
    val previewStylePreset = selectedStylePreset ?: when (kind) {
        InstrumentKind.CREDIT_CARD -> CardStylePresets.genericCreditStyle
        InstrumentKind.SAVINGS_DEBIT -> CardStylePresets.forInstitution(bank.code, CardStyleType.DEBIT).firstOrNull()
            ?: CardStylePresets.genericDebitStyle
        InstrumentKind.WALLET, InstrumentKind.CASH -> null
    }
    val cardForeground = when {
        kind == InstrumentKind.CASH -> MaterialTheme.colorScheme.onSurface
        kind == InstrumentKind.WALLET || selectedColor == "kipu" -> Color.White
        else -> bank.textColor
    }
    val previewTitle = alias.ifBlank {
        selectedProductName ?: when (kind) {
            InstrumentKind.SAVINGS_DEBIT -> "Cuenta ${bank.name}"
            InstrumentKind.CREDIT_CARD -> "Tu tarjeta ${bank.name}"
            InstrumentKind.WALLET -> if (walletProvider == AccountPreset.PLIN) "Billetera Plin" else "Billetera Yape"
            InstrumentKind.CASH -> "Efectivo"
        }
    }
    val previewAmount = when (kind) {
        InstrumentKind.CREDIT_CARD -> creditLimitInput.takeIf(String::isNotBlank)?.let { "${currencySymbol(currency)} $it" }
        else -> "${currencySymbol(currency)} $balanceInput"
    }
    val previewLabel = if (kind == InstrumentKind.CREDIT_CARD) "Línea de crédito" else "Saldo inicial"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nuevo instrumento") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            InstrumentCardPreview(
                title = previewTitle,
                instrumentType = kind.label,
                subtitle = selectedProductName ?: bank.name.takeIf { kind == InstrumentKind.SAVINGS_DEBIT || kind == InstrumentKind.CREDIT_CARD }
                    ?: if (kind == InstrumentKind.WALLET) walletProvider.defaultName else "Kipu · Andean Modernist",
                lastFourDigits = lastFourDigits,
                network = when (kind) {
                    InstrumentKind.SAVINGS_DEBIT, InstrumentKind.CREDIT_CARD -> cardNetworkLabel.takeIf {
                        selectedProductName != null || lastFourDigits.length == 4
                    }
                    else -> null
                },
                balanceLabel = previewLabel,
                amount = previewAmount,
                backgroundColor = cardBackground,
                foregroundColor = cardForeground,
                stylePreset = previewStylePreset,
                showCardHardware = kind == InstrumentKind.CREDIT_CARD ||
                    kind == InstrumentKind.SAVINGS_DEBIT && lastFourDigits.length == 4,
            )

            Text("¿Qué instrumento vas a agregar?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InstrumentKind.entries.forEach { option ->
                    FilterChip(
                        selected = kind == option,
                        onClick = { setKind(option) },
                        label = { Text(option.label) },
                        leadingIcon = {
                            Icon(
                                imageVector = when (option) {
                                    InstrumentKind.SAVINGS_DEBIT -> Icons.Default.AccountBalance
                                    InstrumentKind.CREDIT_CARD -> Icons.Default.CreditCard
                                    InstrumentKind.WALLET -> Icons.Default.Wallet
                                    InstrumentKind.CASH -> Icons.Default.Payments
                                },
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }

            AnimatedContent(
                targetState = kind,
                transitionSpec = {
                    if (reducedMotion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    else (fadeIn(tween(KipuMotionTokens.FastMillis)) + expandVertically(tween(KipuMotionTokens.MediumMillis))) togetherWith
                        (fadeOut(tween(KipuMotionTokens.QuickMillis)) + shrinkVertically(tween(KipuMotionTokens.QuickMillis)))
                },
                label = "instrument_kind_fields",
            ) { visibleKind ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = if (reducedMotion) tween(0) else tween(KipuMotionTokens.MediumMillis),
                        ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (visibleKind) {
                        InstrumentKind.SAVINGS_DEBIT, InstrumentKind.CREDIT_CARD -> {
                            Text("Entidad bancaria", style = MaterialTheme.typography.titleSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                bankChoices.forEach { option ->
                                    FilterChip(
                                        selected = bank.code == option.code,
                                        onClick = {
                                            bank = option
                                            selectedProductName = null
                                            selectedStylePresetId = null
                                            network = CardNetwork.VISA
                                            if (!hasAliasBeenEdited) alias = if (kind == InstrumentKind.CREDIT_CARD) "Tarjeta ${option.name}" else "Cuenta ${option.name}"
                                        },
                                        label = { Text(option.name) },
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    )
                                }
                            }
                            Text(
                                if (visibleKind == InstrumentKind.CREDIT_CARD) "Producto de crédito · catálogo verificado"
                                else "Cuenta de ahorros · plástico opcional",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (productsForBank.isEmpty()) {
                                Text(
                                    if (visibleKind == InstrumentKind.CREDIT_CARD) "No hay productos verificados para esta entidad. Puedes registrar la tarjeta con el estilo genérico."
                                    else "No hay un preset de débito verificado para esta entidad.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                CardProductFamilyAccordion(
                                    products = productsForBank,
                                    selectedProductName = selectedProductName,
                                    reducedMotion = reducedMotion,
                                    onSelect = { product ->
                                        if (selectedProductName == product.name) {
                                            selectedProductName = null
                                            selectedStylePresetId = null
                                            network = CardNetwork.VISA
                                            if (!hasAliasBeenEdited) alias = "Tarjeta ${bank.name}"
                                        } else {
                                            selectedProductName = product.name
                                            selectedStylePresetId = product.stylePreset?.id
                                            network = product.network
                                            if (!hasAliasBeenEdited) alias = product.name
                                        }
                                    },
                                )
                            }
                            if (visibleKind == InstrumentKind.CREDIT_CARD && selectedProduct?.reference != null) {
                                ReferencialTeaCard(selectedProduct.reference, currency)
                            }
                            if (visibleKind == InstrumentKind.CREDIT_CARD) {
                                Text(
                                    "La TEA mostrada es referencial. Tu tasa real es la de tu contrato; esta selección no guarda una TEA personal.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        InstrumentKind.WALLET -> {
                            Text("Proveedor de billetera", style = MaterialTheme.typography.titleSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(AccountPreset.YAPE, AccountPreset.PLIN).forEach { provider ->
                                    FilterChip(
                                        selected = walletProvider == provider,
                                        onClick = {
                                            walletProvider = provider
                                            if (!hasAliasBeenEdited) alias = if (provider == AccountPreset.PLIN) "Billetera Plin" else "Billetera Yape"
                                        },
                                        label = { Text(if (provider == AccountPreset.PLIN) "Plin" else "Yape") },
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    )
                                }
                            }
                            InfoCard("Las billeteras Yape y Plin se registran como saldos independientes en Kipu. El modelo actual no las vincula a una cuenta o tarjeta de débito.")
                        }
                        InstrumentKind.CASH -> InfoCard("El efectivo es una cuenta líquida local y no consume un cupo de instrumento computable.")
                    }
                }
            }

            OutlinedTextField(
                value = alias,
                onValueChange = { value -> if (value.length <= 80) { alias = value; hasAliasBeenEdited = true; aliasError = null } },
                label = { Text("Alias") },
                placeholder = { Text("Ej. Sueldo BCP") },
                isError = aliasError != null || alias.isNotBlank() && Card.isForbiddenSensitiveCardInput(alias),
                supportingText = aliasError?.let { message -> { Text(message) } }
                    ?: when {
                        alias.isBlank() -> ({ Text("Campo obligatorio") })
                        Card.isForbiddenSensitiveCardInput(alias) -> ({ Text("No ingreses el número completo de la tarjeta ni el CVV.") })
                        else -> null
                    },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(if (kind == InstrumentKind.CREDIT_CARD) "Línea de crédito" else "Saldo inicial", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = if (kind == InstrumentKind.CREDIT_CARD) creditLimitInput else balanceInput,
                onValueChange = { value ->
                    val safe = value.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.').take(14)
                    if (kind == InstrumentKind.CREDIT_CARD) { creditLimitInput = safe; limitError = null }
                    else { balanceInput = safe; balanceError = null }
                },
                label = { Text("Importe en ${currency.name}") },
                prefix = { Text(if (currency == Currency.PEN) "S/ " else "$ ") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = (if (kind == InstrumentKind.CREDIT_CARD) limitError else balanceError) != null || parsedAmountForValidation == null,
                supportingText = (if (kind == InstrumentKind.CREDIT_CARD) limitError else balanceError)?.let { message -> { Text(message) } }
                    ?: if (parsedAmountForValidation == null) ({ Text("Ingresa un importe válido con hasta dos decimales.") }) else null,
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            if (kind != InstrumentKind.CASH) {
                Text("Moneda", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Currency.entries.forEach { option ->
                        FilterChip(
                            selected = currency == option,
                            onClick = { currency = option },
                            label = { Text(option.name) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }

            if (kind == InstrumentKind.SAVINGS_DEBIT || kind == InstrumentKind.CREDIT_CARD) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    networkOptions.forEach { option ->
                        FilterChip(
                        selected = network == option,
                            onClick = {
                                if (selectedProduct?.network != null && selectedProduct.network != option) {
                                    selectedProductName = null
                                    if (!hasAliasBeenEdited) alias = "Tarjeta ${bank.name}"
                                }
                                network = option
                            },
                            label = { Text(networkLabel(option)) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = lastFourDigits,
                    onValueChange = { value ->
                        lastFourDigits = value.filter(Char::isDigit).take(4)
                        cardError = null
                    },
                    label = { Text("Últimos 4 dígitos") },
                    placeholder = { Text("0000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = cardError != null,
                    supportingText = cardError?.let { message -> { Text(message) } }
                        ?: {
                            Text(
                                when {
                                    kind == InstrumentKind.CREDIT_CARD && lastFourDigits.length != 4 -> "Ingresa exactamente cuatro dígitos numéricos."
                                    kind == InstrumentKind.SAVINGS_DEBIT -> "Opcional: registra el plástico vinculado a esta cuenta."
                                    else -> "Solo se guardan estos cuatro dígitos."
                                },
                            )
                        },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (kind == InstrumentKind.CREDIT_CARD) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = billingDayInput,
                        onValueChange = { billingDayInput = it.filter(Char::isDigit).take(2); cycleError = null },
                        label = { Text("Día de corte") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = cycleError != null || billingDayInput.toIntOrNull() !in 1..31,
                        supportingText = if (billingDayInput.toIntOrNull() !in 1..31) ({ Text("Usa un día entre 1 y 31") }) else null,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = dueDayInput,
                        onValueChange = { dueDayInput = it.filter(Char::isDigit).take(2); cycleError = null },
                        label = { Text("Día de pago") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = cycleError != null || dueDayInput.toIntOrNull() !in 1..31,
                        supportingText = if (dueDayInput.toIntOrNull() !in 1..31) ({ Text("Usa un día entre 1 y 31") }) else null,
                        modifier = Modifier.weight(1f),
                    )
                }
                cycleError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }

            Text("Personalización", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("institution" to "Banco", "kipu" to "Kipu teal").forEach { (key, label) ->
                    FilterChip(
                        selected = selectedColor == key,
                        onClick = { selectedColor = key },
                        label = { Text(label) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("account_balance", "credit_card", "wallet", "payments").forEach { icon ->
                    FilterChip(
                        selected = selectedIcon == icon,
                        onClick = { selectedIcon = icon },
                        label = { Text(iconLabel(icon)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }

            if (kind == InstrumentKind.CREDIT_CARD) {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "Kipu nunca solicita ni almacena el número completo, la fecha de vencimiento ni el CVV.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val parsedBalance = MoneyInputParser.parseMinorUnits(if (kind == InstrumentKind.CREDIT_CARD) creditLimitInput else balanceInput)
                    var valid = true
                    if (alias.isBlank()) { aliasError = "Escribe un alias para identificarlo."; valid = false }
                    else if (Card.isForbiddenSensitiveCardInput(alias)) {
                        aliasError = "No ingreses el número completo de la tarjeta ni el CVV."
                        valid = false
                    }
                    if (parsedBalance == null || parsedBalance < 0L) {
                        if (kind == InstrumentKind.CREDIT_CARD) limitError = "Ingresa una línea de crédito válida."
                        else balanceError = "Ingresa un saldo válido."
                        valid = false
                    }
                    if (kind == InstrumentKind.CREDIT_CARD && lastFourDigits.length != 4) {
                        cardError = "Ingresa exactamente cuatro dígitos numéricos."
                        valid = false
                    } else if (kind == InstrumentKind.SAVINGS_DEBIT && lastFourDigits.isNotEmpty() && lastFourDigits.length != 4) {
                        cardError = "Completa los cuatro dígitos o deja el campo vacío."
                        valid = false
                    }
                    val billingDay = billingDayInput.toIntOrNull()
                    val dueDay = dueDayInput.toIntOrNull()
                    if (kind == InstrumentKind.CREDIT_CARD && (billingDay !in 1..31 || dueDay !in 1..31)) {
                        cycleError = "El corte y el pago deben estar entre los días 1 y 31."
                        valid = false
                    }
                    if (!valid) return@Button
                    isSubmitting = true
                    val colorToken = if (selectedColor == "kipu") "custom_kipu_teal" else bank.accountPreset.defaultColorToken
                    val iconToken = selectedIcon
                    val trimmedAlias = alias.trim()
                    when (kind) {
                        InstrumentKind.SAVINGS_DEBIT -> {
                            viewModel.createAccount(
                                alias = trimmedAlias,
                                type = AccountType.SAVINGS,
                                currency = currency,
                                preset = bank.accountPreset,
                                initialBalanceMinorUnits = requireNotNull(parsedBalance),
                                colorToken = colorToken,
                                iconToken = iconToken,
                            ) { account ->
                                if (lastFourDigits.length == 4) {
                                    accountCreatedBeforeDebitFailure = true
                                    viewModel.registerDebitCard(
                                        alias = trimmedAlias,
                                        issuer = bank.name,
                                        network = network,
                                        lastFourDigits = lastFourDigits,
                                        linkedAccount = account,
                                        preset = bank.cardPreset,
                                        colorToken = colorToken,
                                        iconToken = iconToken,
                                        stylePresetId = selectedStylePreset?.id
                                            ?: CardStylePresets.forInstitution(bank.code, CardStyleType.DEBIT).firstOrNull()?.id,
                                    ) {
                                        isSubmitting = false
                                        finishInstrumentSave(
                                            "Cuenta y tarjeta de débito registradas.", onSaveSuccess, onNavigateBack, snackbarHostState, scope,
                                        )
                                    }
                                } else {
                                    isSubmitting = false
                                    finishInstrumentSave("Cuenta de ahorro creada.", onSaveSuccess, onNavigateBack, snackbarHostState, scope)
                                }
                            }
                        }
                        InstrumentKind.CREDIT_CARD -> {
                            viewModel.registerCreditCard(
                                alias = trimmedAlias,
                                issuer = bank.name,
                                network = network,
                                lastFourDigits = lastFourDigits,
                                currency = currency,
                                creditLimitMinorUnits = requireNotNull(parsedBalance),
                                billingDay = requireNotNull(billingDay),
                                dueDay = requireNotNull(dueDay),
                                personalTeaBps = null,
                                preset = bank.cardPreset,
                                colorToken = colorToken,
                                iconToken = iconToken,
                                stylePresetId = selectedStylePreset?.id,
                            ) {
                                isSubmitting = false
                                finishInstrumentSave("Tarjeta de crédito registrada.", onSaveSuccess, onNavigateBack, snackbarHostState, scope)
                            }
                        }
                        InstrumentKind.WALLET, InstrumentKind.CASH -> {
                            val preset = when (kind) {
                                InstrumentKind.WALLET -> walletProvider
                                InstrumentKind.CASH -> AccountPreset.CASH
                                else -> error("Unreachable instrument kind")
                            }
                            val type = if (kind == InstrumentKind.WALLET) AccountType.DIGITAL_WALLET else AccountType.CASH
                            viewModel.createAccount(
                                alias = trimmedAlias,
                                type = type,
                                currency = currency,
                                preset = preset,
                                initialBalanceMinorUnits = requireNotNull(parsedBalance),
                                colorToken = if (selectedColor == "kipu") "custom_kipu_teal" else preset.defaultColorToken,
                                iconToken = iconToken,
                            ) {
                                isSubmitting = false
                                finishInstrumentSave(if (type == AccountType.CASH) "Cuenta de efectivo creada." else "Billetera registrada como saldo independiente.", onSaveSuccess, onNavigateBack, snackbarHostState, scope)
                            }
                        }
                    }
                },
                enabled = !isSubmitting && canSaveInstrument,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(if (isSubmitting) "Guardando…" else "Guardar instrumento")
            }
            if (!isSubmitting && !canSaveInstrument) {
                Text(
                    "Completa los campos obligatorios para guardar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ReferencialTeaCard(product: CreditProductReference, currency: Currency) {
    val low = if (currency == Currency.PEN) product.penTeaMinBps else product.usdTeaMinBps
    val high = if (currency == Currency.PEN) product.penTeaMaxBps else product.usdTeaMaxBps
    val rateText = when {
        low != null && high != null && low != high -> "${percentage(low)}–${percentage(high)}%"
        low != null -> "${percentage(low)}%"
        high != null -> "${percentage(high)}%"
        else -> "No se publica una tasa única validada y desglosada para ${currency.name}."
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("TEA referencial publicada · ${currency.name}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(rateText, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
            Text("Fuente: catálogo SBS de Kipu · ${product.catalogAsOf ?: "fecha no disponible"}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
    }
}

private fun finishInstrumentSave(
    message: String,
    onSaveSuccess: ((String) -> Unit)?,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    if (onSaveSuccess != null) onSaveSuccess(message)
    else scope.launch {
        snackbarHostState.showSnackbar(message)
        onNavigateBack()
    }
}

@Composable
private fun CardProductFamilyAccordion(
    products: List<CatalogProductChoice>,
    selectedProductName: String?,
    reducedMotion: Boolean,
    onSelect: (CatalogProductChoice) -> Unit,
) {
    val families = remember(products) {
        products.groupBy { it.familyLabel }.entries.map { (familyLabel, items) ->
            Triple(familyLabel, familyLabel, items)
        }
    }
    val selectedFamilyId = products.firstOrNull { it.name == selectedProductName }?.familyLabel
    var expandedFamilyId by remember(products) { mutableStateOf(selectedFamilyId ?: families.firstOrNull()?.first) }
    LaunchedEffect(selectedFamilyId) {
        if (selectedFamilyId != null) expandedFamilyId = selectedFamilyId
    }

    Column(
        modifier = Modifier.fillMaxWidth().animateContentSize(
            animationSpec = if (reducedMotion) tween(0) else tween(KipuMotionTokens.MediumMillis),
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        families.forEach { (familyId, familyLabel, familyProducts) ->
            val expanded = expandedFamilyId == familyId
            val rotation by animateFloatAsState(
                targetValue = if (expanded) 180f else 0f,
                animationSpec = if (reducedMotion) tween(0) else tween(KipuMotionTokens.MediumMillis),
                label = "family_chevron_$familyId",
            )
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (expanded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (expanded) MaterialTheme.colorScheme.surfaceTint else MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button) { expandedFamilyId = if (expanded) null else familyId }
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Familia $familyLabel, ${familyProducts.size} opciones"
                        stateDescription = if (expanded) "Expandida" else "Contraída"
                        role = Role.Button
                    },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(familyLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${familyProducts.size} ${if (familyProducts.size == 1) "diseño" else "diseños"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.rotate(rotation))
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = if (reducedMotion) fadeIn(tween(0)) + expandVertically(tween(0))
                else fadeIn(tween(KipuMotionTokens.FastMillis)) + expandVertically(tween(KipuMotionTokens.MediumMillis)),
                exit = if (reducedMotion) fadeOut(tween(0)) + shrinkVertically(tween(0))
                else fadeOut(tween(KipuMotionTokens.QuickMillis)) + shrinkVertically(tween(KipuMotionTokens.QuickMillis)),
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(familyProducts, key = { it.name }) { product ->
                        CardPresetMiniTile(
                            product = product,
                            selected = product.name == selectedProductName,
                            onClick = { onSelect(product) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardPresetMiniTile(
    product: CatalogProductChoice,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val preset = product.stylePreset ?: CardStylePresets.genericCreditStyle
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
    val accessibleDescription = buildString {
        append(product.name)
        append(". Red ${networkLabel(product.network)}.")
        product.stylePreset?.let { append(" Nivel ${it.tierLabel}.") }
        append(if (selected) " Seleccionada." else " No seleccionada.")
    }
    Column(
        modifier = Modifier
            .width(156.dp)
            .heightIn(min = 104.dp)
            .clip(shape)
            .background(preset.gradientBrush)
            .border(
                BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.surfaceTint else MaterialTheme.colorScheme.outlineVariant),
                shape,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibleDescription
                this.selected = selected
                stateDescription = if (selected) "Seleccionada" else "No seleccionada"
            }
            .padding(10.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            text = product.name,
            color = preset.textColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${networkLabel(product.network)}${product.stylePreset?.let { " · ${it.tierLabel}" }.orEmpty()}",
            color = preset.textColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun String.toCardNetwork(): CardNetwork = when (uppercase()) {
    "VISA" -> CardNetwork.VISA
    "MASTERCARD" -> CardNetwork.MASTERCARD
    "AMEX", "AMERICAN EXPRESS" -> CardNetwork.AMEX
    "DINERS" -> CardNetwork.DINERS
    else -> CardNetwork.OTHER
}

private val networkOptions = listOf(CardNetwork.VISA, CardNetwork.MASTERCARD, CardNetwork.AMEX, CardNetwork.DINERS)

private fun networkFromCatalog(network: String): CardNetwork? = when (network.uppercase()) {
    "VISA" -> CardNetwork.VISA
    "MASTERCARD" -> CardNetwork.MASTERCARD
    "AMEX", "AMERICAN EXPRESS" -> CardNetwork.AMEX
    "DINERS" -> CardNetwork.DINERS
    else -> null
}

private fun networkLabel(network: CardNetwork): String = when (network) {
    CardNetwork.VISA -> "Visa"
    CardNetwork.MASTERCARD -> "Mastercard"
    CardNetwork.AMEX -> "Amex"
    CardNetwork.DINERS -> "Diners Club"
    CardNetwork.OTHER -> "Otra red"
}

private fun iconLabel(icon: String): String = when (icon) {
    "account_balance" -> "Banco"
    "credit_card" -> "Tarjeta"
    "wallet" -> "Billetera"
    else -> "Efectivo"
}

private fun percentage(basisPoints: Int): String = String.format(Locale.getDefault(), "%.2f", basisPoints / 100.0)

private fun currencySymbol(currency: Currency): String = if (currency == Currency.PEN) "S/" else "\$"
