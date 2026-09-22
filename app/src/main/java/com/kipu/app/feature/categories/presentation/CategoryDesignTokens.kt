package com.kipu.app.feature.categories.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
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
    for (section in CATEGORY_ICON_SECTIONS) {
        val found = section.icons.find { it.id.equals(iconId, ignoreCase = true) }
        if (found != null) return found.icon
    }
    return when (iconId.lowercase()) {
        "food", "alimentacion", "alimentación" -> Icons.Default.Restaurant
        "transport", "transporte" -> Icons.Default.DirectionsCar
        "services", "servicios" -> Icons.Default.ElectricBolt
        "folder" -> Icons.Default.Folder
        else -> Icons.Default.Category
    }
}

fun parseHexColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color(0xFF0F766E)
    }
}
