package com.kipu.app.feature.accounts.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class CreditProductCatalogDto(
    @SerialName("id") val id: String,
    @SerialName("institution_code") val institutionCode: String,
    @SerialName("institution_name") val institutionName: String? = null,
    @SerialName("product_name") val productName: String,
    @SerialName("card_network") val cardNetwork: String? = null,
    @SerialName("reference_tea_bps") val referenceTeaBps: Int? = null,
    @SerialName("reference_tea_pen_min_bps") val referenceTeaPenMinBps: Int? = null,
    @SerialName("reference_tea_pen_max_bps") val referenceTeaPenMaxBps: Int? = null,
    @SerialName("reference_tea_usd_min_bps") val referenceTeaUsdMinBps: Int? = null,
    @SerialName("reference_tea_usd_max_bps") val referenceTeaUsdMaxBps: Int? = null,
    @SerialName("published_tea_summary") val publishedTeaSummary: String? = null,
    @SerialName("published_tcea_summary") val publishedTceaSummary: String? = null,
    @SerialName("membership_fee_pen_minor") val membershipFeePenMinor: Long? = null,
    @SerialName("membership_fee_usd_minor") val membershipFeeUsdMinor: Long? = null,
    @SerialName("membership_condition") val membershipCondition: String? = null,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("verification_status") val verificationStatus: String? = null,
    @SerialName("catalog_as_of") val catalogAsOf: String? = null,
    @SerialName("effective_from") val effectiveFrom: String? = null,
    @SerialName("effective_to") val effectiveTo: String? = null,
)

@Serializable
data class CreditUtilizationNotificationDto(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("body") val body: String,
    @SerialName("notification_type") val notificationType: String,
    @SerialName("reference_entity_type") val referenceEntityType: String? = null,
    @SerialName("reference_entity_id") val referenceEntityId: String? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String,
    @SerialName("event_key") val eventKey: String? = null,
)

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
    @SerialName("style_preset_id") val stylePresetId: String? = null,
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
data class CreditTransactionCommandDto(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String,
    @SerialName("amount_minor") val amountMinor: Long,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("source_account_id") val sourceAccountId: String? = null,
    @SerialName("destination_account_id") val destinationAccountId: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("merchant_id") val merchantId: String? = null,
    @SerialName("merchant_provisional_text") val merchantProvisionalText: String? = null,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("note") val note: String? = null,
    @SerialName("card_id") val cardId: String? = null,
    @SerialName("operation_kind") val operationKind: String? = null,
    @SerialName("installment_count") val installmentCount: Int? = null,
)

@Serializable
data class CreditCommandRequestDto(
    @SerialName("contract_version") val contractVersion: Int = 1,
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("request_hash") val requestHash: String,
    @SerialName("transaction") val transaction: CreditTransactionCommandDto,
)

@Serializable
data class CreditCommandErrorDto(
    @SerialName("code") val code: String,
    @SerialName("field") val field: String? = null,
    @SerialName("retryable") val retryable: Boolean = false,
)

@Serializable
data class CreditPaymentAllocationDto(
    @SerialName("installment_id") val installmentId: String,
    @SerialName("amount_minor") val amountMinor: Long,
)

@Serializable
data class CreditCommandResponseDto(
    @SerialName("status") val status: String,
    @SerialName("transaction_id") val transactionId: String? = null,
    @SerialName("receipt_id") val receiptId: String? = null,
    @SerialName("allocated_total_minor") val allocatedTotalMinor: Long? = null,
    @SerialName("remaining_debt_minor") val remainingDebtMinor: Long? = null,
    @SerialName("allocations") val allocations: List<CreditPaymentAllocationDto> = emptyList(),
    @SerialName("installments") val installments: List<CreditInstallmentResponseDto> = emptyList(),
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("error") val error: CreditCommandErrorDto? = null,
)

@Serializable
data class CreditInstallmentResponseDto(
    @SerialName("id") val id: String,
    @SerialName("installment_number") val installmentNumber: Int,
    @SerialName("due_date") val dueDate: String,
    @SerialName("principal_minor") val principalMinor: Long,
    @SerialName("interest_minor") val interestMinor: Long = 0L,
)

@Serializable
data class UpdatePersonalTeaRequestDto(
    @SerialName("contract_version") val contractVersion: Int = 1,
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("request_hash") val requestHash: String,
    @SerialName("card") val card: PersonalTeaCardCommandDto,
)

@Serializable
data class PersonalTeaCardCommandDto(
    @SerialName("id") val id: String,
    @SerialName("personal_tea_bps") val personalTeaBps: Int?,
)

@Serializable
data class PersonalTeaCommandResponseDto(
    @SerialName("status") val status: String,
    @SerialName("card_id") val cardId: String? = null,
    @SerialName("personal_tea_bps") val personalTeaBps: Int? = null,
    @SerialName("revision") val revision: Long? = null,
    @SerialName("receipt_id") val receiptId: String? = null,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("error") val error: CreditCommandErrorDto? = null,
)

@Serializable
data class UpdateCreditCardTermsRequestDto(
    @SerialName("contract_version") val contractVersion: Int = 1,
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("request_hash") val requestHash: String,
    @SerialName("card") val card: CreditCardTermsCommandDto,
)

@Serializable
data class CreditCardTermsCommandDto(
    @SerialName("id") val id: String,
    @SerialName("credit_limit_minor_units") val creditLimitMinorUnits: Long,
    @SerialName("billing_day") val billingDay: Int,
    @SerialName("due_day") val dueDay: Int,
    @SerialName("expected_revision") val expectedRevision: Long,
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
    @SerialName("payload") val payload: JsonElement? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class PullChangesResponseDto(
    @SerialName("changes") val changes: List<SyncChangeItemDto> = emptyList(),
    @SerialName("next_sequence") val nextSequence: Long,
    @SerialName("has_more") val hasMore: Boolean = false,
)
