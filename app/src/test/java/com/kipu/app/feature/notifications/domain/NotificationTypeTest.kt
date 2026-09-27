package com.kipu.app.feature.notifications.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationTypeTest {
    @Test
    fun `canonical and legacy credit types map to alerts`() {
        assertEquals(NotificationCategory.ALERT, NotificationType.categoryOf("CREDIT_THRESHOLD"))
        assertEquals(NotificationCategory.ALERT, NotificationType.categoryOf("CREDIT_UTILIZATION_THRESHOLD_CROSSED"))
    }

    @Test
    fun `billing due maps to a future reminder`() {
        assertEquals(NotificationCategory.REMINDER, NotificationType.categoryOf("BILLING_DUE"))
    }

    @Test
    fun `known occurred conditions and unknown future values remain visible as alerts`() {
        listOf("BUDGET_ALERT", "GOAL_REACHED", "SYSTEM", "NEW_SERVER_TYPE")
            .forEach { assertEquals(it, NotificationCategory.ALERT, NotificationType.categoryOf(it)) }
    }

    @Test
    fun `reference types normalize without changing stored value`() {
        assertEquals(NotificationReferenceType.CARD, NotificationType.referenceTypeOf("CARD"))
        assertEquals(NotificationReferenceType.CARD, NotificationType.referenceTypeOf("card"))
        assertEquals(NotificationReferenceType.BUDGET, NotificationType.referenceTypeOf(" budget "))
        assertEquals(NotificationReferenceType.DEBT, NotificationType.referenceTypeOf("DEBT"))
        assertEquals(NotificationReferenceType.GOAL, NotificationType.referenceTypeOf("goal"))
        assertEquals(null, NotificationType.referenceTypeOf("unknown"))
    }
}
