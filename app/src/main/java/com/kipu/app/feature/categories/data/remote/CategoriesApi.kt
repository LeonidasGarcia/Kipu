package com.kipu.app.feature.categories.data.remote

import com.kipu.app.core.network.AuthenticatedSessionProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CategoryApiResponse<out T> {
    data class Success<T>(val data: T) : CategoryApiResponse<T>
    data class Error(val statusCode: Int, val message: String?) : CategoryApiResponse<Nothing>
    data class NetworkFailure(val exception: Throwable) : CategoryApiResponse<Nothing>
}

@Singleton
class CategoriesApi @Inject constructor(
    private val httpClient: HttpClient,
    private val sessionProvider: AuthenticatedSessionProvider,
) {
    private suspend fun getAuthHeader(): String? {
        val session = sessionProvider.currentSession() ?: return null
        return "Bearer ${session.accessToken}"
    }

    suspend fun createCategory(request: CreateCategoryRequestDto): CategoryApiResponse<CategoryCommandResponseDto> {
        return callRpc("create_category_v1", request)
    }

    suspend fun updatePresentation(request: UpdateCategoryPresentationRequestDto): CategoryApiResponse<CategoryCommandResponseDto> {
        return callRpc("update_category_presentation_v1", request)
    }

    suspend fun setCategoryActive(request: SetCategoryActiveRequestDto): CategoryApiResponse<CategoryCommandResponseDto> {
        return callRpc("set_category_active_v1", request)
    }

    suspend fun updateMovementClassification(request: UpdateMovementClassificationRequestDto): CategoryApiResponse<CategoryCommandResponseDto> {
        return callRpc("update_movement_classification_v1", request)
    }

    suspend fun resolveConflict(request: ResolveCategoryConflictRequestDto): CategoryApiResponse<CategoryCommandResponseDto> {
        return callRpc("resolve_category_conflict_v1", request)
    }

    suspend fun fetchMerchantCatalog(sinceVersion: Long = 0L): CategoryApiResponse<List<MerchantCatalogItemDto>> {
        return try {
            val auth = getAuthHeader() ?: return CategoryApiResponse.Error(401, "No active session")
            val response = httpClient.get("rest/v1/merchant_services") {
                header(HttpHeaders.Authorization, auth)
                url {
                    parameters.append("select", "id,name,normalized_name,is_active,version")
                    parameters.append("is_active", "eq.true")
                    if (sinceVersion > 0) {
                        parameters.append("version", "gt.$sinceVersion")
                    }
                }
            }
            CategoryApiResponse.Success(response.body())
        } catch (e: Exception) {
            CategoryApiResponse.NetworkFailure(e)
        }
    }

    private suspend inline fun <reified REQ : Any> callRpc(
        rpcName: String,
        body: REQ
    ): CategoryApiResponse<CategoryCommandResponseDto> {
        return try {
            val auth = getAuthHeader() ?: return CategoryApiResponse.Error(401, "No active session")
            val response = httpClient.post("rest/v1/rpc/$rpcName") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, auth)
                setBody(body)
            }
            CategoryApiResponse.Success(response.body())
        } catch (e: Exception) {
            CategoryApiResponse.NetworkFailure(e)
        }
    }
}
