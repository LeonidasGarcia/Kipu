package com.kipu.app.feature.plans.data.remote

import com.kipu.app.core.network.AuthenticatedSession
import com.kipu.app.feature.plans.domain.model.BillingProduct
import com.kipu.app.feature.plans.domain.model.BillingPurchaseRequest
import com.kipu.app.feature.plans.domain.model.BillingVerificationResult
import com.kipu.app.feature.plans.domain.model.GooglePlayPurchaseState
import com.kipu.app.feature.plans.domain.model.PurchaseLifecycle
import com.kipu.app.feature.plans.domain.model.SignedOfflineEntitlementGrant
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class BillingProductDto(
    val id: String,
    @SerialName("store_product_id") val storeProductId: String,
    @SerialName("base_plan_id") val basePlanId: String? = null,
    val name: String,
    @SerialName("plan_type") val planType: String,
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
private data class VerifyPurchaseRequestDto(
    val productId: String,
    val purchaseToken: String,
    val installationPublicKey: String? = null,
) {
    override fun toString(): String = "VerifyPurchaseRequestDto(productId=$productId, purchaseToken=[REDACTED], installationPublicKey=${installationPublicKey != null})"
}

@Serializable
private data class OfflineEntitlementGrantDto(
    val payload: String,
    val signature: String,
    val keyId: String,
)

@Serializable
private data class VerifyPurchaseResponseDto(
    val outcome: String = "RETRYABLE",
    val code: String? = null,
    val purchaseId: String? = null,
    val productId: String? = null,
    val planType: String? = null,
    val purchaseState: String? = null,
    val entitlementState: String? = null,
    val startsAt: String? = null,
    val effectivePremium: Boolean = false,
    val expiresAt: String? = null,
    val effectiveExpiresAt: String? = null,
    val isLifetime: Boolean? = null,
    val willRenew: Boolean? = null,
    val orderId: String? = null,
    val acknowledgementState: String? = null,
    val offlineEntitlementGrant: OfflineEntitlementGrantDto? = null,
    val retryable: Boolean? = null,
)

class VerifyPurchaseApi(
    private val client: HttpClient,
    private val supabaseUrl: String,
) {
    suspend fun loadProducts(session: AuthenticatedSession): List<BillingProduct> {
        val response = client.get(supabaseUrl.trimEnd('/') + "/rest/v1/billing_products") {
            bearerAuth(session.accessToken)
            accept(ContentType.Application.Json)
            parameter("select", "id,store_product_id,base_plan_id,name,plan_type,is_active")
            parameter("is_active", "eq.true")
        }
        if (!response.status.isSuccess()) return emptyList()
        return response.body<List<BillingProductDto>>()
            .filter { it.isActive }
            .mapNotNull { dto ->
                BillingProduct.fromCatalog(dto.id, dto.storeProductId, dto.basePlanId, dto.name, dto.planType)
            }
    }

    suspend fun verify(
        session: AuthenticatedSession,
        request: BillingPurchaseRequest,
        installationPublicKey: String? = null,
    ): BillingVerificationResult {
        return try {
            val response = client.post(
                supabaseUrl.trimEnd('/') + "/functions/v1/verify-purchase/billing/verify",
            ) {
                bearerAuth(session.accessToken)
                header("Content-Type", ContentType.Application.Json.toString())
                accept(ContentType.Application.Json)
                setBody(VerifyPurchaseRequestDto(request.productId, request.purchaseToken.forVerification(), installationPublicKey))
            }
            val body = response.body<VerifyPurchaseResponseDto>()
            if (response.status == HttpStatusCode.Conflict || body.code == "TOKEN_ACCOUNT_CONFLICT") {
                return BillingVerificationResult.Rejected("TOKEN_ACCOUNT_CONFLICT")
            }
            if (response.status == HttpStatusCode.Unauthorized) {
                return BillingVerificationResult.Retryable("UNAUTHENTICATED")
            }
            if (!response.status.isSuccess() && response.status.value < 500) {
                return BillingVerificationResult.Rejected(body.code ?: "INVALID_REQUEST")
            }
            when (body.outcome) {
                "PENDING" -> BillingVerificationResult.PaymentPending(body.productId ?: request.productId)
                "RETRYABLE" -> BillingVerificationResult.Retryable(body.code ?: "PROVIDER_UNAVAILABLE")
                "REJECTED" -> BillingVerificationResult.Rejected(body.code ?: "PURCHASE_REJECTED")
                "VERIFIED" -> {
                    val providerPurchaseState = body.purchaseState?.let {
                        runCatching { GooglePlayPurchaseState.valueOf(it) }.getOrNull()
                    } ?: return BillingVerificationResult.Rejected("INVALID_VERIFICATION_RESPONSE")
                    if (providerPurchaseState == GooglePlayPurchaseState.PENDING) {
                        return BillingVerificationResult.Rejected("INVALID_VERIFICATION_RESPONSE")
                    }
                    val lifecycle = body.entitlementState?.let {
                        runCatching { PurchaseLifecycle.valueOf(it) }.getOrNull()
                    } ?: return BillingVerificationResult.Rejected("INVALID_VERIFICATION_RESPONSE")
                    BillingVerificationResult.Verified(
                        productId = body.productId ?: request.productId,
                        providerPurchaseState = providerPurchaseState,
                        lifecycle = lifecycle,
                        expiresAtEpochMillis = body.expiresAt?.let(Instant::parse)?.toEpochMilli(),
                        effectivePremium = body.effectivePremium,
                        acknowledgementPending = body.acknowledgementState == "PENDING",
                        effectiveExpiresAtEpochMillis = body.effectiveExpiresAt?.let(Instant::parse)?.toEpochMilli(),
                        offlineEntitlementGrant = body.offlineEntitlementGrant?.let {
                            SignedOfflineEntitlementGrant(it.payload, it.signature, it.keyId)
                        },
                    )
                }
                else -> BillingVerificationResult.Retryable("INVALID_VERIFICATION_RESPONSE")
            }
        } catch (_: Exception) {
            BillingVerificationResult.Retryable("UNAVAILABLE")
        }
    }
}
