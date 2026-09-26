package com.kipu.app.feature.accounts.domain.usecase

import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.accounts.domain.model.PersonalTea
import com.kipu.app.feature.accounts.domain.FinancialInstrumentsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetReferentialRates @Inject constructor(
    private val repository: FinancialInstrumentsRepository,
) {
    suspend operator fun invoke() = repository.getCreditProductCatalog()
}

class UpdatePersonalTea @Inject constructor(
    private val repository: FinancialInstrumentsRepository,
) {
    suspend operator fun invoke(cardId: CardId, teaBps: Int?): Result<PersonalTea?> {
        if (teaBps != null && teaBps !in 0..100_000) {
            return Result.failure(IllegalArgumentException("TEA must be from 0% through 1000%"))
        }
        return repository.updatePersonalTea(cardId, teaBps, com.kipu.app.core.finance.domain.model.OperationId.generate())
            .map { teaBps?.let { value -> PersonalTea(cardId, value) } }
    }
}
