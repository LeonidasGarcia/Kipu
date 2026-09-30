package com.kipu.app.feature.notifications.presentation

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.notifications.domain.AppNotification
import com.kipu.app.ui.theme.KipuTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NotificationActionsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun markAllReadAndSingleNoticeActionsAreAvailableWithTouchTargets() {
        val notice = notice()
        var readAllCount = 0
        var readId: String? = null
        var dismissedId: String? = null
        compose.setContent {
            KipuTheme {
                NotificationCenterScreen(
                    state = NotificationsUiState(
                        activeUserId = notice.userId,
                        notifications = listOf(notice),
                        visibleNotifications = listOf(notice),
                    ),
                    unreadCount = 1,
                    onFilterSelected = {},
                    onMarkAllRead = { readAllCount++ },
                    onMarkRead = { readId = it },
                    onDismiss = { dismissedId = it },
                    onOpenNotification = {},
                    onNavigateBack = {},
                    reducedMotion = true,
                )
            }
        }

        compose.onNodeWithText("Marcar todo como leído").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Marcar leído: Umbral de crédito").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Archivar aviso: Umbral de crédito").assertIsDisplayed().performClick()
        compose.onNodeWithTag("notification-row-notice-1").assertHeightIsAtLeast(48.dp)
        assertEquals(1, readAllCount)
        assertEquals("notice-1", readId)
        assertEquals("notice-1", dismissedId)
    }

    @Test
    fun unreadBadgeCapsAt99PlusAndExposesAccessibleCount() {
        var clicked = false
        compose.setContent {
            KipuTheme {
                UnreadNotificationBadge(unreadCount = 101, onClick = { clicked = true })
            }
        }

        compose.onNodeWithText("99+").assertIsDisplayed()
        compose.onNodeWithContentDescription("101 avisos sin leer").assertIsDisplayed().performClick()
        assertEquals(true, clicked)
    }

    @Test
    fun reducedMotionPreservesNotificationContentAndArchiveAction() {
        val notice = notice()
        compose.setContent {
            KipuTheme {
                NotificationCenterScreen(
                    state = NotificationsUiState(
                        activeUserId = notice.userId,
                        notifications = listOf(notice),
                        visibleNotifications = listOf(notice),
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

        compose.onNodeWithText("Umbral de crédito").assertIsDisplayed()
        compose.onNodeWithText("Alerta · Condición ocurrida").assertIsDisplayed()
        compose.onNodeWithContentDescription("Archivar aviso: Umbral de crédito").assertIsDisplayed()
    }

    private fun notice() = AppNotification(
        id = "notice-1",
        userId = "61000000-0000-4000-8000-000000000001",
        title = "Umbral de crédito",
        body = "La utilización alcanzó el 80%.",
        notificationType = "CREDIT_THRESHOLD",
        referenceEntityType = "CARD",
        referenceEntityId = "card-1",
        isRead = false,
        createdAt = Instant.parse("2026-09-26T10:00:00Z"),
        deletedAt = null,
    )
}
