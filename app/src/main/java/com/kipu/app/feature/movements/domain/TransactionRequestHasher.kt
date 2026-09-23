package com.kipu.app.feature.movements.domain

import com.kipu.app.feature.movements.domain.model.RegisterTransactionCommand
import java.security.MessageDigest

class TransactionRequestHasher {

    fun computeHash(command: RegisterTransactionCommand): String {
        // Canonical sorted string representation
        val canonical = buildString {
            append("amount_minor:").append(command.amountMinor).append(";")
            append("category_id:").append(command.categoryId ?: "").append(";")
            append("currency_code:").append(command.currency).append(";")
            append("destination_account_id:").append(command.destinationAccountId ?: "").append(";")
            append("merchant_id:").append(command.merchantId ?: "").append(";")
            append("merchant_provisional_text:").append(command.merchantProvisionalText ?: "").append(";")
            append("note:").append(command.note ?: "").append(";")
            append("occurred_at:").append(command.occurredAt).append(";")
            append("source_account_id:").append(command.sourceAccountId ?: "").append(";")
            append("type:").append(command.type.name).append(";")
            append("user_id:").append(command.userId)
        }

        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(canonical.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
