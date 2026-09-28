package com.kipu.app.feature.accounts.domain.model

import java.nio.charset.StandardCharsets
import java.util.UUID

/** Stable IDs shared by Room migration and the forward-only PostgreSQL backfill. */
object CreditLiabilityAccountIds {
    fun accountId(userId: String, cardId: String): String = derive("account", userId, cardId)

    fun creationOperationId(userId: String, cardId: String): String = derive("create-operation", userId, cardId)

    private fun derive(kind: String, userId: String, cardId: String): String = UUID.nameUUIDFromBytes(
        "kipu:credit-liability:$kind:v1:${userId.lowercase()}:${cardId.lowercase()}"
            .toByteArray(StandardCharsets.UTF_8),
    ).toString()
}
