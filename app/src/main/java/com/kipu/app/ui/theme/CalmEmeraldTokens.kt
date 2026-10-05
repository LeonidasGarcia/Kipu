package com.kipu.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Scoped visual tokens for the Calm Emerald fintech redesign.
 * Direct translation of the reference designs:
 * - Background mint: #F1FBF7 / #F5FAF8
 * - White cards: #FFFFFF with subtle border #DFEAE5
 * - Primary deep emerald: #075E52 / #0B6657
 * - Primary text: #102522
 * - Secondary muted text: #617773
 * - Semantic Expense coral, Income emerald, Transfer blue
 */
@Immutable
data class CalmEmeraldColors(
    val isDark: Boolean,
    val background: Color,
    val surfaceCard: Color,
    val borderSubtle: Color,
    val primaryDeep: Color,
    val onPrimaryDeep: Color,
    val primaryText: Color,
    val secondaryMuted: Color,
    val heroGradientStart: Color,
    val heroGradientEnd: Color,
    val heroSubcardBg: Color,
    val expenseCoral: Color,
    val expenseBg: Color,
    val expenseBorder: Color,
    val incomeEmerald: Color,
    val incomeBg: Color,
    val incomeBorder: Color,
    val transferBlue: Color,
    val transferBg: Color,
    val transferBorder: Color,
    val warningAmber: Color,
    val warningText: Color,
    val warningBg: Color,
    val warningBorder: Color,
    val pillTrack: Color,
    val navCapsuleBg: Color,
    val navSelectedPill: Color,
    val navSelectedContent: Color,
    val navUnselectedContent: Color,
)

private val DarkCalmEmeraldColors = CalmEmeraldColors(
    isDark = true,
    background = Color(0xFF0B1220),
    surfaceCard = Color(0xFF131B2E),
    borderSubtle = Color(0xFF26344B),
    primaryDeep = Color(0xFF14B8A6),
    onPrimaryDeep = Color(0xFF042F2E),
    primaryText = Color(0xFFEFF1F3),
    secondaryMuted = Color(0xFF94A3B8),
    heroGradientStart = Color(0xFF0B463E),
    heroGradientEnd = Color(0xFF062D27),
    heroSubcardBg = Color(0x33000000),
    expenseCoral = Color(0xFFFCA5A5),
    expenseBg = Color(0xFF3B1216),
    expenseBorder = Color(0xFF7F1D1D),
    incomeEmerald = Color(0xFF86EFAC),
    incomeBg = Color(0xFF0A301D),
    incomeBorder = Color(0xFF14532D),
    transferBlue = Color(0xFF93C5FD),
    transferBg = Color(0xFF11284A),
    transferBorder = Color(0xFF1E3A8A),
    warningAmber = Color(0xFFFCD34D),
    warningText = Color(0xFFFDE68A),
    warningBg = Color(0xFF382305),
    warningBorder = Color(0xFF78350F),
    pillTrack = Color(0xFF1A263B),
    navCapsuleBg = Color(0xFF131B2E),
    navSelectedPill = Color(0xFF134E4A),
    navSelectedContent = Color(0xFF5EEAD4),
    navUnselectedContent = Color(0xFF94A3B8),
)

private val LightCalmEmeraldColors = CalmEmeraldColors(
    isDark = false,
    background = Color(0xFFF5FAF8),
    surfaceCard = Color(0xFFFFFFFF),
    borderSubtle = Color(0xFFDFEAE5),
    primaryDeep = KipuPrimary,
    onPrimaryDeep = Color(0xFFFFFFFF),
    primaryText = KipuOnSurface,
    secondaryMuted = Color(0xFF617773),
    heroGradientStart = Color(0xFF075E52),
    heroGradientEnd = Color(0xFF05453C),
    heroSubcardBg = Color(0x2E000000),
    expenseCoral = Color(0xFFDC2626),
    expenseBg = Color(0xFFFEF2F2),
    expenseBorder = Color(0xFFFECACA),
    incomeEmerald = Color(0xFF0B6657),
    incomeBg = Color(0xFFF0FDF4),
    incomeBorder = Color(0xFFBBF7D0),
    transferBlue = Color(0xFF2563EB),
    transferBg = Color(0xFFEFF6FF),
    transferBorder = Color(0xFFBFDBFE),
    warningAmber = Color(0xFFD97706),
    warningText = Color(0xFF92400E),
    warningBg = Color(0xFFFEF3C7),
    warningBorder = Color(0xFFFDE68A),
    pillTrack = Color(0xFFEEF5F2),
    navCapsuleBg = Color(0xFFFFFFFF),
    navSelectedPill = Color(0xFF075E52),
    navSelectedContent = Color(0xFFFFFFFF),
    navUnselectedContent = Color(0xFF617773),
)

@Composable
fun rememberCalmEmeraldColors(): CalmEmeraldColors {
    val isDark = LocalKipuDarkTheme.current
    return LocalCalmEmeraldColors.current ?: if (isDark) DarkCalmEmeraldColors else LightCalmEmeraldColors
}

private val LocalCalmEmeraldColors = compositionLocalOf<CalmEmeraldColors?> { null }

/** Share a single set of theme tokens without per-frame animators. */
@Composable
internal fun ProvideCalmEmeraldColors(content: @Composable () -> Unit) {
    val isDark = LocalKipuDarkTheme.current
    val colors = if (isDark) DarkCalmEmeraldColors else LightCalmEmeraldColors
    CompositionLocalProvider(LocalCalmEmeraldColors provides colors, content = content)
}
