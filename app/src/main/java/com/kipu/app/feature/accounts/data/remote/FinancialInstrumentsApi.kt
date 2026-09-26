package com.kipu.app.feature.accounts.data.remote

import com.kipu.app.core.network.AuthenticatedSessionProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import javax.inject.Inject
import javax.inject.Singleton

sealed interface FinancialApiResponse<out T> {
    data class Success<T>(val data: T) : FinancialApiResponse<T>
    data class Error(val statusCode: Int, val message: String?) : FinancialApiResponse<Nothing>
    data class NetworkFailure(val exception: Throwable) : FinancialApiResponse<Nothing>
}

@Singleton
class FinancialInstrumentsApi @Inject constructor(
    private val httpClient: HttpClient,
    private val sessionProvider: AuthenticatedSessionProvider,
) {
    private suspend fun getAuthHeader(): String? {
        val session = sessionProvider.currentSession() ?: return null
        return "Bearer ${session.accessToken}"
    }

    suspend fun createAccount(request: CreateAccountRequestDto): FinancialApiResponse<CommandResponseDto> {
        return callRpc("create_account_v1", request)
    }

    suspend fun registerCard(request: RegisterCardRequestDto): FinancialApiResponse<CommandResponseDto> {
        return callRpc("register_card_v1", request)
    }

    suspend fun updateAppearance(request: UpdateAppearanceRequestDto): FinancialApiResponse<CommandResponseDto> {
        return callRpc("update_instrument_appearance_v1", request)
    }

    suspend fun setArchived(request: SetArchivedRequestDto): FinancialApiResponse<CommandResponseDto> {
        return callRpc("set_instrument_archived_v1", request)
    }

    suspend fun deleteUnusedCard(request: DeleteUnusedCardRequestDto): FinancialApiResponse<CommandResponseDto> {
        return callRpc("delete_unused_card_v1", request)
    }

    suspend fun recordOpeningAdjustment(request: RecordOpeningAdjustmentRequestDto): FinancialApiResponse<CommandResponseDto> {
        return callRpc("record_opening_adjustment_v1", request)
    }

    suspend fun registerCreditPurchase(request: CreditCommandRequestDto): FinancialApiResponse<CreditCommandResponseDto> {
        return callRpc("register_transaction_v1", request)
    }

    suspend fun allocateCreditPayment(request: CreditCommandRequestDto): FinancialApiResponse<CreditCommandResponseDto> {
        return callRpc("allocate_credit_payment_v1", request)
    }

    suspend fun updatePersonalTea(request: UpdatePersonalTeaRequestDto): FinancialApiResponse<PersonalTeaCommandResponseDto> {
        return callRpc("update_card_personal_tea_v1", request)
    }

    suspend fun fetchCreditProducts(): FinancialApiResponse<List<CreditProductCatalogDto>> {
        return try {
            val auth = getAuthHeader() ?: return FinancialApiResponse.Error(401, "No active session")
            val response = httpClient.get("rest/v1/credit_products") {
                header(HttpHeaders.Authorization, auth)
                url {
                    parameters.append(
                        "select",
                        "id,institution_code,institution_name,product_name,card_network,reference_tea_bps,reference_tea_pen_min_bps,reference_tea_pen_max_bps,reference_tea_usd_min_bps,reference_tea_usd_max_bps,published_tea_summary,published_tcea_summary,membership_fee_pen_minor,membership_fee_usd_minor,membership_condition,source_url,verification_status,catalog_as_of,effective_from,effective_to",
                    )
                    parameters.append("is_catalog_listed", "eq.true")
                    parameters.append("order", "institution_code.asc,product_name.asc")
                }
            }
            when (response.status) {
                HttpStatusCode.OK -> FinancialApiResponse.Success(response.body())
                else -> FinancialApiResponse.Error(response.status.value, response.body<String>())
            }
        } catch (e: Exception) {
            FinancialApiResponse.NetworkFailure(e)
        }
    }

    suspend fun fetchCreditUtilizationNotifications(cardId: String? = null): FinancialApiResponse<List<CreditUtilizationNotificationDto>> {
        return try {
            val auth = getAuthHeader() ?: return FinancialApiResponse.Error(401, "No active session")
            val response = httpClient.get("rest/v1/app_notifications") {
                header(HttpHeaders.Authorization, auth)
                url {
                    parameters.append(
                        "select",
                        "id,title,body,notification_type,reference_entity_type,reference_entity_id,is_read,created_at,event_key",
                    )
                    parameters.append("notification_type", "eq.CREDIT_UTILIZATION_THRESHOLD_CROSSED")
                    parameters.append("reference_entity_type", "eq.CARD")
                    parameters.append("is_read", "eq.false")
                    if (cardId != null) parameters.append("reference_entity_id", "eq.$cardId")
                    parameters.append("order", "created_at.desc")
                }
            }
            when (response.status) {
                HttpStatusCode.OK -> FinancialApiResponse.Success(response.body())
                else -> FinancialApiResponse.Error(response.status.value, response.body<String>())
            }
        } catch (e: Exception) {
            FinancialApiResponse.NetworkFailure(e)
        }
    }

    suspend fun pullChanges(request: PullChangesRequestDto): FinancialApiResponse<PullChangesResponseDto> {
        return try {
            val auth = getAuthHeader() ?: return FinancialApiResponse.Error(401, "No active session")
            val response = httpClient.post("rest/v1/rpc/pull_financial_changes_v1") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, auth)
                setBody(request)
            }
            when (response.status) {
                HttpStatusCode.OK -> FinancialApiResponse.Success(response.body<PullChangesResponseDto>())
                else -> FinancialApiResponse.Error(response.status.value, response.body<String>())
            }
        } catch (e: Exception) {
            FinancialApiResponse.NetworkFailure(e)
        }
    }

    private suspend inline fun <reified T, reified R> callRpc(
        rpcName: String,
        request: T,
    ): FinancialApiResponse<R> {
        return try {
            val auth = getAuthHeader() ?: return FinancialApiResponse.Error(401, "No active session")
            val response = httpClient.post("rest/v1/rpc/$rpcName") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, auth)
                setBody(mapOf("p_command" to request))
            }
            when (response.status) {
                HttpStatusCode.OK -> FinancialApiResponse.Success(response.body<R>())
                else -> FinancialApiResponse.Error(response.status.value, response.body<String>())
            }
        } catch (e: Exception) {
            FinancialApiResponse.NetworkFailure(e)
        }
    }
}
