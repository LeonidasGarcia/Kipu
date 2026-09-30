package com.kipu.app.feature.notifications.data

import com.kipu.app.core.database.DatabaseTransactionRunner
import com.kipu.app.feature.notifications.data.local.AppNotificationDao
import com.kipu.app.feature.notifications.data.local.AppNotificationEntity
import com.kipu.app.feature.notifications.data.local.NotificationSyncOutboxDao
import com.kipu.app.feature.notifications.data.local.NotificationSyncOutboxEntity
import com.kipu.app.feature.notifications.data.remote.NotificationDto
import com.kipu.app.feature.notifications.data.remote.NotificationMutationDto
import com.kipu.app.feature.notifications.data.remote.NotificationsApi
import com.kipu.app.feature.notifications.data.remote.NotificationsApiResponse
import com.kipu.app.feature.notifications.data.sync.NotificationSyncScheduler
import com.kipu.app.feature.notifications.domain.AppNotification
import com.kipu.app.feature.notifications.domain.NotificationsRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class OfflineFirstNotificationsRepository @Inject constructor(
    private val notificationDao: AppNotificationDao,
    private val outboxDao: NotificationSyncOutboxDao,
    private val api: NotificationsApi,
    private val transactionRunner: DatabaseTransactionRunner,
    private val syncScheduler: NotificationSyncScheduler,
    private val clock: Clock,
) : NotificationsRepository {

    override fun observeActive(userId: String): Flow<List<AppNotification>> =
        notificationDao.observeActive(userId).map { rows -> rows.map { it.toDomain() } }

    override fun observeUnreadCount(userId: String): Flow<Int> = notificationDao.observeUnreadCount(userId)

    override suspend fun refresh(userId: String): Result<Unit> = runCatching {
        when (val response = api.fetchForUser(userId)) {
            is NotificationsApiResponse.Success -> {
                val pendingIds = outboxDao.getPending(userId).mapTo(mutableSetOf()) { it.notificationId }
                val remoteRows = response.data
                    .asSequence()
                    .filter { it.userId == userId && it.id !in pendingIds }
                    .map { it.toEntity() }
                    .toList()
                transactionRunner { notificationDao.upsertAll(remoteRows) }
            }
            is NotificationsApiResponse.Error -> error("Notification refresh failed (${response.statusCode}): ${response.message.orEmpty()}")
            is NotificationsApiResponse.NetworkFailure -> throw response.exception
        }
    }

    override suspend fun markRead(userId: String, notificationId: String): Result<Unit> = runCatching {
        val changed = transactionRunner {
            val notice = notificationDao.getById(userId, notificationId)
                ?: error("Notification is no longer available")
            if (notice.deletedAt != null) error("Notification is no longer active")
            val previous = outboxDao.getByNotice(userId, notificationId)
            if (!notice.isRead) {
                val updated = notificationDao.markRead(userId, notificationId)
                if (updated == 0) error("Notification is no longer active")
                outboxDao.upsert(previous.coalesced(userId, notificationId, isRead = true, deletedAt = null))
                true
            } else {
                previous != null
            }
        }
        if (changed) scheduleSafely(userId)
    }

    override suspend fun markAllRead(userId: String): Result<Unit> = runCatching {
        val changed = transactionRunner {
            val unread = notificationDao.getAllActiveUnsorted(userId).filterNot { it.isRead || it.deletedAt != null }
            if (unread.isEmpty()) return@transactionRunner false
            notificationDao.markAllRead(userId)
            unread.forEach { notice ->
                val previous = outboxDao.getByNotice(userId, notice.id)
                outboxDao.upsert(previous.coalesced(userId, notice.id, isRead = true, deletedAt = null))
            }
            true
        }
        if (changed) scheduleSafely(userId)
    }

    override suspend fun dismiss(userId: String, notificationId: String): Result<Unit> = runCatching {
        val changed = transactionRunner {
            val notice = notificationDao.getById(userId, notificationId)
                ?: error("Notification is no longer available")
            val previous = outboxDao.getByNotice(userId, notificationId)
            if (notice.deletedAt != null) {
                previous != null
            } else {
                val deletedAt = clock.instant()
                val updated = notificationDao.dismiss(userId, notificationId, deletedAt)
                if (updated == 0) error("Notification is no longer active")
                outboxDao.upsert(
                    previous.coalesced(
                        userId = userId,
                        notificationId = notificationId,
                        isRead = previous?.isRead,
                        deletedAt = deletedAt,
                    ),
                )
                true
            }
        }
        if (changed) scheduleSafely(userId)
    }

    override suspend fun syncPending(userId: String): Result<Unit> = runCatching {
        for (command in outboxDao.getPending(userId)) {
            when (val response = api.patchForUser(
                userId = userId,
                notificationId = command.notificationId,
                mutation = NotificationMutationDto(
                    isRead = command.isRead,
                    deletedAt = command.deletedAt?.toString(),
                ),
            )) {
                is NotificationsApiResponse.Success -> outboxDao.delete(command.operationId)
                is NotificationsApiResponse.Error -> {
                    outboxDao.incrementAttempt(command.operationId)
                    error("Notification sync failed (${response.statusCode}): ${response.message.orEmpty()}")
                }
                is NotificationsApiResponse.NetworkFailure -> {
                    outboxDao.incrementAttempt(command.operationId)
                    throw response.exception
                }
            }
        }
    }

    private fun scheduleSafely(userId: String) {
        runCatching { syncScheduler.scheduleSync(userId) }
    }

    private fun NotificationSyncOutboxEntity?.coalesced(
        userId: String,
        notificationId: String,
        isRead: Boolean?,
        deletedAt: Instant?,
    ): NotificationSyncOutboxEntity = NotificationSyncOutboxEntity(
        operationId = this?.operationId ?: UUID.randomUUID().toString(),
        userId = userId,
        notificationId = notificationId,
        isRead = isRead ?: this?.isRead,
        deletedAt = deletedAt ?: this?.deletedAt,
        attemptCount = 0,
        createdAt = this?.createdAt ?: clock.instant(),
    )

    private fun AppNotificationEntity.toDomain() = AppNotification(
        id = id,
        userId = userId,
        title = title,
        body = body,
        notificationType = notificationType,
        referenceEntityType = referenceEntityType,
        referenceEntityId = referenceEntityId,
        isRead = isRead,
        createdAt = createdAt,
        deletedAt = deletedAt,
        eventPayloadJson = eventPayloadJson,
    )

    private fun NotificationDto.toEntity() = AppNotificationEntity(
        id = id,
        userId = userId,
        title = title,
        body = body,
        notificationType = notificationType,
        referenceEntityType = referenceEntityType,
        referenceEntityId = referenceEntityId,
        isRead = isRead,
        createdAt = Instant.parse(createdAt),
        deletedAt = deletedAt?.let(Instant::parse),
        eventPayloadJson = eventPayload?.toString() ?: "{}",
    )
}
