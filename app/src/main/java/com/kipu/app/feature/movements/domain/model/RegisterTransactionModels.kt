package com.kipu.app.feature.movements.domain.model

data class RegisterTransactionCommand(
    val idempotencyKey: String,
    val userId: String,
    val type: MovementType,
    val amountMinor: Long,
    val currency: String,
    val sourceAccountId: String? = null,
    val destinationAccountId: String? = null,
    val categoryId: String? = null,
    val merchantId: String? = null,
    val merchantProvisionalText: String? = null,
    val occurredAt: Long = System.currentTimeMillis(),
    val note: String? = null,
    val ignoreSimilarityWarning: Boolean = false,
)

sealed interface RegisterTransactionResult {
    data class Success(
        val transaction: Transaction,
        val isDuplicate: Boolean = false,
    ) : RegisterTransactionResult

    data class SimilarTransactionWarning(
        val existingTransaction: Transaction,
        val proposedCommand: RegisterTransactionCommand,
    ) : RegisterTransactionResult

    data class Conflict(
        val message: String,
    ) : RegisterTransactionResult

    data class ValidationError(
        val field: String?,
        val message: String,
    ) : RegisterTransactionResult

    data class Failure(
        val message: String,
        val cause: Throwable? = null,
    ) : RegisterTransactionResult
}
