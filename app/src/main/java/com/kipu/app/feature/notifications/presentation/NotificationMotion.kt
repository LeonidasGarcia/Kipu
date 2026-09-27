package com.kipu.app.feature.notifications.presentation

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun rememberReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    var reducedMotion by remember(context) { mutableStateOf(readReducedMotion(context)) }

    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reducedMotion = readReducedMotion(context)
            }
        }
        listOf(
            Settings.Global.ANIMATOR_DURATION_SCALE,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            Settings.Global.WINDOW_ANIMATION_SCALE,
        ).forEach { key ->
            context.contentResolver.registerContentObserver(Settings.Global.getUriFor(key), false, observer)
        }
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    return reducedMotion
}

@Suppress("DEPRECATION")
private fun readReducedMotion(context: android.content.Context): Boolean = runCatching {
    listOf(
        Settings.Global.ANIMATOR_DURATION_SCALE,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        Settings.Global.WINDOW_ANIMATION_SCALE,
    ).any { key -> Settings.Global.getFloat(context.contentResolver, key, 1f) <= 0f }
}.getOrDefault(false)
