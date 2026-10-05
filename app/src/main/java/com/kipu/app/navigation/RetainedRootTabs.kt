package com.kipu.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.draw.clipToBounds

/**
 * Both roots stay composed and measured. Selection is read only in layer properties:
 * switching does not recreate the lists or their remembered scroll/sheet state.
 * Both roots are placed into retained layers. The inactive one is outside the clipped
 * viewport, so it cannot receive pointer events or expose on-screen accessibility nodes.
 * This container belongs to the authenticated dashboard back-stack entry, not the Activity.
 */
@Composable
internal fun RetainedRootTabs(
    movementsSelected: State<Boolean>,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = Modifier.fillMaxSize().clipToBounds()) { measurables, constraints ->
        val pages = measurables.map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            pages.forEachIndexed { index, page ->
                page.placeRelativeWithLayer(0, 0) {
                    val active = movementsSelected.value == (index == 1)
                    translationX = if (active) 0f else size.width
                    alpha = if (active) 1f else 0f
                }
            }
        }
    }
}
