package com.kipu.app.feature.movements.data

import com.kipu.app.feature.movements.data.local.TransactionEntity
import com.kipu.app.feature.movements.data.remote.RegisterTransactionRequestDto
import java.time.Instant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class MovementOutboxPayloadFactoryTest {
    @Test
    fun `serializes an ISO timestamp and escapes free text for Postgres`() {
        val occurredAt = Instant.parse("2026-09-22T12:34:56.789Z")
        val note = "Compra \"especial\"\\\nnota"
        val transaction = TransactionEntity(
            id = "transaction-id",
            userId = "user-id",
            type = "EXPENSE",
            amountMinor = 1299L,
            currencyCode = "PEN",
            sourceAccountId = "account-id",
            categoryId = "category-id",
            occurredAt = occurredAt.toEpochMilli(),
        ).copy(note = note, merchantProvisionalText = "Bodega del barrio")

        val payload = MovementOutboxPayloadFactory.build(
            transaction = transaction,
            idempotencyKey = "operation-id",
            requestHash = "request-hash",
        )
        val decoded = Json.decodeFromString<RegisterTransactionRequestDto>(payload)

        assertEquals(2, decoded.contractVersion)
        assertEquals("operation-id", decoded.idempotencyKey)
        assertEquals(occurredAt.toString(), decoded.transaction.occurredAt)
        assertEquals(note, decoded.transaction.note)
        assertEquals(null, decoded.transaction.merchantId)
        assertEquals("Bodega del barrio", decoded.transaction.merchantProvisionalText)
    }

    @Test
    fun `serializes revise command with revised payload and expected revision`() {
        val occurredAt = Instant.parse("2026-10-02T12:00:00Z")
        val revisionPayload = com.kipu.app.feature.movements.domain.model.MovementRevisionPayload(
            type = com.kipu.app.feature.movements.domain.model.MovementType.EXPENSE,
            operationKind = "STANDARD",
            amountMinor = 1500L,
            currency = "PEN",
            sourceAccountId = "account-1",
            destinationAccountId = null,
            categoryId = "cat-1",
            merchantId = null,
            merchantProvisionalText = null,
            occurredAt = occurredAt.toEpochMilli(),
            note = "adjusted",
        )

        val payload = MovementOutboxPayloadFactory.buildRevise(
            idempotencyKey = "cmd-1",
            transactionId = "tx-1",
            expectedRevision = 1L,
            dependsOnCommandId = null,
            reason = "correction",
            requestHash = "hash-1",
            payload = revisionPayload,
        )
        val decoded = Json.decodeFromString<com.kipu.app.feature.movements.data.remote.ReviseOrVoidTransactionRequestDto>(payload)

        assertEquals(1, decoded.contractVersion)
        assertEquals("REVISE_TRANSACTION", decoded.commandType)
        assertEquals("cmd-1", decoded.idempotencyKey)
        assertEquals("tx-1", decoded.transactionId)
        assertEquals(1L, decoded.expectedRevision)
        assertEquals(null, decoded.dependsOnCommandId)
        assertEquals("correction", decoded.reason)
        assertEquals("hash-1", decoded.requestHash)
        assertEquals("EXPENSE", decoded.revisedPayload?.type)
        assertEquals(1500L, decoded.revisedPayload?.amountMinor)
    }

    @Test
    fun `serializes void command with null revised payload`() {
        val payload = MovementOutboxPayloadFactory.buildVoid(
            idempotencyKey = "cmd-void",
            transactionId = "tx-1",
            expectedRevision = 2L,
            dependsOnCommandId = "cmd-1",
            reason = "cancellation",
            requestHash = "hash-void",
        )
        val decoded = Json.decodeFromString<com.kipu.app.feature.movements.data.remote.ReviseOrVoidTransactionRequestDto>(payload)

        assertEquals(1, decoded.contractVersion)
        assertEquals("VOID_TRANSACTION", decoded.commandType)
        assertEquals("cmd-void", decoded.idempotencyKey)
        assertEquals("tx-1", decoded.transactionId)
        assertEquals(2L, decoded.expectedRevision)
        assertEquals("cmd-1", decoded.dependsOnCommandId)
        assertEquals("cancellation", decoded.reason)
        assertEquals("hash-void", decoded.requestHash)
        assertEquals(null, decoded.revisedPayload)
    }
}
