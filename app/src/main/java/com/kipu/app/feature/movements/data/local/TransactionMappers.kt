package com.kipu.app.feature.movements.data.local

import com.kipu.app.feature.movements.domain.model.MovementSyncStatus
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.Transaction
import com.kipu.app.feature.movements.domain.model.TransactionStatus

internal fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    userId = userId,
    type = MovementType.fromString(type),
    amountMinor = amountMinor,
    currency = currencyCode,
    sourceAccountId = sourceAccountId,
    destinationAccountId = destinationAccountId,
    cardId = cardId,
    operationKind = operationKind,
    installmentCount = installmentCount,
    categoryId = categoryId,
    merchantId = merchantId,
    merchantProvisionalText = merchantProvisionalText,
    legacyKind = legacyKind,
    occurredAt = occurredAt,
    note = note,
    status = TransactionStatus.fromString(status),
    syncStatus = MovementSyncStatus.fromString(syncStatus),
    createdAt = createdAt,
    updatedAt = updatedAt,
)
