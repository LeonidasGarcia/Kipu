package com.kipu.app.feature.accounts.domain.usecase

import com.kipu.app.core.finance.domain.CreditCalculations
import com.kipu.app.core.finance.domain.CreditMetrics
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.feature.accounts.domain.FinancialInstrumentsRepository
import com.kipu.app.feature.accounts.domain.model.CreditCard
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

data class CreditCardDetailedSummary(
    val card: CreditCard,
    val metrics: CreditMetrics,
    val nextBillingDate: LocalDate,
    val nextDueDate: LocalDate,
)

class ObserveCreditCardSummary @Inject constructor(
    private val repository: FinancialInstrumentsRepository,
) {
    operator fun invoke(cardId: CardId): Flow<CreditCardDetailedSummary?> {
        return combine(
            repository.observeCardById(cardId),
            repository.observeCardDebt(cardId),
        ) { card, debt ->
            val creditCard = card as? CreditCard ?: return@combine null

            val metrics = CreditCalculations.calculateCreditMetrics(
                creditLimitMinorUnits = creditCard.creditLimitMinorUnits,
                debtMinorUnits = debt.minorUnits,
                currency = creditCard.currency,
            )

            val nextBillingDate = CreditCalculations.calculateNextDate(creditCard.billingDay)
            val nextDueDate = CreditCalculations.calculateNextDate(creditCard.dueDay)

            CreditCardDetailedSummary(
                card = creditCard,
                metrics = metrics,
                nextBillingDate = nextBillingDate,
                nextDueDate = nextDueDate,
            )
        }
    }
}
