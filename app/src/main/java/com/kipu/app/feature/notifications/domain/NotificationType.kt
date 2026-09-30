package com.kipu.app.feature.notifications.domain

import java.util.Locale

enum class NotificationCategory {
    ALERT,
    REMINDER,
}

enum class NotificationReferenceType {
    CARD,
    BUDGET,
    DEBT,
    GOAL,
}

object NotificationType {
    fun categoryOf(rawType: String): NotificationCategory =
        if (rawType.trim().uppercase(Locale.ROOT) == BILLING_DUE) {
            NotificationCategory.REMINDER
        } else {
            NotificationCategory.ALERT
        }

    fun referenceTypeOf(rawType: String?): NotificationReferenceType? = when (rawType?.trim()?.uppercase(Locale.ROOT)) {
        "CARD" -> NotificationReferenceType.CARD
        "BUDGET" -> NotificationReferenceType.BUDGET
        "DEBT" -> NotificationReferenceType.DEBT
        "GOAL" -> NotificationReferenceType.GOAL
        else -> null
    }

    private const val BILLING_DUE = "BILLING_DUE"
}
