package com.kipu.app.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing

/** Shared durations for purposeful, non-financial UI state changes.
 *
 * Compose animation specs run through Android's MotionDurationScale, so the system animation
 * scale (including zero) also applies to these tokens.
 */
object KipuMotionTokens {
    const val MicroMillis = 80
    const val QuickMillis = 150
    const val FastMillis = 250
    const val MediumMillis = 350
    const val SlowMillis = 400
    const val VerySlowMillis = 500

    const val FeedbackMillis = 150

    // Purposeful roles mapped to existing duration scale
    const val NavEnterMillis = 220
    const val NavExitMillis = QuickMillis
    const val TopLevelMillis = 160
    const val RootTabSwitchMillis = 180
    const val SegmentMillis = QuickMillis
    const val ThemeMillis = 0
    const val SheetMillis = 300
    const val CardFlipMillis = 280
    const val SubtreeEnterMillis = 200
    const val SubtreeExitMillis = 200
    const val StateCrossfadeMillis = QuickMillis
}

/**
 * Standard easings aligned with Material 3 motion tokens:
 * - Standard: natural acceleration and deceleration for intra-screen state.
 * - Decelerate: for elements entering the screen.
 * - Accelerate: for elements exiting the screen.
 */
object KipuEasingTokens {
    val Standard: Easing = FastOutSlowInEasing
    val Decelerate: Easing = LinearOutSlowInEasing
    val Accelerate: Easing = FastOutLinearInEasing
}
