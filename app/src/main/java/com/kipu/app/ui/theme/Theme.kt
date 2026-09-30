package com.kipu.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = KipuInversePrimary,
    onPrimary = KipuDarkBackground,
    primaryContainer = KipuPrimaryContainer,
    onPrimaryContainer = KipuOnPrimaryContainer,
    secondary = KipuSecondaryContainer,
    onSecondary = KipuDarkBackground,
    secondaryContainer = KipuSecondary,
    onSecondaryContainer = KipuOnSecondary,
    tertiary = KipuOnTertiaryContainer,
    onTertiary = KipuDarkBackground,
    tertiaryContainer = KipuTertiary,
    onTertiaryContainer = KipuOnTertiaryContainer,
    background = KipuDarkBackground,
    onBackground = KipuInverseOnSurface,
    surface = KipuDarkSurface,
    onSurface = KipuInverseOnSurface,
    surfaceVariant = KipuInverseSurface,
    onSurfaceVariant = KipuDarkOnSurfaceVariant,
    surfaceContainerLowest = KipuDarkBackground,
    surfaceContainerLow = KipuDarkSurface,
    surfaceContainer = KipuDarkSurfaceVariant,
    surfaceContainerHigh = Color(0xFF202D44),
    surfaceContainerHighest = Color(0xFF26344B),
    outline = KipuDarkOutline,
    outlineVariant = KipuDarkOutlineVariant,
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF501D21),
    onErrorContainer = Color(0xFFFCA5A5),
    inverseSurface = KipuBackground,
    inverseOnSurface = KipuOnSurface,
    inversePrimary = KipuPrimary,
    surfaceTint = KipuSelectionRing,
)

private val LightColorScheme = lightColorScheme(
    primary = KipuPrimary,
    onPrimary = KipuOnPrimary,
    primaryContainer = KipuPrimaryContainer,
    onPrimaryContainer = KipuOnPrimaryContainer,
    secondary = KipuSecondary,
    onSecondary = KipuOnSecondary,
    secondaryContainer = KipuSecondaryContainer,
    onSecondaryContainer = KipuOnSecondaryContainer,
    tertiary = KipuTertiary,
    onTertiary = KipuOnTertiary,
    tertiaryContainer = KipuTertiaryContainer,
    onTertiaryContainer = KipuOnTertiaryContainer,
    background = KipuBackground,
    onBackground = KipuOnSurface,
    surface = KipuSurface,
    onSurface = KipuOnSurface,
    surfaceVariant = KipuSurfaceVariant,
    onSurfaceVariant = KipuOnSurfaceVariant,
    surfaceContainerLowest = KipuSurfaceContainerLowest,
    surfaceContainerLow = KipuSurfaceContainerLow,
    surfaceContainer = KipuSurfaceContainer,
    surfaceContainerHigh = KipuSurfaceContainerHigh,
    surfaceContainerHighest = KipuSurfaceContainerHighest,
    outline = KipuOutline,
    outlineVariant = KipuOutlineVariant,
    error = KipuErrorLight,
    onError = KipuOnError,
    errorContainer = KipuErrorContainer,
    onErrorContainer = KipuOnErrorContainer,
    inverseSurface = KipuInverseSurface,
    inverseOnSurface = KipuInverseOnSurface,
    inversePrimary = KipuInversePrimary,
    surfaceTint = KipuSelectionRing,
)

private val KipuShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

@Composable
fun KipuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = KipuTypography,
        shapes = KipuShapes,
        content = content
    )
}
