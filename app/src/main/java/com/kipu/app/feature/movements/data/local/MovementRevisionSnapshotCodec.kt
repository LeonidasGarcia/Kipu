package com.kipu.app.feature.movements.data.local

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

/** Stable, complete local evidence for an accepted or optimistically voided movement. */
object MovementRevisionSnapshotCodec {
    private val json = Json { encodeDefaults = true; explicitNulls = true; ignoreUnknownKeys = true }

    fun encode(transaction: TransactionEntity, status: String = transaction.status, revision: Long = transaction.revision): String =
        json.encodeToString(
            Snapshot(
                id = transaction.id,
                type = transaction.type,
                operationKind = transaction.operationKind,
                legacyKind = transaction.legacyKind,
                amountMinor = transaction.amountMinor,
                currencyCode = transaction.currencyCode,
                sourceAccountId = transaction.sourceAccountId,
                destinationAccountId = transaction.destinationAccountId,
                categoryId = transaction.categoryId,
                merchantId = transaction.merchantId,
                merchantProvisionalText = transaction.merchantProvisionalText,
                cardId = transaction.cardId,
                installmentCount = transaction.installmentCount,
                occurredAt = Instant.ofEpochMilli(transaction.occurredAt).toString(),
                note = transaction.note,
                status = status,
                revision = revision,
            ),
        )

    fun decodeSnapshot(payloadJson: String): MovementSnapshotDto? =
        runCatching {
            val s = json.decodeFromString<Snapshot>(payloadJson)
            MovementSnapshotDto(
                id = s.id,
                type = s.type,
                operationKind = s.operationKind,
                legacyKind = s.legacyKind,
                amountMinor = s.amountMinor,
                currencyCode = s.currencyCode,
                sourceAccountId = s.sourceAccountId,
                destinationAccountId = s.destinationAccountId,
                categoryId = s.categoryId,
                merchantId = s.merchantId,
                merchantProvisionalText = s.merchantProvisionalText,
                cardId = s.cardId,
                installmentCount = s.installmentCount,
                occurredAt = runCatching { Instant.parse(s.occurredAt).toEpochMilli() }.getOrDefault(0L),
                note = s.note,
                status = s.status,
                revision = s.revision,
            )
        }.getOrNull()

    @Serializable
    private data class Snapshot(
        val id: String,
        val type: String,
        @SerialName("operation_kind") val operationKind: String? = null,
        @SerialName("legacy_kind") val legacyKind: String? = null,
        @SerialName("amount_minor") val amountMinor: Long,
        @SerialName("currency_code") val currencyCode: String,
        @SerialName("source_account_id") val sourceAccountId: String? = null,
        @SerialName("destination_account_id") val destinationAccountId: String? = null,
        @SerialName("category_id") val categoryId: String? = null,
        @SerialName("merchant_id") val merchantId: String? = null,
        @SerialName("merchant_provisional_text") val merchantProvisionalText: String? = null,
        @SerialName("card_id") val cardId: String? = null,
        @SerialName("installment_count") val installmentCount: Int? = null,
        @SerialName("occurred_at") val occurredAt: String,
        val note: String? = null,
        val status: String,
        val revision: Long,
    )
}

data class MovementSnapshotDto(
    val id: String,
    val type: String,
    val operationKind: String?,
    val legacyKind: String?,
    val amountMinor: Long,
    val currencyCode: String,
    val sourceAccountId: String?,
    val destinationAccountId: String?,
    val categoryId: String?,
    val merchantId: String?,
    val merchantProvisionalText: String?,
    val cardId: String?,
    val installmentCount: Int?,
    val occurredAt: Long,
    val note: String?,
    val status: String,
    val revision: Long,
)

