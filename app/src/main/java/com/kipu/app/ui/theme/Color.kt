package com.kipu.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable

val KipuPrimary = Color(0xFF0B5F51)
val KipuOnPrimary = Color(0xFFFFFFFF)
val KipuPrimaryContainer = Color(0xFFBCEBDD)
val KipuOnPrimaryContainer = Color(0xFF17211E)
val KipuInversePrimary = Color(0xFF80D5CB)
val KipuSelectionRing = Color(0xFF0B5F51)

val KipuSecondary = Color(0xFF22C79A)
val KipuOnSecondary = Color(0xFF17211E)
val KipuSecondaryContainer = Color(0xFFBCEBDD)
val KipuOnSecondaryContainer = Color(0xFF0B5F51)

val KipuTertiary = Color(0xFFBCEBDD)
val KipuOnTertiary = Color(0xFF17211E)
val KipuTertiaryContainer = Color(0xFFEEF5F2)
val KipuOnTertiaryContainer = Color(0xFF17211E)

val KipuBackground = Color(0xFFF5FAF8)
val KipuOnSurface = Color(0xFF17211E)
val KipuSurface = Color(0xFFF5FAF8)
val KipuSurfaceContainerLowest = Color(0xFFFFFFFF)
val KipuSurfaceContainerLow = Color(0xFFF1FBF7)
val KipuSurfaceContainer = Color(0xFFEEF5F2)
val KipuSurfaceContainerHigh = Color(0xFFE5F1EC)
val KipuSurfaceContainerHighest = Color(0xFFDFEAE5)
val KipuSurfaceVariant = Color(0xFFDFEAE5)
val KipuOnSurfaceVariant = Color(0xFF617773)
val KipuOutline = Color(0xFF6E7977)
val KipuOutlineVariant = Color(0xFFDFEAE5)
val KipuInverseSurface = Color(0xFF2D3133)
val KipuInverseOnSurface = Color(0xFFEFF1F3)
val KipuDarkBackground = Color(0xFF0B1220)
val KipuDarkSurface = Color(0xFF131B2E)
val KipuDarkSurfaceVariant = Color(0xFF1A263B)
val KipuDarkOnSurfaceVariant = Color(0xFF94A3B8)
val KipuDarkOutline = Color(0xFF64748B)
val KipuDarkOutlineVariant = Color(0xFF334155)

val KipuErrorLight = Color(0xFFBA1A1A)
val KipuError: Color @Composable get() = if (rememberKipuColors().isDark) Color(0xFFFCA5A5) else KipuErrorLight
val KipuOnError = Color(0xFFFFFFFF)
val KipuErrorContainer = Color(0xFFFFDAD6)
val KipuOnErrorContainer = Color(0xFF93000A)

val KipuIncome: Color @Composable get() = rememberKipuColors().positive
val KipuExpense: Color @Composable get() = rememberKipuColors().debt
val KipuWarning = Color(0xFFF59E0B)
