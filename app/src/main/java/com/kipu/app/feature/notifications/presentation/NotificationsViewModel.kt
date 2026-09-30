package com.kipu.app.feature.notifications.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.notifications.domain.AppNotification
import com.kipu.app.feature.notifications.domain.NotificationCategory
import com.kipu.app.feature.notifications.domain.NotificationDestination
import com.kipu.app.feature.notifications.domain.NotificationDestinationResolver
import com.kipu.app.feature.notifications.domain.NotificationsRepository
import com.kipu.app.feature.notifications.domain.UNAVAILABLE_DESTINATION_MESSAGE
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NotificationFilter(val label: String) {
    ALL("Todos"),
    ALERTS("Alertas"),
    REMINDERS("Recordatorios"),
}

data class NotificationsUiState(
    val activeUserId: String? = null,
    val notifications: List<AppNotification> = emptyList(),
    val visibleNotifications: List<AppNotification> = emptyList(),
    val selectedFilter: NotificationFilter = NotificationFilter.ALL,
    val isLoading: Boolean = false,
    val emptyMessage: String = "Todo al día. No tienes avisos pendientes",
)

sealed interface NotificationsUiEvent {
    data class OpenCard(val cardId: String) : NotificationsUiEvent
    data class ShowMessage(val message: String) : NotificationsUiEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationsRepository,
    private val sessionCoordinator: SessionCoordinator,
    private val destinationResolver: NotificationDestinationResolver,
) : ViewModel() {
    private val activeUserId: Flow<String?> = sessionCoordinator.localAccess
        .map { access -> (access as? LocalAccess.Available)?.userId }

    private val sourceNotifications = activeUserId.flatMapLatest { userId ->
        if (userId == null) {
            flowOf(emptyList())
        } else {
            repository.observeActive(userId).catch { emit(emptyList()) }
        }
    }

    private val selectedFilter = kotlinx.coroutines.flow.MutableStateFlow(NotificationFilter.ALL)
    private val eventChannel = Channel<NotificationsUiEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    val state: StateFlow<NotificationsUiState> = combine(
        activeUserId,
        sourceNotifications,
        selectedFilter,
    ) { userId, rows, filter ->
        val ordered = rows.asSequence()
            .filter { it.userId == userId && it.deletedAt == null }
            .sortedByDescending { it.createdAt }
            .toList()
        val visible = when (filter) {
            NotificationFilter.ALL -> ordered
            NotificationFilter.ALERTS -> ordered.filter { it.category == NotificationCategory.ALERT }
            NotificationFilter.REMINDERS -> ordered.filter { it.category == NotificationCategory.REMINDER }
        }
        NotificationsUiState(
            activeUserId = userId,
            notifications = ordered,
            visibleNotifications = visible,
            selectedFilter = filter,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, NotificationsUiState())

    val unreadCount: StateFlow<Int> = activeUserId.flatMapLatest { userId ->
        if (userId == null) flowOf(0) else repository.observeUnreadCount(userId)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun setFilter(filter: NotificationFilter) {
        selectedFilter.value = filter
    }

    fun refresh() {
        val userId = currentUserId() ?: return
        viewModelScope.launch { repository.refresh(userId) }
    }

    fun markRead(notificationId: String) {
        mutate { repository.markRead(it, notificationId) }
    }

    fun markAllRead() {
        mutate(repository::markAllRead)
    }

    fun dismiss(notificationId: String) {
        mutate { repository.dismiss(it, notificationId) }
    }

    fun openNotification(notification: AppNotification) {
        val userId = currentUserId() ?: return
        viewModelScope.launch {
            when (val destination = destinationResolver.resolve(userId, notification)) {
                is NotificationDestination.CardDetail -> eventChannel.send(NotificationsUiEvent.OpenCard(destination.cardId))
                is NotificationDestination.Unavailable -> eventChannel.send(
                    NotificationsUiEvent.ShowMessage(destination.message.ifBlank { UNAVAILABLE_DESTINATION_MESSAGE }),
                )
            }
        }
    }

    private fun mutate(action: suspend (String) -> Result<Unit>) {
        val userId = currentUserId() ?: return
        viewModelScope.launch {
            action(userId).onFailure {
                eventChannel.send(NotificationsUiEvent.ShowMessage("No se pudo actualizar el aviso. Inténtalo nuevamente."))
            }
        }
    }

    private fun currentUserId(): String? =
        (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId
}
