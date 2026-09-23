package com.kipu.app.feature.movements.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SyncAccountPayload(
    val id: String,
    val alias: String,
    val type: String,
    val currency: String,
    @SerialName("initial_balance_minor_units") val initialBalanceMinorUnits: Long,
    @SerialName("opened_at") val openedAt: String,
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
    @SerialName("occurred_at") val occurredAt: String,
    val note: String? = null,
    val status: String,
    @SerialName("ledger_entries") val ledgerEntries: List<SyncLedgerEntryPayload> = emptyList(),
)

@Serializable
internal data class SyncLedgerEntryPayload(
    val id: String,
    @SerialName("account_id") val accountId: String,
    val role: String,
    @SerialName("signed_amount_minor") val signedAmountMinor: Long,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("created_at") val createdAt: String,
)
