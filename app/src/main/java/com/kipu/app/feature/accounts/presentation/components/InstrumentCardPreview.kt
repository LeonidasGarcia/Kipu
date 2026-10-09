package com.kipu.app.feature.accounts.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kipu.app.ui.component.MaskedCardReference
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import kotlinx.coroutines.launch

@Composable
fun InstrumentCardPreview(
    title: String,
    instrumentType: String,
    subtitle: String,
    lastFourDigits: String? = null,
    modifier: Modifier = Modifier,
    network: String? = null,
    balanceLabel: String? = null,
    amount: String? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
    foregroundColor: Color = Color.White,
    stylePreset: CardStylePreset? = null,
    showCardHardware: Boolean = true,
) {
    val reducedMotion = rememberReducedMotionEnabled()
    val settleProgress = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var dragRotationX by remember { mutableFloatStateOf(0f) }
    var dragRotationY by remember { mutableFloatStateOf(0f) }
    var settledRotationX by remember { mutableFloatStateOf(0f) }
    var settledRotationY by remember { mutableFloatStateOf(0f) }
    var glarePosition by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    val shape = RoundedCornerShape(16.dp)
    val cardForeground = stylePreset?.textColor ?: foregroundColor
    val cardBrush = stylePreset?.gradientBrush ?: Brush.linearGradient(listOf(backgroundColor, backgroundColor))
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            settleProgress.snapTo(1f)
            isDragging = false
            dragRotationX = 0f
            dragRotationY = 0f
            settledRotationX = 0f
            settledRotationY = 0f
            glarePosition = Offset(0.5f, 0.5f)
        }
    }

    fun settleTilt() {
        settledRotationX = dragRotationX
        settledRotationY = dragRotationY
        isDragging = false
        glarePosition = Offset(0.5f, 0.5f)
        scope.launch {
            settleProgress.snapTo(0f)
            settleProgress.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 360f))
            settledRotationX = 0f
            settledRotationY = 0f
        }
    }
    val accessibleDescription = buildString {
        append("Vista previa de instrumento. ")
        append(title.ifBlank { "Mi instrumento" })
        append(". $instrumentType. $subtitle.")
        if (showCardHardware) append(" Chip EMV y pago sin contacto.")
        if (network != null) append(" Red $network.")
        stylePreset?.let { append(" ${it.institutionCode}, nivel ${it.tierLabel}.") }
        if (balanceLabel != null && amount != null) append(" $balanceLabel: $amount.")
        if (lastFourDigits?.length == 4 && lastFourDigits.all(Char::isDigit)) {
            append(" Termina en $lastFourDigits.")
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp)
            .onSizeChanged { cardSize = it }
            .pointerInput(reducedMotion, cardSize) {
                if (!reducedMotion) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            scope.launch { settleProgress.snapTo(0f) }
                        },
                        onDragEnd = ::settleTilt,
                        onDragCancel = ::settleTilt,
                    ) { change, _ ->
                        val width = cardSize.width.coerceAtLeast(1).toFloat()
                        val height = cardSize.height.coerceAtLeast(1).toFloat()
                        val x = (change.position.x / width).coerceIn(0f, 1f)
                        val y = (change.position.y / height).coerceIn(0f, 1f)
                        dragRotationY = (x - 0.5f) * 12f
                        dragRotationX = (0.5f - y) * 12f
                        glarePosition = Offset(x, y)
                        change.consume()
                    }
                }
            },
    ) {
        Card(
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp)
                .graphicsLayer {
                    if (!reducedMotion) {
                        val recovery = if (isDragging) 0f else settleProgress.value
                        this.rotationX = if (isDragging) dragRotationX else settledRotationX * (1f - recovery)
                        this.rotationY = if (isDragging) dragRotationY else settledRotationY * (1f - recovery)
                        cameraDistance = 14f * density
                    }
                }
                .testTag("instrument_card_preview")
                .semantics(mergeDescendants = true) { contentDescription = accessibleDescription },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp)
                    .clip(shape)
                    .background(cardBrush),
            ) {
                if (stylePreset != null) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(
                            color = stylePreset.accentColor.copy(alpha = 0.18f),
                            radius = this.size.minDimension * 0.42f,
                            center = Offset(this.size.width * 0.88f, this.size.height * 0.12f),
                        )
                        for (index in 0..3) {
                            val startX = this.size.width * (0.52f + index * 0.12f)
                            drawLine(
                                color = stylePreset.accentColor.copy(alpha = 0.16f),
                                start = Offset(startX, this.size.height * 0.08f),
                                end = Offset(startX - this.size.height * 0.42f, this.size.height * 0.92f),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }
                    }
                }
                if (!reducedMotion) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                val center = Offset(
                                    x = size.width * (0.76f + glarePosition.x * 0.20f),
                                    y = size.height * (0.06f + glarePosition.y * 0.30f),
                                )
                                drawRect(
                                    Brush.radialGradient(
                                        colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
                                        center = center,
                                        radius = size.width.coerceAtLeast(1f) * 0.24f,
                                    ),
                                )
                            },
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "$title · $instrumentType",
                            style = MaterialTheme.typography.labelLarge,
                            color = cardForeground,
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = cardForeground,
                        )
                        stylePreset?.let {
                            Text(
                                text = "${it.institutionCode} · ${it.tierLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = cardForeground,
                            )
                        }
                        if (balanceLabel != null && amount != null) {
                            Text(balanceLabel, style = MaterialTheme.typography.labelMedium, color = cardForeground)
                            Text(
                                amount,
                                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.Bold,
                                color = cardForeground,
                            )
                        }
                        if (lastFourDigits?.length == 4 && lastFourDigits.all(Char::isDigit)) {
                            MaskedCardReference(
                                lastFourDigits = lastFourDigits,
                                style = MaterialTheme.typography.labelLarge,
                                color = cardForeground,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (showCardHardware) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 32.dp, height = 24.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(cardForeground.copy(alpha = 0.78f)),
                                )
                                Text(")))", color = cardForeground, style = MaterialTheme.typography.titleMedium)
                            }
                            Icon(
                                imageVector = Icons.Default.CreditCard,
                                contentDescription = null,
                                tint = cardForeground,
                                modifier = Modifier.size(30.dp),
                            )
                            if (!network.isNullOrBlank()) {
                                Text(network.uppercase(), color = cardForeground, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = cardForeground, modifier = Modifier.size(30.dp))
                        }
                    }
                }
            }
        }
    }
}
