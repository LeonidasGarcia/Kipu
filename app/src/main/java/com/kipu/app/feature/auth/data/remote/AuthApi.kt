package com.kipu.app.feature.auth.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ApiResponse<out T> {
    data class Success<T>(val data: T) : ApiResponse<T>
    data class Error(val statusCode: Int, val problem: ProblemDto?, val retryAfter: Int? = null) : ApiResponse<Nothing>
    data class NetworkFailure(val exception: Throwable) : ApiResponse<Nothing>
}

@Singleton
class AuthApi @Inject constructor(
    private val httpClient: HttpClient,
) {
    suspend fun register(request: RegisterRequestDto): ApiResponse<RegisterResponseDto> {
        return try {
            val response = httpClient.post("functions/v1/auth-access/register") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            when (response.status) {
                HttpStatusCode.Created -> ApiResponse.Success(response.body<RegisterResponseDto>())
                else -> {
                    val problem = try { response.body<ProblemDto>() } catch (_: Exception) { null }
                    val retryAfter = response.headers["Retry-After"]?.toIntOrNull()
                    ApiResponse.Error(response.status.value, problem, retryAfter)
                }
            }
        } catch (e: Exception) {
            ApiResponse.NetworkFailure(e)
        }
    }

    suspend fun login(request: LoginRequestDto): ApiResponse<SessionEnvelopeDto> {
        return try {
            val response = httpClient.post("functions/v1/auth-access/login") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            when (response.status) {
                HttpStatusCode.OK -> ApiResponse.Success(response.body<SessionEnvelopeDto>())
                else -> {
                    val problem = try { response.body<ProblemDto>() } catch (_: Exception) { null }
                    val retryAfter = response.headers["Retry-After"]?.toIntOrNull()
                    ApiResponse.Error(response.status.value, problem, retryAfter)
                }
            }
        } catch (e: Exception) {
            ApiResponse.NetworkFailure(e)
        }
    }

    suspend fun recovery(request: RecoveryRequestDto): ApiResponse<Unit> {
        return try {
            val response = httpClient.post("functions/v1/auth-access/recovery") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            when (response.status) {
                HttpStatusCode.Accepted, HttpStatusCode.OK -> ApiResponse.Success(Unit)
                else -> {
                    val problem = try { response.body<ProblemDto>() } catch (_: Exception) { null }
                    val retryAfter = response.headers["Retry-After"]?.toIntOrNull()
                    ApiResponse.Error(response.status.value, problem, retryAfter)
                }
            }
        } catch (e: Exception) {
            ApiResponse.NetworkFailure(e)
        }
    }
}
