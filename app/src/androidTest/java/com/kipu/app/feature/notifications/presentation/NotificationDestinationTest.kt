package com.kipu.app.feature.notifications.presentation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.feature.notifications.domain.AppNotification
import com.kipu.app.feature.notifications.domain.UNAVAILABLE_DESTINATION_MESSAGE
import com.kipu.app.navigation.NOTIFICATION_CENTER_ROUTE
import com.kipu.app.ui.theme.KipuTheme
import java.time.Instant
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NotificationDestinationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `unavailable target shows snackbar keeps center visible and remains unread`() {
        val navController = TestNavHostController(ApplicationProvider.getApplicationContext()).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
        }
        val notice = AppNotification(
            id = "missing-target",
            userId = "user-1",
            title = "Aviso relacionado",
            body = "El objeto de referencia ya no existe.",
            notificationType = "SYSTEM",
            referenceEntityType = "BUDGET",
            referenceEntityId = "deleted-budget",
            isRead = false,
            createdAt = Instant.parse("2026-09-26T10:00:00Z"),
            deletedAt = null,
        )
        var markReadCalls = 0

        compose.setContent {
            KipuTheme {
                NavHost(navController, startDestination = NOTIFICATION_CENTER_ROUTE) {
                    composable(NOTIFICATION_CENTER_ROUTE) {
                        val snackbar = remember { SnackbarHostState() }
                        val scope = rememberCoroutineScope()
                        NotificationCenterScreen(
                            state = NotificationsUiState(
                                activeUserId = "user-1",
                                notifications = listOf(notice),
                                visibleNotifications = listOf(notice),
                                isLoading = false,
                            ),
                            unreadCount = 1,
                            onFilterSelected = {},
                            onMarkAllRead = {},
                            onMarkRead = { markReadCalls += 1 },
                            onDismiss = {},
                            onOpenNotification = {
                                scope.launch { snackbar.showSnackbar(UNAVAILABLE_DESTINATION_MESSAGE) }
                            },
                            onNavigateBack = { navController.popBackStack() },
                            snackbarHostState = snackbar,
                            reducedMotion = true,
                        )
                    }
                    composable("accounts/instrument/{instrumentId}?isCard=true") {
                        androidx.compose.material3.Text("Detalle de tarjeta")
                    }
                }
            }
        }

        compose.onNodeWithTag("notification-row-missing-target").performClick()
        compose.onNodeWithText(UNAVAILABLE_DESTINATION_MESSAGE).assertIsDisplayed()
        compose.onNodeWithText("Avisos").assertIsDisplayed()
        compose.onAllNodesWithText("Detalle de tarjeta").assertCountEquals(0)
        assertEquals(NOTIFICATION_CENTER_ROUTE, navController.currentDestination?.route)
        assertEquals(0, markReadCalls)
    }
}
