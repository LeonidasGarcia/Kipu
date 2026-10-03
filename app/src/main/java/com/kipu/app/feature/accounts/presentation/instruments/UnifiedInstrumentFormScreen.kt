package com.kipu.app.feature.accounts.presentation.instruments

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipu.app.core.finance.domain.MoneyInputParser
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.Card
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CardPreset
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditProductReference
import com.kipu.app.feature.accounts.presentation.AccountUiEvent
import com.kipu.app.feature.accounts.presentation.AccountsViewModel
import com.kipu.app.feature.accounts.presentation.components.CardStylePreset
import com.kipu.app.feature.accounts.presentation.components.CardStylePresets
import com.kipu.app.feature.accounts.presentation.components.CardStyleType
import com.kipu.app.feature.accounts.presentation.components.InstrumentCardPreview
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.KipuMotionTokens
import com.kipu.app.ui.theme.rememberKipuColors
import java.util.Locale
import kotlinx.coroutines.launch

private val KipuTealPrimary: Color @Composable get() = rememberKipuColors().primaryText
private val KipuLightGreenContainer: Color @Composable get() = rememberKipuColors().positiveContainer
private val KipuSurfaceVariantNeutral: Color @Composable get() = rememberKipuColors().surfaceVariant
private val KipuBorderColor: Color @Composable get() = rememberKipuColors().border
private val KipuSuccessGreen: Color @Composable get() = rememberKipuColors().positive
private val KipuInkTitle: Color @Composable get() = rememberKipuColors().inkPrimary
private val KipuTextMuted: Color @Composable get() = if (rememberKipuColors().isDark) rememberKipuColors().inkSecondary else Color(0xFF475569)

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
    onNavigateToCardDetail: ((CreditCard) -> Unit)? = null,
    onNavigateToRecordConsumption: ((CreditCard) -> Unit)? = null,
    initialCreditCard: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val reducedMotion = rememberReducedMotionEnabled()
    val creditProducts by viewModel.creditProductCatalog.collectAsStateWithLifecycle()
    val initialDebitPreset = remember {
        CardStylePresets.forInstitution(bankChoices.first().code, CardStyleType.DEBIT).firstOrNull()
    }

    var kind by rememberSaveable {
        mutableStateOf(if (initialCreditCard) InstrumentKind.CREDIT_CARD else InstrumentKind.SAVINGS_DEBIT)
    }
    var bankCode by rememberSaveable { mutableStateOf(bankChoices.first().code) }
    val bank = bankChoices.firstOrNull { it.code == bankCode } ?: bankChoices.first()
    var walletProvider by rememberSaveable { mutableStateOf(AccountPreset.YAPE) }
    var selectedProductName by rememberSaveable {
        mutableStateOf(if (initialCreditCard) null else initialDebitPreset?.productName)
    }
    var selectedStylePresetId by rememberSaveable {
        mutableStateOf(if (initialCreditCard) null else initialDebitPreset?.id)
    }
    var alias by rememberSaveable {
        mutableStateOf(if (initialCreditCard) "" else "Mi Cuenta de Ahorros")
    }
    var lastFourDigits by rememberSaveable { mutableStateOf("") }
    var network by rememberSaveable { mutableStateOf(CardNetwork.VISA) }
    var currency by rememberSaveable { mutableStateOf(Currency.PEN) }
    var balanceInput by rememberSaveable { mutableStateOf("0.00") }
    var creditLimitInput by rememberSaveable { mutableStateOf("") }
    var billingDayInput by rememberSaveable { mutableStateOf("15") }
    var dueDayInput by rememberSaveable { mutableStateOf("5") }
    var selectedIcon by rememberSaveable { mutableStateOf("account_balance") }
    var selectedColor by rememberSaveable { mutableStateOf("institution") }
    var isBankSelectorExpanded by rememberSaveable { mutableStateOf(false) }
    var isProductTreeExpanded by rememberSaveable { mutableStateOf(false) }
    var isPreviewExpanded by rememberSaveable { mutableStateOf(false) }
    var isPersonalizationExpanded by rememberSaveable { mutableStateOf(false) }
    var hasAliasBeenEdited by rememberSaveable { mutableStateOf(false) }

    // Pristine & submission tracking
    var isSubmitted by rememberSaveable { mutableStateOf(false) }
    var amountTouched by rememberSaveable { mutableStateOf(false) }
    var aliasTouched by rememberSaveable { mutableStateOf(false) }
    var lastFourTouched by rememberSaveable { mutableStateOf(false) }
    var cycleTouched by rememberSaveable { mutableStateOf(false) }

    // Transient UI/async state (never restored to true across recreation)
    var isSubmitting by remember { mutableStateOf(false) }
    var accountCreatedBeforeDebitFailure by remember { mutableStateOf(false) }
    var showSuccessSheet by remember { mutableStateOf(false) }
    var registeredCreditCard by remember { mutableStateOf<CreditCard?>(null) }

    var aliasError by remember { mutableStateOf<String?>(null) }
    var balanceError by remember { mutableStateOf<String?>(null) }
    var cardError by remember { mutableStateOf<String?>(null) }
    var limitError by remember { mutableStateOf<String?>(null) }
    var cycleError by remember { mutableStateOf<String?>(null) }

    val aliasBringIntoViewRequester = remember { BringIntoViewRequester() }
    val amountBringIntoViewRequester = remember { BringIntoViewRequester() }
    val lastFourBringIntoViewRequester = remember { BringIntoViewRequester() }
    val cycleBringIntoViewRequester = remember { BringIntoViewRequester() }

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
        isBankSelectorExpanded = false
        isProductTreeExpanded = false
        isSubmitted = false
        amountTouched = false
        aliasTouched = false
        lastFourTouched = false
        cycleTouched = false
        if (next == InstrumentKind.CASH) {
            selectedIcon = "payments"
            bankCode = bankChoices.first().code
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
    LaunchedEffect(initialCreditCard, kind, productsForBank, selectedProductName) {
        if (initialCreditCard && kind == InstrumentKind.CREDIT_CARD && selectedProductName == null) {
            val preferredProduct = productsForBank.firstOrNull {
                it.network == CardNetwork.VISA && it.name.contains("oro", ignoreCase = true)
            }
            if (preferredProduct != null) {
                selectedProductName = preferredProduct.name
                selectedStylePresetId = preferredProduct.stylePreset?.id
                network = preferredProduct.network
                if (!hasAliasBeenEdited && alias.isBlank()) alias = preferredProduct.name
            }
        }
    }
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
        selectedColor == "kipu" -> rememberKipuColors().primary
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
        kind == InstrumentKind.WALLET || selectedColor == "kipu" -> rememberKipuColors().onPrimary
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

    fun handleSubmit() {
        if (isSubmitting) return
        isSubmitted = true
        var valid = true
        var firstInvalidRequester: BringIntoViewRequester? = null

        if (alias.isBlank()) {
            aliasError = "Escribe un alias para identificarlo."
            valid = false
            if (firstInvalidRequester == null) firstInvalidRequester = aliasBringIntoViewRequester
        } else if (Card.isForbiddenSensitiveCardInput(alias)) {
            aliasError = "No ingreses el número completo de la tarjeta ni el CVV."
            valid = false
            if (firstInvalidRequester == null) firstInvalidRequester = aliasBringIntoViewRequester
        }

        val parsedBalance = MoneyInputParser.parseMinorUnits(
            if (kind == InstrumentKind.CREDIT_CARD) creditLimitInput else balanceInput
        )
        if (parsedBalance == null || parsedBalance < 0L) {
            if (kind == InstrumentKind.CREDIT_CARD) limitError = "Ingresa una línea de crédito válida."
            else balanceError = "Ingresa un saldo válido."
            valid = false
            if (firstInvalidRequester == null) firstInvalidRequester = amountBringIntoViewRequester
        }

        if (kind == InstrumentKind.CREDIT_CARD && lastFourDigits.length != 4) {
            cardError = "Ingresa exactamente cuatro dígitos numéricos."
            valid = false
            if (firstInvalidRequester == null) firstInvalidRequester = lastFourBringIntoViewRequester
        } else if (kind == InstrumentKind.SAVINGS_DEBIT && lastFourDigits.isNotEmpty() && lastFourDigits.length != 4) {
            cardError = "Completa los cuatro dígitos o deja el campo vacío."
            valid = false
            if (firstInvalidRequester == null) firstInvalidRequester = lastFourBringIntoViewRequester
        }

        val billingDay = billingDayInput.toIntOrNull()
        val dueDay = dueDayInput.toIntOrNull()
        if (kind == InstrumentKind.CREDIT_CARD && (billingDay !in 1..31 || dueDay !in 1..31)) {
            cycleError = "El corte y el pago deben estar entre los días 1 y 31."
            valid = false
            if (firstInvalidRequester == null) firstInvalidRequester = cycleBringIntoViewRequester
        }

        if (!valid) {
            firstInvalidRequester?.let { requester ->
                scope.launch {
                    kotlinx.coroutines.yield()
                    requester.bringIntoView()
                }
            }
            return
        }

        isSubmitting = true
        val colorToken = if (selectedColor == "kipu") "custom_kipu_teal" else bank.accountPreset.defaultColorToken
        val iconToken = selectedIcon
        val trimmedAlias = alias.trim()
        when (kind) {
            InstrumentKind.SAVINGS_DEBIT -> {
                val accountTypeToCreate = if (selectedProductName?.startsWith("Cuenta Sueldo", ignoreCase = true) == true) {
                    AccountType.BANK
                } else {
                    AccountType.SAVINGS
                }
                viewModel.createAccount(
                    alias = trimmedAlias,
                    type = accountTypeToCreate,
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
                        finishInstrumentSave("Cuenta creada con éxito.", onSaveSuccess, onNavigateBack, snackbarHostState, scope)
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
                ) { createdCreditCard ->
                    isSubmitting = false
                    registeredCreditCard = createdCreditCard
                    showSuccessSheet = true
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
                    finishInstrumentSave(
                        if (type == AccountType.CASH) "Cuenta de efectivo creada." else "Billetera registrada como saldo independiente.",
                        onSaveSuccess,
                        onNavigateBack,
                        snackbarHostState,
                        scope,
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nuevo instrumento", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, KipuBorderColor),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Button(
                        onClick = ::handleSubmit,
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = rememberKipuColors().primary,
                            contentColor = rememberKipuColors().onPrimary,
                            disabledContainerColor = KipuBorderColor,
                            disabledContentColor = Color(0xFF94A3B8),
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("btn_save_instrument"),
                    ) {
                        Text(
                            text = when {
                                isSubmitting -> "Guardando…"
                                kind == InstrumentKind.CREDIT_CARD -> "Guardar tarjeta de crédito"
                                else -> "Guardar instrumento"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (!canSaveInstrument && isSubmitted) {
                        Text(
                            text = "Revisa los campos destacados antes de guardar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // R8/R9 segmented selector. Other liquid sources stay available below.
            Text(
                text = "Cuenta o tarjeta",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = KipuInkTitle,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = KipuSurfaceVariantNeutral,
                border = BorderStroke(1.dp, KipuBorderColor),
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    InstrumentKindSegment(
                        label = "Ahorros / Débito",
                        icon = Icons.Default.AccountBalance,
                        selected = kind == InstrumentKind.SAVINGS_DEBIT,
                        onClick = { setKind(InstrumentKind.SAVINGS_DEBIT) },
                        modifier = Modifier.weight(1f),
                    )
                    InstrumentKindSegment(
                        label = "Tarjeta de crédito",
                        icon = Icons.Default.CreditCard,
                        selected = kind == InstrumentKind.CREDIT_CARD,
                        onClick = { setKind(InstrumentKind.CREDIT_CARD) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                text = "Otros saldos",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = KipuTextMuted,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(InstrumentKind.WALLET, InstrumentKind.CASH).forEach { option ->
                    val isSelected = kind == option
                    FilterChip(
                        selected = isSelected,
                        onClick = { setKind(option) },
                        label = { Text(option.label, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (option == InstrumentKind.WALLET) Icons.Default.Wallet else Icons.Default.Payments,
                                contentDescription = null,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = rememberKipuColors().primary,
                            selectedLabelColor = rememberKipuColors().onPrimary,
                            containerColor = KipuSurfaceVariantNeutral,
                            labelColor = KipuInkTitle,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) KipuTealPrimary else KipuBorderColor,
                            selectedBorderColor = KipuTealPrimary,
                        ),
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }

            // Animated Kind Content
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
                        InstrumentKind.SAVINGS_DEBIT -> {
                            // R8 Flow: Bank & Accounts Tree Accordion
                            val debitPreset = CardStylePresets.forInstitution(bank.code, CardStyleType.DEBIT).firstOrNull()
                            val branchItems = remember(bank, debitPreset) {
                                listOf(
                                    Triple(
                                        debitPreset?.productName ?: "Tarjeta de Débito ${bank.name}",
                                        "Débito ${debitPreset?.network?.lowercase()?.replaceFirstChar(Char::uppercase) ?: "Visa"} " +
                                            "${if (debitPreset?.tier?.equals("CLASSIC", ignoreCase = true) == true) "Clásica" else debitPreset?.tierLabel ?: "Estándar"} · ${bank.name}",
                                        "Activa",
                                    ),
                                    Triple(
                                        "Cuenta Sueldo ${bank.name}",
                                        "Cuenta para recibir depósitos de sueldo",
                                        "Sueldo",
                                    ),
                                    Triple(
                                        "Cuenta Digital Ahorros",
                                        "Producto de ahorro",
                                        "Ahorros",
                                    ),
                                    Triple(
                                        "Billetera Digital Yape",
                                        "Saldo independiente · Disponible en Kipu",
                                        "Billetera",
                                    ),
                                )
                            }

                            BankTreeSection(
                                bank = bank,
                                bankChoices = bankChoices,
                                isBankSelectorExpanded = isBankSelectorExpanded,
                                onToggleBankSelector = { isBankSelectorExpanded = !isBankSelectorExpanded },
                                onSelectBank = { option ->
                                    bankCode = option.code
                                    selectedProductName = null
                                    selectedStylePresetId = null
                                    network = CardNetwork.VISA
                                    if (!hasAliasBeenEdited) alias = "Cuenta ${option.name}"
                                    isBankSelectorExpanded = false
                                    isProductTreeExpanded = false
                                },
                                countLabel = "4 opciones disponibles",
                                headerTitle = "BANCO Y PRODUCTO ASOCIADO",
                                reducedMotion = reducedMotion,
                            ) {
                                if (selectedProductName != null && !isProductTreeExpanded) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = rememberKipuColors().selectedSurface,
                                        border = BorderStroke(1.5.dp, KipuTealPrimary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { isProductTreeExpanded = true }
                                            .padding(start = 28.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Producto seleccionado",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = KipuTealPrimary,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                                Text(
                                                    text = selectedProductName.orEmpty(),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = KipuInkTitle,
                                                )
                                            }
                                            TextButton(onClick = { isProductTreeExpanded = true }) {
                                                Text("Cambiar", color = KipuTealPrimary, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateContentSize(
                                                animationSpec = if (reducedMotion) tween(0) else tween(KipuMotionTokens.MediumMillis),
                                            ),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        branchItems.forEachIndexed { index, (title, subtitle, badge) ->
                                            val isSelected = selectedProductName == title
                                            TreeBranchItem(
                                                isFirst = index == 0,
                                                isLast = index == branchItems.size - 1,
                                                isSelected = isSelected,
                                            ) {
                                                BranchProductCard(
                                                    title = title,
                                                    subtitle = subtitle,
                                                    badgeText = badge,
                                                    isBadgePrimary = index == 0,
                                                    isSelected = isSelected,
                                                    onClick = {
                                                        if (index == 3) {
                                                            setKind(InstrumentKind.WALLET)
                                                            walletProvider = AccountPreset.YAPE
                                                            if (!hasAliasBeenEdited) alias = "Billetera Yape"
                                                        } else if (isSelected) {
                                                            selectedProductName = null
                                                            selectedStylePresetId = null
                                                            network = CardNetwork.VISA
                                                            if (!hasAliasBeenEdited) alias = "Cuenta ${bank.name}"
                                                        } else {
                                                            selectedProductName = title
                                                            isProductTreeExpanded = false
                                                            if (index == 0) {
                                                                selectedStylePresetId = debitPreset?.id
                                                                network = debitPreset?.network?.toCardNetwork() ?: CardNetwork.VISA
                                                                if (!hasAliasBeenEdited) alias = "Cuenta ${bank.name}"
                                                            } else {
                                                                selectedStylePresetId = debitPreset?.id
                                                                if (!hasAliasBeenEdited) alias = title
                                                            }
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                        DashedTreeAction(
                                            label = "+ Agregar mi propia cuenta personalizada",
                                            onClick = {
                                                selectedProductName = null
                                                selectedStylePresetId = null
                                                isProductTreeExpanded = false
                                                if (!hasAliasBeenEdited) alias = "Mi cuenta personalizada"
                                            },
                                        )
                                        if (selectedProductName != null) {
                                            TextButton(
                                                onClick = { isProductTreeExpanded = false },
                                                modifier = Modifier.align(Alignment.End),
                                            ) {
                                                Text("Plegar opciones", color = KipuTealPrimary, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        InstrumentKind.CREDIT_CARD -> {
                            // R9 Flow: Bank & Verified Credit Products Tree Accordion
                            BankTreeSection(
                                bank = bank,
                                bankChoices = bankChoices,
                                isBankSelectorExpanded = isBankSelectorExpanded,
                                onToggleBankSelector = { isBankSelectorExpanded = !isBankSelectorExpanded },
                                onSelectBank = { option ->
                                    bankCode = option.code
                                    selectedProductName = null
                                    selectedStylePresetId = null
                                    network = CardNetwork.VISA
                                    if (!hasAliasBeenEdited) alias = "Tarjeta ${option.name}"
                                    isBankSelectorExpanded = false
                                    isProductTreeExpanded = false
                                },
                                countLabel = "${productsForBank.size} disponibles",
                                headerTitle = "BANCO O ENTIDAD FINANCIERA",
                                reducedMotion = reducedMotion,
                            ) {
                                if (selectedProductName != null && !isProductTreeExpanded) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = rememberKipuColors().selectedSurface,
                                        border = BorderStroke(1.5.dp, KipuTealPrimary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { isProductTreeExpanded = true }
                                            .padding(start = 28.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Tarjeta seleccionada",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = KipuTealPrimary,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                                Text(
                                                    text = selectedProductName.orEmpty(),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = KipuInkTitle,
                                                )
                                            }
                                            TextButton(onClick = { isProductTreeExpanded = true }) {
                                                Text("Cambiar", color = KipuTealPrimary, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                } else {
                                    if (productsForBank.isEmpty()) {
                                        Text(
                                            text = "No hay productos verificados para esta entidad. Puedes registrar la tarjeta con el estilo genérico.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = KipuTextMuted,
                                            modifier = Modifier.padding(start = 28.dp),
                                        )
                                    } else {
                                        CreditProductFamilyTree(
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
                                                    isProductTreeExpanded = false
                                                    if (!hasAliasBeenEdited) alias = product.name
                                                }
                                            },
                                        )
                                    }
                                    DashedTreeAction(
                                        label = "+ Agregar mi propia tarjeta / producto personalizado",
                                        onClick = {
                                            selectedProductName = null
                                            selectedStylePresetId = null
                                            network = CardNetwork.VISA
                                            isProductTreeExpanded = false
                                            if (!hasAliasBeenEdited) alias = "Mi tarjeta personalizada"
                                        },
                                    )
                                    if (selectedProductName != null) {
                                        TextButton(
                                            onClick = { isProductTreeExpanded = false },
                                            modifier = Modifier.align(Alignment.End),
                                        ) {
                                            Text("Plegar opciones", color = KipuTealPrimary, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }

                            if (selectedProduct?.reference != null) {
                                ReferencialTeaCard(selectedProduct.reference, currency)
                            }
                            Text(
                                text = "La TEA mostrada es referencial. Tu tasa real es la de tu contrato; esta selección no guarda una TEA personal.",
                                style = MaterialTheme.typography.bodySmall,
                                color = KipuTextMuted,
                            )
                        }

                        InstrumentKind.WALLET -> {
                            Text("Proveedor de billetera", style = MaterialTheme.typography.titleSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(AccountPreset.YAPE, AccountPreset.PLIN).forEach { provider ->
                                    val isSelected = walletProvider == provider
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            walletProvider = provider
                                            if (!hasAliasBeenEdited) alias = if (provider == AccountPreset.PLIN) "Billetera Plin" else "Billetera Yape"
                                        },
                                        label = { Text(if (provider == AccountPreset.PLIN) "Plin" else "Yape") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = rememberKipuColors().primary,
                                            selectedLabelColor = rememberKipuColors().onPrimary,
                                            containerColor = KipuSurfaceVariantNeutral,
                                            labelColor = KipuInkTitle,
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSelected,
                                            borderColor = if (isSelected) KipuTealPrimary else KipuBorderColor,
                                            selectedBorderColor = KipuTealPrimary,
                                        ),
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    )
                                }
                            }
                            InfoCard("Las billeteras Yape y Plin se registran como saldos independientes en Kipu. El modelo actual no las vincula a una cuenta o tarjeta de débito.")
                        }

                        InstrumentKind.CASH -> {
                            InfoCard("El efectivo es una cuenta líquida local y no consume un cupo de instrumento computable.")
                        }
                    }
                }
            }

            // Alias Input
            val aliasHasError = (isSubmitted || aliasTouched) && (
                aliasError != null || alias.isBlank() || Card.isForbiddenSensitiveCardInput(alias)
            )
            OutlinedTextField(
                value = alias,
                onValueChange = { value ->
                    if (value.length <= 80) {
                        alias = value
                        hasAliasBeenEdited = true
                        aliasTouched = true
                        aliasError = null
                    }
                },
                label = { Text("Alias") },
                placeholder = { Text(if (kind == InstrumentKind.CREDIT_CARD) "Ej. Tarjeta BCP Oro" else "Ej. Sueldo BCP") },
                isError = aliasHasError,
                supportingText = when {
                    (isSubmitted || aliasTouched) && aliasError != null -> ({ Text(aliasError!!) })
                    (isSubmitted || aliasTouched) && alias.isBlank() -> ({ Text("Campo obligatorio") })
                    (isSubmitted || aliasTouched) && Card.isForbiddenSensitiveCardInput(alias) -> ({ Text("No ingreses el número completo de la tarjeta ni el CVV.") })
                    else -> ({ Text("Nombre descriptivo para reconocer tu instrumento.") })
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(aliasBringIntoViewRequester),
            )

            // Balance or Credit Limit
            Text(
                text = if (kind == InstrumentKind.CREDIT_CARD) "Línea de crédito autorizada" else "Saldo inicial",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = KipuInkTitle,
            )
            val amountHasError = (isSubmitted || amountTouched) && (
                (if (kind == InstrumentKind.CREDIT_CARD) limitError else balanceError) != null ||
                    parsedAmountForValidation == null ||
                    parsedAmountForValidation < 0L
            )
            OutlinedTextField(
                value = if (kind == InstrumentKind.CREDIT_CARD) creditLimitInput else balanceInput,
                onValueChange = { value ->
                    val safe = value.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.').take(14)
                    amountTouched = true
                    if (kind == InstrumentKind.CREDIT_CARD) { creditLimitInput = safe; limitError = null }
                    else { balanceInput = safe; balanceError = null }
                },
                label = { Text("Importe en ${currency.name}") },
                prefix = { Text(if (currency == Currency.PEN) "S/ " else "$ ") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = amountHasError,
                supportingText = when {
                    (isSubmitted || amountTouched) && (if (kind == InstrumentKind.CREDIT_CARD) limitError else balanceError) != null -> {
                        { Text((if (kind == InstrumentKind.CREDIT_CARD) limitError else balanceError)!!) }
                    }
                    (isSubmitted || amountTouched) && (parsedAmountForValidation == null || parsedAmountForValidation < 0L) -> {
                        { Text("Ingresa un importe válido con hasta dos decimales.") }
                    }
                    kind == InstrumentKind.CREDIT_CARD -> {
                        { Text("Línea asignada en tu contrato de tarjeta.") }
                    }
                    else -> {
                        { Text("Saldo disponible al registrar la cuenta.") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(amountBringIntoViewRequester),
            )

            // Currency Chips
            if (kind != InstrumentKind.CASH) {
                Text("Moneda", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = KipuInkTitle)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Currency.entries.forEach { option ->
                        val isSelected = currency == option
                        FilterChip(
                            selected = isSelected,
                            onClick = { currency = option },
                            label = { Text(option.name, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = rememberKipuColors().primary,
                                selectedLabelColor = rememberKipuColors().onPrimary,
                                containerColor = KipuSurfaceVariantNeutral,
                                labelColor = KipuInkTitle,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) KipuTealPrimary else KipuBorderColor,
                                selectedBorderColor = KipuTealPrimary,
                            ),
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }

            // Network & Last 4 digits (NO PAN, NO MM/AA, NO CVV)
            if (kind == InstrumentKind.SAVINGS_DEBIT || kind == InstrumentKind.CREDIT_CARD) {
                Text("Red de la tarjeta", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = KipuInkTitle)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    networkOptions.forEach { option ->
                        val isSelected = network == option
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (selectedProduct?.network != null && selectedProduct.network != option) {
                                    selectedProductName = null
                                    if (!hasAliasBeenEdited) alias = "Tarjeta ${bank.name}"
                                }
                                network = option
                            },
                            label = { Text(networkLabel(option), fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = rememberKipuColors().primary,
                                selectedLabelColor = rememberKipuColors().onPrimary,
                                containerColor = KipuSurfaceVariantNeutral,
                                labelColor = KipuInkTitle,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) KipuTealPrimary else KipuBorderColor,
                                selectedBorderColor = KipuTealPrimary,
                            ),
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }

                val cardHasError = (isSubmitted || lastFourTouched) && (
                    cardError != null ||
                        (kind == InstrumentKind.CREDIT_CARD && lastFourDigits.length != 4) ||
                        (kind == InstrumentKind.SAVINGS_DEBIT && lastFourDigits.isNotEmpty() && lastFourDigits.length != 4)
                )
                OutlinedTextField(
                    value = lastFourDigits,
                    onValueChange = { value ->
                        lastFourDigits = value.filter(Char::isDigit).take(4)
                        lastFourTouched = true
                        cardError = null
                    },
                    label = { Text("Últimos 4 dígitos del plástico") },
                    placeholder = { Text("7548") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = cardHasError,
                    supportingText = when {
                        (isSubmitted || lastFourTouched) && cardError != null -> ({ Text(cardError!!) })
                        (isSubmitted || lastFourTouched) && kind == InstrumentKind.CREDIT_CARD && lastFourDigits.length != 4 -> ({
                            Text("Ingresa exactamente cuatro dígitos numéricos.")
                        })
                        (isSubmitted || lastFourTouched) && kind == InstrumentKind.SAVINGS_DEBIT && lastFourDigits.isNotEmpty() && lastFourDigits.length != 4 -> ({
                            Text("Completa los cuatro dígitos o deja el campo vacío.")
                        })
                        kind == InstrumentKind.SAVINGS_DEBIT -> ({
                            Text("Opcional: registra el plástico vinculado a esta cuenta.")
                        })
                        else -> ({
                            Text("Solo los 4 dígitos finales. Nunca solicitamos tu número completo, vencimiento ni CVV.")
                        })
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(lastFourBringIntoViewRequester),
                )
            }

            // Billing and Due Days for Credit Card
            if (kind == InstrumentKind.CREDIT_CARD) {
                Text("Ciclo de facturación", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = KipuInkTitle)
                val billingHasError = (isSubmitted || cycleTouched) && (cycleError != null || billingDayInput.toIntOrNull() !in 1..31)
                val dueHasError = (isSubmitted || cycleTouched) && (cycleError != null || dueDayInput.toIntOrNull() !in 1..31)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(cycleBringIntoViewRequester),
                ) {
                    OutlinedTextField(
                        value = billingDayInput,
                        onValueChange = {
                            billingDayInput = it.filter(Char::isDigit).take(2)
                            cycleTouched = true
                            cycleError = null
                        },
                        label = { Text("Día de corte") },
                        placeholder = { Text("15") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        isError = billingHasError,
                        supportingText = if (billingHasError) ({ Text("Día entre 1 y 31") }) else null,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = dueDayInput,
                        onValueChange = {
                            dueDayInput = it.filter(Char::isDigit).take(2)
                            cycleTouched = true
                            cycleError = null
                        },
                        label = { Text("Día de pago") },
                        placeholder = { Text("05") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        isError = dueHasError,
                        supportingText = if (dueHasError) ({ Text("Día entre 1 y 31") }) else null,
                        modifier = Modifier.weight(1f),
                    )
                }
                if ((isSubmitted || cycleTouched) && cycleError != null) {
                    Text(cycleError!!, color = MaterialTheme.colorScheme.error)
                }
            }

            // Strict Privacy and Security Card
            if (kind == InstrumentKind.CREDIT_CARD) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = KipuSurfaceVariantNeutral),
                    border = BorderStroke(1.dp, KipuBorderColor),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = KipuTealPrimary)
                        Text(
                            text = "Kipu nunca solicita ni almacena el número completo, la fecha de vencimiento ni el CVV.",
                            style = MaterialTheme.typography.bodySmall,
                            color = KipuInkTitle,
                        )
                    }
                }
            }

            // Personalización (plegable)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = KipuSurfaceVariantNeutral,
                border = BorderStroke(1.dp, KipuBorderColor),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isPersonalizationExpanded = !isPersonalizationExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Personalización",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = KipuInkTitle,
                            )
                            Text(
                                text = "${if (selectedColor == "kipu") "Kipu teal" else "Color del banco"} · ${iconLabel(selectedIcon)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = KipuTextMuted,
                            )
                        }
                        IconButton(
                            onClick = { isPersonalizationExpanded = !isPersonalizationExpanded },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = if (isPersonalizationExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isPersonalizationExpanded) "Plegar personalización" else "Desplegar personalización",
                                tint = KipuInkTitle,
                            )
                        }
                    }

                    if (isPersonalizationExpanded) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Color",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = KipuInkTitle,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("institution" to "Banco", "kipu" to "Kipu teal").forEach { (key, label) ->
                                val isSelected = selectedColor == key
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedColor = key },
                                    label = { Text(label, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = rememberKipuColors().primary,
                                        selectedLabelColor = rememberKipuColors().onPrimary,
                                        containerColor = KipuSurfaceVariantNeutral,
                                        labelColor = KipuInkTitle,
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = if (isSelected) KipuTealPrimary else KipuBorderColor,
                                        selectedBorderColor = KipuTealPrimary,
                                    ),
                                    modifier = Modifier.heightIn(min = 48.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Icono representativo",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = KipuInkTitle,
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("account_balance", "credit_card", "wallet", "payments").forEach { icon ->
                                val isSelected = selectedIcon == icon
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedIcon = icon },
                                    label = { Text(iconLabel(icon)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = rememberKipuColors().primary,
                                        selectedLabelColor = rememberKipuColors().onPrimary,
                                        containerColor = KipuSurfaceVariantNeutral,
                                        labelColor = KipuInkTitle,
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = if (isSelected) KipuTealPrimary else KipuBorderColor,
                                        selectedBorderColor = KipuTealPrimary,
                                    ),
                                    modifier = Modifier.heightIn(min = 48.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Vista previa del instrumento (plegable)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = KipuSurfaceVariantNeutral,
                border = BorderStroke(1.dp, KipuBorderColor),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isPreviewExpanded = !isPreviewExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Vista previa del instrumento",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = KipuInkTitle,
                            )
                            Text(
                                text = previewTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = KipuTextMuted,
                            )
                        }
                        IconButton(
                            onClick = { isPreviewExpanded = !isPreviewExpanded },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = if (isPreviewExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isPreviewExpanded) "Plegar vista previa" else "Desplegar vista previa",
                                tint = KipuInkTitle,
                            )
                        }
                    }

                    if (isPreviewExpanded) {
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(
                                    animationSpec = if (reducedMotion) tween(0) else tween(KipuMotionTokens.MediumMillis),
                                ),
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
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // R9 Flow: Modal Bottom Sheet on Successful Credit Card Registration
    if (showSuccessSheet && registeredCreditCard != null) {
        R9CreditCardSuccessBottomSheet(
            card = registeredCreditCard!!,
            bankChoice = bank,
            reducedMotion = reducedMotion,
            onDismiss = {
                showSuccessSheet = false
                finishInstrumentSave("Tarjeta de crédito registrada.", onSaveSuccess, onNavigateBack, snackbarHostState, scope)
            },
            onViewDetail = { card ->
                showSuccessSheet = false
                if (onNavigateToCardDetail != null) {
                    onNavigateToCardDetail(card)
                } else {
                    finishInstrumentSave("Tarjeta de crédito registrada.", onSaveSuccess, onNavigateBack, snackbarHostState, scope)
                }
            },
            onRecordFirstConsumption = { card ->
                showSuccessSheet = false
                if (onNavigateToRecordConsumption != null) {
                    onNavigateToRecordConsumption(card)
                } else {
                    finishInstrumentSave("Tarjeta de crédito registrada.", onSaveSuccess, onNavigateBack, snackbarHostState, scope)
                }
            },
        )
    }
}

@Composable
private fun BankTreeSection(
    bank: BankChoice,
    bankChoices: List<BankChoice>,
    isBankSelectorExpanded: Boolean,
    onToggleBankSelector: () -> Unit,
    onSelectBank: (BankChoice) -> Unit,
    countLabel: String,
    headerTitle: String,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val bankChevronRotation by animateFloatAsState(
        targetValue = if (isBankSelectorExpanded) 180f else 0f,
        animationSpec = if (reducedMotion) tween(0) else tween(KipuMotionTokens.MediumMillis),
        label = "bankChevronRotation",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = headerTitle,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                color = KipuTextMuted,
            )
            TextButton(
                onClick = onToggleBankSelector,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = if (isBankSelectorExpanded) "Cerrar banco ✕" else "Cambiar banco ⇄",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = KipuTealPrimary,
                )
            }
        }

        // Bank header card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = rememberKipuColors().surface,
            border = BorderStroke(1.dp, KipuBorderColor),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggleBankSelector)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Banco ${bank.name}, tocar para cambiar banco"
                    stateDescription = if (isBankSelectorExpanded) "Selector desplegado" else "Banco seleccionado"
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bank.color),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = bank.name.take(3).uppercase(),
                        color = bank.textColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Banco de Crédito ${bank.name}".takeIf { bank.code == "BCP" } ?: bank.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = KipuInkTitle,
                    )
                    Text(
                        text = "Entidad vinculada · ${bank.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = KipuTextMuted,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = KipuLightGreenContainer,
                ) {
                    Text(
                        text = countLabel,
                        color = KipuTealPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = KipuTextMuted,
                    modifier = Modifier.rotate(bankChevronRotation),
                )
            }
        }

        // Expanded bank selector dropdown
        AnimatedVisibility(
            visible = isBankSelectorExpanded,
            enter = if (reducedMotion) fadeIn(tween(0)) + expandVertically(tween(0))
            else fadeIn(tween(KipuMotionTokens.FastMillis)) + expandVertically(tween(KipuMotionTokens.MediumMillis)),
            exit = if (reducedMotion) fadeOut(tween(0)) + shrinkVertically(tween(0))
            else fadeOut(tween(KipuMotionTokens.QuickMillis)) + shrinkVertically(tween(KipuMotionTokens.QuickMillis)),
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = KipuSurfaceVariantNeutral),
                border = BorderStroke(1.dp, KipuBorderColor),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Selecciona una entidad bancaria:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = KipuTextMuted,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                    bankChoices.forEach { option ->
                        val isSelected = bank.code == option.code
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) rememberKipuColors().surface else Color.Transparent,
                            border = if (isSelected) BorderStroke(1.5.dp, KipuTealPrimary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.RadioButton) { onSelectBank(option) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(option.color),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        option.name.take(3).uppercase(),
                                        color = option.textColor,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Text(
                                    text = option.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = KipuInkTitle,
                                    modifier = Modifier.weight(1f),
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Seleccionado",
                                        tint = KipuTealPrimary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Tree root connector
        Box(
            modifier = Modifier
                .padding(start = 27.dp)
                .width(2.dp)
                .height(8.dp)
                .background(KipuBorderColor),
        )

        // Tree content branches
        content()
    }
}

@Composable
private fun InstrumentKindSegment(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val colors = rememberKipuColors()
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) colors.primary else Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
                this.role = Role.RadioButton
            }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) colors.onPrimary else KipuTextMuted,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = if (selected) colors.onPrimary else KipuInkTitle,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 2,
            softWrap = true,
        )
    }
}

@Composable
private fun DashedTreeAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dashedBorderColor = KipuTealPrimary.copy(alpha = 0.55f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                color = dashedBorderColor,
                topLeft = Offset.Zero,
                size = Size(size.width, size.height),
                cornerRadius = CornerRadius(14.dp.toPx()),
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())),
                ),
            )
        }
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            color = KipuTealPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TreeBranchItem(
    isFirst: Boolean,
    isLast: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val lineColor = KipuBorderColor
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val lineX = size.width / 2f
                val centerY = size.height / 2f
                val strokeWidth = 2.dp.toPx()
                // Top segment from parent/previous branch
                drawLine(
                    color = lineColor,
                    start = Offset(lineX, 0f),
                    end = Offset(lineX, centerY),
                    strokeWidth = strokeWidth,
                )
                // Bottom segment to next branch (if not last)
                if (!isLast) {
                    drawLine(
                        color = lineColor,
                        start = Offset(lineX, centerY),
                        end = Offset(lineX, size.height),
                        strokeWidth = strokeWidth,
                    )
                }
                // Horizontal arm to card
                drawLine(
                    color = lineColor,
                    start = Offset(lineX, centerY),
                    end = Offset(size.width, centerY),
                    strokeWidth = strokeWidth,
                )
            }

            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) rememberKipuColors().primary else rememberKipuColors().surface)
                    .border(
                        BorderStroke(if (isSelected) 2.dp else 1.5.dp, if (isSelected) KipuTealPrimary else KipuBorderColor),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

@Composable
private fun BranchProductCard(
    title: String,
    subtitle: String?,
    badgeText: String?,
    isBadgePrimary: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) rememberKipuColors().selectedSurface else rememberKipuColors().surface,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) KipuTealPrimary else KipuBorderColor,
        ),
        shadowElevation = if (isSelected) 1.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.selected = isSelected
                role = Role.RadioButton
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = KipuInkTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    badgeText?.let { badge ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isBadgePrimary) KipuLightGreenContainer else KipuSurfaceVariantNeutral,
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (isBadgePrimary) KipuTealPrimary else KipuTextMuted,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = KipuTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Seleccionado",
                    tint = KipuSuccessGreen,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun CreditProductFamilyTree(
    products: List<CatalogProductChoice>,
    selectedProductName: String?,
    reducedMotion: Boolean,
    onSelect: (CatalogProductChoice) -> Unit,
) {
    val families = remember(products) {
        products.groupBy { it.familyLabel }.entries.map { (label, items) ->
            Triple(label, label, items)
        }
    }
    val selectedFamily = products.firstOrNull { it.name == selectedProductName }?.familyLabel
    var expandedFamily by remember(products) { mutableStateOf(selectedFamily ?: families.firstOrNull()?.first) }
    LaunchedEffect(selectedFamily) {
        if (selectedFamily != null) expandedFamily = selectedFamily
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = if (reducedMotion) tween(0) else tween(KipuMotionTokens.MediumMillis),
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        families.forEachIndexed { familyIndex, (familyId, familyLabel, familyProducts) ->
            val isExpanded = expandedFamily == familyId
            val hasSelectedProduct = familyProducts.any { it.name == selectedProductName }
            val chevronRotation by animateFloatAsState(
                targetValue = if (isExpanded) 180f else 0f,
                animationSpec = if (reducedMotion) tween(0) else tween(KipuMotionTokens.MediumMillis),
                label = "familyChevron_$familyId",
            )

            TreeBranchItem(
                isFirst = familyIndex == 0,
                isLast = familyIndex == families.size - 1 && !isExpanded,
                isSelected = hasSelectedProduct,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (hasSelectedProduct) rememberKipuColors().selectedSurface else rememberKipuColors().surface,
                    border = BorderStroke(
                        width = if (hasSelectedProduct) 1.5.dp else 1.dp,
                        color = if (hasSelectedProduct) KipuTealPrimary else KipuBorderColor,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) {
                            expandedFamily = if (isExpanded) null else familyId
                        }
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Familia $familyLabel, ${familyProducts.size} opciones"
                            stateDescription = if (isExpanded) "Expandida" else "Contraída"
                            role = Role.Button
                        },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = familyLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = KipuInkTitle,
                            )
                            Text(
                                text = "${familyProducts.size} ${if (familyProducts.size == 1) "diseño verificado" else "diseños verificados"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = KipuTextMuted,
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = KipuTextMuted,
                            modifier = Modifier.rotate(chevronRotation),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = if (reducedMotion) fadeIn(tween(0)) + expandVertically(tween(0))
                else fadeIn(tween(KipuMotionTokens.FastMillis)) + expandVertically(tween(KipuMotionTokens.MediumMillis)),
                exit = if (reducedMotion) fadeOut(tween(0)) + shrinkVertically(tween(0))
                else fadeOut(tween(KipuMotionTokens.QuickMillis)) + shrinkVertically(tween(KipuMotionTokens.QuickMillis)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, top = 4.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    familyProducts.forEachIndexed { prodIndex, product ->
                        val isSelected = product.name == selectedProductName
                        TreeBranchItem(
                            isFirst = prodIndex == 0,
                            isLast = prodIndex == familyProducts.size - 1,
                            isSelected = isSelected,
                        ) {
                            BranchProductCard(
                                title = product.name,
                                subtitle = "${networkLabel(product.network)}${product.stylePreset?.let { " · ${it.tierLabel}" }.orEmpty()}",
                                badgeText = product.stylePreset?.tierLabel,
                                isBadgePrimary = isSelected,
                                isSelected = isSelected,
                                onClick = { onSelect(product) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun R9CreditCardSuccessBottomSheet(
    card: CreditCard,
    bankChoice: BankChoice,
    reducedMotion: Boolean,
    onDismiss: () -> Unit,
    onViewDetail: (CreditCard) -> Unit,
    onRecordFirstConsumption: (CreditCard) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isMounted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isMounted = true
    }
    val checkScale by animateFloatAsState(
        targetValue = if (isMounted) 1f else 0f,
        animationSpec = if (reducedMotion) tween(0) else spring(
            dampingRatio = 0.55f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "successCheckScale",
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = rememberKipuColors().surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(rememberKipuColors().dragHandle),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Elastic pop-in green check squircle
            Box(
                modifier = Modifier
                    .scale(checkScale)
                    .size(68.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(KipuLightGreenContainer),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(rememberKipuColors().primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Éxito",
                        tint = rememberKipuColors().onPrimary,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            // Title & subtitle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "¡Tarjeta registrada con éxito!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = KipuInkTitle,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Tu ${card.alias ?: card.issuer} ha sido vinculada a tu panel de instrumentos financieros.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = KipuTextMuted,
                    textAlign = TextAlign.Center,
                )
            }

            // Binding summary snippet
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = KipuSurfaceVariantNeutral,
                border = BorderStroke(1.dp, KipuBorderColor),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(bankChoice.color),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = bankChoice.name.take(3).uppercase(),
                            color = bankChoice.textColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "${card.issuer} ${card.alias ?: card.network.name} (•••• ${card.lastFourDigits})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = KipuInkTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(KipuSuccessGreen),
                            )
                            Text(
                                text = "Activa para registrar consumos",
                                style = MaterialTheme.typography.labelSmall,
                                color = KipuSuccessGreen,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            // Authorized credit line
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = rememberKipuColors().surface,
                border = BorderStroke(1.dp, KipuBorderColor),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Línea de crédito autorizada",
                            style = MaterialTheme.typography.labelSmall,
                            color = KipuTextMuted,
                        )
                        Text(
                            text = "${currencySymbol(card.currency)} ${String.format(Locale.getDefault(), "%,.2f", card.creditLimitMinorUnits / 100.0)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = KipuInkTitle,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(KipuLightGreenContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreditCard,
                            contentDescription = null,
                            tint = KipuTealPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // Billing and due days
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = rememberKipuColors().surface,
                    border = BorderStroke(1.dp, KipuBorderColor),
                    modifier = Modifier.weight(1f),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = KipuTextMuted,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "Próximo corte",
                                style = MaterialTheme.typography.labelSmall,
                                color = KipuTextMuted,
                            )
                        }
                        Text(
                            text = "${card.billingDay.toString().padStart(2, '0')} de cada mes",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = KipuInkTitle,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = rememberKipuColors().surface,
                    border = BorderStroke(1.dp, KipuBorderColor),
                    modifier = Modifier.weight(1f),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = KipuTextMuted,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "Próx. vencimiento",
                                style = MaterialTheme.typography.labelSmall,
                                color = KipuTextMuted,
                            )
                        }
                        Text(
                            text = "${card.dueDay.toString().padStart(2, '0')} de cada mes",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = KipuInkTitle,
                        )
                    }
                }
            }

            // Educational tip card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = KipuLightGreenContainer,
                border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = KipuTealPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Tip financiero: procura mantener tus consumos por debajo del 30% de tu línea para conservar un uso moderado del crédito.",
                        style = MaterialTheme.typography.bodySmall,
                        color = KipuInkTitle,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Action buttons
            Button(
                onClick = { onViewDetail(card) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = rememberKipuColors().primary,
                    contentColor = rememberKipuColors().onPrimary,
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(
                    text = "Ver detalle de la tarjeta →",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            OutlinedButton(
                onClick = { onRecordFirstConsumption(card) },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = KipuTealPrimary,
                ),
                border = BorderStroke(1.dp, KipuTealPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(
                    text = "+ Registrar primer consumo",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
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
        shape = RoundedCornerShape(14.dp),
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
        colors = CardDefaults.cardColors(containerColor = KipuSurfaceVariantNeutral),
        border = BorderStroke(1.dp, KipuBorderColor),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = KipuInkTitle)
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
