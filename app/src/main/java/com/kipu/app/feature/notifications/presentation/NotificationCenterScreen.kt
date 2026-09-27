package com.kipu.app.feature.notifications.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.notifications.domain.AppNotification
import com.kipu.app.feature.notifications.domain.NotificationCategory
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterScreen(
    state: NotificationsUiState,
    unreadCount: Int,
    onFilterSelected: (NotificationFilter) -> Unit,
    onMarkAllRead: () -> Unit,
    onMarkRead: (String) -> Unit,
    onDismiss: (String) -> Unit,
    onOpenNotification: (AppNotification) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val disableMotion = reducedMotion ?: rememberReducedMotionEnabled()
    AnimatedVisibility(
        visible = true,
        enter = if (disableMotion) EnterTransition.None else slideInVertically { -it / 12 } + fadeIn(),
        modifier = modifier.fillMaxSize(),
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("Avisos") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { insets ->
            Column(
                modifier = Modifier.fillMaxSize().padding(insets),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NotificationFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = state.selectedFilter == filter,
                            onClick = { onFilterSelected(filter) },
                            label = { Text(filter.label) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }

                if (unreadCount > 0) {
                    TextButton(
                        onClick = onMarkAllRead,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp),
                    ) {
                        Text("Marcar todo como leído")
                    }
                }

                when {
                    state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    state.visibleNotifications.isEmpty() -> {
                        val message = if (state.notifications.isEmpty()) state.emptyMessage else "No hay avisos en esta categoría."
                        Column(
                            modifier = Modifier.fillMaxSize().padding(32.dp).testTag("notification-empty"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsNone,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(message, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().testTag("notification-list"),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.visibleNotifications, key = { it.id }) { notification ->
                                val rowModifier = if (disableMotion) Modifier else Modifier.animateItem()
                                NotificationRow(
                                    notification = notification,
                                    modifier = rowModifier,
                                    onOpen = { onOpenNotification(notification) },
                                    onMarkRead = { onMarkRead(notification.id) },
                                    onDismiss = { onDismiss(notification.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: AppNotification,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onMarkRead: () -> Unit,
    onDismiss: () -> Unit,
) {
    val timestampFormatter = remember {
        DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.forLanguageTag("es-PE"))
            .withZone(ZoneId.systemDefault())
    }
    val kindLabel = if (notification.category == NotificationCategory.REMINDER) {
        "Recordatorio · Fecha prevista"
    } else {
        "Alerta · Condición ocurrida"
    }
    val readLabel = if (notification.isRead) "Leído" else "No leído"
    val dueDateLabel = notification.expectedDueDateLabel()
    val receivedAt = timestampFormatter.format(notification.createdAt)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .testTag("notification-row-${notification.id}")
            .clickable(onClick = onOpen)
            .semantics {
                contentDescription = listOfNotNull(
                    notification.title,
                    kindLabel,
                    dueDateLabel,
                    notification.body,
                    readLabel,
                    "Recibido $receivedAt",
                ).joinToString(", ")
            },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isRead) MaterialTheme.colorScheme.surfaceContainerLowest
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!notification.isRead) {
                    Spacer(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .semantics { contentDescription = "Aviso sin leer" },
                    )
                }
                Text(kindLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                Text(readLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(notification.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            dueDateLabel?.let { dueDate ->
                Text(dueDate, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text(notification.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Recibido $receivedAt",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!notification.isRead) {
                    IconButton(
                        onClick = onMarkRead,
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "Marcar leído: ${notification.title}" },
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Archivar aviso: ${notification.title}" },
                ) {
                    Icon(Icons.Default.Archive, contentDescription = null)
                }
            }
        }
    }
}
