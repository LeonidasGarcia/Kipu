package com.kipu.app.feature.plans.data.remote

import com.kipu.app.feature.plans.data.local.SyncOutboxEntity
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val code: String, val retryable: Boolean, val retryAfterSeconds: Long?) : ApiResult<Nothing>
}

class PlanSelectionApi(private val client: HttpClient, private val baseUrl: String) {
    suspend fun eligibility(token: String): ApiResult<TrialEligibilityResponseDto> = request(token, "$baseUrl/plans/eligibility", HttpMethod.Get, null)
    suspend fun select(token: String, operation: SyncOutboxEntity): ApiResult<PlanSelectionResponseDto> = request(token, "$baseUrl/plans/selection", HttpMethod.Post, operation.toRequestDto())
    suspend fun selectQuota(token: String, request: QuotaSelectionRequestDto): ApiResult<QuotaSelectionResponseDto> = request(token, "$baseUrl/plans/quota-selection", HttpMethod.Post, request)
    private suspend inline fun <reified T> request(token: String, url: String, method: HttpMethod, payload: Any?): ApiResult<T> = try {
        val response = client.request(url) {
            this.method = method
            bearerAuth(token)
            accept(ContentType.Application.Json)
            if (payload != null) { contentType(ContentType.Application.Json); setBody(payload) }
        }
        if (response.status.isSuccess()) ApiResult.Success(response.body()) else {
            val error = response.body<ErrorResponseDto>()
            ApiResult.Failure(error.code, error.retryable, error.retryAfterSeconds ?: response.headers[HttpHeaders.RetryAfter]?.toLongOrNull())
        }
    } catch (_: Exception) { ApiResult.Failure("UNAVAILABLE", true, null) }
}
