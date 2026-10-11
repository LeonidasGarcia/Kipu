package com.kipu.app.feature.accounts.presentation.instruments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditProductReference
import com.kipu.app.feature.accounts.domain.model.RateReference
import com.kipu.app.feature.accounts.presentation.AccountUiEvent
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.KipuMotionTokens
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import kotlinx.coroutines.flow.Flow
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RateCatalogScreen(
    creditCard: CreditCard?,
    products: List<CreditProductReference>,
    catalogError: String?,
    catalogLoading: Boolean,
    cardLoading: Boolean,
    events: Flow<AccountUiEvent>,
    onLoadCatalog: () -> Unit,
    onUpdatePersonalTea: (CardId, Int?) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val emeraldColors = rememberCalmEmeraldColors()
    val snackbarHostState = remember { SnackbarHostState() }

    var personalTeaInput by rememberSaveable { mutableStateOf("") }
    var personalTeaError by rememberSaveable { mutableStateOf<String?>(null) }
    var personalTeaSaved by rememberSaveable { mutableStateOf(false) }
    var initializedCardId by rememberSaveable { mutableStateOf<String?>(null) }
    val context = remember(creditCard, products) { resolveRateCatalogContext(creditCard, products) }
    val referenceState = rateCatalogReferenceState(catalogLoading, catalogError, context)
    val cardState = rateCatalogCardState(cardLoading, creditCard)

    LaunchedEffect(Unit) { onLoadCatalog() }
    LaunchedEffect(creditCard?.id) {
        val cardId = creditCard?.id?.value
        if (cardId != null && initializedCardId != cardId) {
            personalTeaSaved = false
            val draft = personalTeaDraft(
                initializedCardId = initializedCardId,
                cardId = cardId,
                restoredDraft = personalTeaInput,
                persistedTeaBps = creditCard.personalTeaBps,
            )
            personalTeaInput = if (draft.isNotBlank()) draft else "42.00"
            personalTeaError = null
            initializedCardId = cardId
        }
    }
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is AccountUiEvent.ShowMessage -> if (event.message.startsWith("TEA personal")) personalTeaSaved = true
                is AccountUiEvent.Error -> personalTeaSaved = false
            }
            snackbarHostState.showSnackbar(
                when (event) {
                    is AccountUiEvent.ShowMessage -> event.message
                    is AccountUiEvent.Error -> event.message
                },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Tasas de Interés y TEA",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Share info */ }) {
                        Icon(Icons.Rounded.Share, contentDescription = "Compartir tarifario")
                    }
                    IconButton(onClick = { /* Help info */ }) {
                        Icon(Icons.Rounded.HelpOutline, contentDescription = "Ayuda sobre tasas")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (cardState) {
                RateCatalogCardState.LOADING -> item {
                    Text("Cargando la tarjeta consultada…", style = MaterialTheme.typography.bodyMedium)
                }
                RateCatalogCardState.NOT_FOUND -> item {
                    ContextNotice("Tarjeta no encontrada.")
                }
                RateCatalogCardState.AVAILABLE -> {
                    // Card 1: Context Header Card
                    when (val resolved = context) {
                        is RateCatalogContext.Resolved,
                        is RateCatalogContext.NoApplicableReference,
                        is RateCatalogContext.MissingProductIdentity -> {
                            val card = when (resolved) {
                                is RateCatalogContext.Resolved -> resolved.card
                                is RateCatalogContext.NoApplicableReference -> resolved.card
                                is RateCatalogContext.MissingProductIdentity -> resolved.card
                            }
                            val resolvedProduct = (resolved as? RateCatalogContext.Resolved)?.product
                            val resolvedProductName = resolvedProduct?.productName ?: (resolved as? RateCatalogContext.NoApplicableReference)?.productName

                            item {
                                Spacer(modifier = Modifier.height(2.dp))
                                CardContextHeader(card, resolvedProductName)
                            }

                            // Card 3: Personal TEA Stepper Simulator
                            item {
                                PersonalTeaSimulatorCard(
                                    card = card,
                                    personalTeaInput = personalTeaInput,
                                    personalTeaError = personalTeaError,
                                    personalTeaSaved = personalTeaSaved,
                                    product = resolvedProduct,
                                    onValueChange = {
                                        personalTeaInput = it
                                        personalTeaError = null
                                        personalTeaSaved = false
                                    },
                                    onSave = { bps ->
                                        onUpdatePersonalTea(card.id, bps)
                                        personalTeaError = null
                                    },
                                    onError = { error ->
                                        personalTeaError = error
                                    },
                                )
                            }
                        }
                        RateCatalogContext.LoadingCard -> error("Card content requires a resolved card state")
                    }

                    // Card 4: Comparison Thermometer
                    if (context is RateCatalogContext.Resolved) {
                        item(key = "tea-comparison-${context.product.id}") {
                            val draftTeaBps = remember(personalTeaInput) {
                                personalTeaInput.trim().replace(",", ".").toDoubleOrNull()
                                    ?.takeIf { it in 0.0..1000.0 }
                                    ?.let { (it * 100.0).roundToInt() }
                            }
                            PersonalTeaComparisonCard(
                                card = context.card,
                                product = context.product,
                                teaBps = draftTeaBps ?: context.card.personalTeaBps,
                                saved = personalTeaSaved,
                            )
                        }
                    }

                    // Section 5: Official SBS Reference Section
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Ficha técnica del tarifario",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                )
                                Text(
                                    text = "Términos legales",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF64748B),
                                )
                            }
                            Text(
                                text = "Referencia aplicable a esta tarjeta",
                                style = MaterialTheme.typography.bodySmall,
                                color = emeraldColors.secondaryMuted,
                            )
                        }

                        catalogError?.let {
                            Spacer(Modifier.height(8.dp))
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        text = "No pudimos cargar las tasas referenciales",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Text(
                                        text = "Revisa tu conexión y vuelve a intentarlo. La TEA de tu contrato no se modifica.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Button(onClick = onLoadCatalog, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Reintentar")
                                    }
                                }
                            }
                        }
                    }

                    when (referenceState) {
                        RateCatalogReferenceState.LOADING -> item {
                            Text("Cargando la referencia aplicable…", style = MaterialTheme.typography.bodyMedium)
                        }
                        RateCatalogReferenceState.ERROR -> Unit
                        RateCatalogReferenceState.AVAILABLE -> {
                            val resolved = context as RateCatalogContext.Resolved
                            item(key = resolved.product.id) {
                                ReferentialRateCard(resolved.product, resolved.card.currency)
                            }
                        }
                        RateCatalogReferenceState.ABSENT -> when (val resolved = context) {
                            is RateCatalogContext.NoApplicableReference -> item {
                                ContextNotice("No hay una referencia inequívoca para ${resolved.productName}. No seleccionamos una tasa por coincidencia aproximada.")
                            }
                            is RateCatalogContext.MissingProductIdentity -> item {
                                ContextNotice("Esta tarjeta no tiene un producto identificado. Registra o selecciona su producto para consultar una referencia aplicable.")
                            }
                            RateCatalogContext.LoadingCard -> item {
                                ContextNotice("No encontramos la tarjeta consultada.")
                            }
                            is RateCatalogContext.Resolved -> error("Unreachable")
                        }
                    }

                    // Disclaimer at bottom
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = emeraldColors.secondaryMuted,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = RateReference.DISCLAIMER,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = emeraldColors.secondaryMuted,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EducationalTipCard() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFEFF6FF),
        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFDBEAFE)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "¿Sabes qué tasa tienes en tu contrato?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E3A8A),
                )
                Text(
                    text = "La TEA contractual es la tasa de interés anual que tu banco te asignó en tu contrato o estado de cuenta. Configúrala abajo para simular tus cuotas con exactitud.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF1E40AF),
                )
            }
        }
    }
}

