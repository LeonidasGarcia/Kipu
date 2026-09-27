package com.kipu.app.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.kipu.app.feature.notifications.presentation.NotificationCenterScreen
import com.kipu.app.feature.notifications.presentation.NotificationsUiEvent
import com.kipu.app.feature.notifications.presentation.NotificationsViewModel

const val NOTIFICATION_CENTER_ROUTE = "notifications/center"

fun NavController.navigateToNotifications() {
    navigate(NOTIFICATION_CENTER_ROUTE) { launchSingleTop = true }
}

fun NavGraphBuilder.notificationDestinations(navController: NavController) {
    composable(NOTIFICATION_CENTER_ROUTE) {
        val viewModel: NotificationsViewModel = hiltViewModel()
        val state by viewModel.state.collectAsState()
        val unreadCount by viewModel.unreadCount.collectAsState()
        val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

        LaunchedEffect(viewModel, navController) {
            viewModel.events.collect { event ->
                when (event) {
                    is NotificationsUiEvent.OpenCard -> navController.navigateToCardDetail(event.cardId)
                    is NotificationsUiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                }
            }
        }

        NotificationCenterScreen(
            state = state,
            unreadCount = unreadCount,
            onFilterSelected = viewModel::setFilter,
            onMarkAllRead = viewModel::markAllRead,
            onMarkRead = viewModel::markRead,
            onDismiss = viewModel::dismiss,
            onOpenNotification = viewModel::openNotification,
            onNavigateBack = { navController.popBackStack() },
            snackbarHostState = snackbarHostState,
        )
    }
}
