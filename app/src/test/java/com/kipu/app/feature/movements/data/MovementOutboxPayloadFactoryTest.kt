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
        ).copy(note = note)

        val payload = MovementOutboxPayloadFactory.build(
            transaction = transaction,
            idempotencyKey = "operation-id",
            requestHash = "request-hash",
        )
        val decoded = Json.decodeFromString<RegisterTransactionRequestDto>(payload)

        assertEquals(1, decoded.contractVersion)
        assertEquals("operation-id", decoded.idempotencyKey)
        assertEquals(occurredAt.toString(), decoded.transaction.occurredAt)
        assertEquals(note, decoded.transaction.note)
    }
}
