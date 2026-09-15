package com.kipu.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = KipuPrimary,
    secondary = KipuPrimaryDark,
    background = KipuInk,
    surface = KipuInk,
    onPrimary = KipuSurface,
    onBackground = KipuSurface,
    onSurface = KipuSurface,
)

private val LightColorScheme = lightColorScheme(
    primary = KipuPrimary,
    secondary = KipuPrimaryDark,
    background = KipuBackground,
    surface = KipuSurface,
    onPrimary = KipuSurface,
    onBackground = KipuInk,
    onSurface = KipuInk,
)

@Composable
fun KipuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
