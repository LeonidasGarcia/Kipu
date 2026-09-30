package com.kipu.app.feature.movements.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SyncAccountPayload(
    val id: String,
    @SerialName("creation_operation_id") val creationOperationId: String? = null,
    val alias: String,
    val type: String,
    val currency: String,
    @SerialName("initial_balance_minor_units") val initialBalanceMinorUnits: Long,
    @SerialName("opened_at") val openedAt: String,
    @SerialName("is_archived") val isArchived: Boolean = false,
)

@Serializable
internal data class SyncCardPayload(
    val id: String,
    @SerialName("creation_operation_id") val creationOperationId: String? = null,
    @SerialName("account_id") val accountId: String? = null,
    val alias: String? = null,
    val type: String,
    val currency: String,
    val network: String,
    val issuer: String,
    @SerialName("last_four_digits") val lastFourDigits: String,
    @SerialName("credit_limit_minor_units") val creditLimitMinorUnits: Long? = null,
    @SerialName("billing_day") val billingDay: Int? = null,
    @SerialName("due_day") val dueDay: Int? = null,
    @SerialName("preset_id") val presetId: String? = null,
    @SerialName("style_preset_id") val stylePresetId: String? = null,
    val color: String? = null,
    val icon: String? = null,
    @SerialName("is_archived") val isArchived: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("personal_tea_bps") val personalTeaBps: Int? = null,
)

@Serializable
internal data class SyncTransactionPayload(
    val id: String,
    val type: String,
    @SerialName("amount_minor") val amountMinor: Long,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("source_account_id") val sourceAccountId: String? = null,
    @SerialName("destination_account_id") val destinationAccountId: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("merchant_id") val merchantId: String? = null,
    @SerialName("merchant_provisional_text") val merchantProvisionalText: String? = null,
    @SerialName("card_id") val cardId: String? = null,
    @SerialName("operation_kind") val operationKind: String? = null,
    @SerialName("installment_count") val installmentCount: Int? = null,
    @SerialName("occurred_at") val occurredAt: String,
    val note: String? = null,
    val status: String,
    @SerialName("ledger_entries") val ledgerEntries: List<SyncLedgerEntryPayload> = emptyList(),
    val installments: List<SyncInstallmentPayload> = emptyList(),
    val allocations: List<SyncAllocationPayload> = emptyList(),
)

@Serializable
internal data class SyncInstallmentPayload(
    val id: String,
    @SerialName("installment_number") val installmentNumber: Int,
    @SerialName("due_date") val dueDate: String,
    @SerialName("principal_minor") val principalMinor: Long,
    @SerialName("interest_minor") val interestMinor: Long = 0L,
)

@Serializable
internal data class SyncAllocationPayload(
    @SerialName("installment_id") val installmentId: String,
    @SerialName("amount_minor") val amountMinor: Long,
)

@Serializable
internal data class SyncLedgerEntryPayload(
    val id: String? = null,
    @SerialName("account_id") val accountId: String,
    val role: String,
    @SerialName("signed_amount_minor") val signedAmountMinor: Long,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("created_at") val createdAt: String? = null,
)
