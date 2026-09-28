package com.kipu.app.feature.accounts.data.remote

import com.kipu.app.core.network.AuthenticatedSession
import com.kipu.app.core.network.AuthenticatedSessionProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialInstrumentsApiTest {

    @Test
    fun `credit catalog does not select optional institution name column`() = runTest {
        var requestedSelect: String? = null
        val client = HttpClient(MockEngine { request ->
            requestedSelect = request.url.parameters["select"]
            respond(
                content = """[{"id":"p1","institution_code":"BCP","product_name":"Visa Clásica","card_network":"VISA"}]""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest { url("https://kipu.test/") }
        }
        val api = FinancialInstrumentsApi(client, Sessions())

        val result = api.fetchCreditProducts()

        assertTrue(result is FinancialApiResponse.Success)
        assertEquals(null, (result as FinancialApiResponse.Success).data.single().institutionName)
        assertTrue(requestedSelect.orEmpty().split(',').none { it == "institution_name" })
    }

    private class Sessions : AuthenticatedSessionProvider {
        private val session = AuthenticatedSession(UUID.randomUUID(), "test-token")
        override suspend fun currentSession(): AuthenticatedSession = session
        override suspend fun refreshSession(): AuthenticatedSession = session
    }
}
