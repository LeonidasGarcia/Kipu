package com.kipu.app.feature.plans.presentation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kipu.app.ui.theme.KipuMotionTokens

@Composable
internal fun purchaseCardScale(selected: Boolean, reduceMotion: Boolean): Float {
    val scale by animateFloatAsState(
        targetValue = if (selected && !reduceMotion) 1.02f else 1f,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.78f, stiffness = 480f),
        label = "purchase-card-scale",
    )
    return scale
}

@Composable
internal fun PurchasePriceShimmer(modifier: Modifier = Modifier, reduceMotion: Boolean) {
    val phase = if (reduceMotion) {
        0f
    } else {
        val animatedPhase by rememberInfiniteTransition(label = "purchase-price-shimmer").animateFloat(
            initialValue = -120f,
            targetValue = 220f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = KipuMotionTokens.SlowMillis * 3, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "purchase-price-shimmer-phase",
        )
        animatedPhase
    }
    val colors = listOf(
        Color(0xFFE6E8EA),
        Color(0xFFF2F4F6),
        Color(0xFFE6E8EA),
    )
    val brush = if (reduceMotion) Brush.horizontalGradient(colors) else Brush.linearGradient(
        colors = colors,
        start = Offset(phase, 0f),
        end = Offset(phase + 140f, 20f),
    )
    Box(
        modifier = modifier
            .width(132.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(brush)
            .testTag("purchase-price-shimmer"),
    )
}