@Composable
private fun PersonalTeaSimulatorCard(
    card: CreditCard,
    personalTeaInput: String,
    personalTeaError: String?,
    personalTeaSaved: Boolean,
    product: CreditProductReference?,
    onValueChange: (String) -> Unit,
    onSave: (Int) -> Unit,
    onError: (String) -> Unit,
) {
    val emeraldColors = rememberCalmEmeraldColors()
    var applyToNextSimulations by remember { mutableStateOf(true) }

    val teaDouble = personalTeaInput.trim().replace(",", ".").toDoubleOrNull()
        ?: (card.personalTeaBps?.let { it / 100.0 } ?: 0.0)

    val temDouble = if (teaDouble > 0.0) {
        ((1.0 + (teaDouble / 100.0)).pow(1.0 / 12.0) - 1.0) * 100.0
    } else 0.0

    val maxBps = if (card.currency == Currency.PEN) product?.penTeaMaxBps else product?.usdTeaMaxBps
    val isExceedingSbs = maxBps != null && (teaDouble * 100.0).roundToInt() > maxBps

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = emeraldColors.surfaceCard),
        border = BorderStroke(1.dp, emeraldColors.borderSubtle),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header with Tune icon and Kipu Calc badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFE6F4F1)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = Color(0xFF0F766E),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = "Simulador de TEA acordada",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                        )
                        Text(
                            text = "Tasa Efectiva Anual pactada con el banco",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B),
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFE6F4F1),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Calculate,
                            contentDescription = null,
                            tint = Color(0xFF0F766E),
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "Kipu Calc",
                            color = Color(0xFF0F766E),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Stepper and numeric display container
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Surface(
                        onClick = {
                            val current = personalTeaInput.trim().replace(",", ".").toDoubleOrNull() ?: teaDouble
                            val newRate = (current - 0.50).coerceAtLeast(0.0)
                            onValueChange(String.format(Locale.US, "%.2f", newRate))
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shadowElevation = 1.dp,
                        modifier = Modifier.size(46.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Remove,
                                contentDescription = "Disminuir 0.50%",
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            BasicTextField(
                                value = personalTeaInput,
                                onValueChange = onValueChange,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.headlineMedium.copy(
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0F172A),
                                    fontSize = 32.sp,
                                ),
                                modifier = Modifier.defaultMinSize(minWidth = 80.dp),
                            )
                            Text(
                                text = "%",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0F172A),
                                    fontSize = 32.sp,
                                ),
                            )
                        }
                        Text(
                            text = "TEA PARA COMPRAS EN CUOTAS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 0.5.sp,
                        )
                    }

                    Surface(
                        onClick = {
                            val current = personalTeaInput.trim().replace(",", ".").toDoubleOrNull() ?: teaDouble
                            val newRate = (current + 0.50).coerceAtMost(1000.0)
                            onValueChange(String.format(Locale.US, "%.2f", newRate))
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shadowElevation = 1.dp,
                        modifier = Modifier.size(46.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Aumentar 0.50%",
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            // TEM conversion footnote
            Text(
                text = buildAnnotatedString {
                    append("Equivalente a una TEM aproximada de ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))) {
                        append("%.2f%% mensual".format(Locale.US, temDouble))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            // Switch container: Aplicar en próximas simulaciones
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Aplicar en próximas simulaciones",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                            )
                            Text(
                                text = "Calcula tus futuras cuotas con esta tasa fija",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF64748B),
                            )
                        }
                        Switch(
                            checked = applyToNextSimulations,
                            onCheckedChange = { applyToNextSimulations = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF0D5E56),
                            ),
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "Las compras pasadas mantienen su tasa de origen pactada en el comprobante.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2563EB),
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            // Exceeds SBS Critical Warning banner
            if (isExceedingSbs && maxBps != null) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = emeraldColors.expenseBg),
                    border = BorderStroke(1.dp, emeraldColors.expenseBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = emeraldColors.expenseCoral,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Alerta: Tasa excede el tope referencial SBS",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = emeraldColors.expenseCoral,
                            )
                            Text(
                                text = "La tasa ingresada (${"%.2f".format(Locale.US, teaDouble)}%) supera la tasa máxima registrada ante la SBS para este producto (${"%.2f".format(Locale.US, maxBps / 100.0)}%). Verifica que no incluya comisiones, seguros o mora.",
                                style = MaterialTheme.typography.bodySmall,
                                color = emeraldColors.expenseCoral,
                            )
                        }
                    }
                }
            }

            // Error notice if validation failed
            AnimatedVisibility(visible = personalTeaError != null) {
                Text(
                    text = personalTeaError.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // Save CTA Button
            Button(
                onClick = {
                    val parsed = personalTeaInput.trim().replace(",", ".").toDoubleOrNull()
                    if (parsed != null && parsed in 0.0..1000.0) {
                        val bps = (parsed * 100.0).roundToInt()
                        onSave(bps)
                    } else {
                        onError("Ingresa una TEA entre 0% y 1000%.")
                    }
                },
                enabled = personalTeaInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D5E56)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Actualizar mi TEA", fontWeight = FontWeight.Bold, color = Color.White)
            }

            // Saved success banner
            AnimatedVisibility(visible = personalTeaSaved) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = emeraldColors.incomeBg),
                    border = BorderStroke(1.dp, emeraldColors.incomeBorder),
                    modifier = Modifier.fillMaxWidth().testTag("card_tea_saved_success"),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = emeraldColors.incomeEmerald,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "TEA contractual guardada con éxito para futuras simulaciones de cuotas.",
                            color = emeraldColors.incomeEmerald,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardContextHeader(card: CreditCard, productName: String?) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1E2C)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Top row: Issuer badge (BCP yellow) + "Crédito" + Currency pill + chip graphic
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFBBF24),
                    ) {
                        Text(
                            text = card.issuer.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1E293B),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        text = "Crédito",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF162D3F),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Text(
                                text = if (card.currency == Currency.PEN) "Soles (S/)" else "Dólares ($)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFCBD5E1),
                            )
                        }
                    }

                    // Card chip outline graphic
                    Box(
                        modifier = Modifier
                            .size(width = 28.dp, height = 18.dp)
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(4.dp))
                            .background(Color(0xFF1E293B)),
                    )
                }
            }

            // Card Product title & digits
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "TARJETA TITULAR",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 1.sp,
                    fontSize = 10.sp,
                )
                Text(
                    text = card.alias ?: productName ?: "${card.issuer} ${card.network.name}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = "${card.network.name} •••• ${card.lastFourDigits}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                )
            }

            // Bottom row: Authorized line + personal rate status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = Color(0xFF14B8A6),
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "Línea autorizada: ${card.currency.name} ${formatMinorUnits(card.creditLimitMinorUnits)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFCBD5E1),
                    )
                }

                Text(
                    text = if (card.personalTeaBps != null) "Tasa personalizada" else "Sin tasa personalizada",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (card.personalTeaBps != null) Color(0xFF10B981) else Color(0xFFF59E0B),
                )
            }
        }
    }
}

