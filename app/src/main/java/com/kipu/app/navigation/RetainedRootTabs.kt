package com.kipu.app.navigation

import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.KipuEasingTokens
import com.kipu.app.ui.theme.KipuMotionTokens

/** Keeps root-tab UI and its local state composed while only measuring the visible transition pair. */
@Composable
internal fun RetainedRootTabs(
    selectedTabIndex: State<Int>,
    content: @Composable () -> Unit,
) {
    val reducedMotion = rememberReducedMotionEnabled()
    val density = LocalDensity.current
    val durationMillis = if (reducedMotion) 0 else KipuMotionTokens.RootTabSwitchMillis
    val transition = updateTransition(selectedTabIndex.value, label = "retained_root_tabs")
    val slideDistance = 64.dp
    val pageAlpha = (0..2).map { index ->
        transition.animateFloat(
            transitionSpec = { tween(durationMillis, easing = KipuEasingTokens.Standard) },
            label = "root_page_alpha_$index",
        ) { targetIndex -> if (targetIndex == index) 1f else 0f }
    }
    val pageOffset = (0..2).map { index ->
        transition.animateDp(
            transitionSpec = { tween(durationMillis, easing = KipuEasingTokens.Standard) },
            label = "root_page_offset_$index",
        ) { targetIndex ->
            when {
                targetIndex == index -> 0.dp
                index < targetIndex -> -slideDistance
                else -> slideDistance
            }
        }
    }

    Layout(
        content = content,
        modifier = Modifier.fillMaxSize().clipToBounds(),
    ) { measurables, constraints ->
        val pages = measurables.map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            pages.forEachIndexed { index, page ->
                page.placeRelativeWithLayer(
                    x = 0,
                    y = 0,
                    zIndex = if (index == transition.targetState) 1f else 0f,
                ) {
                    translationX = with(density) { pageOffset[index].value.toPx() }
                    alpha = pageAlpha[index].value
                }
            }
        }
    }
}
