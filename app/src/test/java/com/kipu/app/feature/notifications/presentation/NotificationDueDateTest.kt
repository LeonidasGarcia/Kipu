package com.kipu.app.feature.notifications.presentation

import com.kipu.app.feature.notifications.domain.AppNotification
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationDueDateTest {
    @Test
    fun `renders structured due date with an explicit expected future prefix`() {
        assertEquals("Vence el 30 de setiembre de 2026", notification().expectedDueDateLabel())
    }

    @Test
    fun `does not duplicate expected date already present in producer body`() {
        assertNull(notification(body = "Tu pago vence el 30 de septiembre.").expectedDueDateLabel())
    }

    @Test
    fun `invalid payload and non-reminder notifications have no due date label`() {
        assertNull(notification(eventPayloadJson = "not-json").expectedDueDateLabel())
        assertNull(notification(type = "SYSTEM").expectedDueDateLabel())
    }

    private fun notification(
        body: String = "Se acerca tu vencimiento.",
        type: String = "BILLING_DUE",
        eventPayloadJson: String = """{"due_date":"2026-09-30"}""",
    ) = AppNotification(
        id = "notice-1",
        userId = "user-1",
        title = "Cuota próxima",
        body = body,
        notificationType = type,
        referenceEntityType = null,
        referenceEntityId = null,
        isRead = false,
        createdAt = Instant.parse("2026-09-26T10:00:00Z"),
        deletedAt = null,
        eventPayloadJson = eventPayloadJson,
    )
}
