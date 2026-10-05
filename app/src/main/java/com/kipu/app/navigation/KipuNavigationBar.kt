package com.kipu.app.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.KipuMotionTokens
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign

import androidx.compose.material.icons.filled.EventNote
import androidx.compose.ui.semantics.disabled

@Composable
fun KipuNavigationBar(
    currentRoute: String?,
    onNavigateToDinero: () -> Unit,
    onNavigateToMovimientos: () -> Unit,
    modifier: Modifier = Modifier,
    onRegisterClick: (() -> Unit)? = null,
) {
    val isDineroSelected = currentRoute == ACCOUNTS_DASHBOARD_ROUTE
    val isMovimientosSelected = currentRoute == MOVEMENTS_HISTORY_PATTERN ||
        currentRoute == MOVEMENTS_HISTORY_ROUTE ||
        currentRoute?.startsWith("movements/history") == true

    val colors = rememberCalmEmeraldColors()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
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
                    .heightIn(min = 56.dp)
                    .clip(CircleShape),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    KipuFloatingNavItem(
                        selected = isDineroSelected,
                        onClick = onNavigateToDinero,
                        icon = Icons.Default.AccountBalanceWallet,
                        label = "Dinero",
                        tag = "nav_item_dinero",
                        modifier = Modifier.weight(1f),
                    )

                    KipuFloatingNavItem(
                        selected = isMovimientosSelected,
                        onClick = onNavigateToMovimientos,
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        label = "Movimientos",
                        tag = "nav_item_movimientos",
                        modifier = Modifier.weight(1f),
                    )

                    Column(
                        modifier = Modifier
                            .size(48.dp)
                            .semantics {
                                contentDescription = "Planificación. Próximamente"
                                disabled()
                                role = Role.Tab
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Default.EventNote, contentDescription = null,
                            tint = colors.navUnselectedContent.copy(alpha = 0.5f))
                    }
                }
            }

            // Separate Circular + Button
            if (onRegisterClick != null) {
                Spacer(modifier = Modifier.width(12.dp))
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
                            contentDescription = "Registrar movimiento"
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

    val bgColor = animateColorAsState(
        targetValue = if (selected) colors.navSelectedPill else Color.Transparent,
        animationSpec = tween(durationMillis = animDuration),
        label = "nav_bg_$label",
    )
    val contentColor = colors.navSelectedContent

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
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
        Box(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .clip(CircleShape)
                .drawBehind { drawRect(bgColor.value) },
        ) {
            if (largeText) {
                Column(
                    modifier = Modifier.graphicsLayer { alpha = if (selected) 1f else 0f }.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
                    Text(
                        text = label,
                        color = contentColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                    )
                }
            } else Row(
                modifier = Modifier
                    .graphicsLayer { alpha = if (selected) 1f else 0f }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp),
                )
                run {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = label,
                        color = contentColor,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        ),
                    )
                }
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.navUnselectedContent,
                modifier = Modifier.align(Alignment.Center).size(20.dp)
                    .graphicsLayer { alpha = if (selected) 0f else 1f },
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
