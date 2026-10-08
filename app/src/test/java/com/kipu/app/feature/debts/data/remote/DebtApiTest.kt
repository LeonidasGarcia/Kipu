package com.kipu.app.feature.debts.data.remote

import com.kipu.app.core.network.AuthenticatedSession
import com.kipu.app.core.network.AuthenticatedSessionProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebtApiTest {
    @Test
    fun `opening command posts owner scoped payload to the versioned debt RPC`() = runTest {
        val payload = buildJsonObject {
            put("contract_version", 1)
            put("operation_id", "debt-operation")
            put("request_hash", "a".repeat(64))
            put("debt_id", "debt-id")
            put("obligation_type", "PAYABLE")
        }
        val client = client(MockEngine { request ->
            assertEquals("/rest/v1/rpc/open_debt_v1", request.url.encodedPath)
            assertEquals("Bearer session-token", request.headers[HttpHeaders.Authorization])
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(payload, body.getValue("p_payload").jsonObject)
            respond(
                """{"status":"APPLIED","debt_id":"debt-id","revision":1,"remaining_minor":5000}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })

        try {
            val response = DebtApi(client, Sessions()).execute("OPEN_DEBT", payload)
            assertTrue(response is DebtApiResponse.Success)
            val result = (response as DebtApiResponse.Success).data
            assertEquals("APPLIED", result.status)
            assertEquals("debt-id", result.debtId)
            assertEquals(1L, result.revision)
            assertEquals(5_000L, result.remainingMinor)
        } finally {
            client.close()
        }
    }

    @Test
    fun `missing session returns unauthorized without network access`() = runTest {
        var requests = 0
        val client = client(MockEngine { requests++; error("No request expected") })
        val sessions = object : AuthenticatedSessionProvider {
            override suspend fun currentSession(): AuthenticatedSession? = null
            override suspend fun refreshSession(): AuthenticatedSession? = null
        }
        try {
            val response = DebtApi(client, sessions).execute("OPEN_DEBT", buildJsonObject {})
            assertEquals(401, (response as DebtApiResponse.Error).statusCode)
            assertEquals(0, requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun `unknown command never reaches the network`() = runTest {
        var requests = 0
        val client = client(MockEngine { requests++; error("No request expected") })
        try {
            val response = DebtApi(client, Sessions()).execute("UNKNOWN", buildJsonObject {})
            assertEquals(400, (response as DebtApiResponse.Error).statusCode)
            assertEquals(0, requests)
        } finally {
            client.close()
        }
    }

    private fun client(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
        defaultRequest { url("https://kipu.test/") }
    }

    private class Sessions : AuthenticatedSessionProvider {
        private val session = AuthenticatedSession(UUID.randomUUID(), "session-token")
        override suspend fun currentSession(): AuthenticatedSession = session
        override suspend fun refreshSession(): AuthenticatedSession = session
    }
}
