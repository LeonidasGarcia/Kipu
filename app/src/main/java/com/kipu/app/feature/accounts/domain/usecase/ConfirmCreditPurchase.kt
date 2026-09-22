package com.kipu.app.feature.accounts.domain.usecase

import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.OperationId
import com.kipu.app.feature.accounts.domain.FinancialInstrumentsRepository
import java.time.Instant
import javax.inject.Inject

class ConfirmCreditPurchase @Inject constructor(
    private val repository: FinancialInstrumentsRepository,
) {
    suspend operator fun invoke(
        cardId: CardId,
        amount: Money,
        merchant: String,
        effectiveAt: Instant = Instant.now(),
        installments: Int = 1,
        operationId: OperationId = OperationId.generate(),
    ): Result<Unit> {
        return repository.confirmCreditPurchase(
            cardId = cardId,
            amount = amount,
            merchant = merchant,
            effectiveAt = effectiveAt,
            installments = installments,
            operationId = operationId,
        )
    }
}
