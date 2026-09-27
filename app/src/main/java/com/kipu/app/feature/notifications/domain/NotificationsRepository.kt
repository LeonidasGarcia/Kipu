package com.kipu.app.feature.notifications.domain

import kotlinx.coroutines.flow.Flow

interface NotificationsRepository {
    fun observeActive(userId: String): Flow<List<AppNotification>>
    fun observeUnreadCount(userId: String): Flow<Int>
    suspend fun refresh(userId: String): Result<Unit>
    suspend fun markRead(userId: String, notificationId: String): Result<Unit>
    suspend fun markAllRead(userId: String): Result<Unit>
    suspend fun dismiss(userId: String, notificationId: String): Result<Unit>
    suspend fun syncPending(userId: String): Result<Unit>
}
