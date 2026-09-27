package com.kipu.app.feature.notifications.data.remote

import com.kipu.app.core.network.AuthenticatedSession
import com.kipu.app.core.network.AuthenticatedSessionProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationsApiTest {
    @Test
    fun `fetch scopes query to authenticated user and includes tombstones`() = runTest {
        val client = client { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("eq.$USER_ID", request.url.parameters["user_id"])
            assertEquals("created_at.desc", request.url.parameters["order"])
            assertTrue(request.url.parameters["select"].orEmpty().contains("deleted_at"))
            assertTrue(request.url.parameters["select"].orEmpty().contains("event_payload"))
            assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
            respond(
                content = """[{"id":"$NOTICE_ID","user_id":"$USER_ID","title":"Vence","body":"Próximo vencimiento","notification_type":"BILLING_DUE","reference_entity_type":"DEBT","reference_entity_id":null,"is_read":false,"created_at":"2026-09-26T10:00:00Z","deleted_at":null,"event_payload":{"due_date":"2026-09-30","due_day":30},"future_field":"ignored"}]""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val response = NotificationsApi(client, Sessions(AuthenticatedSession(UUID.fromString(USER_ID), "test-token")))
            .fetchForUser(USER_ID)

        assertTrue(response is NotificationsApiResponse.Success)
        val notice = (response as NotificationsApiResponse.Success).data.single()
        assertEquals("BILLING_DUE", notice.notificationType)
        assertEquals("Próximo vencimiento", notice.body)
        assertEquals("2026-09-30", notice.eventPayload?.get("due_date")?.toString()?.trim('"'))
    }

    @Test
    fun `patch read sends only allowed desired state and both owner filters`() = runTest {
        var captured: HttpRequestData? = null
        val client = client { request ->
            captured = request
            respond(content = "", status = HttpStatusCode.NoContent)
        }

        val response = NotificationsApi(client, Sessions(AuthenticatedSession(UUID.fromString(USER_ID), "token")))
            .patchForUser(USER_ID, NOTICE_ID, NotificationMutationDto(isRead = true))

        assertTrue(response is NotificationsApiResponse.Success)
        val request = requireNotNull(captured)
        assertEquals(HttpMethod.Patch, request.method)
        assertEquals("eq.$NOTICE_ID", request.url.parameters["id"])
        assertEquals("eq.$USER_ID", request.url.parameters["user_id"])
        assertEquals("{\"is_read\":true}", (request.body as TextContent).text)
    }

    @Test
    fun `patch dismissal sends only soft-delete field`() = runTest {
        var captured: HttpRequestData? = null
        val client = client { request ->
            captured = request
            respond(content = "", status = HttpStatusCode.NoContent)
        }
        val timestamp = "2026-09-26T10:00:00Z"

        val response = NotificationsApi(client, Sessions(AuthenticatedSession(UUID.fromString(USER_ID), "token")))
            .patchForUser(USER_ID, NOTICE_ID, NotificationMutationDto(deletedAt = timestamp))

        assertTrue(response is NotificationsApiResponse.Success)
        assertEquals("{\"deleted_at\":\"$timestamp\"}", (requireNotNull(captured).body as TextContent).text)
    }

    @Test
    fun `missing or mismatched session cannot issue request`() = runTest {
        val client = client { error("unauthenticated request must not be sent") }
        val missing = NotificationsApi(client, Sessions(null)).fetchForUser(USER_ID)
        val mismatched = NotificationsApi(
            client,
            Sessions(AuthenticatedSession(UUID.fromString("61000000-0000-4000-8000-000000000002"), "other-token")),
        ).fetchForUser(USER_ID)

        assertEquals(401, (missing as NotificationsApiResponse.Error).statusCode)
        assertEquals(403, (mismatched as NotificationsApiResponse.Error).statusCode)
    }

    @Test
    fun `http errors preserve status for retry policy`() = runTest {
        val client = client { respond("denied", HttpStatusCode.Forbidden) }
        val response = NotificationsApi(client, Sessions(AuthenticatedSession(UUID.fromString(USER_ID), "token")))
            .fetchForUser(USER_ID)
        assertEquals(403, (response as NotificationsApiResponse.Error).statusCode)
    }

    private fun client(handler: MockRequestHandler): HttpClient =
        HttpClient(MockEngine(handler)) {
            expectSuccess = false
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) }
            defaultRequest { url("https://kipu.test/") }
        }

    private class Sessions(private val value: AuthenticatedSession?) : AuthenticatedSessionProvider {
        override suspend fun currentSession(): AuthenticatedSession? = value
        override suspend fun refreshSession(): AuthenticatedSession? = value
    }

    private companion object {
        const val USER_ID = "61000000-0000-4000-8000-000000000001"
        const val NOTICE_ID = "62000000-0000-4000-8000-000000000001"
    }
}
