package com.kipu.app.feature.notifications.data.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.notifications.domain.NotificationsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncNotificationsWorkerTest {
    @Test
    fun `matching verified owner syncs pending notifications`() = runTest {
        val repository = mockk<NotificationsRepository>()
        coEvery { repository.syncPending(USER_A) } returns Result.success(Unit)
        val session = session(USER_A)
        val worker = worker(repository, session, USER_A)

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify { repository.syncPending(USER_A) }
    }

    @Test
    fun `owner mismatch defers without accessing another account outbox`() = runTest {
        val repository = mockk<NotificationsRepository>(relaxed = true)
        val worker = worker(repository, session(USER_B), USER_A)

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
        coVerify(exactly = 0) { repository.syncPending(any()) }
    }

    @Test
    fun `failed sync retries and keeps repository outbox available`() = runTest {
        val repository = mockk<NotificationsRepository>()
        coEvery { repository.syncPending(USER_A) } returns Result.failure(IllegalStateException("offline"))
        val worker = worker(repository, session(USER_A), USER_A)

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
        coVerify { repository.syncPending(USER_A) }
    }

    private fun session(userId: String): SessionCoordinator = mockk {
        every { currentOwner } returns LocalOwner(userId)
    }

    private fun worker(
        repository: NotificationsRepository,
        session: SessionCoordinator,
        targetUserId: String?,
    ) = SyncNotificationsWorker(
        appContext = mockk<Context>(relaxed = true),
        workerParams = mockk<WorkerParameters> {
            every { inputData } returns workDataOf(SyncNotificationsWorker.KEY_USER_ID to targetUserId)
        },
        repository = repository,
        sessionCoordinator = session,
    )

    private companion object {
        const val USER_A = "61000000-0000-4000-8000-000000000001"
        const val USER_B = "61000000-0000-4000-8000-000000000002"
    }
}
