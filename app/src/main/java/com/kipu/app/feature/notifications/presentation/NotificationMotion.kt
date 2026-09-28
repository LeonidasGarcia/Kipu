package com.kipu.app.feature.notifications.presentation

import androidx.compose.runtime.Composable

@Composable
internal fun rememberReducedMotionEnabled(): Boolean {
    return com.kipu.app.ui.motion.rememberReducedMotionEnabled()
}
