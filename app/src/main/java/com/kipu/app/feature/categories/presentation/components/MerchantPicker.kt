package com.kipu.app.feature.categories.presentation.components

import android.net.Uri
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kipu.app.BuildConfig
import com.kipu.app.feature.categories.domain.CategoryRules
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.CategoryId

data class MerchantVisualProfile(
    val initials: String,
    val backgroundColor: Color,
    val foregroundColor: Color,
    val subtitle: String,
)

fun getMerchantVisualProfile(name: String, brandColor: String? = null): MerchantVisualProfile {
    val normalizedName = name.lowercase().trim()
    val words = name.trim().split(" ").filter { it.isNotBlank() }
    val initials = if (words.size >= 2) {
        "${words[0].first().uppercaseChar()}${words[1].first().uppercaseChar()}"
    } else if (words.isNotEmpty()) {
        words[0].take(2).uppercase()
    } else {
        "C"
    }
    val parsedColor = runCatching {
        brandColor?.let { Color(android.graphics.Color.parseColor(it)) }
    }.getOrNull()
    val fallbackColor = when (normalizedName) {
        "tambo" -> Color(0xFF652D90)
        "starbucks" -> Color(0xFF006241)
        "plaza vea" -> Color(0xFFC8102E)
        "metro" -> Color(0xFFE5AB00)
        "tottus" -> Color(0xFFE2231A)
        "netflix" -> Color(0xFFE50914)
        "spotify" -> Color(0xFF1DB954)
        "uber" -> Color(0xFF000000)
        else -> Color.hsl(((normalizedName.hashCode() and Int.MAX_VALUE) % 360).toFloat(), 0.62f, 0.42f)
    }
    val backgroundColor = parsedColor ?: fallbackColor
    val foregroundColor = if (backgroundColor.red * 0.2126f + backgroundColor.green * 0.7152f + backgroundColor.blue * 0.0722f > 0.5f) {
        Color.Black
    } else {
        Color.White
    }
    return MerchantVisualProfile(initials, backgroundColor, foregroundColor, "Comercio o servicio")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantPickerBottomSheet(
    state: MerchantPickerState,
    onQueryChange: (String) -> Unit,
    onSelectMerchant: (MerchantCatalogEntry) -> Unit,
    onSetProvisionalText: (String) -> Unit,
    onClearSelection: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header: Título y botón cerrar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(18.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Seleccionar Comercio o Servicio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar")
                }
            }

            // Embedded MerchantPicker
            MerchantPicker(
                state = state,
                onQueryChange = onQueryChange,
                onSelectMerchant = {
                    onSelectMerchant(it)
                    onDismiss()
                },
                onSetProvisionalText = {
                    onSetProvisionalText(it)
                    onDismiss()
                },
                onClearSelection = onClearSelection,
            )
        }
    }
}

