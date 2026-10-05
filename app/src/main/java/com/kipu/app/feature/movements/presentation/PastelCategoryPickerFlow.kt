package com.kipu.app.feature.movements.presentation

import com.kipu.app.feature.categories.presentation.categories.CategoryFormContent
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.CategoryType

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kipu.app.feature.categories.presentation.resolveCategoryIcon
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.KipuMotionTokens
import com.kipu.app.ui.theme.KipuTheme
import com.kipu.app.ui.theme.rememberKipuColors
import kotlinx.coroutines.launch

// ============================================================================
// 1. DESIGN TOKENS & PASTEL PALETTES (Andean Modernist & Soft Pastel)
// ============================================================================

internal object PastelDesignTokens {
    val TealPrimary: Color @Composable get() = rememberKipuColors().primaryText
    val TealDark: Color @Composable get() = if (rememberKipuColors().isDark) Color(0xFF5EEAD4) else Color(0xFF074842)
    val TealLightBg: Color @Composable get() = if (rememberKipuColors().isDark) Color(0xFF123B38) else Color(0xFFF0FDF4)

    val SurfaceCard: Color @Composable get() = rememberKipuColors().surfaceSubtle
    val SurfaceWhite: Color @Composable get() = rememberKipuColors().surface
    val BorderSubtle: Color @Composable get() = rememberKipuColors().border
    val TextMain: Color @Composable get() = rememberKipuColors().inkPrimary
    val TextMuted: Color @Composable get() = if (rememberKipuColors().isDark) rememberKipuColors().inkSecondary else Color(0xFF475569)
    val TextPlaceholder: Color @Composable get() = if (rememberKipuColors().isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val ChipBg: Color @Composable get() = rememberKipuColors().surfaceVariant

    // Semantic Pastel Token definition
    data class PastelToken(
        val name: String,
        val containerColor: Color,
        val borderColor: Color,
        val contentColor: Color,
        val hexCode: String,
    )

    private val BaseMint = PastelToken("Mint", Color(0xFFDCFCE7), Color(0xFF166534), Color(0xFF166534), "#DCFCE7")
    private val BaseOrange = PastelToken("Orange", Color(0xFFFFEDD5), Color(0xFF9A3412), Color(0xFF9A3412), "#FFEDD5")
    private val BasePurple = PastelToken("Purple", Color(0xFFEDE9FE), Color(0xFF5B21B6), Color(0xFF5B21B6), "#EDE9FE")
    private val BaseSky = PastelToken("Sky", Color(0xFFE0F2FE), Color(0xFF075985), Color(0xFF075985), "#E0F2FE")
    private val BaseRose = PastelToken("Rose", Color(0xFFFFE4E6), Color(0xFF9F1239), Color(0xFF9F1239), "#FFE4E6")
    private val BaseAmber = PastelToken("Amber", Color(0xFFFEF3C7), Color(0xFF92400E), Color(0xFF92400E), "#FEF3C7")
    private val BaseEmerald = PastelToken("Emerald", Color(0xFFD1FAE5), Color(0xFF065F46), Color(0xFF065F46), "#D1FAE5")
    private val BaseLavender = PastelToken("Lavender", Color(0xFFF3E8FF), Color(0xFF6B21A8), Color(0xFF6B21A8), "#F3E8FF")
    private val BaseIndigo = PastelToken("Indigo", Color(0xFFE0E7FF), Color(0xFF3730A3), Color(0xFF3730A3), "#E0E7FF")
    private val BaseBlue = PastelToken("Blue", Color(0xFFDBEAFE), Color(0xFF1D4ED8), Color(0xFF1D4ED8), "#DBEAFE")
    private val BaseTeal = PastelToken("Teal", Color(0xFFCCFBF1), Color(0xFF0F766E), Color(0xFF0F766E), "#CCFBF1")
    private val BaseSlate = PastelToken("Slate", Color(0xFFF1F5F9), Color(0xFF334155), Color(0xFF334155), "#F1F5F9")
    private val BaseRed = PastelToken("Red", Color(0xFFFEE2E2), Color(0xFFB91C1C), Color(0xFFB91C1C), "#FEE2E2")
    private val BaseRecommendedPastelChips = listOf(
        BaseMint, BaseOrange, BasePurple, BaseSky, BaseRose, BaseAmber, BaseEmerald,
        BaseLavender, BaseIndigo, BaseBlue, BaseTeal, BaseSlate,
    )

    @Composable
    private fun themed(base: PastelToken): PastelToken {
        if (!rememberKipuColors().isDark) return base
        val (container, foreground) = when (base.name) {
            "Mint" -> Color(0xFF14532D) to Color(0xFF86EFAC)
            "Orange" -> Color(0xFF7C2D12) to Color(0xFFFDBA74)
            "Purple" -> Color(0xFF4C1D95) to Color(0xFFC4B5FD)
            "Sky" -> Color(0xFF0C4A6E) to Color(0xFF7DD3FC)
            "Rose" -> Color(0xFF881337) to Color(0xFFFDA4AF)
            "Amber" -> Color(0xFF78350F) to Color(0xFFFCD34D)
            "Emerald" -> Color(0xFF064E3B) to Color(0xFF6EE7B7)
            "Lavender" -> Color(0xFF581C87) to Color(0xFFD8B4FE)
            "Indigo" -> Color(0xFF312E81) to Color(0xFFA5B4FC)
            "Blue" -> Color(0xFF1E3A8A) to Color(0xFF93C5FD)
            "Teal" -> Color(0xFF134E4A) to Color(0xFF5EEAD4)
            "Red" -> Color(0xFF7F1D1D) to Color(0xFFFCA5A5)
            else -> Color(0xFF334155) to Color(0xFFCBD5E1)
        }
        return base.copy(containerColor = container, borderColor = foreground, contentColor = foreground)
    }

    val Mint: PastelToken @Composable get() = themed(BaseMint)
    val Orange: PastelToken @Composable get() = themed(BaseOrange)
    val Purple: PastelToken @Composable get() = themed(BasePurple)
    val Sky: PastelToken @Composable get() = themed(BaseSky)
    val Rose: PastelToken @Composable get() = themed(BaseRose)
    val Amber: PastelToken @Composable get() = themed(BaseAmber)
    val Emerald: PastelToken @Composable get() = themed(BaseEmerald)
    val Lavender: PastelToken @Composable get() = themed(BaseLavender)
    val Indigo: PastelToken @Composable get() = themed(BaseIndigo)
    val Blue: PastelToken @Composable get() = themed(BaseBlue)
    val Teal: PastelToken @Composable get() = themed(BaseTeal)
    val Slate: PastelToken @Composable get() = themed(BaseSlate)
    val Red: PastelToken @Composable get() = themed(BaseRed)
    val RecommendedPastelChips: List<PastelToken> @Composable get() = BaseRecommendedPastelChips.map { themed(it) }

    @Composable
    fun resolvePastelToken(name: String, icon: String = ""): PastelToken {
        val n = name.lowercase()
        val i = icon.lowercase()
        val base = when {
            n.contains("alim") || n.contains("rest") || n.contains("comid") || n.contains("super") || n.contains("merc") ||
                i.contains("restaurant") || i.contains("shopping") || i.contains("cafe") -> BaseOrange
            n.contains("susc") || n.contains("stream") || n.contains("soft") || n.contains("cine") || n.contains("tv") ||
                i.contains("movie") || i.contains("wifi") -> BasePurple
            n.contains("trans") || n.contains("movil") || n.contains("auto") || n.contains("gas") || n.contains("viaj") ||
                i.contains("car") || i.contains("transit") || i.contains("gas") || i.contains("flight") -> BaseSky
            n.contains("salud") || n.contains("farm") || n.contains("med") || n.contains("cuid") ||
                i.contains("medical") || i.contains("health") -> BaseRose
            n.contains("hogar") || n.contains("casa") || n.contains("serv") || n.contains("luz") || n.contains("agua") ||
                i.contains("home") || i.contains("bolt") || i.contains("water") -> BaseAmber
            n.contains("educ") || n.contains("lib") || n.contains("univ") || n.contains("curs") ||
                i.contains("school") -> BaseEmerald
            n.contains("ingres") || n.contains("sueld") || n.contains("sala") || n.contains("ahorr") -> BaseMint
            n.contains("depor") || n.contains("gym") || n.contains("bien") || i.contains("fitness") -> BaseLavender
            else -> {
                val index = Math.abs(name.hashCode()) % BaseRecommendedPastelChips.size
                BaseRecommendedPastelChips[index]
            }
        }
        return themed(base)
    }
}

// ============================================================================
// 2. ICON BADGE & ICON CATALOG SPECIFICATION
// ============================================================================

@Composable
internal fun PastelIconBadge(
    icon: ImageVector,
    containerColor: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    shapeRadius: Dp = 12.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color = containerColor, shape = RoundedCornerShape(shapeRadius)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

internal data class CatalogIconItem(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val category: String,
    val keywords: List<String>,
)

internal val CATALOG_TABS = listOf(
    "Todos",
    "Películas & Series",
    "Finanzas",
    "Hogar",
    "Tecnología",
    "Alimentación",
    "Transporte",
    "Bienestar",
)

internal val CATALOG_ICONS = listOf(
    // Películas & Series
    CatalogIconItem("tv", Icons.Default.Tv, "TV Streaming", "Películas & Series", listOf("tv", "streaming", "netflix", "disney", "serie", "pantalla", "pelicula")),
    CatalogIconItem("movie", Icons.Default.Movie, "Película", "Películas & Series", listOf("pelicula", "cine", "film", "video")),
    CatalogIconItem("theaters", Icons.Default.Theaters, "Cine", "Películas & Series", listOf("cine", "sala", "teatro", "pelicula")),
    CatalogIconItem("videocam", Icons.Default.Videocam, "Proyector", "Películas & Series", listOf("proyector", "video", "camara", "pelicula")),
    CatalogIconItem("confirmation_number", Icons.Default.ConfirmationNumber, "Ticket", "Películas & Series", listOf("ticket", "boleto", "entrada", "cine", "pelicula")),
    CatalogIconItem("headphones", Icons.Default.Headphones, "Auriculares", "Películas & Series", listOf("auriculares", "audifonos", "sonido", "musica")),
    CatalogIconItem("music_note", Icons.Default.MusicNote, "Música", "Películas & Series", listOf("musica", "spotify", "cancion", "audio")),
    CatalogIconItem("theater_comedy", Icons.Default.TheaterComedy, "Espectáculo", "Películas & Series", listOf("teatro", "comedia", "show")),

    // Finanzas
    CatalogIconItem("account_balance", Icons.Default.AccountBalance, "Banco", "Finanzas", listOf("banco", "cuenta", "bcp", "bbva", "interbank")),
    CatalogIconItem("account_balance_wallet", Icons.Default.AccountBalanceWallet, "Billetera", "Finanzas", listOf("billetera", "wallet", "efectivo")),
    CatalogIconItem("credit_card", Icons.Default.CreditCard, "Tarjeta", "Finanzas", listOf("tarjeta", "credito", "debito", "visa")),
    CatalogIconItem("payments", Icons.Default.Payments, "Efectivo", "Finanzas", listOf("efectivo", "pago", "dinero", "billete")),
    CatalogIconItem("receipt", Icons.Default.Receipt, "Recibo", "Finanzas", listOf("recibo", "factura", "boleta", "comprobante")),
    CatalogIconItem("savings", Icons.Default.Savings, "Ahorros", "Finanzas", listOf("ahorro", "alcancia", "meta")),
    CatalogIconItem("work", Icons.Default.Work, "Trabajo", "Finanzas", listOf("trabajo", "empleo", "sueldo", "oficina")),

    // Hogar
    CatalogIconItem("home", Icons.Default.Home, "Hogar", "Hogar", listOf("hogar", "casa", "alquiler", "depa")),
    CatalogIconItem("electric_bolt", Icons.Default.ElectricBolt, "Electricidad", "Hogar", listOf("luz", "electricidad", "energia")),
    CatalogIconItem("water_drop", Icons.Default.WaterDrop, "Agua", "Hogar", listOf("agua", "sedapal", "servicio")),
    CatalogIconItem("wifi", Icons.Default.Wifi, "Internet", "Hogar", listOf("internet", "wifi", "fibra", "cable")),
    CatalogIconItem("build", Icons.Default.Build, "Mantenimiento", "Hogar", listOf("mantenimiento", "reparacion", "herramientas")),
    CatalogIconItem("cleaning_services", Icons.Default.CleaningServices, "Limpieza", "Hogar", listOf("limpieza", "aseo", "orden")),

    // Tecnología
    CatalogIconItem("sports_esports", Icons.Default.SportsEsports, "Videojuegos", "Tecnología", listOf("juegos", "playstation", "xbox", "gaming")),
    CatalogIconItem("laptop", Icons.Default.Laptop, "Computadora", "Tecnología", listOf("laptop", "computadora", "pc", "software")),
    CatalogIconItem("phone_android", Icons.Default.PhoneAndroid, "Celular", "Tecnología", listOf("celular", "telefono", "movil", "plan")),
    CatalogIconItem("cloud", Icons.Default.Cloud, "Nube", "Tecnología", listOf("nube", "cloud", "almacenamiento", "drive")),
    CatalogIconItem("devices", Icons.Default.Devices, "Dispositivos", "Tecnología", listOf("dispositivos", "gadgets", "tech")),

    // Alimentación
    CatalogIconItem("restaurant", Icons.Default.Restaurant, "Restaurante", "Alimentación", listOf("restaurante", "comida", "almuerzo", "cena")),
    CatalogIconItem("shopping_cart", Icons.Default.ShoppingCart, "Supermercado", "Alimentación", listOf("supermercado", "compras", "mercado", "metro")),
    CatalogIconItem("local_cafe", Icons.Default.LocalCafe, "Cafetería", "Alimentación", listOf("cafe", "starbucks", "cafeteria", "postre")),
    CatalogIconItem("storefront", Icons.Default.Storefront, "Bodega", "Alimentación", listOf("bodega", "tienda", "market")),
    CatalogIconItem("fastfood", Icons.Default.Fastfood, "Comida Rápida", "Alimentación", listOf("hamburguesa", "pizza", "fast food")),
    CatalogIconItem("local_bar", Icons.Default.LocalBar, "Bebidas", "Alimentación", listOf("bar", "bebidas", "tragos", "cerveza")),

    // Transporte
    CatalogIconItem("directions_car", Icons.Default.DirectionsCar, "Auto", "Transporte", listOf("auto", "carro", "taxi", "uber", "gasolina")),
    CatalogIconItem("directions_transit", Icons.Default.DirectionsTransit, "Transporte", "Transporte", listOf("pasaje", "bus", "metropolitano", "metro")),
    CatalogIconItem("local_gas_station", Icons.Default.LocalGasStation, "Gasolina", "Transporte", listOf("gasolina", "grifo", "combustible", "glp")),
    CatalogIconItem("flight", Icons.Default.Flight, "Vuelos", "Transporte", listOf("vuelo", "avion", "viaje", "aerolinea")),
    CatalogIconItem("pedal_bike", Icons.Default.PedalBike, "Bicicleta", "Transporte", listOf("bici", "bicicleta", "ciclovia")),

    // Bienestar
    CatalogIconItem("medical_services", Icons.Default.MedicalServices, "Salud", "Bienestar", listOf("salud", "medico", "doctor", "clinica", "farmacia")),
    CatalogIconItem("fitness_center", Icons.Default.FitnessCenter, "Gimnasio", "Bienestar", listOf("gym", "gimnasio", "crossfit", "deporte", "fitness")),
    CatalogIconItem("spa", Icons.Default.Spa, "Bienestar", "Bienestar", listOf("spa", "masaje", "relajacion")),
    CatalogIconItem("pets", Icons.Default.Pets, "Mascotas", "Bienestar", listOf("mascota", "perro", "gato", "veterinaria")),
    CatalogIconItem("school", Icons.Default.School, "Educación", "Bienestar", listOf("educacion", "universidad", "colegio", "curso", "libro")),
    CatalogIconItem("card_giftcard", Icons.Default.CardGiftcard, "Regalos", "Bienestar", listOf("regalo", "cumpleanos", "obsequio")),
    CatalogIconItem("content_cut", Icons.Default.ContentCut, "Cuidado", "Bienestar", listOf("peluqueria", "barberia", "corte", "salon")),
    CatalogIconItem("favorite", Icons.Default.Favorite, "Amor / Salud", "Bienestar", listOf("amor", "pareja", "donacion", "favorito")),
)

internal fun resolveCatalogIcon(iconId: String): ImageVector {
    val found = CATALOG_ICONS.find { it.id.equals(iconId, ignoreCase = true) }
    if (found != null) return found.icon
    return resolveCategoryIcon(iconId)
}

// ============================================================================
// 3. COLOR CONVERSION UTILITIES (HSV <-> RGB <-> HEX)
// ============================================================================

internal object PastelColorUtils {
    fun colorToHsv(color: Color): FloatArray {
        val hsv = FloatArray(3)
        val argb = color.toArgb()
        android.graphics.Color.colorToHSV(argb, hsv)
        return hsv
    }

    fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
        val hsv = floatArrayOf(
            hue.coerceIn(0f, 360f),
            saturation.coerceIn(0f, 1f),
            value.coerceIn(0f, 1f),
        )
        val argb = android.graphics.Color.HSVToColor(hsv)
        return Color(argb)
    }

    fun colorToHex(color: Color): String {
        val r = (color.red * 255).toInt().coerceIn(0, 255)
        val g = (color.green * 255).toInt().coerceIn(0, 255)
        val b = (color.blue * 255).toInt().coerceIn(0, 255)
        return String.format("#%02X%02X%02X", r, g, b)
    }

    fun hexToColor(hex: String, fallback: Color = Color(0xFF0D6E64)): Color {
        val cleaned = hex.trim().removePrefix("#")
        if (cleaned.length != 6 && cleaned.length != 8) return fallback
        return try {
            Color(android.graphics.Color.parseColor("#$cleaned"))
        } catch (_: Exception) {
            fallback
        }
    }
}

// ============================================================================
// 4. MAIN FLOW ENUM & STATE
// ============================================================================

private enum class PastelFlowScreen {
    BROWSE_CATEGORIES,
    CREATE_SUBCATEGORY,
}

// ============================================================================
// 5. PUBLIC BOTTOM SHEET COMPOSABLE (PRIMARY & ALIASES)
// ============================================================================

/**
 * Public Bottom Sheet for R4/R5 Pastel Category & Subcategory selection and creation flow.
 *
 * Implements all seven reference states:
 * 1. Closed pastel root list
 * 2. Expanded children
 * 3. Live search results
 * 4. Empty result / create subcategory action
 * 5. New subcategory form
 * 6. Searchable/tabbed four-column icon catalog pop-up
 * 7. Custom HSV/RGB pastel color modal with 2D canvas, sliders, HEX/RGB and pastel chips
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastelCategoryPickerBottomSheet(
    categories: List<CategoryOption>,
    selectedCategoryId: String?,
    onSelect: (CategoryOption) -> Unit,
    onCreateCategory: suspend (
        parentId: String?,
        name: String,
        iconId: String,
        colorHex: String,
        rememberFrequentMerchant: Boolean,
    ) -> Result<CategoryOption>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialParentCategoryId: String? = null,
    isCreating: Boolean = false,
    errorMessage: String? = null,
    directSelectionMode: Boolean = false,
) {
    val coroutineScope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotionEnabled()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var currentScreen by remember { mutableStateOf(PastelFlowScreen.BROWSE_CATEGORIES) }
    var currentSelectedId by remember(selectedCategoryId) { mutableStateOf(selectedCategoryId) }

    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var expandedRootIds by remember {
        mutableStateOf(
            if (initialParentCategoryId != null) setOf(initialParentCategoryId) else emptySet()
        )
    }

    // New subcategory creation state
    var newSubcategoryName by remember { mutableStateOf("") }
    var selectedParentId by remember(categories, initialParentCategoryId) {
        val defaultParent = initialParentCategoryId
            ?: categories.firstOrNull { it.parentCategoryId == null }?.id
            ?: ""
        mutableStateOf(defaultParent)
    }
    var selectedIconId by remember { mutableStateOf("tv") }
    var selectedColorHex by remember { mutableStateOf("#EDE9FE") }
    var rememberFrequentMerchant by remember { mutableStateOf(true) }

    // Async creation status
    var localIsCreating by remember { mutableStateOf(false) }
    var localCreationError by remember { mutableStateOf<String?>(null) }
    val effectiveIsCreating = isCreating || localIsCreating
    val effectiveErrorMessage = errorMessage ?: localCreationError

    val kipuColors = rememberKipuColors()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = kipuColors.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(kipuColors.dragHandle),
            )
        },
        modifier = modifier.testTag("pastel_category_picker_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f),
        ) {
            when (currentScreen) {
                PastelFlowScreen.BROWSE_CATEGORIES -> {
                    BrowseCategoriesView(
                        categories = categories,
                        selectedCategoryId = currentSelectedId,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        expandedRootIds = expandedRootIds,
                        onToggleRootExpand = { rootId ->
                            expandedRootIds = if (rootId in expandedRootIds) {
                                expandedRootIds - rootId
                            } else {
                                expandedRootIds + rootId
                            }
                        },
                        onSelectCategory = { option ->
                            currentSelectedId = option.id
                        },
                        onConfirmSelection = { chosenOption ->
                            val option = chosenOption ?: categories.find { it.id == currentSelectedId }
                            if (option != null) {
                                onSelect(option)
                                onDismiss()
                            }
                        },
                        onNavigateToCreate = { prefillQuery ->
                            newSubcategoryName = prefillQuery
                            selectedParentId = initialParentCategoryId ?: ""
                            currentScreen = PastelFlowScreen.CREATE_SUBCATEGORY
                        },
                        onDismiss = onDismiss,
                        reducedMotion = reducedMotion,
                        directSelectionMode = directSelectionMode,
                    )
                }

                PastelFlowScreen.CREATE_SUBCATEGORY -> {
                    CategoryFormContent(
                        isEditing = false,
                        categoryType = categories.find { it.id == selectedParentId }?.categoryType ?: CategoryType.EXPENSE,
                        name = newSubcategoryName,
                        icon = selectedIconId,
                        color = selectedColorHex,
                        parentId = selectedParentId.takeIf { it.isNotBlank() }?.let { CategoryId(it) },
                        availableRoots = categories.filter { it.parentCategoryId == null },
                        onNameChange = { newSubcategoryName = it },
                        onIconChange = { selectedIconId = it },
                        onColorChange = { selectedColorHex = it },
                        onParentIdChange = { selectedParentId = it?.value ?: "" },
                        rememberFrequentMerchant = rememberFrequentMerchant,
                        onRememberFrequentMerchantChange = { rememberFrequentMerchant = it },
                        onBack = { currentScreen = PastelFlowScreen.BROWSE_CATEGORIES },
                        onDismiss = onDismiss,
                        onConfirm = {
                            if (newSubcategoryName.isNotBlank()) {
                                coroutineScope.launch {
                                    localIsCreating = true
                                    localCreationError = null
                                    val result = onCreateCategory(
                                        selectedParentId.takeIf { it.isNotBlank() },
                                        newSubcategoryName.trim(),
                                        selectedIconId,
                                        selectedColorHex,
                                        rememberFrequentMerchant,
                                    )
                                    localIsCreating = false
                                    result.fold(
                                        onSuccess = { createdOption ->
                                            onSelect(createdOption)
                                            onDismiss()
                                        },
                                        onFailure = { error ->
                                            localCreationError = error.localizedMessage
                                                ?: "Error al crear la categoría"
                                        },
                                    )
                                }
                            }
                        },
                        isCreating = effectiveIsCreating,
                        errorMessage = effectiveErrorMessage,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/**
 * Flow alias for [PastelCategoryPickerBottomSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastelCategoryPickerFlow(
    categories: List<CategoryOption>,
    selectedCategoryId: String?,
    onSelect: (CategoryOption) -> Unit,
    onCreateSubcategory: suspend (
        parentId: String,
        name: String,
        iconId: String,
        colorHex: String,
        rememberFrequentMerchant: Boolean,
    ) -> Result<CategoryOption>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialParentCategoryId: String? = null,
    isCreating: Boolean = false,
    errorMessage: String? = null,
    directSelectionMode: Boolean = false,
) = PastelCategoryPickerBottomSheet(
    categories = categories,
    selectedCategoryId = selectedCategoryId,
    onSelect = onSelect,
    onCreateCategory = { parentId, name, iconId, colorHex, rememberFrequentMerchant ->
        onCreateSubcategory(parentId ?: "", name, iconId, colorHex, rememberFrequentMerchant)
    },
    onDismiss = onDismiss,
    modifier = modifier,
    initialParentCategoryId = initialParentCategoryId,
    isCreating = isCreating,
    errorMessage = errorMessage,
    directSelectionMode = directSelectionMode,
)


// ============================================================================
// 6. VIEW: BROWSE CATEGORIES (States 1, 2, 3, 4)
// ============================================================================

@Composable
private fun BrowseCategoriesView(
    categories: List<CategoryOption>,
    selectedCategoryId: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    expandedRootIds: Set<String>,
    onToggleRootExpand: (String) -> Unit,
    onSelectCategory: (CategoryOption) -> Unit,
    onConfirmSelection: (CategoryOption?) -> Unit,
    onNavigateToCreate: (prefillQuery: String) -> Unit,
    onDismiss: () -> Unit,
    reducedMotion: Boolean,
    directSelectionMode: Boolean = false,
) {
    val roots = remember(categories) { categories.filter { it.parentCategoryId == null } }
    val isSearching = searchQuery.isNotBlank()

    // Filtered items
    val searchResults = remember(categories, searchQuery) {
        if (!isSearching) emptyList()
        else {
            val query = searchQuery.trim().lowercase()
            categories.filter {
                it.name.lowercase().contains(query) || (it.parentName?.lowercase()?.contains(query) == true)
            }
        }
    }

    val selectedOption = remember(categories, selectedCategoryId) {
        categories.find { it.id == selectedCategoryId }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Elegir categoría",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = PastelDesignTokens.TextMain,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(PastelDesignTokens.ChipBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (isSearching) "${searchResults.size} resultados" else "${categories.size} categorías",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PastelDesignTokens.TextMuted,
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("category_picker_close_button"),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar",
                    tint = PastelDesignTokens.TextMuted,
                )
            }
        }

        Text(
            text = "Selecciona una categoría o subcategoría para clasificar tu gasto",
            style = MaterialTheme.typography.bodyMedium,
            color = PastelDesignTokens.TextMuted,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("category_search_input"),
            placeholder = {
                Text(
                    "Buscar categoría o subcategoría...",
                    color = PastelDesignTokens.TextPlaceholder,
                    fontSize = 14.sp,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Buscar",
                    tint = PastelDesignTokens.TextPlaceholder,
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("category_search_clear"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Limpiar búsqueda",
                            tint = PastelDesignTokens.TextMuted,
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = PastelDesignTokens.SurfaceWhite,
                unfocusedContainerColor = PastelDesignTokens.SurfaceCard,
                focusedBorderColor = PastelDesignTokens.TealPrimary,
                unfocusedBorderColor = PastelDesignTokens.BorderSubtle,
            ),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Content Area: Search Results or Root Tree
        Box(modifier = Modifier.weight(1f)) {
            if (isSearching) {
                if (searchResults.isEmpty()) {
                    // State 4: Empty search result
                    EmptySearchState(
                        searchQuery = searchQuery,
                        onCreateSubcategory = { onNavigateToCreate(searchQuery.trim()) },
                    )
                } else {
                    // State 3: Live search results grouped
                    SearchResultsList(
                        searchResults = searchResults,
                        allCategories = categories,
                        searchQuery = searchQuery,
                        selectedCategoryId = selectedCategoryId,
                        onSelectCategory = { option ->
                            onSelectCategory(option)
                            if (directSelectionMode) onConfirmSelection(option)
                        },
                    )
                }
            } else {
                // State 1 & 2: Root Categories with Expandable Children
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Quick "+ Nueva categoría" header row
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onNavigateToCreate("") }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(PastelDesignTokens.TealLightBg, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = PastelDesignTokens.TealPrimary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Crear nueva categoría",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = PastelDesignTokens.TealPrimary,
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = PastelDesignTokens.TealPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        HorizontalDivider(
                            color = PastelDesignTokens.BorderSubtle,
                            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                        )
                    }

                    items(roots, key = { it.id }) { root ->
                        val children = categories.filter { it.parentCategoryId == root.id }
                        val isExpanded = root.id in expandedRootIds
                        val isRootSelected = root.id == selectedCategoryId
                        val pastel = PastelDesignTokens.resolvePastelToken(root.name, root.icon)

                        val rotation by animateFloatAsState(
                            targetValue = if (isExpanded) 180f else 0f,
                            animationSpec = tween(
                                durationMillis = if (reducedMotion) 0 else KipuMotionTokens.FastMillis,
                                easing = FastOutSlowInEasing,
                            ),
                            label = "chevron_rotation_${root.id}",
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    width = if (isRootSelected) 1.5.dp else 1.dp,
                                    color = if (isRootSelected) PastelDesignTokens.TealPrimary else PastelDesignTokens.BorderSubtle,
                                    shape = RoundedCornerShape(16.dp),
                                )
                                .background(if (isRootSelected) PastelDesignTokens.TealLightBg else PastelDesignTokens.SurfaceWhite),
                        ) {
                            // Root Item Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 64.dp)
                                    .clickable {
                                        onSelectCategory(root)
                                        if (directSelectionMode) {
                                            onConfirmSelection(root)
                                        } else if (children.isNotEmpty()) {
                                            onToggleRootExpand(root.id)
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                                    .semantics { selected = isRootSelected }
                                    .testTag("category_root_${root.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PastelIconBadge(
                                    icon = resolveCatalogIcon(root.icon),
                                    containerColor = pastel.containerColor,
                                    iconTint = pastel.contentColor,
                                )

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = root.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = PastelDesignTokens.TextMain,
                                        )
                                        if (isRootSelected) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(PastelDesignTokens.Mint.containerColor)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                            ) {
                                                Text(
                                                    text = "Actual",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = PastelDesignTokens.Mint.contentColor,
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = if (children.isNotEmpty()) {
                                            children.joinToString(", ") { it.name }.take(42) + "..."
                                        } else {
                                            "Categoría principal"
                                        },
                                        fontSize = 12.sp,
                                        color = PastelDesignTokens.TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                if (children.isNotEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(PastelDesignTokens.ChipBg)
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                        ) {
                                            Text(
                                                text = "${children.size} Subc",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = PastelDesignTokens.TextMuted,
                                            )
                                        }

                                        IconButton(
                                            onClick = { onToggleRootExpand(root.id) },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .semantics {
                                                    contentDescription = if (isExpanded) "Contraer ${root.name}" else "Expandir ${root.name}"
                                                    stateDescription = if (isExpanded) "Expandido" else "Contraído"
                                                }
                                                .testTag("category_expand_${root.id}"),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ExpandMore,
                                                contentDescription = null,
                                                tint = PastelDesignTokens.TextMuted,
                                                modifier = Modifier.graphicsLayer { rotationZ = rotation },
                                            )
                                        }
                                    }
                                } else if (isRootSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Seleccionado",
                                        tint = PastelDesignTokens.TealPrimary,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }

                            // State 2: Expanded Children Container
                            AnimatedVisibility(
                                visible = isExpanded && children.isNotEmpty(),
                                enter = if (reducedMotion) EnterTransition.None else
                                    expandVertically(tween(KipuMotionTokens.FastMillis, easing = FastOutSlowInEasing)) +
                                        fadeIn(tween(KipuMotionTokens.FastMillis)),
                                exit = if (reducedMotion) ExitTransition.None else
                                    shrinkVertically(tween(KipuMotionTokens.FastMillis, easing = FastOutSlowInEasing)) +
                                        fadeOut(tween(KipuMotionTokens.FastMillis)),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(PastelDesignTokens.SurfaceCard)
                                        .border(
                                            width = 1.dp,
                                            color = PastelDesignTokens.BorderSubtle,
                                            shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                                        )
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    children.forEach { child ->
                                        val isChildSelected = child.id == selectedCategoryId
                                        val childPastel = PastelDesignTokens.resolvePastelToken(child.name, child.icon)

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .border(
                                                    width = if (isChildSelected) 1.5.dp else 1.dp,
                                                    color = if (isChildSelected) PastelDesignTokens.TealPrimary else PastelDesignTokens.BorderSubtle,
                                                    shape = RoundedCornerShape(12.dp),
                                                )
                                                .background(if (isChildSelected) PastelDesignTokens.TealLightBg else PastelDesignTokens.SurfaceWhite)
                                                .clickable {
                                                    onSelectCategory(child)
                                                    if (directSelectionMode) onConfirmSelection(child)
                                                }
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                                .semantics { selected = isChildSelected }
                                                .testTag("category_subcategory_${child.id}"),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            PastelIconBadge(
                                                icon = resolveCatalogIcon(child.icon),
                                                containerColor = childPastel.containerColor,
                                                iconTint = childPastel.contentColor,
                                                size = 36.dp,
                                                shapeRadius = 10.dp,
                                            )

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Text(
                                                text = child.name,
                                                fontWeight = if (isChildSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 14.sp,
                                                color = PastelDesignTokens.TextMain,
                                                modifier = Modifier.weight(1f),
                                            )

                                            if (isChildSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Seleccionado",
                                                    tint = PastelDesignTokens.TealPrimary,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }

        // Sticky Bottom CTA Button
        if (!directSelectionMode) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PastelDesignTokens.SurfaceWhite,
                tonalElevation = 8.dp,
            ) {
                val hasSelection = selectedOption != null
                val buttonLabel = if (selectedOption != null) {
                    "Confirmar \"${selectedOption.name}\" ✓"
                } else {
                    "Confirmar categoría"
                }

                Button(
                    onClick = { onConfirmSelection(null) },
                    enabled = hasSelection,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .padding(vertical = 8.dp)
                        .testTag("confirm_category_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = rememberKipuColors().primary,
                        contentColor = rememberKipuColors().onPrimary,
                        disabledContainerColor = rememberKipuColors().border,
                        disabledContentColor = PastelDesignTokens.TextPlaceholder,
                    ),
                ) {
                    Text(
                        text = buttonLabel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

// ============================================================================
// 7. COMPONENT: LIVE SEARCH RESULTS (State 3)
// ============================================================================

@Composable
private fun SearchResultsList(
    searchResults: List<CategoryOption>,
    allCategories: List<CategoryOption>,
    searchQuery: String,
    selectedCategoryId: String?,
    onSelectCategory: (CategoryOption) -> Unit,
) {
    // Group subcategories by parent
    val grouped = remember(searchResults, allCategories) {
        val groups = mutableMapOf<String, MutableList<CategoryOption>>()
        searchResults.forEach { item ->
            val parentName = if (item.parentCategoryId != null) {
                allCategories.find { it.id == item.parentCategoryId }?.name ?: (item.parentName ?: "Subcategorías")
            } else {
                item.name
            }
            groups.getOrPut(parentName) { mutableListOf() }.add(item)
        }
        groups
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text = "RESULTADOS PARA \"${searchQuery.uppercase()}\"",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = PastelDesignTokens.TextPlaceholder,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        grouped.forEach { (parentName, items) ->
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, PastelDesignTokens.BorderSubtle, RoundedCornerShape(16.dp))
                        .background(PastelDesignTokens.SurfaceWhite)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = parentName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = PastelDesignTokens.TextMain,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(PastelDesignTokens.ChipBg)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "${items.size} coincidencia${if (items.size > 1) "s" else ""}",
                                fontSize = 11.sp,
                                color = PastelDesignTokens.TextMuted,
                            )
                        }
                    }

                    HorizontalDivider(color = PastelDesignTokens.BorderSubtle.copy(alpha = 0.5f))

                    items.forEach { option ->
                        val isSelected = option.id == selectedCategoryId
                        val pastel = PastelDesignTokens.resolvePastelToken(option.name, option.icon)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) PastelDesignTokens.TealPrimary else PastelDesignTokens.BorderSubtle,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .background(if (isSelected) PastelDesignTokens.TealLightBg else PastelDesignTokens.SurfaceCard)
                                .clickable { onSelectCategory(option) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .semantics { selected = isSelected }
                                .testTag("category_search_item_${option.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PastelIconBadge(
                                icon = resolveCatalogIcon(option.icon),
                                containerColor = pastel.containerColor,
                                iconTint = pastel.contentColor,
                                size = 36.dp,
                                shapeRadius = 10.dp,
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = option.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp,
                                color = PastelDesignTokens.TextMain,
                                modifier = Modifier.weight(1f),
                            )

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Seleccionado",
                                    tint = PastelDesignTokens.TealPrimary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ============================================================================
// 8. COMPONENT: EMPTY SEARCH RESULT (State 4)
// ============================================================================

@Composable
private fun EmptySearchState(
    searchQuery: String,
    onCreateSubcategory: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .background(PastelDesignTokens.ChipBg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.HelpOutline,
                contentDescription = null,
                tint = PastelDesignTokens.TextPlaceholder,
                modifier = Modifier.size(36.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No encontramos esa categoría",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = PastelDesignTokens.TextMain,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "No existe ninguna categoría o subcategoría que coincida con \"$searchQuery\".",
            style = MaterialTheme.typography.bodyMedium,
            color = PastelDesignTokens.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedButton(
            onClick = onCreateSubcategory,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("category_empty_create_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = PastelDesignTokens.TealPrimary,
                containerColor = PastelDesignTokens.TealLightBg,
            ),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = Brush.horizontalGradient(listOf(PastelDesignTokens.TealPrimary, PastelDesignTokens.TealPrimary))
            ),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "+ Crear categoría personalizada",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Buscar en otra categoría o revisar la ortografía",
            fontSize = 12.sp,
            color = PastelDesignTokens.TextPlaceholder,
            textAlign = TextAlign.Center,
        )
    }
}

// ============================================================================
// 9. VIEW: CREATE NEW SUBCATEGORY (State 5)
// ============================================================================

@Composable
internal fun CreateSubcategoryView(
    categories: List<CategoryOption>,
    selectedParentId: String,
    onOpenParentChooser: () -> Unit = {},
    name: String,
    onNameChange: (String) -> Unit,
    selectedIconId: String,
    onSelectQuickIcon: (String) -> Unit,
    onOpenIconCatalog: () -> Unit = {},
    selectedColorHex: String,
    onSelectQuickColor: (String) -> Unit,
    onOpenColorPicker: () -> Unit = {},
    rememberFrequentMerchant: Boolean,
    onRememberFrequentMerchantChange: (Boolean) -> Unit,
    isCreating: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    CategoryFormContent(
        showBack = true,
        selectOnSave = true,
        isEditing = false,
        categoryType = categories.find { it.id == selectedParentId }?.categoryType ?: CategoryType.EXPENSE,
        name = name,
        icon = selectedIconId,
        color = selectedColorHex,
        parentId = selectedParentId.takeIf { it.isNotBlank() }?.let { CategoryId(it) },
        availableRoots = categories.filter { it.parentCategoryId == null },
        onNameChange = onNameChange,
        onIconChange = onSelectQuickIcon,
        onColorChange = onSelectQuickColor,
        onParentIdChange = { /* updated via chooser */ },
        rememberFrequentMerchant = rememberFrequentMerchant,
        onRememberFrequentMerchantChange = onRememberFrequentMerchantChange,
        onBack = onBack,
        onDismiss = onDismiss,
        onConfirm = onSave,
        isCreating = isCreating,
        errorMessage = errorMessage,
        modifier = Modifier.fillMaxSize(),
    )
}

