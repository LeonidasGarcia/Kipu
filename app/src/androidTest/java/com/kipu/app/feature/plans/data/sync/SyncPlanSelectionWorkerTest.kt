package com.kipu.app.feature.plans.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.network.AuthenticatedSession
import com.kipu.app.core.network.AuthenticatedSessionProvider
import com.kipu.app.feature.plans.data.local.OutboxStatus
import com.kipu.app.feature.plans.data.local.PlanPreferencesDao
import com.kipu.app.feature.plans.data.remote.PlanSelectionApi
import com.kipu.app.feature.plans.domain.model.PlanSelection
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncPlanSelectionWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val userId = UUID.fromString("10000000-0000-0000-0000-000000000001")
    private val operationId = UUID.fromString("30000000-0000-0000-0000-000000000003")
    private val now = Instant.parse("2026-09-14T15:03:12.123456Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private lateinit var database: KipuDatabase
    private lateinit var dao: PlanPreferencesDao

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.planPreferencesDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun oneShotWorkRequiresNetworkAndUsesOneUniqueQueuePerUser() {
        val request = PlanSyncScheduler.oneShotRequest(userId)
        assertEquals(androidx.work.NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(PlanSyncScheduler.uniqueWorkName(userId), "plan-selection-sync-$userId")

        val scheduler = PlanSyncScheduler(WorkManager.getInstance(context))
        scheduler.enqueue(userId)
        scheduler.enqueue(userId)
        val work = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(PlanSyncScheduler.uniqueWorkName(userId)).get()
        assertEquals(1, work.count { !it.state.isFinished })
    }

    @Test
    fun appliedDuplicateAndStaleCompleteWhileConflictRemainsDiagnosable() = runTest {
        val expectations = listOf(
            "APPLIED" to OutboxStatus.COMPLETED,
            "DUPLICATE" to OutboxStatus.COMPLETED,
            "STALE" to OutboxStatus.COMPLETED,
            "CONFLICT" to OutboxStatus.CONFLICT,
        )

        expectations.forEachIndexed { index, (result, expected) ->
            val id = UUID(operationId.mostSignificantBits, operationId.leastSignificantBits + index)
            dao.confirmSelection(userId, PlanSelection.TRIAL_INTENT, now.plusSeconds(index.toLong()), id)
            val worker = worker(responseEngine(successBody(id, result, index + 1L)))

            assertTrue(worker.doWork() is ListenableWorker.Result.Success)
            assertEquals(expected, dao.findOutbox(id)?.status)
            assertEquals(index + 1L, dao.findSyncState(userId)?.acceptedRevision)
        }
    }

    @Test
    fun oneRunDrainsEveryEligibleOperationForTheUser() = runTest {
        val secondId = UUID.fromString("30000000-0000-0000-0000-000000000004")
        dao.confirmSelection(userId, PlanSelection.TRIAL_INTENT, now, operationId)
        dao.confirmSelection(userId, PlanSelection.FREE, now.plusSeconds(1), secondId)
        val ids = listOf(operationId, secondId)
        var requestIndex = 0
        val engine = MockEngine {
            val id = ids[requestIndex]
            requestIndex += 1
            respond(
                successBody(id, "APPLIED", requestIndex.toLong()),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        assertTrue(worker(engine).doWork() is ListenableWorker.Result.Success)
        assertEquals(2, requestIndex)
        assertEquals(OutboxStatus.COMPLETED, dao.findOutbox(operationId)?.status)
        assertEquals(OutboxStatus.COMPLETED, dao.findOutbox(secondId)?.status)
    }

    @Test
    fun missingOrDifferentUserSessionWaitsForAuthenticationWithoutCallingNetwork() = runTest {
        seed()
        var requests = 0
        val engine = MockEngine { requests++; error("network must not be called") }

        assertTrue(worker(engine, session = null).doWork() is ListenableWorker.Result.Success)
        assertEquals(OutboxStatus.WAITING_FOR_AUTH, dao.findOutbox(operationId)?.status)
        assertEquals(0, requests)

        database.clearAllTables()
        seed()
        val otherUser = AuthenticatedSession(UUID.randomUUID(), "other-token")
        assertTrue(worker(engine, session = otherUser).doWork() is ListenableWorker.Result.Success)
        assertEquals(OutboxStatus.WAITING_FOR_AUTH, dao.findOutbox(operationId)?.status)
        assertEquals(0, requests)
    }

    @Test
    fun validationAndForbiddenErrorsAreTerminalAndDoNotLoop() = runTest {
        listOf(HttpStatusCode.BadRequest to "INVALID_REQUEST", HttpStatusCode.BadRequest to "UNSUPPORTED_VERSION", HttpStatusCode.Forbidden to "FORBIDDEN")
            .forEach { (status, code) ->
                database.clearAllTables()
                seed()
                val worker = worker(errorEngine(status, code, retryable = false))
                assertTrue(worker.doWork() is ListenableWorker.Result.Success)
                assertEquals(OutboxStatus.TERMINAL_ERROR, dao.findOutbox(operationId)?.status)
                assertEquals(code, dao.findOutbox(operationId)?.lastErrorCode)
            }
    }

    @Test
    fun rateLimitUnavailableAndAmbiguousTimeoutRetryTheIdenticalPayloadWithBackoff() = runTest {
        seed()
        val bodies = mutableListOf<String>()
        val engine = MockEngine { request ->
            bodies += (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            if (bodies.size == 1) {
                respond(
                    """{"code":"UNAVAILABLE","retryable":true,"retry_after_seconds":60}""",
                    HttpStatusCode.TooManyRequests,
                    headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.RetryAfter to listOf("60")),
                )
            } else {
                throw java.net.SocketTimeoutException("ambiguous post-commit timeout")
            }
        }

        assertTrue(worker(engine).doWork() is ListenableWorker.Result.Retry)
        val afterRateLimit = dao.findOutbox(operationId)!!
        assertEquals(OutboxStatus.PENDING, afterRateLimit.status)
        assertTrue(afterRateLimit.nextAttemptAt!! >= now.plusSeconds(60))

        dao.makePendingNow(operationId, now)
        assertTrue(worker(engine).doWork() is ListenableWorker.Result.Retry)
        val afterTimeout = dao.findOutbox(operationId)!!
        assertEquals(OutboxStatus.PENDING, afterTimeout.status)
        assertTrue(afterTimeout.attemptCount >= 2)
        assertEquals(bodies.first(), bodies.last())
        assertTrue(bodies.singlePayloadContainsStableIdentity(operationId, 1L))
    }

    @Test
    fun expiredLeaseIsRecoveredAndSentOnTheNextRun() = runTest {
        seed()
        dao.acquireNextPending(userId, now.minusSeconds(120), now.minusSeconds(60))
        assertEquals(OutboxStatus.IN_FLIGHT, dao.findOutbox(operationId)?.status)

        val result = worker(responseEngine(successBody(operationId, "APPLIED", 1))).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(OutboxStatus.COMPLETED, dao.findOutbox(operationId)?.status)
        assertNull(dao.findOutbox(operationId)?.leaseUntil)
    }

    private suspend fun seed() {
        dao.confirmSelection(userId, PlanSelection.TRIAL_INTENT, now, operationId)
    }

    private fun worker(
        engine: MockEngine,
        session: AuthenticatedSession? = AuthenticatedSession(userId, "test-token"),
    ): SyncPlanSelectionWorker {
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = false }) }
        }
        val api = PlanSelectionApi(client, "https://example.test/functions/v1")
        return TestListenableWorkerBuilder<SyncPlanSelectionWorker>(context)
            .setInputData(workDataOf(PlanSyncScheduler.USER_ID to userId.toString()))
            .setWorkerFactory(SyncPlanSelectionWorker.factory(dao, api, SessionProvider(session), clock))
            .build()
    }

    private fun responseEngine(body: String) = MockEngine {
        assertEquals("Bearer test-token", it.headers[HttpHeaders.Authorization])
        assertEquals("/functions/v1/plans/selection", it.url.encodedPath)
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    private fun errorEngine(status: HttpStatusCode, code: String, retryable: Boolean) = MockEngine {
        respond(
            """{"code":"$code","retryable":$retryable}""",
            status,
            headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

    private fun successBody(id: UUID, result: String, revision: Long) = """
        {
          "contract_version":1,
          "operation_id":"$id",
          "result":"$result",
          "accepted_revision":"$revision",
          "current_preference":{
            "selection":"TRIAL_INTENT",
            "selected_at":"2026-09-14T15:03:12.123456Z",
            "updated_at":"2026-09-14T15:03:13.123456Z"
          },
          "free_limits":{"policy_version":1,"instruments":4,"custom_categories":5,"debts":2,"goals":2,"budgets":2},
          "server_time":"2026-09-14T15:03:13.123456Z"
        }
    """.trimIndent()

    private fun List<String>.singlePayloadContainsStableIdentity(id: UUID, revision: Long): Boolean =
        all { body ->
            body.contains("\"operation_id\":\"$id\"") &&
                body.contains("\"selection_revision\":\"$revision\"") &&
                body.contains("\"selected_at\":\"2026-09-14T15:03:12.123456Z\"")
        }

    private class SessionProvider(private val session: AuthenticatedSession?) : AuthenticatedSessionProvider {
        override suspend fun currentSession(): AuthenticatedSession? = session
        override suspend fun refreshSession(): AuthenticatedSession? = session
    }
}
