package com.kipu.app.feature.accounts.presentation.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipu.app.core.finance.domain.MoneyInputParser
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.PurchaseCandidate
import com.kipu.app.feature.accounts.presentation.PurchaseCategoryOption
import com.kipu.app.feature.accounts.presentation.instruments.InstallmentSimulatorScreen
import com.kipu.app.feature.categories.presentation.components.MerchantPicker
import com.kipu.app.feature.categories.presentation.components.MerchantPickerViewModel
import com.kipu.app.ui.component.KipuBottomSheet
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.KipuEasingTokens
import com.kipu.app.ui.theme.KipuMotionTokens
import java.time.Instant
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

private enum class PurchaseDraftStep {
    FORM,
    MERCHANT,
    CATEGORY,
    SIMULATION,
}

/** A compact credit purchase flow that reuses the merchant catalog picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditPurchaseDraftSheet(
    card: CreditCard,
    categories: List<PurchaseCategoryOption>,
    onDismiss: () -> Unit,
    onConfirmPurchase: (PurchaseCandidate, installments: Int) -> Unit,
    modifier: Modifier = Modifier,
    merchantPickerViewModel: MerchantPickerViewModel = hiltViewModel(),
) {
    val merchantState by merchantPickerViewModel.uiState.collectAsStateWithLifecycle()
    val reducedMotion = rememberReducedMotionEnabled()
    var step by remember(card.id.value) { mutableStateOf(PurchaseDraftStep.FORM) }
    var amountText by remember(card.id.value) { mutableStateOf("") }
    var selectedCategoryId by remember(card.id.value) { mutableStateOf<String?>(null) }
    var categoryQuery by remember(card.id.value) { mutableStateOf("") }
    var candidate by remember(card.id.value) { mutableStateOf<PurchaseCandidate?>(null) }
    var selectedInstallments by remember(card.id.value) { mutableIntStateOf(3) }

    LaunchedEffect(merchantPickerViewModel) {
        merchantPickerViewModel.clearSelection()
    }

    BackHandler(enabled = step != PurchaseDraftStep.FORM) {
        step = PurchaseDraftStep.FORM
    }

    val amountMinorUnits = remember(amountText) {
        MoneyInputParser.parseMinorUnits(amountText)?.takeIf { it > 0L }
    }
    val merchantName = merchantState.selectedMerchant?.name ?: merchantState.provisionalText.orEmpty()
    val selectedCategory = remember(categories, selectedCategoryId) {
        categories.firstOrNull { it.id == selectedCategoryId }
    }
    val categorySearchEntries = remember(categories) {
        categories.map { category -> category to category.searchKey() }
    }
    val canContinue = amountMinorUnits != null && merchantName.isNotBlank() && selectedCategory != null

    KipuBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        header = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            if (step == PurchaseDraftStep.FORM) onDismiss() else step = PurchaseDraftStep.FORM
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = if (step == PurchaseDraftStep.FORM) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (step == PurchaseDraftStep.FORM) "Cerrar" else "Volver",
                        )
                    }
                    Text(
                        text = when (step) {
                            PurchaseDraftStep.FORM -> "Nueva compra"
                            PurchaseDraftStep.MERCHANT -> "Elegir comercio"
                            PurchaseDraftStep.CATEGORY -> "Elegir categoría"
                            PurchaseDraftStep.SIMULATION -> "Revisar compra"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    Spacer(Modifier.size(48.dp))
                }
                Text(
                    text = when (step) {
                        PurchaseDraftStep.FORM -> "La simulación no registra el consumo."
                        PurchaseDraftStep.MERCHANT -> "Busca en tu catálogo o usa un nombre provisional."
                        PurchaseDraftStep.CATEGORY -> "Selecciona la categoría del gasto."
                        PurchaseDraftStep.SIMULATION -> "Confirma solo cuando estés conforme con las cuotas."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                )
            }
        },
        footer = {
            if (step == PurchaseDraftStep.FORM) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = {
                            val minorUnits = amountMinorUnits ?: return@Button
                            val category = selectedCategory ?: return@Button
                            if (merchantName.isBlank()) return@Button
                            candidate = PurchaseCandidate(
                                id = UUID.randomUUID().toString(),
                                cardId = card.id,
                                amount = Money(minorUnits, card.currency),
                                merchant = merchantName.trim(),
                                occurredAt = Instant.now(),
                                suggestedInstallments = selectedInstallments,
                                categoryId = category.id,
                                merchantId = merchantState.selectedMerchant?.id?.value,
                            )
                            step = PurchaseDraftStep.SIMULATION
                        },
                        enabled = canContinue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        Text("Ver simulación")
                    }
                }
            } else if (step == PurchaseDraftStep.SIMULATION && candidate != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = { step = PurchaseDraftStep.FORM },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { Text("Editar") }
                        Button(
                            onClick = { onConfirmPurchase(requireNotNull(candidate), selectedInstallments) },
                            modifier = Modifier.weight(1.4f).heightIn(min = 48.dp),
                        ) { Text("Confirmar compra") }
                    }
                }
            }
        },
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (reducedMotion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    val direction = if (targetState == PurchaseDraftStep.FORM) -1 else 1
                    (fadeIn(tween(KipuMotionTokens.QuickMillis, easing = KipuEasingTokens.Decelerate)) +
                        slideInVertically(
                            animationSpec = tween(KipuMotionTokens.QuickMillis, easing = KipuEasingTokens.Decelerate),
                            initialOffsetY = { height -> direction * height / 32 },
                        )) togetherWith
                        (fadeOut(tween(KipuMotionTokens.MicroMillis, easing = KipuEasingTokens.Accelerate)) +
                            slideOutVertically(
                                animationSpec = tween(KipuMotionTokens.MicroMillis, easing = KipuEasingTokens.Accelerate),
                                targetOffsetY = { height -> -direction * height / 48 },
                            ))
                }
            },
            label = "credit_purchase_draft_step",
        ) { currentStep ->
            when (currentStep) {
                        PurchaseDraftStep.FORM -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    Icons.Default.CreditCard,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = card.alias?.takeIf { it.isNotBlank() } ?: card.issuer,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "•••• ${card.lastFourDigits} · ${card.currency.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { value ->
                                amountText = value.filter { it.isDigit() || it == '.' || it == ',' }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Importe (${card.currency.name})") },
                            placeholder = { Text("0.00") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = amountText.isNotBlank() && amountMinorUnits == null,
                            supportingText = if (amountText.isNotBlank() && amountMinorUnits == null) {
                                { Text("Usa un importe mayor que cero y hasta 2 decimales.") }
                            } else {
                                null
                            },
                        )

                        PurchaseDraftChoice(
                            title = "Comercio",
                            value = merchantName.takeIf(String::isNotBlank) ?: "Seleccionar comercio",
                            supportingText = when {
                                merchantState.selectedMerchant != null -> "Del catálogo"
                                merchantState.provisionalText != null -> "Nombre provisional"
                                else -> null
                            },
                            leadingIcon = Icons.Default.Storefront,
                            onClick = { step = PurchaseDraftStep.MERCHANT },
                        )
                        PurchaseDraftChoice(
                            title = "Categoría",
                            value = selectedCategory?.name ?: "Seleccionar categoría",
                            leadingIcon = Icons.Default.Category,
                            onClick = {
                                categoryQuery = ""
                                step = PurchaseDraftStep.CATEGORY
                            },
                        )
                    }
                }

                PurchaseDraftStep.MERCHANT -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        MerchantPicker(
                            state = merchantState,
                            onQueryChange = merchantPickerViewModel::onQueryChanged,
                            onSelectMerchant = { merchant ->
                                merchantPickerViewModel.selectMerchant(merchant)
                                step = PurchaseDraftStep.FORM
                            },
                            onSetProvisionalText = { name ->
                                merchantPickerViewModel.setProvisionalText(name)
                                step = PurchaseDraftStep.FORM
                            },
                            onClearSelection = merchantPickerViewModel::clearSelection,
                        )
                    }
                }

                PurchaseDraftStep.CATEGORY -> {
                    val filteredCategories = remember(categorySearchEntries, categoryQuery) {
                        val query = categoryQuery.trim().searchKey()
                        if (query.isBlank()) categories
                        else categorySearchEntries
                            .filter { (_, normalizedName) -> normalizedName.contains(query) }
                            .map { (category, _) -> category }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = categoryQuery,
                            onValueChange = { categoryQuery = it },
                            modifier = Modifier.fillMaxWidth().testTag("credit_purchase_category_search"),
                            label = { Text("Buscar categoría") },
                            singleLine = true,
                        )
                        if (filteredCategories.isEmpty()) {
                            Text(
                                text = if (categories.isEmpty()) "No hay categorías de gasto disponibles." else "No se encontraron categorías.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(filteredCategories, key = PurchaseCategoryOption::id) { category ->
                                    val isSelected = category.id == selectedCategoryId
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 52.dp)
                                            .clickable(role = Role.Button) {
                                                selectedCategoryId = category.id
                                                step = PurchaseDraftStep.FORM
                                            },
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = category.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                modifier = Modifier.weight(1f),
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = "Seleccionada",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                PurchaseDraftStep.SIMULATION -> {
                    val purchase = candidate
                    if (purchase != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            InstallmentSimulatorScreen(
                                candidate = purchase,
                                card = card,
                                teaBps = null,
                                selectedInstallments = selectedInstallments,
                                onInstallmentsChanged = { selectedInstallments = it },
                                onConfirmPurchase = { count ->
                                    onConfirmPurchase(purchase, count)
                                },
                                onRejectPurchase = { step = PurchaseDraftStep.FORM },
                                showHeader = false,
                                showActions = false,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val diacriticsPattern = Regex("\\p{Mn}+")

private fun PurchaseCategoryOption.searchKey(): String = name.searchKey()

private fun String.searchKey(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
    .replace(diacriticsPattern, "")
    .lowercase(Locale.ROOT)

@Composable
private fun PurchaseDraftChoice(
    title: String,
    value: String,
    leadingIcon: ImageVector,
    onClick: () -> Unit,
    supportingText: String? = null,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(leadingIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supportingText != null) {
                    Text(supportingText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
