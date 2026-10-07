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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kipu.app.BuildConfig
import com.kipu.app.feature.categories.domain.CategoryRules
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.CategoryId

// TAXONOMÍA DE INTERFAZ (UI TAXONOMY)
// Documentado en EP-CCO: Agrupación visual coherente para facilitar la búsqueda
// independiente de las subcategorías contables de la base de datos.
enum class UiMerchantGroup(
    val label: String,
    val canonicalNames: Set<String>,
    val canonicalIds: Set<String> = emptySet(),
) {
    RESTAURANTS(
        label = "Restaurantes y Delivery",
        canonicalNames = setOf("bembos", "burger king", "kfc", "mcdonalds", "pedidosya", "pizza hut", "rappi", "starbucks"),
        canonicalIds = setOf(
            "00000000-0000-0001-0000-000000000002", // Starbucks
            "00000000-0000-0000-0001-000000000010", // KFC
            "00000000-0000-0000-0001-000000000011", // McDonald's
            "00000000-0000-0000-0001-000000000012", // Bembos
            "00000000-0000-0000-0001-000000000013", // Burger King
            "00000000-0000-0000-0001-000000000014", // Pizza Hut
            "00000000-0000-0000-0001-000000000015", // Rappi
            "00000000-0000-0000-0001-000000000016", // PedidosYa
        ),
    ),
    ENTERTAINMENT(
        label = "Entretenimiento y Streaming",
        canonicalNames = setOf("cinemark", "cineplanet", "crunchyroll", "disney", "disney plus", "joinnus", "max", "netflix", "prime video", "spotify", "teleticket", "youtube premium"),
        canonicalIds = setOf(
            "00000000-0000-0000-0001-000000000040", // Netflix
            "00000000-0000-0000-0001-000000000041", // Disney+
            "00000000-0000-0000-0001-000000000042", // Prime Video
            "00000000-0000-0000-0001-000000000043", // Spotify
            "00000000-0000-0000-0001-000000000046", // Max
            "00000000-0000-0000-0001-000000000047", // YouTube Premium
            "00000000-0000-0000-0001-000000000048", // Crunchyroll
            "00000000-0000-0000-0001-000000000060", // Cineplanet
            "00000000-0000-0000-0001-000000000061", // Cinemark
            "00000000-0000-0000-0001-000000000062", // Teleticket
            "00000000-0000-0000-0001-000000000063", // Joinnus
        ),
    ),
    SUPERMARKETS(
        label = "Supermercados y Tiendas",
        canonicalNames = setOf("listo", "mass", "metro", "oxxo", "plaza vea", "tambo", "tottus", "wong"),
        canonicalIds = setOf(
            "00000000-0000-0001-0000-000000000001", // Tambo
            "00000000-0000-0001-0000-000000000003", // Plaza Vea
            "00000000-0000-0000-0001-000000000004", // Tottus
            "00000000-0000-0000-0001-000000000005", // Metro
            "00000000-0000-0000-0001-000000000006", // Wong
            "00000000-0000-0000-0001-000000000007", // Mass
            "00000000-0000-0000-0001-000000000008", // Oxxo
            "00000000-0000-0000-0001-000000000009", // Listo!
        ),
    ),
    TRANSPORT(
        label = "Transporte y Viajes",
        canonicalNames = setOf("cabify", "didi", "indrive", "latam airlines", "latam", "lima expresa", "linea 1", "metropolitano", "rutas de lima", "uber"),
        canonicalIds = setOf(
            "00000000-0000-0000-0001-000000000019", // Uber
            "00000000-0000-0000-0001-000000000020", // inDrive
            "00000000-0000-0000-0001-000000000021", // DiDi
            "00000000-0000-0000-0001-000000000022", // Cabify
            "00000000-0000-0000-0001-000000000026", // Metropolitano
            "00000000-0000-0000-0001-000000000027", // Línea 1
            "00000000-0000-0000-0001-000000000028", // Rutas de Lima
            "00000000-0000-0000-0001-000000000029", // Lima Expresa
            "00000000-0000-0000-0001-000000000030", // LATAM Airlines
        ),
    ),
    TELECOM(
        label = "Telecomunicaciones",
        canonicalNames = setOf("bitel", "claro", "entel", "movistar", "win"),
        canonicalIds = setOf(
            "00000000-0000-0000-0001-000000000035", // Claro
            "00000000-0000-0000-0001-000000000036", // Movistar
            "00000000-0000-0000-0001-000000000037", // Entel
            "00000000-0000-0000-0001-000000000038", // Bitel
            "00000000-0000-0000-0001-000000000039", // Win
        ),
    ),
    TECH(
        label = "Productividad e IA",
        canonicalNames = setOf("adobe", "canva", "chatgpt", "claude", "google ai pro", "google one", "icloud", "icloud plus", "microsoft 365", "notion", "perplexity"),
        canonicalIds = setOf(
            "00000000-0000-0000-0001-000000000044", // Google One
            "00000000-0000-0000-0001-000000000045", // iCloud+
            "00000000-0000-0000-0001-000000000049", // ChatGPT
            "00000000-0000-0000-0001-000000000050", // Claude
            "00000000-0000-0000-0001-000000000051", // Google AI Pro
            "00000000-0000-0000-0001-000000000052", // Perplexity
            "00000000-0000-0000-0001-000000000053", // Canva
            "00000000-0000-0000-0001-000000000054", // Microsoft 365
            "00000000-0000-0000-0001-000000000055", // Adobe
            "00000000-0000-0000-0001-000000000056", // Notion
        ),
    ),
    GAMING(
        label = "Videojuegos",
        canonicalNames = setOf("playstation", "steam", "xbox"),
        canonicalIds = setOf(
            "00000000-0000-0000-0001-000000000057", // Steam
            "00000000-0000-0000-0001-000000000058", // PlayStation
            "00000000-0000-0000-0001-000000000059", // Xbox
        ),
    ),
    UTILITIES(
        label = "Servicios Básicos",
        canonicalNames = setOf("calidda", "luz del sur", "pluz energia", "sedapal"),
        canonicalIds = setOf(
            "00000000-0000-0000-0001-000000000031", // Pluz Energía
            "00000000-0000-0000-0001-000000000032", // Luz del Sur
            "00000000-0000-0000-0001-000000000033", // Sedapal
            "00000000-0000-0000-0001-000000000034", // Cálidda
        ),
    ),
    GAS(
        label = "Estaciones de Servicio",
        canonicalNames = setOf("petroperu", "primax", "repsol"),
        canonicalIds = setOf(
            "00000000-0000-0000-0001-000000000023", // Primax
            "00000000-0000-0000-0001-000000000024", // Repsol
            "00000000-0000-0000-0001-000000000025", // Petroperú
        ),
    ),
    HEALTH(
        label = "Farmacias y Salud",
        canonicalNames = setOf("inkafarma", "mifarma"),
        canonicalIds = setOf(
            "00000000-0000-0000-0001-000000000017", // Inkafarma
            "00000000-0000-0000-0001-000000000018", // Mifarma
        ),
    ),
    EDUCATION(
        label = "Educación",
        canonicalNames = setOf("wikipedia"),
    );

    val merchants: Set<String> get() = canonicalNames

    fun matches(entry: MerchantCatalogEntry): Boolean {
        if (canonicalIds.contains(entry.id.value)) return true
        val assignedToOtherGroup = entries.any { other ->
            other != this && other.canonicalIds.contains(entry.id.value)
        }
        if (assignedToOtherGroup) return false
        val normName = CategoryRules.normalizeText(entry.name)
        val normStored = CategoryRules.normalizeText(entry.normalizedName)
        return canonicalNames.contains(normName) || canonicalNames.contains(normStored)
    }
}

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
    var selectedUiGroup by remember { mutableStateOf<UiMerchantGroup?>(null) }

    val catalogToDisplay = remember(state.catalogEntries, state.searchResults, state.query) {
        if (state.query.isBlank()) {
            state.catalogEntries
        } else {
            val baseList = if (state.catalogEntries.isNotEmpty()) state.catalogEntries else state.searchResults
            baseList.filter { CategoryRules.matchesNormalized(it.name, state.query) }
        }
    }
    
    val availableGroups = remember(state.catalogEntries, catalogToDisplay) {
        val baseCatalog = state.catalogEntries.ifEmpty { catalogToDisplay }
        UiMerchantGroup.entries.filter { group ->
            baseCatalog.any { merchant -> group.matches(merchant) }
        }
    }

    LaunchedEffect(availableGroups, selectedUiGroup) {
        if (selectedUiGroup != null && selectedUiGroup !in availableGroups) {
            selectedUiGroup = null
        }
    }

    // Una vez obtenida la lista de coincidencias correcta, aplicamos el filtro de taxonomía UI canónico.
    val visibleMerchants = remember(catalogToDisplay, selectedUiGroup) {
        if (selectedUiGroup == null) {
            catalogToDisplay
        } else {
            catalogToDisplay.filter { merchant -> selectedUiGroup!!.matches(merchant) }
        }
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

        // Chips de filtro UI (Taxonomía Visual)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item {
                FilterChip(
                    selected = selectedUiGroup == null,
                    onClick = { selectedUiGroup = null },
                    label = { Text("Todos", fontSize = 12.sp) },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
            
            items(availableGroups, key = { it.name }) { group ->
                FilterChip(
                    selected = selectedUiGroup == group,
                    onClick = {
                        selectedUiGroup = if (selectedUiGroup == group) null else group
                    },
                    label = { Text(group.label, fontSize = 12.sp) },
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
fun MerchantAvatar(
    drawableId: Int,
    remoteLogoUrl: String?,
    profile: MerchantVisualProfile,
    size: Dp = 42.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        var isRemoteLoaded by remember(remoteLogoUrl) { mutableStateOf(false) }
        var isRemoteError by remember(remoteLogoUrl) { mutableStateOf(false) }

        if (remoteLogoUrl != null && !isRemoteError) {
            if (!isRemoteLoaded) {
                if (drawableId != 0) {
                    Box(
                        modifier = Modifier
                            .size(size)
                            .background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(drawableId),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(size),
                        )
                    }
                } else {
                    MerchantInitialsBox(profile = profile, size = size)
                }
            }

            AsyncImage(
                model = remoteLogoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(size)
                    .then(if (isRemoteLoaded) Modifier.background(Color.White) else Modifier),
                onSuccess = { isRemoteLoaded = true },
                onError = { isRemoteError = true },
            )
        } else if (drawableId != 0) {
            Box(
                modifier = Modifier
                    .size(size)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(drawableId),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(size),
                )
            }
        } else {
            MerchantInitialsBox(profile = profile, size = size)
        }
    }
}

@Composable
private fun MerchantInitialsBox(
    profile: MerchantVisualProfile,
    size: Dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(profile.backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = profile.initials,
            color = profile.foregroundColor,
            fontWeight = FontWeight.Bold,
            fontSize = if (size <= 40.dp) 13.sp else 14.sp,
        )
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
    
    val context = LocalContext.current
    val drawableId = remember(selectedMerchant?.logoKey) {
        selectedMerchant?.logoKey?.let { context.resources.getIdentifier(it, "drawable", context.packageName) } ?: 0
    }
    val remoteLogoUrl = remember(selectedMerchant?.priority, selectedMerchant?.logoKey) {
        val supabaseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        selectedMerchant?.logoKey
            ?.takeIf { selectedMerchant.priority != "A" && it.isNotBlank() && supabaseUrl.isNotBlank() }
            ?.let { "$supabaseUrl/storage/v1/object/public/merchant-logos/${Uri.encode(it, "/")}" }
    }

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
            MerchantAvatar(
                drawableId = drawableId,
                remoteLogoUrl = remoteLogoUrl,
                profile = profile,
                size = 40.dp,
            )

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
            MerchantAvatar(
                drawableId = drawableId,
                remoteLogoUrl = remoteLogoUrl,
                profile = profile,
                size = 42.dp,
            )

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
