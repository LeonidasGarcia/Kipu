package com.kipu.app.feature.movements.data

import com.kipu.app.feature.movements.data.local.TransactionEntity
import com.kipu.app.feature.movements.data.remote.RegisterTransactionRequestDto
import com.kipu.app.feature.movements.data.remote.TransactionDto
import com.kipu.app.feature.movements.data.remote.ReviseOrVoidTransactionRequestDto
import com.kipu.app.feature.movements.data.remote.RevisedMovementPayloadDto
import com.kipu.app.feature.movements.domain.model.MovementRevisionPayload
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
            contractVersion = 2,
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
                merchantProvisionalText = transaction.merchantProvisionalText,
                occurredAt = Instant.ofEpochMilli(transaction.occurredAt).toString(),
                note = transaction.note,
            ),
        ),
    )

    fun buildRevise(
        idempotencyKey: String,
        transactionId: String,
        expectedRevision: Long,
        dependsOnCommandId: String?,
        reason: String?,
        requestHash: String,
        payload: MovementRevisionPayload,
    ): String = json.encodeToString(
        ReviseOrVoidTransactionRequestDto(
            contractVersion = 1,
            commandType = "REVISE_TRANSACTION",
            idempotencyKey = idempotencyKey,
            transactionId = transactionId,
            expectedRevision = expectedRevision,
            dependsOnCommandId = dependsOnCommandId,
            reason = reason,
            requestHash = requestHash,
            revisedPayload = RevisedMovementPayloadDto(
                type = payload.type.name,
                operationKind = payload.operationKind ?: "STANDARD",
                amountMinor = payload.amountMinor,
                currencyCode = payload.currency,
                sourceAccountId = payload.sourceAccountId,
                destinationAccountId = payload.destinationAccountId,
                categoryId = payload.categoryId,
                merchantId = payload.merchantId,
                merchantProvisionalText = payload.merchantProvisionalText,
                occurredAt = Instant.ofEpochMilli(payload.occurredAt).toString(),
                note = payload.note,
            ),
        ),
    )

    fun buildVoid(
        idempotencyKey: String,
        transactionId: String,
        expectedRevision: Long,
        dependsOnCommandId: String?,
        reason: String?,
        requestHash: String,
    ): String = json.encodeToString(
        ReviseOrVoidTransactionRequestDto(
            contractVersion = 1,
            commandType = "VOID_TRANSACTION",
            idempotencyKey = idempotencyKey,
            transactionId = transactionId,
            expectedRevision = expectedRevision,
            dependsOnCommandId = dependsOnCommandId,
            reason = reason,
            requestHash = requestHash,
            revisedPayload = null,
        ),
    )
}

