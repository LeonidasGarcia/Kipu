package com.kipu.app.feature.debts.data.remote

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
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

sealed interface DebtApiResponse<out T> {
    data class Success<T>(val data: T) : DebtApiResponse<T>
    data class Error(val statusCode: Int, val message: String?) : DebtApiResponse<Nothing>
    data class NetworkFailure(val exception: Throwable) : DebtApiResponse<Nothing>
}

@Singleton
class DebtApi @Inject constructor(
    private val httpClient: HttpClient,
    private val sessionProvider: AuthenticatedSessionProvider,
) {
    suspend fun execute(commandType: String, payload: JsonObject): DebtApiResponse<DebtCommandRemoteResult> {
        val rpc = RPC_BY_COMMAND[commandType]
            ?: return DebtApiResponse.Error(400, "Unsupported debt command")
        return try {
            val session = sessionProvider.currentSession()
                ?: return DebtApiResponse.Error(401, "No active session")
            val response = httpClient.post("rest/v1/rpc/$rpc") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
                setBody(DebtRpcRequestDto(payload))
            }
            when (response.status) {
                HttpStatusCode.OK -> DebtApiResponse.Success(response.body<DebtCommandRemoteResult>())
                else -> DebtApiResponse.Error(response.status.value, response.body<String>())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DebtApiResponse.NetworkFailure(e)
        }
    }

    private companion object {
        val RPC_BY_COMMAND = mapOf(
            "OPEN_DEBT" to "open_debt_v1",
            "EDIT_DEBT_DETAILS" to "edit_debt_details_v1",
            "DELETE_DEBT_IF_UNREFERENCED" to "delete_debt_if_unreferenced_v1",
            "SETTLE_DEBT" to "settle_debt_v1",
            "SET_DEBT_SCHEDULE" to "set_debt_schedule_v1",
            "CLOSE_DEBT" to "close_debt_v1",
        )
    }
}
