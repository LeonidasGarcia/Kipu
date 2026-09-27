package com.kipu.app.feature.notifications.data

import com.kipu.app.core.database.DatabaseTransactionRunner
import com.kipu.app.feature.notifications.data.local.AppNotificationDao
import com.kipu.app.feature.notifications.data.local.AppNotificationEntity
import com.kipu.app.feature.notifications.data.local.NotificationSyncOutboxDao
import com.kipu.app.feature.notifications.data.local.NotificationSyncOutboxEntity
import com.kipu.app.feature.notifications.data.remote.NotificationsApi
import com.kipu.app.feature.notifications.data.sync.NotificationSyncScheduler
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationMutationTest {
    @Test
    fun `mark read updates local state and atomically queues desired state`() = runTest {
        val dao = mockk<AppNotificationDao>()
        val outbox = mockk<NotificationSyncOutboxDao>()
        val scheduler = RecordingScheduler()
        coEvery { dao.getById(USER_ID, NOTICE_ID) } returns notification()
        coEvery { outbox.getByNotice(USER_ID, NOTICE_ID) } returns null
        coEvery { dao.markRead(USER_ID, NOTICE_ID) } returns 1
        val queued = slot<NotificationSyncOutboxEntity>()
        coEvery { outbox.upsert(capture(queued)) } returns Unit
        val repository = repository(dao, outbox, scheduler)

        val result = repository.markRead(USER_ID, NOTICE_ID)

        assertTrue(result.isSuccess)
        assertEquals(true, queued.captured.isRead)
        assertEquals(null, queued.captured.deletedAt)
        assertEquals(1, scheduler.users.size)
        coVerify { dao.markRead(USER_ID, NOTICE_ID) }
    }

    @Test
    fun `mark all reads only active unread rows for the requested user`() = runTest {
        val dao = mockk<AppNotificationDao>()
        val outbox = mockk<NotificationSyncOutboxDao>()
        val scheduler = RecordingScheduler()
        coEvery { dao.getAllActiveUnsorted(USER_ID) } returns listOf(
            notification(NOTICE_ID, USER_ID, read = false),
            notification("already-read", USER_ID, read = true),
        )
        coEvery { dao.markAllRead(USER_ID) } returns 1
        coEvery { outbox.getByNotice(USER_ID, NOTICE_ID) } returns null
        val queued = slot<NotificationSyncOutboxEntity>()
        coEvery { outbox.upsert(capture(queued)) } returns Unit
        val repository = repository(dao, outbox, scheduler)

        val result = repository.markAllRead(USER_ID)

        assertTrue(result.isSuccess)
        assertEquals(true, queued.captured.isRead)
        coVerify(exactly = 1) { outbox.upsert(any()) }
        assertEquals(listOf(USER_ID), scheduler.users)
    }

    @Test
    fun `dismiss preserves prior read state and coalesces the existing outbox row`() = runTest {
        val dao = mockk<AppNotificationDao>()
        val outbox = mockk<NotificationSyncOutboxDao>()
        val scheduler = RecordingScheduler()
        val prior = NotificationSyncOutboxEntity(
            operationId = "stable-op",
            userId = USER_ID,
            notificationId = NOTICE_ID,
            isRead = true,
            deletedAt = null,
            createdAt = NOW,
        )
        coEvery { dao.getById(USER_ID, NOTICE_ID) } returns notification(read = true)
        coEvery { outbox.getByNotice(USER_ID, NOTICE_ID) } returns prior
        coEvery { dao.dismiss(USER_ID, NOTICE_ID, NOW) } returns 1
        val queued = slot<NotificationSyncOutboxEntity>()
        coEvery { outbox.upsert(capture(queued)) } returns Unit
        val repository = repository(dao, outbox, scheduler)

        val result = repository.dismiss(USER_ID, NOTICE_ID)

        assertTrue(result.isSuccess)
        assertEquals("stable-op", queued.captured.operationId)
        assertEquals(true, queued.captured.isRead)
        assertEquals(NOW, queued.captured.deletedAt)
        assertEquals(listOf(USER_ID), scheduler.users)
    }

    private fun repository(
        dao: AppNotificationDao,
        outbox: NotificationSyncOutboxDao,
        scheduler: RecordingScheduler,
    ) = OfflineFirstNotificationsRepository(
        notificationDao = dao,
        outboxDao = outbox,
        api = mockk<NotificationsApi>(relaxed = true),
        transactionRunner = object : DatabaseTransactionRunner {
            override suspend operator fun <R> invoke(block: suspend () -> R): R = block()
        },
        syncScheduler = scheduler,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    private fun notification(
        id: String = NOTICE_ID,
        userId: String = USER_ID,
        read: Boolean = false,
    ) = AppNotificationEntity(
        id = id,
        userId = userId,
        title = "Aviso",
        body = "Detalle",
        notificationType = "SYSTEM",
        referenceEntityType = null,
        referenceEntityId = null,
        isRead = read,
        createdAt = NOW,
    )

    private class RecordingScheduler : NotificationSyncScheduler {
        val users = mutableListOf<String>()
        override fun scheduleSync(userId: String) { users += userId }
    }

    private companion object {
        const val USER_ID = "61000000-0000-4000-8000-000000000001"
        const val NOTICE_ID = "62000000-0000-4000-8000-000000000001"
        val NOW: Instant = Instant.parse("2026-09-26T10:00:00Z")
    }
}
