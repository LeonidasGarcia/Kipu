package com.kipu.app.feature.notifications.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class NotificationDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("title") val title: String,
    @SerialName("body") val body: String,
    @SerialName("notification_type") val notificationType: String,
    @SerialName("reference_entity_type") val referenceEntityType: String? = null,
    @SerialName("reference_entity_id") val referenceEntityId: String? = null,
    @SerialName("is_read") val isRead: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("event_payload") val eventPayload: JsonObject? = null,
)

@Serializable
data class NotificationMutationDto(
    @SerialName("is_read") val isRead: Boolean? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

sealed interface NotificationsApiResponse<out T> {
    data class Success<T>(val data: T) : NotificationsApiResponse<T>
    data class Error(val statusCode: Int, val message: String?) : NotificationsApiResponse<Nothing>
    data class NetworkFailure(val exception: Throwable) : NotificationsApiResponse<Nothing>
}