@Composable
fun MerchantPicker(
    state: MerchantPickerState,
    onQueryChange: (String) -> Unit,
    onSelectMerchant: (MerchantCatalogEntry) -> Unit,
    onSetProvisionalText: (String) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedCategoryId by remember { mutableStateOf<CategoryId?>(null) }
    val availableCategoryIds = state.categoryFilters.mapTo(hashSetOf()) { it.categoryId }
    val activeCategoryId = selectedCategoryId?.takeIf { it in availableCategoryIds }
    val catalogToDisplay = remember(state.catalogEntries, state.searchResults, state.query) {
        if (state.query.isBlank()) {
            state.catalogEntries
        } else {
            val baseList = if (state.catalogEntries.isNotEmpty()) state.catalogEntries else state.searchResults
            baseList.filter { CategoryRules.matchesNormalized(it.name, state.query) }
        }
    }
    val visibleMerchants = remember(catalogToDisplay, activeCategoryId) {
        catalogToDisplay.filter { activeCategoryId == null || it.defaultCategoryId == activeCategoryId }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Preview de selección actual
        if (state.hasSelection) {
            SelectedMerchantCard(
                selectedMerchant = state.selectedMerchant,
                provisionalText = state.provisionalText,
                onClear = onClearSelection,
            )
        }

        // Barra de Búsqueda
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = { Text("Buscar comercio o ingresar nuevo...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Limpiar búsqueda")
                    }
                } else if (state.isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("merchant_search_input"),
        )

        // Chips de filtro
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item {
                FilterChip(
                    selected = activeCategoryId == null,
                    onClick = { selectedCategoryId = null },
                    label = { Text("Todos", fontSize = 12.sp) },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
            items(state.categoryFilters, key = { it.categoryId.value }) { category ->
                FilterChip(
                    selected = activeCategoryId == category.categoryId,
                    onClick = {
                        selectedCategoryId = if (activeCategoryId == category.categoryId) null else category.categoryId
                    },
                    label = { Text(category.name, fontSize = 12.sp) },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }

        // Banner de estado de catálogo
        if (state.catalogStatus != CatalogStatus.AVAILABLE) {
            CatalogStatusBanner(status = state.catalogStatus)
        }

        // Category chips filter the local merchant cache immediately.
        if (state.query.isNotBlank() || state.catalogEntries.isNotEmpty()) {
            Text(
                text = if (state.query.isBlank()) "Comercios del catálogo:" else "Resultados del catálogo:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (visibleMerchants.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .testTag("merchant_results_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visibleMerchants, key = { it.id.value }) { entry ->
                        MerchantResultItem(
                            entry = entry,
                            onClick = { onSelectMerchant(entry) },
                        )
                    }
                    if (state.query.isNotBlank()) {
                        item {
                            FallbackProvisionalRow(
                                query = state.query,
                                onUseProvisional = { onSetProvisionalText(state.query) },
                            )
                        }
                    }
                }
            } else if (state.query.isNotBlank() && !state.isSearching) {
                EmptyResultWithProvisionalOption(
                    query = state.query,
                    onUseProvisional = { onSetProvisionalText(state.query) },
                )
            }
        }
    }
}

@Composable
fun SelectedMerchantCard(
    selectedMerchant: MerchantCatalogEntry?,
    provisionalText: String?,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayName = selectedMerchant?.name ?: provisionalText ?: ""
    val profile = getMerchantVisualProfile(displayName, selectedMerchant?.brandColor)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(profile.backgroundColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = profile.initials,
                    color = profile.foregroundColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (selectedMerchant != null) "Comercio del catálogo" else "Texto provisional",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            IconButton(
                onClick = onClear,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("clear_merchant_button"),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Quitar comercio seleccionado",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun MerchantResultItem(
    entry: MerchantCatalogEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile = getMerchantVisualProfile(entry.name, entry.brandColor)
    val context = LocalContext.current
    val drawableId = remember(entry.logoKey) {
        entry.logoKey?.let { context.resources.getIdentifier(it, "drawable", context.packageName) } ?: 0
    }
    val remoteLogoUrl = remember(entry.priority, entry.logoKey) {
        val supabaseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        entry.logoKey
            ?.takeIf { entry.priority != "A" && it.isNotBlank() && supabaseUrl.isNotBlank() }
            ?.let { "$supabaseUrl/storage/v1/object/public/merchant-logos/${Uri.encode(it, "/")}" }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(profile.backgroundColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = profile.initials,
                    color = profile.foregroundColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                when {
                    drawableId != 0 -> Image(
                        painter = painterResource(drawableId),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(34.dp),
                    )
                    remoteLogoUrl != null -> AsyncImage(
                        model = remoteLogoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = profile.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
fun FallbackProvisionalRow(
    query: String,
    onUseProvisional: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initial = query.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onUseProvisional),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Añadir comercio personalizado:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "\"$query\"",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun EmptyResultWithProvisionalOption(
    query: String,
    onUseProvisional: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "No se encontró \"$query\" en el catálogo",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Puedes asignarlo como texto provisional para este movimiento.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onUseProvisional,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("use_provisional_button"),
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Usar \"$query\" como texto provisional", fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun CatalogStatusBanner(
    status: CatalogStatus,
    modifier: Modifier = Modifier,
) {
    val message = when (status) {
        CatalogStatus.STALE -> "Catálogo sin sincronizar recientemente. Los resultados locales pueden estar desactualizados."
        CatalogStatus.UNAVAILABLE -> "Catálogo de comercios no disponible sin conexión. Puedes usar un texto provisional."
        CatalogStatus.AVAILABLE -> ""
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = "Estado del catálogo",
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}
