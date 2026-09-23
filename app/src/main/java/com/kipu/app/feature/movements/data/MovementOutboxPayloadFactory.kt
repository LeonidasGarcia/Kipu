package com.kipu.app.feature.movements.data

import com.kipu.app.feature.movements.data.local.TransactionEntity
import com.kipu.app.feature.movements.data.remote.RegisterTransactionRequestDto
import com.kipu.app.feature.movements.data.remote.TransactionDto
import java.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object MovementOutboxPayloadFactory {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
    }

    fun build(
        transaction: TransactionEntity,
        idempotencyKey: String,
        requestHash: String,
    ): String = json.encodeToString(
        RegisterTransactionRequestDto(
            contractVersion = 1,
            idempotencyKey = idempotencyKey,
            requestHash = requestHash,
            transaction = TransactionDto(
                id = transaction.id,
                type = transaction.type,
                amountMinor = transaction.amountMinor,
                currencyCode = transaction.currencyCode,
                sourceAccountId = transaction.sourceAccountId,
                destinationAccountId = transaction.destinationAccountId,
                categoryId = transaction.categoryId,
                merchantId = transaction.merchantId,
                occurredAt = Instant.ofEpochMilli(transaction.occurredAt).toString(),
                note = transaction.note,
            ),
        ),
    )
}