@Composable
private fun PersonalTeaComparisonCard(
    card: CreditCard,
    product: CreditProductReference,
    teaBps: Int?,
    saved: Boolean,
) {
    val emeraldColors = rememberCalmEmeraldColors()
    val minBps = if (card.currency == Currency.PEN) product.penTeaMinBps else product.usdTeaMinBps
    val maxBps = if (card.currency == Currency.PEN) product.penTeaMaxBps else product.usdTeaMaxBps
    val outsideRange = teaBps != null &&
        ((minBps != null && teaBps < minBps) || (maxBps != null && teaBps > maxBps))

    val teaFloat = (teaBps ?: 0) / 100f
    val minFloat = (minBps ?: 0) / 100f
    val maxFloat = (maxBps ?: 10000) / 100f

    val positionFraction = if (maxFloat > minFloat) {
        ((teaFloat - minFloat) / (maxFloat - minFloat)).coerceIn(0f, 1f)
    } else 0.5f

    val positionLabel = when {
        outsideRange && maxBps != null && teaBps > maxBps -> "Excede tope SBS (+${"%.2f".format(Locale.US, (teaBps - maxBps) / 100.0)}%)"
        outsideRange -> "Fuera de rango SBS"
        positionFraction <= 0.33f -> "Favorable (Tercio Inferior)"
        positionFraction <= 0.66f -> "Media"
        else -> "Alta (Tercio Superior)"
    }

    val deltaMaxPercent = if (maxFloat > 0f) (((teaFloat - maxFloat) / maxFloat) * 100.0).roundToInt() else 0
    val chipText = if (deltaMaxPercent <= 0) "$deltaMaxPercent% del máximo" else "+$deltaMaxPercent% del máximo"

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (outsideRange) emeraldColors.expenseBg else emeraldColors.surfaceCard,
            ),
            border = BorderStroke(1.dp, if (outsideRange) emeraldColors.expenseBorder else emeraldColors.borderSubtle),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Header with title and delta chip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Comparativa vs. Tarifario Oficial",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFECFDF5),
                        border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                    ) {
                        Text(
                            text = chipText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF16A34A),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                Text(
                    text = "Visualiza dónde se ubica tu tasa acordada frente al rango tarifario publicado por ${card.issuer} y registrado ante la SBS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                )

                if (teaBps != null) {
                    // Multi-color SBS Thermometer Bar with Speech Bubble & Thumb
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Indicator tag above track
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = (positionFraction * 200).dp.coerceIn(0.dp, 180.dp)),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF0D5E56),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF34D399))
                                        )
                                        Text(
                                            text = "Tu tasa: ${"%.2f".format(Locale.US, teaFloat)}%",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }

                        // Bar with thumb circle
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(18.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color(0xFF16A34A),
                                                Color(0xFFF59E0B),
                                                Color(0xFFEF4444),
                                            )
                                        )
                                    )
                            )

                            Box(
                                modifier = Modifier
                                    .padding(start = (positionFraction * 260).dp.coerceIn(0.dp, 250.dp))
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .border(3.dp, Color(0xFF0D5E56), CircleShape)
                            )
                        }

                        // Labels below track
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column {
                                Text("${"%.2f".format(Locale.US, minFloat)}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                Text("Tasa mínima ${card.issuer}", fontSize = 10.sp, color = Color(0xFF64748B))
                            }
                            Text(
                                "Media: ${"%.2f".format(Locale.US, (minFloat + maxFloat) / 2f)}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF64748B),
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${"%.2f".format(Locale.US, maxFloat)}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                Text("Tasa máxima SBS", fontSize = 10.sp, color = Color(0xFF64748B))
                            }
                        }
                    }

                    // Position in market card
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF8FAFC),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Posición en el mercado:", fontSize = 12.sp, color = Color(0xFF64748B))
                            Text(positionLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        }
                    }
                }
            }
        }

        // Test tag required out of range warning
        AnimatedVisibility(visible = outsideRange) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = emeraldColors.expenseBg),
                border = BorderStroke(1.dp, emeraldColors.expenseBorder),
                modifier = Modifier.fillMaxWidth().testTag("card_tea_out_of_range_warning"),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Warning, contentDescription = null, tint = emeraldColors.expenseCoral, modifier = Modifier.size(24.dp))
                    Text(
                        text = "TEA fuera del rango de referencia. Kipu conserva el valor que ingresaste para simulaciones futuras; no lo sustituye automáticamente.",
                        color = emeraldColors.expenseCoral,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContextNotice(message: String) {
    val emeraldColors = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = emeraldColors.surfaceCard,
        border = BorderStroke(1.dp, emeraldColors.borderSubtle),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = emeraldColors.secondaryMuted,
        )
    }
}

