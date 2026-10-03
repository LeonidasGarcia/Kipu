package com.kipu.app.feature.movements.domain

import com.kipu.app.feature.movements.domain.model.MovementRevisionCommand
import java.security.MessageDigest
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

class MovementRevisionRequestHasher {
    fun computeHash(command: MovementRevisionCommand): String {
        fun text(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull
        fun id(value: String?): JsonElement = text(value?.lowercase(Locale.ROOT))
        val payload = (command as? MovementRevisionCommand.Revise)?.payload?.let { p -> JsonArray(listOf(
            text(p.type.name), text(p.operationKind?.uppercase(Locale.ROOT)), text(p.amountMinor.toString()),
            text(p.currency.uppercase(Locale.ROOT)),id(p.sourceAccountId),id(p.destinationAccountId),
            id(p.categoryId),id(p.merchantId),text(p.merchantProvisionalText),text(p.occurredAt.toString()),text(p.note),
        )) } ?: JsonNull
        val canonical = JsonArray(listOf(text("MOV_REVISION_V1"),
            text(if (command is MovementRevisionCommand.Revise) "REVISE" else "VOID"),
            id(command.idempotencyKey),id(command.transactionId),text(command.expectedRevision.toString()),
            id(command.dependsOnCommandId),text(command.reason),payload,
        )).toString()
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
