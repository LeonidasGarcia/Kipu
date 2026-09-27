package com.kipu.app.feature.notifications.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import java.time.Instant

@Entity(
    tableName = "app_notifications",
    primaryKeys = ["user_id", "id"],
    indices = [
        Index(value = ["user_id", "created_at"], name = "app_notifications_user_created_idx"),
        Index(value = ["user_id", "is_read", "created_at"], name = "app_notifications_user_read_created_idx"),
    ],
)
data class AppNotificationEntity(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "body") val body: String,
    @ColumnInfo(name = "notification_type") val notificationType: String,
    @ColumnInfo(name = "reference_entity_type") val referenceEntityType: String?,
    @ColumnInfo(name = "reference_entity_id") val referenceEntityId: String?,
    @ColumnInfo(name = "is_read") val isRead: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null,
    @ColumnInfo(name = "event_payload") val eventPayloadJson: String = "{}",
)

@Entity(
    tableName = "notification_sync_outbox",
    indices = [
        Index(value = ["user_id", "notification_id"], unique = true, name = "notification_outbox_owner_notice_uq"),
        Index(value = ["user_id", "created_at"], name = "notification_outbox_owner_created_idx"),
    ],
)
data class NotificationSyncOutboxEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "operation_id") val operationId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "notification_id") val notificationId: String,
    @ColumnInfo(name = "is_read") val isRead: Boolean?,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant?,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)