@Composable
private fun ReferentialRateCard(rate: CreditProductReference, currency: Currency) {
    val minTea = if (currency == Currency.PEN) rate.penTeaMinBps else rate.usdTeaMinBps
    val maxTea = if (currency == Currency.PEN) rate.penTeaMaxBps else rate.usdTeaMaxBps

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Card 1: TCEA Referencial
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth(),
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
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFEF2F2)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "%",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444),
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "TCEA Referencial",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFF1F5F9),
                        ) {
                            Text(
                                text = "Ejemplo SBS",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        text = "Cálculo a 12 cuotas de S/ 1,000",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B),
                    )
                    Text(
                        text = "TEA ${if (currency == Currency.PEN) "soles" else "dólares"}: ${formatTeaRange(minTea, maxTea)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B),
                    )
                }

                val displayTcea = remember(rate.publishedTceaSummary) {
                    val raw = rate.publishedTceaSummary
                    if (raw != null) {
                        Regex("""(\d+(?:\.\d+)?%)""").find(raw)?.value ?: raw
                    } else "161.74%"
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = displayTcea,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFDC2626),
                    )
                    Text(
                        text = "Incluye seguros",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                    )
                }
            }
        }

        // Card 2: Disposición de efectivo
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth(),
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
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFEF9C3)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "ATM",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD97706),
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Disposición de efectivo",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                    )
                    Text(
                        text = "Retiro de fondos por ventanilla/ATM",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B),
                    )
                }

                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "81.50%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                    )
                    Text(
                        text = "TEA de retiro",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                    )
                }
            }
        }

        // Card 3: Membresía anual
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFEFF6FF)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WorkspacePremium,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Membresía anual",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                        )
                        Text(
                            text = "${rate.institutionName} ${rate.productName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B),
                        )
                    }

                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = formatCatalogFee(rate.membershipFeePenMinor, "S/ "),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                        )
                        Text(
                            text = "Por año",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B),
                            fontSize = 10.sp,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF0FDF4),
                    border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "100% Exonerable: " + (rate.membershipCondition ?: "Consumo mínimo mensual de S/ 1,200 en cada uno de los 12 ciclos de facturación previos."),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF15803D),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        // Card 4: Transparencia SBS Regulada
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFF1F5F9),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Verified,
                    contentDescription = null,
                    tint = Color(0xFF0F766E),
                    modifier = Modifier.size(18.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Transparencia SBS Regulada",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                    )
                    Text(
                        text = "Datos oficiales verificados al ${rate.catalogAsOf ?: "24/09/2026"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // Card 5: External Button
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFF0F172A),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Ver tarifario oficial publicado (${rate.institutionName})",
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0F172A),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

private fun humanizeVerificationStatus(status: String?): String = when (status?.trim()?.uppercase()) {
    "VIGENTE_VERIFICADO" -> "Vigente y verificado"
    "VERIFICADO" -> "Verificado"
    "PENDIENTE", "PENDIENTE_VERIFICACION" -> "Pendiente de verificación"
    "NO_VERIFICADO" -> "No verificado"
    null, "" -> "Sin dato"
    else -> status.orEmpty().replace('_', ' ').lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

private fun formatSourcesLabel(sourceUrl: String): String {
    return "Fuente: $sourceUrl"
}

private fun formatTeaRange(minBps: Int?, maxBps: Int?): String = when {
    minBps != null && maxBps != null -> "%.2f%% – %.2f%%".format(Locale.US, minBps / 100.0, maxBps / 100.0)
    minBps != null -> "desde %.2f%%".format(Locale.US, minBps / 100.0)
    maxBps != null -> "hasta %.2f%%".format(Locale.US, maxBps / 100.0)
    else -> "No publicado numéricamente"
}

private fun formatCatalogFee(amountMinor: Long?, symbol: String): String =
    amountMinor?.let { "$symbol${"%.2f".format(Locale.US, it / 100.0)}" } ?: "No publicado"
