package com.kipu.app.feature.accounts.domain.usecase

import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.OperationId
import com.kipu.app.feature.accounts.domain.FinancialInstrumentsRepository
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.DebitCard
import javax.inject.Inject

class RegisterDebitCard @Inject constructor(
    private val repository: FinancialInstrumentsRepository,
) {
    suspend operator fun invoke(
        card: DebitCard,
        operationId: OperationId = OperationId.generate(),
    ): Result<DebitCard> {
        val currentCount = repository.getActiveComputableCount()
        if (currentCount >= FinancialInstrumentsRepository.FREE_TIER_MAX_COMPUTABLE_INSTRUMENTS) {
            return Result.failure(QuotaExceededException("Límite de instrumentos activos alcanzado para el plan Free (máx. ${FinancialInstrumentsRepository.FREE_TIER_MAX_COMPUTABLE_INSTRUMENTS})"))
        }

        if (card.lastFourDigits.length != 4 || !card.lastFourDigits.all { it.isDigit() }) {
            return Result.failure(IllegalArgumentException("Solo se permiten los últimos 4 dígitos numéricos"))
        }

        return repository.registerDebitCard(card, operationId)
    }
}

class RegisterCreditCard @Inject constructor(
    private val repository: FinancialInstrumentsRepository,
) {
    suspend operator fun invoke(
        card: CreditCard,
        operationId: OperationId = OperationId.generate(),
    ): Result<CreditCard> {
        val currentCount = repository.getActiveComputableCount()
        if (currentCount >= FinancialInstrumentsRepository.FREE_TIER_MAX_COMPUTABLE_INSTRUMENTS) {
            return Result.failure(QuotaExceededException("Límite de instrumentos activos alcanzado para el plan Free (máx. ${FinancialInstrumentsRepository.FREE_TIER_MAX_COMPUTABLE_INSTRUMENTS})"))
        }

        if (card.lastFourDigits.length != 4 || !card.lastFourDigits.all { it.isDigit() }) {
            return Result.failure(IllegalArgumentException("Solo se permiten los últimos 4 dígitos numéricos"))
        }

        if (card.creditLimitMinorUnits < 0) {
            return Result.failure(IllegalArgumentException("La línea de crédito no puede ser negativa"))
        }

        if (card.billingDay !in 1..31) {
            return Result.failure(IllegalArgumentException("El día de corte debe estar entre 1 y 31"))
        }

        if (card.dueDay !in 1..31) {
            return Result.failure(IllegalArgumentException("El día de pago debe estar entre 1 y 31"))
        }

        return repository.registerCreditCard(card, operationId)
    }
}

class CheckCardDuplicate @Inject constructor(
    private val repository: FinancialInstrumentsRepository,
) {
    suspend operator fun invoke(
        issuer: String,
        network: CardNetwork,
        lastFourDigits: String,
    ): Boolean {
        return repository.hasCardWithIdentity(issuer, network, lastFourDigits)
    }
}

class DeleteUnusedCard @Inject constructor(
    private val repository: FinancialInstrumentsRepository,
) {
    suspend operator fun invoke(
        cardId: CardId,
        operationId: OperationId = OperationId.generate(),
    ): Result<Unit> {
        return repository.deleteUnusedCard(cardId, operationId)
    }
}
