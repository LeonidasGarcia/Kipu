package com.kipu.app.feature.notifications.domain

import java.time.Instant

data class AppNotification(
    val id: String,
    val userId: String,
    val title: String,
    val body: String,
    val notificationType: String,
    val referenceEntityType: String?,
    val referenceEntityId: String?,
    val isRead: Boolean,
    val createdAt: Instant,
    val deletedAt: Instant?,
    val eventPayloadJson: String = "{}",
) {
    val category: NotificationCategory get() = NotificationType.categoryOf(notificationType)
    val referenceType: NotificationReferenceType? get() = NotificationType.referenceTypeOf(referenceEntityType)
}

sealed interface NotificationDestination {
    data class CardDetail(val cardId: String) : NotificationDestination
    data class Unavailable(val message: String = UNAVAILABLE_DESTINATION_MESSAGE) : NotificationDestination
}

fun interface NotificationDestinationResolver {
    suspend fun resolve(userId: String, notification: AppNotification): NotificationDestination
}

const val UNAVAILABLE_DESTINATION_MESSAGE = "El elemento relacionado ya no existe o fue eliminado."
