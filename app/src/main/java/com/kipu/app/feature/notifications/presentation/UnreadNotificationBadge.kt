package com.kipu.app.feature.notifications.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun UnreadNotificationBadge(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean? = null,
) {
    val disableMotion = reducedMotion ?: rememberReducedMotionEnabled()
    val visibleCount = unreadCount.coerceAtLeast(0)
    val badgeText = if (visibleCount > 99) "99+" else visibleCount.toString()
    val description = when (visibleCount) {
        0 -> "Notificaciones"
        1 -> "1 aviso sin leer"
        else -> "$visibleCount avisos sin leer"
    }

    Box(modifier = modifier.size(48.dp), contentAlignment = Alignment.Center) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = description },
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        AnimatedVisibility(
            visible = visibleCount > 0,
            enter = if (disableMotion) EnterTransition.None else scaleIn(initialScale = 0.7f) + fadeIn(),
            exit = if (disableMotion) ExitTransition.None else scaleOut(targetScale = 0.85f) + fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Badge(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                AnimatedContent(
                    targetState = badgeText,
                    label = "unread-count",
                    transitionSpec = {
                        if (disableMotion) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            (scaleIn(initialScale = 0.7f) + fadeIn()) togetherWith
                                (scaleOut(targetScale = 0.85f) + fadeOut())
                        }
                    },
                ) { text -> Text(text = text, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}
