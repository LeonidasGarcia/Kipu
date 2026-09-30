package com.kipu.app.feature.notifications.data

import com.kipu.app.feature.accounts.data.local.CardDao
import com.kipu.app.feature.notifications.domain.AppNotification
import com.kipu.app.feature.notifications.domain.NotificationDestination
import com.kipu.app.feature.notifications.domain.NotificationDestinationResolver
import com.kipu.app.feature.notifications.domain.NotificationReferenceType
import javax.inject.Inject

class RegisteredNotificationDestinationResolver @Inject constructor(
    private val cardDao: CardDao,
) : NotificationDestinationResolver {
    override suspend fun resolve(userId: String, notification: AppNotification): NotificationDestination {
        if (notification.userId != userId) return NotificationDestination.Unavailable()
        val referenceId = notification.referenceEntityId?.takeIf(String::isNotBlank)
            ?: return NotificationDestination.Unavailable()
        return when (notification.referenceType) {
            NotificationReferenceType.CARD -> {
                if (cardDao.getById(userId, referenceId) == null) {
                    NotificationDestination.Unavailable()
                } else {
                    NotificationDestination.CardDetail(referenceId)
                }
            }
            NotificationReferenceType.BUDGET,
            NotificationReferenceType.DEBT,
            NotificationReferenceType.GOAL,
            null -> NotificationDestination.Unavailable()
        }
    }
}
