package com.kipu.app.ui.theme

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
}
