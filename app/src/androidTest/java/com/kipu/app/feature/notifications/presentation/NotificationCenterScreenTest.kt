package com.kipu.app.feature.notifications.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kipu.app.feature.notifications.domain.AppNotification
import com.kipu.app.ui.theme.KipuTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NotificationCenterScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun emptyCenterShowsApprovedCopyOnFirstLocalRender() {
        compose.setContent {
            KipuTheme {
                NotificationCenterScreen(
                    state = NotificationsUiState(isLoading = false),
                    unreadCount = 0,
                    onFilterSelected = {},
                    onMarkAllRead = {},
                    onMarkRead = {},
                    onDismiss = {},
                    onOpenNotification = {},
                    onNavigateBack = {},
                )
            }
        }

        compose.waitUntil(2_000) {
            compose.onAllNodesWithText("Todo al día. No tienes avisos pendientes").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Todo al día. No tienes avisos pendientes").assertIsDisplayed()
    }

    @Test
    fun filterChipsReportAlertAndReminderSelection() {
        var selected: NotificationFilter? = null
        compose.setContent {
            KipuTheme {
                NotificationCenterScreen(
                    state = NotificationsUiState(isLoading = false),
                    unreadCount = 0,
                    onFilterSelected = { selected = it },
                    onMarkAllRead = {},
                    onMarkRead = {},
                    onDismiss = {},
                    onOpenNotification = {},
                    onNavigateBack = {},
                )
            }
        }

        compose.onNodeWithText("Alertas").performClick()
        assertEquals(NotificationFilter.ALERTS, selected)
        compose.onNodeWithText("Recordatorios").performClick()
        assertEquals(NotificationFilter.REMINDERS, selected)
        compose.onNodeWithText("Todos").performClick()
        assertEquals(NotificationFilter.ALL, selected)
    }

    @Test
    fun billingReminderKeepsExpectedDateVisibleAndDoesNotClaimPayment() {
        val reminder = AppNotification(
            id = "notice-1",
            userId = "user-1",
            title = "Cuota próxima",
            body = "Vence el 30 de septiembre",
            notificationType = "BILLING_DUE",
            referenceEntityType = "DEBT",
            referenceEntityId = "debt-1",
            isRead = false,
            createdAt = Instant.parse("2026-09-26T10:00:00Z"),
            deletedAt = null,
        )
        compose.setContent {
            KipuTheme {
                NotificationCenterScreen(
                    state = NotificationsUiState(
                        activeUserId = "user-1",
                        notifications = listOf(reminder),
                        visibleNotifications = listOf(reminder),
                        isLoading = false,
                    ),
                    unreadCount = 1,
                    onFilterSelected = {},
                    onMarkAllRead = {},
                    onMarkRead = {},
                    onDismiss = {},
                    onOpenNotification = {},
                    onNavigateBack = {},
                )
            }
        }

        compose.onNodeWithText("Cuota próxima").assertIsDisplayed()
        compose.onNodeWithText("Vence el 30 de septiembre").assertIsDisplayed()
        compose.onNodeWithText("Recordatorio · Fecha prevista").assertIsDisplayed()
    }

    @Test
    fun billingReminderRendersStructuredDueDateAsExpectedFutureDate() {
        val reminder = AppNotification(
            id = "notice-2",
            userId = "user-1",
            title = "Cuota próxima",
            body = "Se acerca tu vencimiento.",
            notificationType = "BILLING_DUE",
            referenceEntityType = "DEBT",
            referenceEntityId = "debt-1",
            isRead = false,
            createdAt = Instant.parse("2026-09-26T10:00:00Z"),
            deletedAt = null,
            eventPayloadJson = """{"due_date":"2026-09-30"}""",
        )
        compose.setContent {
            KipuTheme {
                NotificationCenterScreen(
                    state = NotificationsUiState(
                        activeUserId = "user-1",
                        notifications = listOf(reminder),
                        visibleNotifications = listOf(reminder),
                        isLoading = false,
                    ),
                    unreadCount = 1,
                    onFilterSelected = {},
                    onMarkAllRead = {},
                    onMarkRead = {},
                    onDismiss = {},
                    onOpenNotification = {},
                    onNavigateBack = {},
                    reducedMotion = true,
                )
            }
        }

        compose.onNodeWithText("Vence el 30 de setiembre de 2026").assertIsDisplayed()
        compose.onNodeWithText("Recordatorio · Fecha prevista").assertIsDisplayed()
        compose.onNodeWithTag("notification-row-notice-2")
            .assertContentDescriptionContains("Vence el 30 de setiembre de 2026")
            .assertContentDescriptionContains("Se acerca tu vencimiento.")
    }
}
