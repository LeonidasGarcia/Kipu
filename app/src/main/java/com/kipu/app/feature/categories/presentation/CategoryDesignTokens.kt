package com.kipu.app.feature.categories.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class CategoryColor(val name: String, val hex: String)

val CATEGORY_PALETTE = listOf(
    CategoryColor("Verde Kipu", "#0F766E"),
    CategoryColor("Esmeralda", "#10B981"),
    CategoryColor("Coral", "#E85D5D"),
    CategoryColor("Ámbar", "#F59E0B"),
    CategoryColor("Azul", "#0284C7"),
    CategoryColor("Púrpura", "#8B5CF6"),
    CategoryColor("Rosa", "#EC4899"),
    CategoryColor("Pizarra", "#475569"),
)

data class CategoryIconItem(
    val id: String,
    val icon: ImageVector,
    val label: String,
)

data class CategoryIconSection(
    val title: String,
    val icons: List<CategoryIconItem>,
)

val CATEGORY_ICON_SECTIONS = listOf(
    CategoryIconSection(
        title = "Alimentación y Compras",
        icons = listOf(
            CategoryIconItem("restaurant", Icons.Default.Restaurant, "Restaurante"),
            CategoryIconItem("shopping_cart", Icons.Default.ShoppingCart, "Compras"),
            CategoryIconItem("local_cafe", Icons.Default.LocalCafe, "Cafetería"),
            CategoryIconItem("storefront", Icons.Default.Storefront, "Tienda"),
        ),
    ),
    CategoryIconSection(
        title = "Transporte y Viajes",
        icons = listOf(
            CategoryIconItem("directions_car", Icons.Default.DirectionsCar, "Auto"),
            CategoryIconItem("directions_transit", Icons.Default.DirectionsTransit, "Transporte"),
            CategoryIconItem("local_gas_station", Icons.Default.LocalGasStation, "Gasolina"),
            CategoryIconItem("flight", Icons.Default.Flight, "Viajes"),
        ),
    ),
    CategoryIconSection(
        title = "Hogar y Servicios",
        icons = listOf(
            CategoryIconItem("home", Icons.Default.Home, "Hogar"),
            CategoryIconItem("electric_bolt", Icons.Default.ElectricBolt, "Electricidad"),
            CategoryIconItem("water_drop", Icons.Default.WaterDrop, "Agua"),
            CategoryIconItem("wifi", Icons.Default.Wifi, "Internet"),
        ),
    ),
    CategoryIconSection(
        title = "Entretenimiento y Bienestar",
        icons = listOf(
            CategoryIconItem("movie", Icons.Default.Movie, "Cine"),
            CategoryIconItem("fitness_center", Icons.Default.FitnessCenter, "Deportes"),
            CategoryIconItem("medical_services", Icons.Default.MedicalServices, "Salud"),
            CategoryIconItem("school", Icons.Default.School, "Educación"),
        ),
    ),
)

fun resolveCategoryIcon(iconId: String): ImageVector {
    return when (iconId.lowercase()) {
        "tv", "streaming", "video" -> Icons.Default.Tv
        "music_note", "musica", "música", "audio" -> Icons.Default.MusicNote
        "directions_car", "car", "auto", "transporte", "transport" -> Icons.Default.DirectionsCar
        "content_cut", "barberia", "peluqueria", "corte" -> Icons.Default.ContentCut
        "receipt", "recibo", "factura", "servicios", "services" -> Icons.Default.Receipt
        "shopping_cart", "cart", "compras", "supermercado" -> Icons.Default.ShoppingCart
        "restaurant", "food", "comida", "alimentacion", "alimentación" -> Icons.Default.Restaurant
        "fitness_center", "gym", "gimnasio", "deporte" -> Icons.Default.FitnessCenter
        "local_cafe", "cafe", "cafeteria" -> Icons.Default.LocalCafe
        "storefront", "tienda" -> Icons.Default.Storefront
        "directions_transit", "metro", "bus" -> Icons.Default.DirectionsTransit
        "local_gas_station", "gasolina", "combustible" -> Icons.Default.LocalGasStation
        "flight", "vuelo", "viaje", "avion" -> Icons.Default.Flight
        "home", "casa", "hogar" -> Icons.Default.Home
        "electric_bolt", "luz", "electricidad" -> Icons.Default.ElectricBolt
        "water_drop", "agua" -> Icons.Default.WaterDrop
        "wifi", "internet" -> Icons.Default.Wifi
        "movie", "cine", "pelicula" -> Icons.Default.Movie
        "medical_services", "salud", "medico", "farmacia" -> Icons.Default.MedicalServices
        "school", "educacion", "colegio", "universidad" -> Icons.Default.School
        "spa", "masaje", "bienestar" -> Icons.Default.Spa
        "pets", "mascota", "veterinaria" -> Icons.Default.Pets
        "sports_esports", "juegos", "gamer" -> Icons.Default.SportsEsports
        "devices", "laptop", "tecnologia", "software" -> Icons.Default.Devices
        "cleaning_services", "limpieza" -> Icons.Default.CleaningServices
        "savings", "ahorro" -> Icons.Default.Savings
        "payments", "pago", "sueldo" -> Icons.Default.Payments
        "work", "trabajo" -> Icons.Default.Work
        "folder" -> Icons.Default.Folder
        "credit_card" -> Icons.Default.CreditCard
        "account_balance" -> Icons.Default.AccountBalance
        "account_balance_wallet" -> Icons.Default.AccountBalanceWallet
        "card_giftcard", "regalo" -> Icons.Default.CardGiftcard
        "favorite" -> Icons.Default.Favorite
        "pedal_bike", "bici" -> Icons.Default.PedalBike
        "star" -> Icons.Default.Star
        "label", "tag", "etiqueta", "category", "categoria", "categoría" -> Icons.Default.Label
        else -> {
            for (section in CATEGORY_ICON_SECTIONS) {
                val found = section.icons.find { it.id.equals(iconId, ignoreCase = true) }
                if (found != null) return found.icon
            }
            Icons.Default.Label
        }
    }
}

fun parseHexColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color(0xFF0F766E)
    }
}
