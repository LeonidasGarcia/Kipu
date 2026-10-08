package com.kipu.app.feature.categories.presentation.merchantrules

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kipu.app.feature.categories.domain.model.MerchantAliasRule
import com.kipu.app.feature.categories.domain.model.MerchantAliasRuleId

@Composable
fun MerchantAliasRulesScreen(
    onNavigateBack: () -> Unit,
    viewModel: MerchantAliasRulesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    MerchantAliasRulesContent(
        state = state,
        onSourceTextChange = viewModel::onSourceTextChange,
        onMerchantQueryChange = viewModel::onMerchantQueryChange,
        onSelectMerchant = viewModel::onSelectMerchant,
        onConfirmMerchant = viewModel::onConfirmMerchant,
        onSave = viewModel::onSave,
        onEdit = viewModel::onEdit,
        onDelete = viewModel::onDelete,
        onCancelEdit = viewModel::onCancelEdit,
        onNavigateBack = onNavigateBack,
    )
}

@Composable
fun MerchantAliasRulesContent(
    state: MerchantAliasRulesUiState,
    onSourceTextChange: (String) -> Unit,
    onMerchantQueryChange: (String) -> Unit,
    onSelectMerchant: (com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry) -> Unit,
    onConfirmMerchant: () -> Unit = {},
    onSave: () -> Unit,
    onEdit: (MerchantAliasRuleId) -> Unit = {},
    onDelete: (MerchantAliasRuleId) -> Unit,
    onCancelEdit: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
) {
    val animationDuration = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ValueAnimator.areAnimatorsEnabled()) 0 else 160
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onNavigateBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
            }
            Column {
                Text("Reglas de alias", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("Reconoce comercios con el texto de origen exacto", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Nueva regla", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (!state.isPremiumVerified && state.editingRule == null) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(
                            "Premium necesario para crear una regla nueva",
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
                OutlinedTextField(
                    value = state.sourceText,
                    onValueChange = onSourceTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Texto fuente exacto") },
                    supportingText = { Text("Se normalizarán mayúsculas, tildes y espacios. El texto original permanece separado.") },
                    minLines = 1,
                    maxLines = 3,
                    shape = RoundedCornerShape(16.dp),
                )
                OutlinedTextField(
                    value = state.merchantQuery,
                    onValueChange = onMerchantQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Buscar comercio canónico") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
                state.merchants.take(5).forEach { merchant ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .semantics { role = Role.Button }
                            .clickable { onSelectMerchant(merchant) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (state.candidateMerchant?.id == merchant.id) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Text(merchant.name, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                state.candidateMerchant?.let { candidate ->
                    Button(onClick = onConfirmMerchant, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text("Confirmar ${candidate.name}")
                    }
                }
                AnimatedVisibility(
                    visible = state.selectedMerchant != null,
                    enter = fadeIn(tween(animationDuration)) + expandVertically(tween(animationDuration)),
                    exit = fadeOut(tween(animationDuration)) + shrinkVertically(tween(animationDuration)),
                ) {
                    state.selectedMerchant?.let { merchant ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Column {
                                    Text("Comercio confirmado", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    Text("Comercio canónico: ${merchant.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Coincidencia exacta normalizada; la puntuación se conserva.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Sin coincidencia, el comercio no se asigna y el texto queda disponible para revisión.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Si las reglas apuntan a comercios distintos, la decisión requiere revisión manual.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Si Premium o el consentimiento no están vigentes, puedes registrar el movimiento manualmente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = onSave,
                    enabled = !state.isSaving && state.sourceText.isNotBlank() && state.selectedMerchant != null &&
                        (state.isPremiumVerified || state.editingRule != null),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(if (state.editingRule == null) "Guardar alias" else "Guardar cambios")
                }
                if (state.editingRule != null) {
                    OutlinedButton(onClick = onCancelEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Cancelar edición")
                    }
                }
                state.message?.let { message ->
                    Text(
                        message,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = if (message.contains("no pudo", ignoreCase = true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Text("Tus reglas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        val activeRules = state.rules.filter { it.deletedAt == null }
        if (activeRules.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Text("Aún no guardaste alias. Tus movimientos confirmados no se modificarán.", Modifier.padding(18.dp), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            activeRules.forEach { rule ->
                AliasRuleCard(
                    rule = rule,
                    merchantName = state.catalog.firstOrNull { it.id == rule.merchantId }?.name ?: "Comercio del catálogo",
                    onEdit = { onEdit(rule.id) },
                    onDelete = { onDelete(rule.id) },
                )
            }
        }
    }
}

@Composable
private fun AliasRuleCard(
    rule: MerchantAliasRule,
    merchantName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Texto fuente normalizado", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(rule.normalizedPattern, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Comercio canónico: $merchantName", style = MaterialTheme.typography.bodyMedium)
            Text(syncStateLabel(rule.syncState.name), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Editar")
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Eliminar")
                }
            }
        }
    }
}

private fun syncStateLabel(value: String): String = when (value) {
    "SYNCED" -> "Sincronizado"
    "CONFLICT" -> "Requiere revisión"
    "FAILED" -> "Sin sincronizar; intenta de nuevo"
    else -> "Pendiente de sincronización"
}
