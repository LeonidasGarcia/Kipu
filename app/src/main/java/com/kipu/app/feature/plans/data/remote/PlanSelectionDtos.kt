package com.kipu.app.feature.plans.data.remote

import com.kipu.app.feature.plans.data.local.RemoteSelectionResult
import com.kipu.app.feature.plans.data.local.SyncOutboxEntity
import com.kipu.app.feature.plans.domain.model.FreePlanLimits
import com.kipu.app.feature.plans.domain.model.PlanSelection
import com.kipu.app.feature.plans.domain.model.TrialEligibilitySnapshot
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PlanSelectionDto {
    FREE,
    TRIAL_INTENT,
    PREMIUM_INTENT,
}

@Serializable
enum class SelectionResultDto {
    APPLIED,
    DUPLICATE,
    STALE,
    CONFLICT,
}

@Serializable
enum class TrialEligibilityStatusDto {
    ELIGIBLE,
    INELIGIBLE,
    UNKNOWN,
}

@Serializable
enum class TrialEligibilitySourceDto {
    VERIFIED_ACCOUNT_HISTORY,
    UNAVAILABLE,
}

@Serializable
data class PlanSelectionRequestDto(
    @SerialName("contract_version") val contractVersion: Int,
    @SerialName("operation_id") val operationId: String,
    @SerialName("selection_revision") val selectionRevision: String,
    val selection: PlanSelectionDto,
    @SerialName("selected_at") val selectedAt: String,
)

@Serializable
data class CurrentPreferenceDto(
    val selection: PlanSelectionDto,
    @SerialName("selected_at") val selectedAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class FreeLimitsDto(
    @SerialName("policy_version") val policyVersion: Int,
    val instruments: Int,
    @SerialName("custom_categories") val customCategories: Int,
    val debts: Int,
    val goals: Int,
    val budgets: Int,
)

@Serializable
data class PlanSelectionResponseDto(
    @SerialName("contract_version") val contractVersion: Int,
    @SerialName("operation_id") val operationId: String,
    val result: SelectionResultDto,
    @SerialName("accepted_revision") val acceptedRevision: String,
    @SerialName("current_preference") val currentPreference: CurrentPreferenceDto,
    @SerialName("free_limits") val freeLimits: FreeLimitsDto,
    @SerialName("server_time") val serverTime: String,
)

@Serializable
data class ErrorResponseDto(
    val code: String,
    val retryable: Boolean,
    @SerialName("retry_after_seconds") val retryAfterSeconds: Long? = null,
    @SerialName("correlation_id") val correlationId: String? = null,
)

@Serializable
data class TrialEligibilityResponseDto(
    val status: TrialEligibilityStatusDto,
    val source: TrialEligibilitySourceDto,
    @SerialName("verified_at") val verifiedAt: String? = null,
    @SerialName("valid_until") val validUntil: String? = null,
)

@Serializable
data class QuotaSelectionItemDto(
    @SerialName("resource_id") val resourceId: String,
    @SerialName("resource_type") val resourceType: String,
)

@Serializable
data class QuotaSelectionRequestDto(
    @SerialName("contract_version") val contractVersion: Int = 1,
    @SerialName("operation_id") val operationId: String,
    @SerialName("feature_key") val featureKey: String,
    @SerialName("selection_revision") val selectionRevision: String,
    val items: List<QuotaSelectionItemDto>,
)

@Serializable
data class QuotaSelectionResponseDto(
    @SerialName("contract_version") val contractVersion: Int? = null,
    @SerialName("operation_id") val operationId: String? = null,
    val result: String? = null,
    @SerialName("feature_key") val featureKey: String? = null,
    @SerialName("accepted_revision") val acceptedRevision: String? = null,
    @SerialName("current_items") val currentItems: List<QuotaSelectionItemDto>? = null,
    @SerialName("server_time") val serverTime: String? = null,
)

fun SyncOutboxEntity.toRequestDto() = PlanSelectionRequestDto(
    contractVersion,
    operationId.toString(),
    selectionRevision.toString(),
    PlanSelectionDto.valueOf(selection.name),
    selectedAt.toString(),
)

fun PlanSelectionDto.toDomain() = PlanSelection.valueOf(name)

fun SelectionResultDto.toDomain() = RemoteSelectionResult.valueOf(name)

fun FreeLimitsDto.toDomain() = FreePlanLimits(
    policyVersion = policyVersion,
    instruments = instruments,
    customCategories = customCategories,
    debts = debts,
    goals = goals,
    budgets = budgets,
    customExpenseCategories = customCategories,
    customIncomeCategories = customCategories,
)

fun TrialEligibilityResponseDto.toDomain(): TrialEligibilitySnapshot = when (status) {
    TrialEligibilityStatusDto.ELIGIBLE -> {
        require(source == TrialEligibilitySourceDto.VERIFIED_ACCOUNT_HISTORY)
        TrialEligibilitySnapshot.eligible(
            Instant.parse(requireNotNull(verifiedAt)),
            Instant.parse(requireNotNull(validUntil)),
        )
    }
    TrialEligibilityStatusDto.INELIGIBLE -> {
        require(source == TrialEligibilitySourceDto.VERIFIED_ACCOUNT_HISTORY)
        require(validUntil == null)
        TrialEligibilitySnapshot.ineligible(Instant.parse(requireNotNull(verifiedAt)))
    }
    TrialEligibilityStatusDto.UNKNOWN -> {
        require(source == TrialEligibilitySourceDto.UNAVAILABLE)
        require(verifiedAt == null && validUntil == null)
        TrialEligibilitySnapshot.UNKNOWN
    }
}
