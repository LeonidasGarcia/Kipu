package com.kipu.app.feature.accounts.domain.usecase

import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.OperationId
import com.kipu.app.feature.accounts.domain.FinancialInstrumentsRepository
import java.time.Instant
import javax.inject.Inject

class PayCreditCard @Inject constructor(
    private val repository: FinancialInstrumentsRepository,
) {
    suspend operator fun invoke(
        cardId: CardId,
        sourceAccountId: AccountId,
        paymentAmount: Money,
        effectiveAt: Instant = Instant.now(),
        operationId: OperationId = OperationId.generate(),
    ): Result<Unit> {
        if (paymentAmount.minorUnits <= 0) {
            return Result.failure(IllegalArgumentException("El monto de pago debe ser mayor a cero"))
        }

        return repository.payCreditCard(cardId, sourceAccountId, paymentAmount, effectiveAt, operationId)
    }
}
