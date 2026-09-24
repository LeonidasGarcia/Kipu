package com.kipu.app.feature.plans.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.network.AuthenticatedSession
import com.kipu.app.core.network.AuthenticatedSessionProvider
import com.kipu.app.feature.plans.data.local.PlanQuotaSelectionDao
import com.kipu.app.feature.plans.data.remote.PlanSelectionApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import java.net.SocketTimeoutException
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncPlanQuotaWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val userId = UUID.fromString("10000000-0000-0000-0000-000000000001")
    private val featureKey = "CUSTOM_CATEGORIES"
    private lateinit var database: KipuDatabase
    private lateinit var dao: PlanQuotaSelectionDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.planQuotaSelectionDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun wrongOwnerSessionDefersWithoutSendingAnySelection() = runTest {
        seed()
        var requests = 0
        val engine = MockEngine { requests++; error("network must not be called") }

        val result = worker(engine, SessionProvider(AuthenticatedSession(UUID.randomUUID(), "other-token"))).doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
        assertEquals(0, requests)
    }

    @Test
    fun missingSessionDefersWithoutSendingAnySelection() = runTest {
        seed()
        var requests = 0
        val engine = MockEngine { requests++; error("network must not be called") }

        val result = worker(engine, SessionProvider(null)).doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
        assertEquals(0, requests)
    }

    @Test
    fun sessionOwnerIsCheckedAgainBeforeEverySelectionSend() = runTest {
        seed()
        dao.replaceSelection(
            userId = userId.toString(),
            featureKey = "DEBTS",
            resourceType = "DEBT",
            resourceIds = listOf("local-debt"),
            now = 150L,
        )
        var requests = 0
        val engine = MockEngine {
            requests++
            respond(successBody("APPLIED", "1"), HttpStatusCode.OK, jsonHeaders())
        }
        val sessions = SequenceSessionProvider(
            listOf(
                AuthenticatedSession(userId, "first-token"),
                AuthenticatedSession(userId, "first-token"),
                AuthenticatedSession(UUID.randomUUID(), "other-token"),
            ),
        )

        val result = worker(engine, sessions).doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
        assertEquals(1, requests)
        assertEquals(3, sessions.calls)
    }

    @Test
    fun transientApiFailureRetriesTheWork() = runTest {
        seed()
        val engine = MockEngine {
            respond(
                """{"code":"UNAVAILABLE","retryable":true}""",
                HttpStatusCode.ServiceUnavailable,
                jsonHeaders(),
            )
        }

        assertTrue(worker(engine).doWork() is ListenableWorker.Result.Retry)
    }

    @Test
    fun unauthenticatedApiResponseDefersTheWork() = runTest {
        seed()
        val engine = MockEngine {
            respond(
                """{"code":"UNAUTHENTICATED","retryable":true}""",
                HttpStatusCode.Unauthorized,
                jsonHeaders(),
            )
        }

        assertTrue(worker(engine).doWork() is ListenableWorker.Result.Retry)
    }

    @Test
    fun ambiguousRetryUsesTheSameCanonicalPayloadAndOperationId() = runTest {
        seed()
        val bodies = mutableListOf<String>()
        val engine = MockEngine { request ->
            bodies += (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            if (bodies.size == 1) {
                throw SocketTimeoutException("ambiguous post-commit timeout")
            }
            respond(successBody("APPLIED", "1"), HttpStatusCode.OK, jsonHeaders())
        }

        assertTrue(worker(engine).doWork() is ListenableWorker.Result.Retry)
        assertTrue(worker(engine).doWork() is ListenableWorker.Result.Success)

        assertEquals(2, bodies.size)
        assertEquals(bodies.first(), bodies.last())
        assertTrue(bodies.first().contains("\"operation_id\""))
        assertTrue(bodies.first().contains("\"resource_id\":\"local-1\""))
        assertTrue(bodies.first().contains("\"resource_id\":\"local-2\""))
    }

    @Test
    fun staleAndConflictRebaseTheLatestLocalItemsAndRetry() = runTest {
        listOf("STALE", "CONFLICT").forEach { remoteResult ->
            database.clearAllTables()
            seed()
            val engine = MockEngine {
                // Simulate a local edit racing with the in-flight request.
                dao.replaceSelection(
                    userId = userId.toString(),
                    featureKey = featureKey,
                    resourceType = "CATEGORY_ROOT",
                    resourceIds = listOf("latest-local"),
                    now = 200L,
                    resourceTypesById = mapOf("latest-local" to "CATEGORY_CHILD"),
                )
                respond(
                    """{"result":"$remoteResult","accepted_revision":"5","current_items":[{"resource_id":"remote-item","resource_type":"CATEGORY_ROOT"}]}""",
                    HttpStatusCode.OK,
                    jsonHeaders(),
                )
            }

            assertTrue(worker(engine).doWork() is ListenableWorker.Result.Retry)

            val snapshot = dao.getSelectionSnapshot(userId.toString(), featureKey)!!
            assertEquals(6L, snapshot.selection.revision)
            assertEquals(listOf("latest-local"), snapshot.items.map { it.resourceId })
            assertEquals("CATEGORY_CHILD", snapshot.items.single().resourceType)
        }
    }

    @Test
    fun appliedAndDuplicateResponsesAreAcknowledged() = runTest {
        listOf("APPLIED", "DUPLICATE").forEach { remoteResult ->
            database.clearAllTables()
            seed()
            val engine = MockEngine {
                respond(successBody(remoteResult, "1"), HttpStatusCode.OK, jsonHeaders())
            }

            assertTrue(worker(engine).doWork() is ListenableWorker.Result.Success)
            assertEquals(1L, dao.getSelection(userId.toString(), featureKey)?.revision)
        }
    }

    @Test
    fun terminalApiFailuresBecomeSafeTerminalWorkFailuresWithoutLeakingServerDetails() = runTest {
        listOf("INVALID_REQUEST", "UNSUPPORTED_VERSION", "FORBIDDEN", "sensitive-detail").forEach { code ->
            database.clearAllTables()
            seed()
            val engine = MockEngine {
                respond(
                    """{"code":"$code","retryable":false}""",
                    HttpStatusCode.BadRequest,
                    jsonHeaders(),
                )
            }

            val result = worker(engine).doWork()

            assertTrue("$code must not be reported as success or retried", result is ListenableWorker.Result.Failure)
            val safeCode = code.takeIf { it in setOf("INVALID_REQUEST", "UNSUPPORTED_VERSION", "FORBIDDEN") }
                ?: "UNKNOWN"
            assertEquals(
                safeCode,
                (result as ListenableWorker.Result.Failure).outputData.getString(SyncPlanQuotaWorker.KEY_ERROR_CODE),
            )
        }
    }

    private suspend fun seed() {
        dao.replaceSelection(
            userId = userId.toString(),
            featureKey = featureKey,
            resourceType = "CATEGORY_ROOT",
            resourceIds = listOf("local-2", "local-1"),
            now = 100L,
            resourceTypesById = mapOf("local-1" to "CATEGORY_ROOT", "local-2" to "CATEGORY_CHILD"),
        )
    }

    private fun worker(
        engine: MockEngine,
        sessions: AuthenticatedSessionProvider = SessionProvider(AuthenticatedSession(userId, "test-token")),
    ): SyncPlanQuotaWorker {
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = false }) }
        }
        val api = PlanSelectionApi(client, "https://example.test/functions/v1")
        return TestListenableWorkerBuilder<SyncPlanQuotaWorker>(context)
            .setInputData(androidx.work.workDataOf(SyncPlanQuotaWorker.KEY_USER_ID to userId.toString()))
            .setWorkerFactory(SyncPlanQuotaWorker.factory(dao, api, sessions))
            .build()
    }

    private fun successBody(result: String, acceptedRevision: String) =
        """{"result":"$result","accepted_revision":"$acceptedRevision","current_items":[]}"""

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private class SessionProvider(private val session: AuthenticatedSession?) : AuthenticatedSessionProvider {
        override suspend fun currentSession(): AuthenticatedSession? = session
        override suspend fun refreshSession(): AuthenticatedSession? = session
    }

    private class SequenceSessionProvider(private val sessions: List<AuthenticatedSession?>) : AuthenticatedSessionProvider {
        var calls = 0
            private set

        override suspend fun currentSession(): AuthenticatedSession? = sessions.getOrNull(calls++)
        override suspend fun refreshSession(): AuthenticatedSession? = currentSession()
    }
}
