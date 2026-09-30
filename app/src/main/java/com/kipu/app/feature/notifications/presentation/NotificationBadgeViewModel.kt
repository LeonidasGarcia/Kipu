package com.kipu.app.feature.notifications.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.notifications.domain.NotificationsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotificationBadgeViewModel @Inject constructor(
    private val sessionCoordinator: SessionCoordinator,
    private val repository: NotificationsRepository,
) : ViewModel() {
    private val activeUserId = sessionCoordinator.localAccess
        .map { access -> (access as? LocalAccess.Available)?.userId }
        .distinctUntilChanged()
    val unreadCount: StateFlow<Int> = activeUserId.flatMapLatest { userId ->
            if (userId == null) flowOf(0)
            else repository.observeUnreadCount(userId).catch { emit(0) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        viewModelScope.launch {
            activeUserId.collectLatest { userId ->
                if (userId != null) runCatching { repository.refresh(userId) }
            }
        }
    }
}
