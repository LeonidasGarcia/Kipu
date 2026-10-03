package com.kipu.app.feature.movements.domain

import com.kipu.app.feature.movements.domain.model.RegisterTransactionCommand
import java.security.MessageDigest
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

class TransactionRequestHasher {
    // Only new commands use v2. Persisted v1 outbox hashes are never recalculated.
    fun computeHash(command: RegisterTransactionCommand): String {
        val values = listOf(
            "MOV_REGISTER_V2", command.userId.lowercase(Locale.ROOT), command.type.name,
            command.amountMinor.toString(), command.currency.uppercase(Locale.ROOT),
            command.sourceAccountId?.lowercase(Locale.ROOT),
            command.destinationAccountId?.lowercase(Locale.ROOT),
            command.categoryId?.lowercase(Locale.ROOT), command.merchantId?.lowercase(Locale.ROOT),
            command.merchantProvisionalText, command.occurredAt.toString(), command.note,
        )
        val canonical = JsonArray(values.map { it?.let(::JsonPrimitive) ?: JsonNull }).toString()
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
