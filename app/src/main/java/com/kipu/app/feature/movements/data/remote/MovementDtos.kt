package com.kipu.app.feature.movements.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterTransactionRequestDto(
    @SerialName("contract_version")
    val contractVersion: Int = 1,
    @SerialName("idempotency_key")
    val idempotencyKey: String,
    @SerialName("request_hash")
    val requestHash: String,
    @SerialName("transaction")
    val transaction: TransactionDto,
)

@Serializable
data class TransactionDto(
    @SerialName("id")
    val id: String,
    @SerialName("type")
    val type: String,
    @SerialName("amount_minor")
    val amountMinor: Long,
    @SerialName("currency_code")
    val currencyCode: String,
    @SerialName("source_account_id")
    val sourceAccountId: String? = null,
    @SerialName("destination_account_id")
    val destinationAccountId: String? = null,
    @SerialName("category_id")
    val categoryId: String? = null,
    @SerialName("merchant_id")
    val merchantId: String? = null,
    @SerialName("merchant_provisional_text")
    val merchantProvisionalText: String? = null,
    @SerialName("occurred_at")
    val occurredAt: String,
    @SerialName("note")
    val note: String? = null,
)

@Serializable
data class RegisterTransactionResponseDto(
    @SerialName("status")
    val status: String,
    @SerialName("transaction_id")
    val transactionId: String? = null,
    @SerialName("receipt_id")
    val receiptId: String? = null,
    @SerialName("server_updated_at")
    val serverUpdatedAt: String? = null,
    @SerialName("error")
    val error: RemoteErrorDto? = null,
)

@Serializable
data class RemoteErrorDto(
    @SerialName("code")
    val code: String,
    @SerialName("field")
    val field: String? = null,
    @SerialName("retryable")
    val retryable: Boolean = false,
)

@Serializable
data class ReviseOrVoidTransactionRequestDto(
    @SerialName("contract_version")
    val contractVersion: Int = 1,
    @SerialName("command_type")
    val commandType: String,
    @SerialName("idempotency_key")
    val idempotencyKey: String,
    @SerialName("transaction_id")
    val transactionId: String,
    @SerialName("expected_revision")
    val expectedRevision: Long,
    @SerialName("depends_on_command_id")
    val dependsOnCommandId: String? = null,
    @SerialName("reason")
    val reason: String? = null,
    @SerialName("request_hash")
    val requestHash: String,
    @SerialName("revised_payload")
    val revisedPayload: RevisedMovementPayloadDto? = null,
)

@Serializable
data class RevisedMovementPayloadDto(
    @SerialName("type")
    val type: String,
    @SerialName("operation_kind")
    val operationKind: String? = "STANDARD",
    @SerialName("amount_minor")
    val amountMinor: Long,
    @SerialName("currency_code")
    val currencyCode: String,
    @SerialName("source_account_id")
    val sourceAccountId: String? = null,
    @SerialName("destination_account_id")
    val destinationAccountId: String? = null,
    @SerialName("category_id")
    val categoryId: String? = null,
    @SerialName("merchant_id")
    val merchantId: String? = null,
    @SerialName("merchant_provisional_text")
    val merchantProvisionalText: String? = null,
    @SerialName("occurred_at")
    val occurredAt: String,
    @SerialName("note")
    val note: String? = null,
)

@Serializable
data class ReviseOrVoidTransactionResponseDto(
    @SerialName("status")
    val status: String,
    @SerialName("result")
    val result: String? = null,
    @SerialName("transaction_id")
    val transactionId: String? = null,
    @SerialName("receipt_id")
    val receiptId: String? = null,
    @SerialName("resulting_revision")
    val resultingRevision: Long? = null,
    @SerialName("current_revision")
    val currentRevision: Long? = null,
    @SerialName("server_updated_at")
    val serverUpdatedAt: String? = null,
    @SerialName("error")
    val error: RemoteErrorDto? = null,
)

