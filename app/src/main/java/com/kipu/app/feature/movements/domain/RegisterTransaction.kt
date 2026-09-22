package com.kipu.app.feature.movements.domain

import com.kipu.app.feature.movements.domain.model.RegisterTransactionCommand
import com.kipu.app.feature.movements.domain.model.RegisterTransactionResult
import javax.inject.Inject

class RegisterTransaction @Inject constructor(
    private val repository: MovementRepository,
    private val validator: RegisterTransactionValidator,
) {
    suspend operator fun invoke(command: RegisterTransactionCommand): RegisterTransactionResult {
        when (val validation = validator.validate(command)) {
            is ValidationResult.Invalid -> {
                return RegisterTransactionResult.ValidationError(
                    field = validation.field,
                    message = validation.message,
                )
            }
            ValidationResult.Valid -> {
                // Check similarity within 5-minute window if not ignored
                if (!command.ignoreSimilarityWarning && !command.sourceAccountId.isNullOrBlank()) {
                    val similar = repository.findSimilarTransactions(
                        userId = command.userId,
                        sourceAccountId = command.sourceAccountId,
                        type = command.type,
                        amountMinor = command.amountMinor,
                        currency = command.currency,
                        occurredAt = command.occurredAt,
                        windowMillis = 300_000L, // 5 minutes
                    )
                    if (similar.isNotEmpty()) {
                        return RegisterTransactionResult.SimilarTransactionWarning(
                            existingTransaction = similar.first(),
                            proposedCommand = command,
                        )
                    }
                }

                return repository.registerTransaction(command)
            }
        }
    }
}
