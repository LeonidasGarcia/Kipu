package com.kipu.app.feature.accounts.presentation.instruments

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditProductReference
import com.kipu.app.feature.accounts.domain.model.RateReference
import com.kipu.app.feature.accounts.presentation.AccountUiEvent
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
    events: Flow<AccountUiEvent>,
    onLoadCatalog: () -> Unit,
    onUpdatePersonalTea: (CardId, Int?) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    var personalTeaInput by rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    var personalTeaError by rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val context = remember(creditCard, products) { resolveRateCatalogContext(creditCard, products) }
    val referenceState = rateCatalogReferenceState(catalogLoading, catalogError, context)

    LaunchedEffect(Unit) { onLoadCatalog() }
    LaunchedEffect(creditCard?.id, creditCard?.personalTeaBps) {
        personalTeaInput = creditCard?.personalTeaBps?.let(::formatTeaInput).orEmpty()
        personalTeaError = null
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
                        Text(
                            text = RateReference.DISCLAIMER,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                        else -> error("Unreachable")
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
                                text = "Configura la TEA contratada para simular cuotas con precisión. No afecta simulaciones pasadas.",
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
                                    label = { Text("TEA (%)") },
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
                RateCatalogContext.LoadingCard -> item {
                    Text("Cargando la tarjeta consultada…", style = MaterialTheme.typography.bodyMedium)
                }
            }

            item {
                Text(
                    text = "Referencia aplicable a esta tarjeta",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Tasa referencial al 24/09/2026",
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
                )
                rate.cardNetwork?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
            }
            Spacer(modifier = Modifier.height(6.dp))
            val minTea = if (currency == Currency.PEN) rate.penTeaMinBps else rate.usdTeaMinBps
            val maxTea = if (currency == Currency.PEN) rate.penTeaMaxBps else rate.usdTeaMaxBps
            Text("TEA ${if (currency == Currency.PEN) "soles" else "dólares"}: ${formatTeaRange(minTea, maxTea)}", style = MaterialTheme.typography.bodyMedium)
            rate.publishedTeaSummary?.let { Text("Tarifario TEA: $it", style = MaterialTheme.typography.bodySmall) }
            rate.publishedTceaSummary?.let { Text("TCEA publicada: $it", style = MaterialTheme.typography.bodySmall) }
            rate.membershipCondition?.let { Text("Membresía: $it", style = MaterialTheme.typography.bodySmall) }
            Text(
                "Membresía publicada: ${formatCatalogFee(rate.membershipFeePenMinor, "S/")} · ${formatCatalogFee(rate.membershipFeeUsdMinor, "US$ ")}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Verificación: ${humanizeVerificationStatus(rate.verificationStatus)} · Vigencia sin caducidad automática",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            rate.sourceUrl?.let { Text(formatSourcesLabel(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
    minBps != null && maxBps != null -> "%.2f%% – %.2f%%".format(minBps / 100.0, maxBps / 100.0)
    minBps != null -> "desde %.2f%%".format(minBps / 100.0)
    maxBps != null -> "hasta %.2f%%".format(maxBps / 100.0)
    else -> "No publicado numéricamente"
}

private fun formatCatalogFee(amountMinor: Long?, symbol: String): String =
    amountMinor?.let { "$symbol${"%.2f".format(it / 100.0)}" } ?: "No publicado"

private fun formatTeaInput(teaBps: Int): String = "%.2f".format(teaBps / 100.0)
