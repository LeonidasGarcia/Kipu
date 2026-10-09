package com.kipu.app.navigation

import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.KipuMotionTokens
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.offset
import com.kipu.app.ui.theme.KipuEasingTokens


@Composable
fun KipuNavigationBar(
    currentRoute: String?,
    onNavigateToDinero: () -> Unit,
    onNavigateToMovimientos: () -> Unit,
    modifier: Modifier = Modifier,
    onRegisterClick: (() -> Unit)? = null,
    onNavigateToDeudas: () -> Unit = {},
) {
    val isDineroSelected = currentRoute == ACCOUNTS_DASHBOARD_ROUTE
    val isMovimientosSelected = currentRoute == MOVEMENTS_HISTORY_PATTERN ||
        currentRoute == MOVEMENTS_HISTORY_ROUTE ||
        currentRoute?.startsWith("movements/history") == true
    val isDeudasSelected = currentRoute == DEBT_LIST_ROUTE
    val selectedTabIndex = when {
        isDineroSelected -> 0
        isMovimientosSelected -> 1
        isDeudasSelected -> 2
        else -> -1
    }

    val colors = rememberCalmEmeraldColors()
    val largeText = LocalDensity.current.fontScale > 1.3f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("kipu_bottom_navigation_bar"),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            // Floating Capsule Pill for Destinations (weight 1 reserving gap + FAB)
            Surface(
                shape = CircleShape,
                color = colors.navCapsuleBg,
                border = BorderStroke(1.dp, colors.borderSubtle),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = if (largeText) 68.dp else 56.dp)
                    .clip(CircleShape),
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    val reducedMotion = rememberReducedMotionEnabled()
                    val durationMillis = if (reducedMotion) 0 else KipuMotionTokens.RootTabSwitchMillis
                    val tabTransition = updateTransition(selectedTabIndex, label = "root_tab_selection")
                    val itemWidths = (0..2).map { index ->
                        tabTransition.animateDp(
                            transitionSpec = {
                                tween(durationMillis = durationMillis, easing = KipuEasingTokens.Standard)
                            },
                            label = "root_tab_width_$index",
                        ) { targetIndex ->
                            when {
                                targetIndex < 0 -> maxWidth / 3
                                targetIndex == index -> maxWidth / 2
                                else -> maxWidth / 4
                            }
                        }
                    }
                    val selectedPillOffset = tabTransition.animateDp(
                        transitionSpec = {
                            tween(durationMillis = durationMillis, easing = KipuEasingTokens.Standard)
                        },
                        label = "root_tab_indicator_offset",
                    ) { targetIndex ->
                        when (targetIndex) {
                            1 -> maxWidth / 4
                            2 -> maxWidth / 2
                            else -> 0.dp
                        }
                    }
                    val selectedPillWidth = tabTransition.animateDp(
                        transitionSpec = {
                            tween(durationMillis = durationMillis, easing = KipuEasingTokens.Standard)
                        },
                        label = "root_tab_indicator_width",
                    ) { targetIndex -> if (targetIndex < 0) 0.dp else maxWidth / 2 }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset { IntOffset(selectedPillOffset.value.roundToPx(), 0) }
                            .deferredWidth(selectedPillWidth)
                            .heightIn(min = if (largeText) 52.dp else 40.dp)
                            .clip(CircleShape)
                            .background(colors.navSelectedPill)
                            .testTag("nav_selected_indicator"),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KipuFloatingNavItem(
                            selected = isDineroSelected,
                            onClick = onNavigateToDinero,
                            icon = Icons.Default.AccountBalanceWallet,
                            label = "Dinero",
                            tag = "nav_item_dinero",
                            modifier = Modifier.deferredWidth(itemWidths[0]),
                        )

                        KipuFloatingNavItem(
                            selected = isMovimientosSelected,
                            onClick = onNavigateToMovimientos,
                            icon = Icons.AutoMirrored.Filled.ReceiptLong,
                            label = "Movimientos",
                            tag = "nav_item_movimientos",
                            modifier = Modifier.deferredWidth(itemWidths[1]),
                        )

                        KipuFloatingNavItem(
                            selected = isDeudasSelected,
                            onClick = onNavigateToDeudas,
                            icon = Icons.Default.AccountBalance,
                            label = "Deudas",
                            tag = "nav_item_deudas",
                            modifier = Modifier.deferredWidth(itemWidths[2]),
                        )
                    }
                }
            }

            // Separate Circular + Button
            if (onRegisterClick != null) {
                Spacer(modifier = Modifier.width(8.dp))
                FloatingActionButton(
                    onClick = onRegisterClick,
                    shape = CircleShape,
                    containerColor = colors.primaryDeep,
                    contentColor = colors.onPrimaryDeep,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp, pressedElevation = 6.dp),
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("btn_root_register")
                        .semantics {
                            contentDescription = if (isDeudasSelected) {
                                "Agregar deuda o préstamo"
                            } else {
                                "Registrar movimiento"
                            }
                        },
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = colors.onPrimaryDeep,
                    )
                }
            }
        }
    }
}

/** Reads animated width during measurement so tab-size animation does not recompose the bar. */
private fun Modifier.deferredWidth(width: State<Dp>): Modifier = layout { measurable, constraints ->
    val widthPx = width.value.roundToPx().coerceIn(constraints.minWidth, constraints.maxWidth)
    val placeable = measurable.measure(constraints.copy(minWidth = widthPx, maxWidth = widthPx))
    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
}

@Composable
private fun KipuFloatingNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val colors = rememberCalmEmeraldColors()
    val reducedMotion = rememberReducedMotionEnabled()
    val animDuration = if (reducedMotion) 0 else KipuMotionTokens.SegmentMillis
    val largeText = LocalDensity.current.fontScale > 1.3f

    val selectedContentAlpha = animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = animDuration, easing = KipuEasingTokens.Standard),
        label = "nav_content_$label",
    )
    val contentColor = colors.navSelectedContent

    Box(
        modifier = modifier
            .heightIn(min = if (largeText) 56.dp else 48.dp)
            .clip(CircleShape)
            .clickable(
                // The animated selected pill supplies feedback without a second ripple layer.
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .testTag(tag)
            .semantics {
                this.contentDescription = label
                this.selected = selected
                this.role = Role.Tab
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = if (largeText) 52.dp else 40.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .graphicsLayer { alpha = selectedContentAlpha.value }
                    .padding(horizontal = 3.dp, vertical = if (largeText) 4.dp else 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = label,
                    color = contentColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.testTag("nav_label_${tag.removePrefix("nav_item_")}"),
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.navUnselectedContent,
                modifier = Modifier.align(Alignment.Center).size(20.dp)
                    .graphicsLayer { alpha = 1f - selectedContentAlpha.value },
            )
        }
    }
}

@Composable
private fun KipuFloatingNavIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberCalmEmeraldColors()
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.navUnselectedContent,
            modifier = Modifier.size(20.dp),
        )
    }
}
