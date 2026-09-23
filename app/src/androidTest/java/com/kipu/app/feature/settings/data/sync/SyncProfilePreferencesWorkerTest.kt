package com.kipu.app.feature.settings.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.settings.domain.ProfilePreferencesRepository
import com.kipu.app.feature.settings.domain.model.ProfilePreferenceDelta
import com.kipu.app.feature.settings.domain.model.UserProfile
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import io.mockk.every
import io.mockk.mockk

@RunWith(AndroidJUnit4::class)
class SyncProfilePreferencesWorkerTest {

    private lateinit var context: Context
    private val userId = UUID.randomUUID()

    private class FakeCoordinator(override val currentOwner: LocalOwner?) : SessionCoordinator {
        override val remoteSession = MutableStateFlow(com.kipu.app.core.session.RemoteSession.Absent)
        override val localAccess = MutableStateFlow<com.kipu.app.core.session.LocalAccess>(
            com.kipu.app.core.session.LocalAccess.Available(currentOwner!!.verifiedUserId, com.kipu.app.core.session.RemoteSession.Absent)
        )
        override suspend fun setActiveOwner(userId: String) {}
        override suspend fun clearActiveOwner(explicit: Boolean) {}
        override suspend fun updateRemoteSession(session: com.kipu.app.core.session.RemoteSession) {}
        override suspend fun updateLockState(isLocked: Boolean, reason: String) {}
    }

    private class FakeRepo : ProfilePreferencesRepository {
        var syncResult: Result<Unit> = Result.success(Unit)

        override fun observeProfile(userId: UUID): Flow<UserProfile?> = MutableStateFlow(null)
        override suspend fun getProfile(userId: UUID): UserProfile? = null
        override suspend fun updatePreferences(userId: UUID, delta: ProfilePreferenceDelta): Result<UserProfile> =
            Result.success(UserProfile(userId = userId, displayName = ""))
        override suspend fun setHideBalances(userId: UUID, hideBalances: Boolean): Result<UserProfile> =
            Result.success(UserProfile(userId = userId, displayName = ""))
        override suspend fun syncPendingPreferences(userId: UUID): Result<Unit> = syncResult
        override suspend fun refreshProfile(userId: UUID): Result<UserProfile> =
            Result.success(UserProfile(userId = userId, displayName = ""))
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun doWork_returns_success_when_repository_syncs_successfully() = runBlocking {
        val repo = FakeRepo()
        repo.syncResult = Result.success(Unit)
        val coordinator = FakeCoordinator(LocalOwner(userId.toString(), false))

        val workerParams = mockk<WorkerParameters>(relaxed = true)
        every { workerParams.inputData } returns workDataOf(SyncProfilePreferencesWorker.KEY_USER_ID to userId.toString())

        val worker = SyncProfilePreferencesWorker(context, workerParams, repo, coordinator)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun doWork_returns_failure_when_conflict_detected() = runBlocking {
        val repo = FakeRepo()
        repo.syncResult = Result.failure(IllegalStateException("Revision conflict detected during sync"))
        val coordinator = FakeCoordinator(LocalOwner(userId.toString(), false))

        val workerParams = mockk<WorkerParameters>(relaxed = true)
        every { workerParams.inputData } returns workDataOf(SyncProfilePreferencesWorker.KEY_USER_ID to userId.toString())

        val worker = SyncProfilePreferencesWorker(context, workerParams, repo, coordinator)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun doWork_returns_retry_on_network_or_transient_error() = runBlocking {
        val repo = FakeRepo()
        repo.syncResult = Result.failure(Exception("Network timeout"))
        val coordinator = FakeCoordinator(LocalOwner(userId.toString(), false))

        val workerParams = mockk<WorkerParameters>(relaxed = true)
        every { workerParams.inputData } returns workDataOf(SyncProfilePreferencesWorker.KEY_USER_ID to userId.toString())

        val worker = SyncProfilePreferencesWorker(context, workerParams, repo, coordinator)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }
}
