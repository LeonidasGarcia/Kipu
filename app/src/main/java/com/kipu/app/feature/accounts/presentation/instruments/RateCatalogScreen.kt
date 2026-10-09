package com.kipu.app.feature.accounts.presentation.instruments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditProductReference
import com.kipu.app.feature.accounts.domain.model.RateReference
import com.kipu.app.feature.accounts.presentation.AccountUiEvent
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.KipuEasingTokens
import com.kipu.app.ui.theme.KipuMotionTokens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
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
    val snackbarHostState = remember { SnackbarHostState() }
    var showRateInfo by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }

    var personalTeaInput by rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    var personalTeaError by rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var initializedCardId by rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val context = remember(creditCard, products) { resolveRateCatalogContext(creditCard, products) }
    val referenceState = rateCatalogReferenceState(catalogLoading, catalogError, context)
    val cardState = rateCatalogCardState(cardLoading, creditCard)

    LaunchedEffect(Unit) { onLoadCatalog() }
    LaunchedEffect(creditCard?.id) {
        val cardId = creditCard?.id?.value
        if (cardId != null && initializedCardId != cardId) {
            personalTeaInput = personalTeaDraft(
                initializedCardId = initializedCardId,
                cardId = cardId,
                restoredDraft = personalTeaInput,
                persistedTeaBps = creditCard.personalTeaBps,
            )
            personalTeaError = null
            initializedCardId = cardId
        }
    }
    LaunchedEffect(events) {
        events.collect { event ->
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
                title = { Text("Tasas y TEA Referencial") },
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
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Disclaimer Banner
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "La tasa del tarifario es referencial. Para simular, se prioriza tu TEA personal.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = { showRateInfo = true },
                                modifier = Modifier.heightIn(min = 40.dp),
                            ) { Text("Cómo leer estas tasas") }
                        }
                    }
                }
            }

            when (val resolved = context) {
                is RateCatalogContext.Resolved,
                is RateCatalogContext.NoApplicableReference,
                is RateCatalogContext.MissingProductIdentity -> {
                    val card = when (resolved) {
                        is RateCatalogContext.Resolved -> resolved.card
                        is RateCatalogContext.NoApplicableReference -> resolved.card
                        is RateCatalogContext.MissingProductIdentity -> resolved.card
                    }
                    item { CardContextHeader(card, (resolved as? RateCatalogContext.Resolved)?.product?.productName ?: (resolved as? RateCatalogContext.NoApplicableReference)?.productName) }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Mi TEA Personalizada",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Se usa en simulaciones futuras y no modifica compras registradas.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedTextField(
                                    value = personalTeaInput,
                                    onValueChange = { personalTeaInput = it; personalTeaError = null },
                                    label = { Text("TEA de tu contrato (%)") },
                                    placeholder = { Text("Ej. 45.50") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    isError = personalTeaError != null,
                                    supportingText = personalTeaError?.let { message -> { Text(message) } },
                                    modifier = Modifier.weight(1f),
                                )
                                Button(
                                    onClick = {
                                        val teaDouble = personalTeaInput.trim().replace(",", ".").toDoubleOrNull()
                                        if (teaDouble != null && teaDouble in 0.0..1000.0) {
                                            val bps = (teaDouble * 100.0).roundToInt()
                                            onUpdatePersonalTea(card.id, bps)
                                            personalTeaError = null
                                        } else {
                                            personalTeaError = "Ingresa una TEA entre 0% y 1000%."
                                        }
                                    },
                                    enabled = personalTeaInput.isNotBlank(),
                                ) {
                                    Text("Guardar")
                                }
                            }
                        }
                    }
                }
                }
                RateCatalogContext.LoadingCard -> error("Card content requires a resolved card state")
            }

            item {
                Text(
                    text = "Referencia aplicable a esta tarjeta",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                val snapshotDate = (context as? RateCatalogContext.Resolved)?.product?.catalogAsOf
                Text(
                    text = snapshotDate?.let {
                        "Datos publicados al ${it.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))}"
                    } ?: "Datos del catálogo referencial",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                catalogError?.let {
                    Card(
                        shape = MaterialTheme.shapes.large,
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
                                Icon(Icons.Default.Refresh, contentDescription = null)
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
                    item(key = resolved.product.id) { ReferentialRateCard(resolved.product, resolved.card.currency) }
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

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
                }
            }
        }
    }

    if (showRateInfo) {
        AlertDialog(
            onDismissRequest = { showRateInfo = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            title = { Text("Qué significa la TEA") },
            text = { Text(RateReference.DISCLAIMER) },
            confirmButton = {
                TextButton(onClick = { showRateInfo = false }) { Text("Entendido") }
            },
        )
    }
}

@Composable
private fun CardContextHeader(card: CreditCard, productName: String?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Tarjeta consultada", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(card.alias ?: "${card.issuer} •••• ${card.lastFourDigits}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${card.issuer} · ${productName ?: "Producto sin identificar"} · ${card.currency.name}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ContextNotice(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
        Text(message, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReferentialRateCard(rate: CreditProductReference, currency: Currency) {
    var detailsExpanded by rememberSaveable(rate.id) { androidx.compose.runtime.mutableStateOf(false) }
    val reducedMotion = rememberReducedMotionEnabled()
    val uriHandler = LocalUriHandler.current
    val currencyName = if (currency == Currency.PEN) "soles" else "dólares"
    val minTea = if (currency == Currency.PEN) rate.penTeaMinBps else rate.usdTeaMinBps
    val maxTea = if (currency == Currency.PEN) rate.penTeaMaxBps else rate.usdTeaMaxBps
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${rate.institutionName} - ${rate.productName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                rate.cardNetwork?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.widthIn(min = 40.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("TEA de compras en $currencyName", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = formatTeaRange(minTea, maxTea),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Tasa referencial del tarifario. Tu contrato define la tasa aplicable.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { detailsExpanded = !detailsExpanded },
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            ) {
                Icon(
                    imageVector = if (detailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                )
                Spacer(Modifier.width(6.dp))
                Text(if (detailsExpanded) "Ocultar detalle" else "Ver detalle del tarifario")
            }
            AnimatedVisibility(
                visible = detailsExpanded,
                enter = if (reducedMotion) EnterTransition.None else
                    expandVertically(animationSpec = tween(KipuMotionTokens.SubtreeEnterMillis, easing = KipuEasingTokens.Decelerate)) +
                        fadeIn(animationSpec = tween(KipuMotionTokens.SubtreeEnterMillis)),
                exit = if (reducedMotion) ExitTransition.None else
                    shrinkVertically(animationSpec = tween(KipuMotionTokens.SubtreeExitMillis, easing = KipuEasingTokens.Accelerate)) +
                        fadeOut(animationSpec = tween(KipuMotionTokens.SubtreeExitMillis)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider()
                    rate.publishedTeaSummary?.let { RateDetailLine("TEA publicada", it) }
                    rate.publishedTceaSummary?.let { RateDetailLine("TCEA publicada", it) }
                    rate.membershipCondition?.let { RateDetailLine("Condición de membresía", it) }
                    RateDetailLine(
                        "Membresía",
                        "${formatCatalogFee(rate.membershipFeePenMinor, "S/")} · ${formatCatalogFee(rate.membershipFeeUsdMinor, "US$ ")}",
                    )
                    RateDetailLine("Verificación", humanizeVerificationStatus(rate.verificationStatus))
                    rate.catalogAsOf?.let { RateDetailLine("Datos publicados", it.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))) }
                    rate.effectiveTo?.let { RateDetailLine("Vigencia publicada hasta", it.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))) }
                    rate.sourceUrl?.let { source ->
                        TextButton(
                            onClick = { runCatching { uriHandler.openUri(source) } },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Abrir tarifario oficial")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RateDetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
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

private fun formatTeaRange(minBps: Int?, maxBps: Int?): String = when {
    minBps != null && maxBps != null -> "%.2f%% – %.2f%%".format(minBps / 100.0, maxBps / 100.0)
    minBps != null -> "desde %.2f%%".format(minBps / 100.0)
    maxBps != null -> "hasta %.2f%%".format(maxBps / 100.0)
    else -> "No publicado numéricamente"
}

private fun formatCatalogFee(amountMinor: Long?, symbol: String): String =
    amountMinor?.let { "$symbol${"%.2f".format(it / 100.0)}" } ?: "No publicado"
