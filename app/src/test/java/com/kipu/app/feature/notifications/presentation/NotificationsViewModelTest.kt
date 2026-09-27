package com.kipu.app.feature.notifications.presentation

import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.notifications.domain.AppNotification
import com.kipu.app.feature.notifications.domain.NotificationDestination
import com.kipu.app.feature.notifications.domain.NotificationDestinationResolver
import com.kipu.app.feature.notifications.domain.NotificationsRepository
import com.kipu.app.feature.notifications.domain.UNAVAILABLE_DESTINATION_MESSAGE
import java.time.Instant
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
    private lateinit var localAccess: MutableStateFlow<LocalAccess>

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `filters classify billing due separately and preserve newest first order`() = runTest(dispatcher) {
        val newestAlert = notice("new-alert", USER_A, "SYSTEM", NOW)
        val reminder = notice("reminder", USER_A, "BILLING_DUE", NOW.minusSeconds(30))
        val oldestAlert = notice("old-alert", USER_A, "CREDIT_UTILIZATION_THRESHOLD_CROSSED", NOW.minusSeconds(60))
        val repository = FakeRepository(mapOf(USER_A to listOf(oldestAlert, reminder, newestAlert)))
        val viewModel = viewModel(repository)

        assertEquals(listOf("new-alert", "reminder", "old-alert"), viewModel.state.value.visibleNotifications.map { it.id })
        viewModel.setFilter(NotificationFilter.ALERTS)
        assertEquals(listOf("new-alert", "old-alert"), viewModel.state.value.visibleNotifications.map { it.id })
        viewModel.setFilter(NotificationFilter.REMINDERS)
        assertEquals(listOf("reminder"), viewModel.state.value.visibleNotifications.map { it.id })
        viewModel.setFilter(NotificationFilter.ALL)
        assertEquals(3, viewModel.state.value.visibleNotifications.size)
    }

    @Test
    fun `empty account reports exact empty-state copy`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeRepository(mapOf(USER_A to emptyList())))
        assertEquals("Todo al día. No tienes avisos pendientes", viewModel.state.value.emptyMessage)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `account switch replaces prior account list`() = runTest(dispatcher) {
        val access = MutableStateFlow<LocalAccess>(available(USER_A))
        localAccess = access
        val viewModel = viewModel(
            FakeRepository(
                mapOf(
                    USER_A to listOf(notice("private-a", USER_A, "SYSTEM", NOW)),
                    USER_B to listOf(notice("private-b", USER_B, "SYSTEM", NOW)),
                ),
            ),
            access,
        )

        assertEquals(listOf("private-a"), viewModel.state.value.visibleNotifications.map { it.id })
        access.value = available(USER_B)
        assertEquals(listOf("private-b"), viewModel.state.value.visibleNotifications.map { it.id })
        assertEquals(USER_B, viewModel.state.value.activeUserId)
    }

    @Test
    fun `unavailable destination emits friendly message and does not mark notice read`() = runTest(dispatcher) {
        val notice = notice("missing-target", USER_A, "SYSTEM", NOW)
        val repository = FakeRepository(mapOf(USER_A to listOf(notice)))
        val resolver = NotificationDestinationResolver { _, _ -> NotificationDestination.Unavailable() }
        val viewModel = viewModel(repository, resolver = resolver)
        val nextEvent = async { viewModel.events.first() }

        viewModel.openNotification(notice)

        assertEquals(NotificationsUiEvent.ShowMessage(UNAVAILABLE_DESTINATION_MESSAGE), nextEvent.await())
        assertEquals(0, repository.markReadCalls)
    }

    private fun viewModel(
        repository: NotificationsRepository,
        access: MutableStateFlow<LocalAccess> = MutableStateFlow(available(USER_A)),
        resolver: NotificationDestinationResolver = mockk(relaxed = true),
    ): NotificationsViewModel {
        localAccess = access
        val session = mockk<SessionCoordinator>()
        every { session.localAccess } returns localAccess
        every { session.remoteSession } returns MutableStateFlow(RemoteSession.Absent)
        return NotificationsViewModel(repository, session, resolver)
    }

    private class FakeRepository(notices: Map<String, List<AppNotification>>) : NotificationsRepository {
        private val flows = notices.mapValues { MutableStateFlow(it.value) }
        var markReadCalls: Int = 0
            private set
        override fun observeActive(userId: String): Flow<List<AppNotification>> = flows[userId] ?: MutableStateFlow(emptyList())
        override fun observeUnreadCount(userId: String): Flow<Int> = observeActive(userId).map { rows -> rows.count { !it.isRead && it.deletedAt == null } }
        override suspend fun refresh(userId: String) = Result.success(Unit)
        override suspend fun markRead(userId: String, notificationId: String): Result<Unit> {
            markReadCalls += 1
            return Result.success(Unit)
        }
        override suspend fun markAllRead(userId: String) = Result.success(Unit)
        override suspend fun dismiss(userId: String, notificationId: String) = Result.success(Unit)
        override suspend fun syncPending(userId: String) = Result.success(Unit)
    }

    private fun available(userId: String) = LocalAccess.Available(userId, RemoteSession.Absent)

    private fun notice(id: String, userId: String, type: String, createdAt: Instant) = AppNotification(
        id = id,
        userId = userId,
        title = id,
        body = "Aviso $id",
        notificationType = type,
        referenceEntityType = null,
        referenceEntityId = null,
        isRead = false,
        createdAt = createdAt,
        deletedAt = null,
    )

    private companion object {
        const val USER_A = "61000000-0000-4000-8000-000000000001"
        const val USER_B = "61000000-0000-4000-8000-000000000002"
        val NOW = Instant.parse("2026-09-26T10:00:00Z")
    }
}
