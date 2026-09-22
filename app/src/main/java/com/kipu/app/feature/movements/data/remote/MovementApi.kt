package com.kipu.app.feature.movements.data.remote

import com.kipu.app.core.network.AuthenticatedSessionProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import javax.inject.Inject
import javax.inject.Singleton

sealed interface MovementApiResponse<out T> {
    data class Success<T>(val data: T) : MovementApiResponse<T>
    data class Error(val statusCode: Int, val message: String?) : MovementApiResponse<Nothing>
    data class NetworkFailure(val exception: Throwable) : MovementApiResponse<Nothing>
}

@Singleton
class MovementApi @Inject constructor(
    private val httpClient: HttpClient,
    private val sessionProvider: AuthenticatedSessionProvider,
) {
    private suspend fun getAuthHeader(): String? {
        val session = sessionProvider.currentSession() ?: return null
        return "Bearer ${session.accessToken}"
    }

    suspend fun registerTransaction(
        request: RegisterTransactionRequestDto,
    ): MovementApiResponse<RegisterTransactionResponseDto> {
        return try {
            val auth = getAuthHeader() ?: return MovementApiResponse.Error(401, "No active session")
            val response = httpClient.post("rest/v1/rpc/register_transaction_v1") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, auth)
                setBody(mapOf("p_command" to request))
            }
            when (response.status) {
                HttpStatusCode.OK -> MovementApiResponse.Success(response.body<RegisterTransactionResponseDto>())
                else -> MovementApiResponse.Error(response.status.value, response.body<String>())
            }
        } catch (e: Exception) {
            MovementApiResponse.NetworkFailure(e)
        }
    }
}
