package com.kipu.app.feature.notifications.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AppNotificationDao {
    @Query("SELECT * FROM app_notifications WHERE user_id = :userId AND deleted_at IS NULL ORDER BY created_at DESC")
    fun observeActive(userId: String): Flow<List<AppNotificationEntity>>

    @Query("SELECT COUNT(*) FROM app_notifications WHERE user_id = :userId AND is_read = 0 AND deleted_at IS NULL")
    fun observeUnreadCount(userId: String): Flow<Int>

    @Query("SELECT * FROM app_notifications WHERE user_id = :userId AND id = :id LIMIT 1")
    suspend fun getById(userId: String, id: String): AppNotificationEntity?

    @Query("SELECT * FROM app_notifications WHERE user_id = :userId AND id = :id LIMIT 1")
    fun observeById(userId: String, id: String): Flow<AppNotificationEntity?>

    @Query("SELECT * FROM app_notifications WHERE user_id = :userId AND deleted_at IS NULL ORDER BY created_at DESC")
    suspend fun getActive(userId: String): List<AppNotificationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(notifications: List<AppNotificationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(notification: AppNotificationEntity)

    @Query("UPDATE app_notifications SET is_read = 1 WHERE user_id = :userId AND id = :id AND deleted_at IS NULL")
    suspend fun markRead(userId: String, id: String): Int

    @Query("UPDATE app_notifications SET is_read = 1 WHERE user_id = :userId AND is_read = 0 AND deleted_at IS NULL")
    suspend fun markAllRead(userId: String): Int

    @Query("UPDATE app_notifications SET deleted_at = :deletedAt WHERE user_id = :userId AND id = :id AND deleted_at IS NULL")
    suspend fun dismiss(userId: String, id: String, deletedAt: java.time.Instant): Int

    @Query("SELECT COUNT(*) FROM app_notifications WHERE user_id = :userId AND is_read = 0 AND deleted_at IS NULL")
    suspend fun unreadCount(userId: String): Int

    @Query("SELECT * FROM app_notifications WHERE user_id = :userId AND deleted_at IS NULL")
    suspend fun getAllActiveUnsorted(userId: String): List<AppNotificationEntity>
}

@Dao
interface NotificationSyncOutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(command: NotificationSyncOutboxEntity)

    @Query("SELECT * FROM notification_sync_outbox WHERE user_id = :userId ORDER BY created_at ASC")
    suspend fun getPending(userId: String): List<NotificationSyncOutboxEntity>

    @Query("SELECT * FROM notification_sync_outbox WHERE user_id = :userId AND notification_id = :notificationId LIMIT 1")
    suspend fun getByNotice(userId: String, notificationId: String): NotificationSyncOutboxEntity?

    @Query("UPDATE notification_sync_outbox SET attempt_count = attempt_count + 1 WHERE operation_id = :operationId")
    suspend fun incrementAttempt(operationId: String)

    @Query("DELETE FROM notification_sync_outbox WHERE operation_id = :operationId")
    suspend fun delete(operationId: String)

    @Query("SELECT COUNT(*) FROM notification_sync_outbox WHERE user_id = :userId")
    suspend fun pendingCount(userId: String): Int
}
