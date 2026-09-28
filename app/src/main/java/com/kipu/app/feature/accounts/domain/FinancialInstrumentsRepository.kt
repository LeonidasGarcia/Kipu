package com.kipu.app.feature.accounts.domain

import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.FinancialMovement
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.OperationId
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.Card
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CardPreset
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditProductReference
import com.kipu.app.feature.accounts.domain.model.CreditUtilizationNotification
import com.kipu.app.feature.accounts.domain.model.DebitCard
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface FinancialInstrumentsRepository {
    companion object {
        const val FREE_TIER_MAX_COMPUTABLE_INSTRUMENTS = 4
    }

    // Account operations
    suspend fun createLiquidAccount(account: Account, operationId: OperationId): Result<Account>
    suspend fun recordOpeningAdjustment(accountId: AccountId, correctedAmount: Money, correctedDate: Instant, operationId: OperationId): Result<Unit>
    suspend fun updateAccountAppearance(accountId: AccountId, alias: String, preset: AccountPreset?, colorToken: String?, iconToken: String?, operationId: OperationId): Result<Unit>
    
    // Card operations
    suspend fun registerDebitCard(card: DebitCard, operationId: OperationId): Result<DebitCard>
    suspend fun registerCreditCard(card: CreditCard, operationId: OperationId): Result<CreditCard>
    suspend fun updatePersonalTea(cardId: CardId, teaBps: Int?, operationId: OperationId): Result<Unit> =
        Result.failure(UnsupportedOperationException("Personal TEA updates are unavailable"))
    suspend fun getCreditProductCatalog(): Result<List<CreditProductReference>> =
        Result.failure(UnsupportedOperationException("Credit-product catalog is unavailable"))
    suspend fun getCreditUtilizationNotifications(cardId: String? = null): Result<List<CreditUtilizationNotification>> =
        Result.failure(UnsupportedOperationException("Shared credit notifications are unavailable"))
    suspend fun updateCardAppearance(cardId: CardId, alias: String?, preset: CardPreset?, colorToken: String?, iconToken: String?, operationId: OperationId): Result<Unit>
    suspend fun deleteUnusedCard(cardId: CardId, operationId: OperationId): Result<Unit>
    suspend fun payCreditCard(
        cardId: CardId,
        sourceAccountId: AccountId,
        paymentAmount: Money,
        effectiveAt: Instant,
        operationId: OperationId = OperationId.generate(),
    ): Result<Unit>
    suspend fun confirmCreditPurchase(
        cardId: CardId,
        amount: Money,
        merchant: String,
        effectiveAt: Instant,
        installments: Int = 1,
        operationId: OperationId = OperationId.generate(),
        categoryId: String,
        merchantId: String? = null,
        note: String? = null,
    ): Result<Unit>

    // Shared lifecycle
    suspend fun archiveInstrument(instrumentId: String, isCard: Boolean, operationId: OperationId): Result<Unit>
    suspend fun reactivateInstrument(instrumentId: String, isCard: Boolean, operationId: OperationId): Result<Unit>

    // Queries & Flows (owner-scoped by SessionCoordinator)
    fun observeAccounts(activeOnly: Boolean = false): Flow<List<Account>>
    fun observeAccountById(accountId: AccountId): Flow<Account?>
    fun observeAccountBalance(accountId: AccountId): Flow<Money>
    fun observeCards(activeOnly: Boolean = false): Flow<List<Card>>
    fun observeCardById(cardId: CardId): Flow<Card?>
    fun observeCardDebt(cardId: CardId): Flow<Money>
    fun observeMovementsByAccount(accountId: AccountId): Flow<List<FinancialMovement>>
    fun observeActiveComputableCount(): Flow<Int>
    suspend fun getActiveComputableCount(): Int
    suspend fun hasCardWithIdentity(issuer: String, network: CardNetwork, lastFourDigits: String): Boolean

    fun observeSelectedFreeInstrumentIds(): Flow<Set<String>> = flowOf(emptySet())
    suspend fun saveSelectedFreeInstrumentIds(ids: Set<String>): Result<Unit> =
        Result.failure(UnsupportedOperationException("Instrument quota selection is unavailable"))
}