// ============================================================================
// 10. DIALOG: PARENT CATEGORY CHOOSER
// ============================================================================

@Composable
internal fun ParentCategoryChooserDialog(
    categories: List<CategoryOption>,
    currentParentId: String,
    onSelectParent: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val roots = remember(categories) { categories.filter { it.parentCategoryId == null } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 520.dp)
                .clip(RoundedCornerShape(24.dp)),
            color = PastelDesignTokens.SurfaceWhite,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Seleccionar categoría padre",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PastelDesignTokens.TextMain,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = PastelDesignTokens.TextMuted)
                    }
                }

                Text(
                    text = "Elige la categoría a la que pertenecerá esta subcategoría",
                    style = MaterialTheme.typography.bodySmall,
                    color = PastelDesignTokens.TextMuted,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                HorizontalDivider(color = PastelDesignTokens.BorderSubtle)

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        val isNoneSelected = currentParentId.isBlank()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isNoneSelected) 1.5.dp else 1.dp,
                                    color = if (isNoneSelected) PastelDesignTokens.TealPrimary else PastelDesignTokens.BorderSubtle,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .background(if (isNoneSelected) PastelDesignTokens.TealLightBg else PastelDesignTokens.SurfaceCard)
                                .clickable { onSelectParent("") }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .semantics { selected = isNoneSelected }
                                .testTag("parent_chooser_item_none"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PastelIconBadge(
                                icon = Icons.Default.Folder,
                                containerColor = if (isNoneSelected) PastelDesignTokens.TealLightBg else PastelDesignTokens.BorderSubtle,
                                iconTint = PastelDesignTokens.TealPrimary,
                                size = 36.dp,
                                shapeRadius = 10.dp,
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ninguna (Categoría Principal)",
                                    fontWeight = if (isNoneSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp,
                                    color = PastelDesignTokens.TextMain,
                                )
                                Text(
                                    text = "Crear como categoría de nivel superior",
                                    fontSize = 12.sp,
                                    color = PastelDesignTokens.TextMuted,
                                )
                            }

                            if (isNoneSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Seleccionado",
                                    tint = PastelDesignTokens.TealPrimary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    items(roots) { root ->
                        val isSelected = root.id == currentParentId
                        val pastel = PastelDesignTokens.resolvePastelToken(root.name, root.icon)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) PastelDesignTokens.TealPrimary else PastelDesignTokens.BorderSubtle,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .background(if (isSelected) PastelDesignTokens.TealLightBg else PastelDesignTokens.SurfaceCard)
                                .clickable { onSelectParent(root.id) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .semantics { selected = isSelected }
                                .testTag("parent_chooser_item_${root.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PastelIconBadge(
                                icon = resolveCatalogIcon(root.icon),
                                containerColor = pastel.containerColor,
                                iconTint = pastel.contentColor,
                                size = 36.dp,
                                shapeRadius = 10.dp,
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = root.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp,
                                color = PastelDesignTokens.TextMain,
                                modifier = Modifier.weight(1f),
                            )

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Seleccionado",
                                    tint = PastelDesignTokens.TealPrimary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = rememberKipuColors().primary),
                ) {
                    Text("Cerrar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ============================================================================
// 11. DIALOG: ICON CATALOG (State 6)
// ============================================================================

@Composable
internal fun IconCatalogDialog(
    initialIconId: String,
    onConfirmIcon: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf("Todos") }
    var tempSelectedId by remember { mutableStateOf(initialIconId) }

    val filteredIcons = remember(searchQuery, selectedTab) {
        val q = searchQuery.trim().lowercase()
        CATALOG_ICONS.filter { iconItem ->
            val matchesTab = selectedTab == "Todos" || iconItem.category.equals(selectedTab, ignoreCase = true)
            val matchesSearch = q.isEmpty() ||
                iconItem.label.lowercase().contains(q) ||
                iconItem.keywords.any { it.contains(q) }
            matchesTab && matchesSearch
        }
    }

    val selectedCatalogItem = remember(tempSelectedId) {
        CATALOG_ICONS.find { it.id.equals(tempSelectedId, ignoreCase = true) }
            ?: CATALOG_ICONS.first()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp)),
            color = PastelDesignTokens.SurfaceWhite,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Catálogo de Iconos",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = PastelDesignTokens.TextMain,
                        )
                        Text(
                            text = "Elige el símbolo gráfico para tu subcategoría",
                            fontSize = 12.sp,
                            color = PastelDesignTokens.TextMuted,
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = PastelDesignTokens.TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("icon_catalog_search_input"),
                    placeholder = {
                        Text("🔍 pelicula, comida, auto...", color = PastelDesignTokens.TextPlaceholder, fontSize = 13.sp)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = PastelDesignTokens.TextMuted)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = PastelDesignTokens.SurfaceWhite,
                        unfocusedContainerColor = PastelDesignTokens.SurfaceCard,
                        focusedBorderColor = PastelDesignTokens.TealPrimary,
                        unfocusedBorderColor = PastelDesignTokens.BorderSubtle,
                    ),
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Category Tabs in Horizontal Scroll
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(CATALOG_TABS) { tab ->
                        val isTabSelected = tab == selectedTab
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isTabSelected) rememberKipuColors().primary else PastelDesignTokens.SurfaceCard)
                                .border(
                                    width = 1.dp,
                                    color = if (isTabSelected) PastelDesignTokens.TealPrimary else PastelDesignTokens.BorderSubtle,
                                    shape = RoundedCornerShape(20.dp),
                                )
                                .clickable { selectedTab = tab }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .semantics { selected = isTabSelected },
                        ) {
                            Text(
                                text = tab,
                                fontSize = 12.sp,
                                fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isTabSelected) rememberKipuColors().onPrimary else PastelDesignTokens.TextMain,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4-Column Grid of Icons
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filteredIcons, key = { it.id }) { iconItem ->
                        val isSelected = tempSelectedId.equals(iconItem.id, ignoreCase = true)

                        Column(
                            modifier = Modifier
                                .height(72.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) PastelDesignTokens.TealPrimary else PastelDesignTokens.BorderSubtle,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .background(if (isSelected) PastelDesignTokens.Mint.containerColor else PastelDesignTokens.SurfaceCard)
                                .clickable { tempSelectedId = iconItem.id }
                                .padding(4.dp)
                                .semantics { selected = isSelected }
                                .testTag("icon_item_${iconItem.id}"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = iconItem.icon,
                                contentDescription = iconItem.label,
                                tint = if (isSelected) PastelDesignTokens.TealPrimary else PastelDesignTokens.TextMain,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = iconItem.label,
                                fontSize = 10.sp,
                                color = if (isSelected) PastelDesignTokens.TealPrimary else PastelDesignTokens.TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Selected Icon Summary
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(PastelDesignTokens.SurfaceCard)
                        .border(1.dp, PastelDesignTokens.BorderSubtle, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PastelIconBadge(
                        icon = selectedCatalogItem.icon,
                        containerColor = PastelDesignTokens.Mint.containerColor,
                        iconTint = PastelDesignTokens.TealPrimary,
                        size = 36.dp,
                        shapeRadius = 10.dp,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Icono seleccionado: ${selectedCatalogItem.label}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = PastelDesignTokens.TextMain,
                        )
                        Text(
                            text = "Categoría: ${selectedCatalogItem.category}",
                            fontSize = 11.sp,
                            color = PastelDesignTokens.TextMuted,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Actions: Cancel & Confirm
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Cancelar", color = PastelDesignTokens.TextMuted, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = { onConfirmIcon(tempSelectedId) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("icon_catalog_confirm_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = rememberKipuColors().primary),
                    ) {
                        Text("Confirmar icono", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

// ============================================================================
// 12. DIALOG: CUSTOM HSV/RGB PASTEL COLOR MODAL (State 7)
// ============================================================================

@Composable
internal fun CustomPastelColorDialog(
    initialColorHex: String,
    parentName: String,
    onApplyColor: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val recommendedPastelChips = PastelDesignTokens.RecommendedPastelChips

    val initialColor = remember(initialColorHex) {
        PastelColorUtils.hexToColor(initialColorHex)
    }

    val initialHsv = remember(initialColor) {
        PastelColorUtils.colorToHsv(initialColor)
    }

    var currentHue by remember { mutableFloatStateOf(initialHsv[0]) }
    var currentSaturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var currentValue by remember { mutableFloatStateOf(initialHsv[2]) }

    val currentColor = remember(currentHue, currentSaturation, currentValue) {
        PastelColorUtils.hsvToColor(currentHue, currentSaturation, currentValue)
    }
    val currentHex = remember(currentColor) {
        PastelColorUtils.colorToHex(currentColor)
    }

    // RGB Strings
    var rText by remember(currentColor) { mutableStateOf(((currentColor.red * 255).toInt()).toString()) }
    var gText by remember(currentColor) { mutableStateOf(((currentColor.green * 255).toInt()).toString()) }
    var bText by remember(currentColor) { mutableStateOf(((currentColor.blue * 255).toInt()).toString()) }
    var hexText by remember(currentHex) { mutableStateOf(currentHex) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(24.dp)),
            color = PastelDesignTokens.SurfaceWhite,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(currentColor),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Color personalizado",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = PastelDesignTokens.TextMain,
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = PastelDesignTokens.TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 1. 2D HSV CANVAS (Saturation X, Value Y) with Selector Thumb
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, PastelDesignTokens.BorderSubtle, RoundedCornerShape(16.dp)),
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectDragGestures { change, _ ->
                                        val x = change.position.x.coerceIn(0f, size.width.toFloat())
                                        val y = change.position.y.coerceIn(0f, size.height.toFloat())
                                        currentSaturation = (x / size.width.toFloat()).coerceIn(0f, 1f)
                                        currentValue = (1f - y / size.height.toFloat()).coerceIn(0f, 1f)
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        val x = offset.x.coerceIn(0f, size.width.toFloat())
                                        val y = offset.y.coerceIn(0f, size.height.toFloat())
                                        currentSaturation = (x / size.width.toFloat()).coerceIn(0f, 1f)
                                        currentValue = (1f - y / size.height.toFloat()).coerceIn(0f, 1f)
                                    }
                                },
                        ) {
                            val pureHue = PastelColorUtils.hsvToColor(currentHue, 1f, 1f)
                            // Horizontal gradient: white to pure hue
                            drawRect(
                                brush = Brush.horizontalGradient(listOf(Color.White, pureHue))
                            )
                            // Vertical gradient: transparent to black
                            drawRect(
                                brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
                            )

                            // Circular selector thumb
                            val thumbX = currentSaturation * size.width
                            val thumbY = (1f - currentValue) * size.height

                            drawCircle(
                                color = Color.White,
                                radius = 12.dp.toPx(),
                                center = Offset(thumbX, thumbY),
                            )
                            drawCircle(
                                color = currentColor,
                                radius = 9.dp.toPx(),
                                center = Offset(thumbX, thumbY),
                            )
                            drawCircle(
                                color = Color.Black.copy(alpha = 0.35f),
                                radius = 12.dp.toPx(),
                                center = Offset(thumbX, thumbY),
                                style = Stroke(width = 2.dp.toPx()),
                            )
                        }
                    }

                    // 2. SLIDERS: Tono (Hue) & Suavidad/Saturación
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Tono", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PastelDesignTokens.TextMain)
                            Text("${currentHue.toInt()}°", fontSize = 12.sp, color = PastelDesignTokens.TextMuted)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color.Red, Color.Yellow, Color.Green, Color.Cyan,
                                                Color.Blue, Color.Magenta, Color.Red
                                            )
                                        )
                                    ),
                            )
                            Slider(
                                value = currentHue,
                                onValueChange = { currentHue = it },
                                valueRange = 0f..360f,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.Transparent,
                                    inactiveTrackColor = Color.Transparent,
                                ),
                            )
                        }
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Suavidad / Saturación", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PastelDesignTokens.TextMain)
                            Text("${(currentSaturation * 100).toInt()}%", fontSize = 12.sp, color = PastelDesignTokens.TextMuted)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color.White, PastelColorUtils.hsvToColor(currentHue, 1f, currentValue))
                                        )
                                    ),
                            )
                            Slider(
                                value = currentSaturation,
                                onValueChange = { currentSaturation = it },
                                valueRange = 0f..1f,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.Transparent,
                                    inactiveTrackColor = Color.Transparent,
                                ),
                            )
                        }
                    }

                    // 3. LIVE COMPARATOR: Actual vs Nuevo + Vista de etiqueta
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(PastelDesignTokens.SurfaceCard)
                            .border(1.dp, PastelDesignTokens.BorderSubtle, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Actual
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(initialColor)
                                        .border(1.dp, Color.Black.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                                )
                                Text("Actual", fontSize = 10.sp, color = PastelDesignTokens.TextMuted)
                            }

                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = PastelDesignTokens.TextPlaceholder,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            // Nuevo
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(currentColor)
                                        .border(1.dp, Color.Black.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                                )
                                Text("Nuevo", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PastelDesignTokens.TealPrimary)
                            }
                        }

                        // Vista de etiqueta
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Vista de etiqueta", fontSize = 10.sp, color = PastelDesignTokens.TextMuted)
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(currentColor.copy(alpha = 0.35f))
                                    .border(1.dp, currentColor, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = parentName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PastelDesignTokens.TealDark,
                                )
                            }
                        }
                    }

                    // 4. NUMERIC INPUTS: HEX & RGB
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // HEX Input
                        OutlinedTextField(
                            value = hexText,
                            onValueChange = { input ->
                                hexText = input
                                val parsed = PastelColorUtils.hexToColor(input, Color.Unspecified)
                                if (parsed != Color.Unspecified) {
                                    val hsv = PastelColorUtils.colorToHsv(parsed)
                                    currentHue = hsv[0]
                                    currentSaturation = hsv[1]
                                    currentValue = hsv[2]
                                }
                            },
                            modifier = Modifier
                                .weight(1.4f)
                                .heightIn(min = 48.dp)
                                .testTag("color_modal_hex_input"),
                            label = { Text("HEX", fontSize = 11.sp) },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(
                                    onClick = { clipboardManager.setText(AnnotatedString(currentHex)) },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copiar HEX",
                                        tint = PastelDesignTokens.TealPrimary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                        )

                        // R
                        OutlinedTextField(
                            value = rText,
                            onValueChange = { input ->
                                rText = input
                                val r = input.toIntOrNull()
                                if (r != null && r in 0..255) {
                                    val newCol = Color(r, (currentColor.green * 255).toInt(), (currentColor.blue * 255).toInt())
                                    val hsv = PastelColorUtils.colorToHsv(newCol)
                                    currentHue = hsv[0]
                                    currentSaturation = hsv[1]
                                    currentValue = hsv[2]
                                }
                            },
                            modifier = Modifier
                                .weight(0.8f)
                                .heightIn(min = 48.dp),
                            label = { Text("R", fontSize = 11.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(10.dp),
                        )

                        // G
                        OutlinedTextField(
                            value = gText,
                            onValueChange = { input ->
                                gText = input
                                val g = input.toIntOrNull()
                                if (g != null && g in 0..255) {
                                    val newCol = Color((currentColor.red * 255).toInt(), g, (currentColor.blue * 255).toInt())
                                    val hsv = PastelColorUtils.colorToHsv(newCol)
                                    currentHue = hsv[0]
                                    currentSaturation = hsv[1]
                                    currentValue = hsv[2]
                                }
                            },
                            modifier = Modifier
                                .weight(0.8f)
                                .heightIn(min = 48.dp),
                            label = { Text("G", fontSize = 11.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(10.dp),
                        )

                        // B
                        OutlinedTextField(
                            value = bText,
                            onValueChange = { input ->
                                bText = input
                                val b = input.toIntOrNull()
                                if (b != null && b in 0..255) {
                                    val newCol = Color((currentColor.red * 255).toInt(), (currentColor.green * 255).toInt(), b)
                                    val hsv = PastelColorUtils.colorToHsv(newCol)
                                    currentHue = hsv[0]
                                    currentSaturation = hsv[1]
                                    currentValue = hsv[2]
                                }
                            },
                            modifier = Modifier
                                .weight(0.8f)
                                .heightIn(min = 48.dp),
                            label = { Text("B", fontSize = 11.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(10.dp),
                        )
                    }

                    // 5. RECOMMENDED PASTEL CHIPS (Colección Kipu Soft Pastels)
                    Column {
                        Text(
                            text = "Paleta recomendada (Pasteles suaves) · Colección Kipu",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PastelDesignTokens.TextPlaceholder,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(recommendedPastelChips) { token ->
                                val isSelected = currentHex.equals(token.hexCode, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(token.containerColor)
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) PastelDesignTokens.TealPrimary else token.borderColor,
                                            shape = CircleShape,
                                        )
                                        .clickable {
                                            val hsv = PastelColorUtils.colorToHsv(token.containerColor)
                                            currentHue = hsv[0]
                                            currentSaturation = hsv[1]
                                            currentValue = hsv[2]
                                        }
                                        .semantics { selected = isSelected },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = token.contentColor,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Actions: Restablecer & Aplicar color
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            val hsv = PastelColorUtils.colorToHsv(initialColor)
                            currentHue = hsv[0]
                            currentSaturation = hsv[1]
                            currentValue = hsv[2]
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Restablecer", color = PastelDesignTokens.TextMuted, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = { onApplyColor(currentHex) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("color_modal_apply_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = rememberKipuColors().primary),
                    ) {
                        Text("✓ Aplicar color", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun PastelTokenPreviewContent() {
    val tokens = PastelDesignTokens.RecommendedPastelChips
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Categorías", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tokens.forEach { token ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = token.containerColor,
                    border = BorderStroke(1.dp, token.borderColor),
                ) {
                    Text(token.name, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), color = token.contentColor)
                }
            }
        }
    }
}

@Preview(name = "Categorías · Claro", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
private fun PastelCategoriesLightPreview() {
    KipuTheme(darkTheme = false) { PastelTokenPreviewContent() }
}

@Preview(name = "Categorías · Oscuro", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PastelCategoriesDarkPreview() {
    KipuTheme(darkTheme = true) { PastelTokenPreviewContent() }
}
