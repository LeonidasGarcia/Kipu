package com.kipu.app.feature.accounts.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateAccountRequestDto(
    @SerialName("operation_id") val operationId: String,
    @SerialName("account_id") val accountId: String,
    @SerialName("opening_movement_id") val openingMovementId: String,
    @SerialName("alias") val alias: String,
    @SerialName("type") val type: String,
    @SerialName("currency") val currency: String,
    @SerialName("preset_id") val presetId: String? = null,
    @SerialName("color") val color: String? = null,
    @SerialName("icon") val icon: String? = null,
    @SerialName("initial_balance_minor_units") val initialBalanceMinorUnits: Long,
    @SerialName("opened_at") val openedAt: String,
    @SerialName("payload_hash") val payloadHash: String,
)

@Serializable
data class RegisterCardRequestDto(
    @SerialName("operation_id") val operationId: String,
    @SerialName("card_id") val cardId: String,
    @SerialName("account_id") val accountId: String? = null,
    @SerialName("alias") val alias: String? = null,
    @SerialName("type") val type: String,
    @SerialName("currency") val currency: String? = null,
    @SerialName("network") val network: String,
    @SerialName("issuer") val issuer: String,
    @SerialName("last_four_digits") val lastFourDigits: String,
    @SerialName("credit_limit_minor_units") val creditLimitMinorUnits: Long? = null,
    @SerialName("billing_day") val billingDay: Int? = null,
    @SerialName("due_day") val dueDay: Int? = null,
    @SerialName("preset_id") val presetId: String? = null,
    @SerialName("color") val color: String? = null,
    @SerialName("icon") val icon: String? = null,
    @SerialName("payload_hash") val payloadHash: String,
)

@Serializable
data class UpdateAppearanceRequestDto(
    @SerialName("operation_id") val operationId: String,
    @SerialName("instrument_id") val instrumentId: String,
    @SerialName("instrument_type") val instrumentType: String,
    @SerialName("alias") val alias: String,
    @SerialName("preset_id") val presetId: String? = null,
    @SerialName("color") val color: String? = null,
    @SerialName("icon") val icon: String? = null,
    @SerialName("expected_revision") val expectedRevision: Long? = null,
)

@Serializable
data class SetArchivedRequestDto(
    @SerialName("operation_id") val operationId: String,
    @SerialName("instrument_id") val instrumentId: String,
    @SerialName("instrument_type") val instrumentType: String,
    @SerialName("is_archived") val isArchived: Boolean,
)

@Serializable
data class DeleteUnusedCardRequestDto(
    @SerialName("card_id") val cardId: String,
)

@Serializable
data class RecordOpeningAdjustmentRequestDto(
    @SerialName("operation_id") val operationId: String,
    @SerialName("account_id") val accountId: String,
    @SerialName("reversal_movement_id") val reversalMovementId: String,
    @SerialName("adjustment_movement_id") val adjustmentMovementId: String,
    @SerialName("new_amount_minor_units") val newAmountMinorUnits: Long,
    @SerialName("new_effective_at") val newEffectiveAt: String,
)

@Serializable
data class CommandResponseDto(
    @SerialName("status") val status: String,
    @SerialName("account_id") val accountId: String? = null,
    @SerialName("card_id") val cardId: String? = null,
    @SerialName("instrument_id") val instrumentId: String? = null,
    @SerialName("revision") val revision: Long? = null,
    @SerialName("error") val error: String? = null,
)

@Serializable
data class PullChangesRequestDto(
    @SerialName("contract_version") val contractVersion: Int = 1,
    @SerialName("after_sequence") val afterSequence: Long,
    @SerialName("limit") val limit: Int = 100,
)

@Serializable
data class SyncChangeItemDto(
    @SerialName("sequence") val sequence: Long,
    @SerialName("entity_type") val entityType: String,
    @SerialName("entity_id") val entityId: String,
    @SerialName("revision") val revision: Long,
    @SerialName("operation") val operation: String,
    @SerialName("payload") val payload: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class PullChangesResponseDto(
    @SerialName("changes") val changes: List<SyncChangeItemDto> = emptyList(),
    @SerialName("next_sequence") val nextSequence: Long,
    @SerialName("has_more") val hasMore: Boolean = false,
)
