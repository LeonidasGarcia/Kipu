package com.kipu.app.feature.movements.data.remote

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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementApiTest {
    @Test
    fun `persisted v1 and new v2 use their own RPC without altering identity or hash`() = runTest {
        for (version in listOf(1, 2)) {
            val client = client(MockEngine { request ->
                assertEquals("/rest/v1/rpc/register_transaction_v$version", request.url.encodedPath)
                val command = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    .getValue("p_command").jsonObject
                assertEquals("original-key", command.getValue("idempotency_key").jsonPrimitive.content)
                assertEquals("original-hash", command.getValue("request_hash").jsonPrimitive.content)
                assertEquals("old note;with:delimiters", command.getValue("transaction").jsonObject
                    .getValue("note").jsonPrimitive.content)
                respond("""{"status":"APPLIED"}""", HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"))
            })
            try {
                assertTrue(MovementApi(client, Sessions()).registerTransaction(command(version)) is MovementApiResponse.Success)
            } finally { client.close() }
        }
    }

    @Test
    fun `unsupported contract does not invoke HTTP`() = runTest {
        var calls = 0
        val client = client(MockEngine { calls++; error("Unexpected HTTP request") })
        try {
            val response = MovementApi(client, Sessions()).registerTransaction(command(3))
            assertEquals(400, (response as MovementApiResponse.Error).statusCode)
            assertEquals(0, calls)
        } finally { client.close() }
    }

    @Test
    fun `cancellation from session is propagated`() = runTest {
        val client = client(MockEngine { error("Unexpected HTTP request") })
        val sessions = object : AuthenticatedSessionProvider {
            override suspend fun currentSession(): AuthenticatedSession? = throw CancellationException("cancelled")
            override suspend fun refreshSession(): AuthenticatedSession? = null
        }
        try {
            var cancelled = false
            try { MovementApi(client, sessions).registerTransaction(command(1)) }
            catch (_: CancellationException) { cancelled = true }
            assertTrue(cancelled)
        } finally { client.close() }
    }

    @Test
    fun `revise transaction posts to revise_transaction_v1 and parses result`() = runTest {
        val client = client(MockEngine { request ->
            assertEquals("/rest/v1/rpc/revise_transaction_v1", request.url.encodedPath)
            val command = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                .getValue("p_command").jsonObject
            assertEquals("cmd-revise", command.getValue("idempotency_key").jsonPrimitive.content)
            respond("""{"status":"APPLIED","result":"REVISED","resulting_revision":2}""", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val req = ReviseOrVoidTransactionRequestDto(
                contractVersion = 1,
                commandType = "REVISE_TRANSACTION",
                idempotencyKey = "cmd-revise",
                transactionId = "tx-1",
                expectedRevision = 1L,
                requestHash = "hash-1",
                revisedPayload = RevisedMovementPayloadDto(
                    type = "EXPENSE",
                    amountMinor = 1500L,
                    currencyCode = "PEN",
                    sourceAccountId = "acc-1",
                    occurredAt = "2026-10-02T12:00:00Z",
                ),
            )
            val resp = MovementApi(client, Sessions()).reviseOrVoidTransaction(req)
            assertTrue(resp is MovementApiResponse.Success)
            assertEquals("APPLIED", (resp as MovementApiResponse.Success).data.status)
            assertEquals("REVISED", resp.data.result)
            assertEquals(2L, resp.data.resultingRevision)
        } finally { client.close() }
    }

    @Test
    fun `void transaction posts to void_transaction_v1 and parses result`() = runTest {
        val client = client(MockEngine { request ->
            assertEquals("/rest/v1/rpc/void_transaction_v1", request.url.encodedPath)
            val command = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                .getValue("p_command").jsonObject
            assertEquals("cmd-void", command.getValue("idempotency_key").jsonPrimitive.content)
            respond("""{"status":"APPLIED","result":"VOIDED","resulting_revision":3}""", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val req = ReviseOrVoidTransactionRequestDto(
                contractVersion = 1,
                commandType = "VOID_TRANSACTION",
                idempotencyKey = "cmd-void",
                transactionId = "tx-1",
                expectedRevision = 2L,
                requestHash = "hash-void",
                revisedPayload = null,
            )
            val resp = MovementApi(client, Sessions()).reviseOrVoidTransaction(req)
            assertTrue(resp is MovementApiResponse.Success)
            assertEquals("APPLIED", (resp as MovementApiResponse.Success).data.status)
            assertEquals("VOIDED", resp.data.result)
            assertEquals(3L, resp.data.resultingRevision)
        } finally { client.close() }
    }

    private fun command(version: Int) = RegisterTransactionRequestDto(
        contractVersion = version, idempotencyKey = "original-key", requestHash = "original-hash",
        transaction = TransactionDto(id = "transaction-id", type = "INCOME", amountMinor = 100L,
            currencyCode = "PEN", sourceAccountId = "account-id", occurredAt = "2026-10-02T00:00:00Z",
            note = "old note;with:delimiters"),
    )

    private fun client(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
        defaultRequest { url("https://kipu.test/") }
    }

    private class Sessions : AuthenticatedSessionProvider {
        private val session = AuthenticatedSession(UUID.randomUUID(), "test-token")
        override suspend fun currentSession(): AuthenticatedSession = session
        override suspend fun refreshSession(): AuthenticatedSession = session
    }
}
