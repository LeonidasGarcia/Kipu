package com.kipu.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable

val KipuPrimary = Color(0xFF0F766E)
val KipuOnPrimary = Color(0xFFFFFFFF)
val KipuPrimaryContainer = Color(0xFFCCFBF1)
val KipuOnPrimaryContainer = Color(0xFF134E4A)
val KipuInversePrimary = Color(0xFF5EEAD4)
val KipuSelectionRing = Color(0xFF0F766E)

val KipuSecondary = Color(0xFF14B8A6)
val KipuOnSecondary = Color(0xFF0F172A)
val KipuSecondaryContainer = Color(0xFFCCFBF1)
val KipuOnSecondaryContainer = Color(0xFF115E59)

val KipuTertiary = Color(0xFFF59E0B)
val KipuOnTertiary = Color(0xFF0F172A)
val KipuTertiaryContainer = Color(0xFFFEF3C7)
val KipuOnTertiaryContainer = Color(0xFF78350F)

val KipuBackground = Color(0xFFF8FAFC)
val KipuOnSurface = Color(0xFF0F172A)
val KipuSurface = Color(0xFFFFFFFF)
val KipuSurfaceContainerLowest = Color(0xFFFFFFFF)
val KipuSurfaceContainerLow = Color(0xFFF8FAFC)
val KipuSurfaceContainer = Color(0xFFF1F5F9)
val KipuSurfaceContainerHigh = Color(0xFFE2E8F0)
val KipuSurfaceContainerHighest = Color(0xFFCBD5E1)
val KipuSurfaceVariant = Color(0xFFF1F5F9)
val KipuOnSurfaceVariant = Color(0xFF475569)
val KipuOutline = Color(0xFF64748B)
val KipuOutlineVariant = Color(0xFFCBD5E1)
val KipuInverseSurface = Color(0xFF0F172A)
val KipuInverseOnSurface = Color(0xFFF8FAFC)
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
