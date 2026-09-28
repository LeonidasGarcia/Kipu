package com.kipu.app.feature.accounts.domain

import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.FinancialMovement
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.OperationId
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.Card
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CardPreset
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.DebitCard
import com.kipu.app.feature.accounts.domain.usecase.ArchiveInstrument
import com.kipu.app.feature.accounts.domain.usecase.CreateLiquidAccount
import com.kipu.app.feature.accounts.domain.usecase.ConfirmCreditPurchase
import com.kipu.app.feature.accounts.domain.usecase.QuotaExceededException
import com.kipu.app.feature.accounts.domain.usecase.ReactivateInstrument
import com.kipu.app.feature.accounts.domain.usecase.RecordOpeningAdjustment
import com.kipu.app.feature.accounts.domain.usecase.UpdateInstrumentAppearance
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

open class FakeFinancialInstrumentsRepository : FinancialInstrumentsRepository {
    val createdAccounts = mutableListOf<Account>()
    val recordedAdjustments = mutableListOf<Triple<AccountId, Money, Instant>>()
    val archivedInstruments = mutableListOf<String>()
    val reactivatedInstruments = mutableListOf<String>()
    var activeComputableCount = 0
    data class ConfirmedPurchase(
        val cardId: CardId,
        val amount: Money,
        val merchant: String,
        val effectiveAt: Instant,
        val installments: Int,
        val categoryId: String?,
        val merchantId: String?,
        val note: String?,
    )
    val confirmedPurchases = mutableListOf<ConfirmedPurchase>()

    override suspend fun createLiquidAccount(account: Account, operationId: OperationId): Result<Account> {
        createdAccounts.add(account)
        if (account.isComputableForQuota) {
            activeComputableCount++
        }
        return Result.success(account)
    }

    override suspend fun recordOpeningAdjustment(
        accountId: AccountId,
        correctedAmount: Money,
        correctedDate: Instant,
        operationId: OperationId
    ): Result<Unit> {
        recordedAdjustments.add(Triple(accountId, correctedAmount, correctedDate))
        return Result.success(Unit)
    }

    override suspend fun updateAccountAppearance(
        accountId: AccountId,
        alias: String,
        preset: AccountPreset?,
        colorToken: String?,
        iconToken: String?,
        operationId: OperationId
    ): Result<Unit> = Result.success(Unit)

    override suspend fun registerDebitCard(card: DebitCard, operationId: OperationId): Result<DebitCard> = Result.success(card)

    override suspend fun registerCreditCard(card: CreditCard, operationId: OperationId): Result<CreditCard> = Result.success(card)

    override suspend fun updateCardAppearance(
        cardId: CardId,
        alias: String?,
        preset: CardPreset?,
        colorToken: String?,
        iconToken: String?,
        operationId: OperationId
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteUnusedCard(cardId: CardId, operationId: OperationId): Result<Unit> = Result.success(Unit)

    override suspend fun payCreditCard(
        cardId: CardId,
        sourceAccountId: AccountId,
        paymentAmount: Money,
        effectiveAt: Instant,
        operationId: OperationId
    ): Result<Unit> = Result.success(Unit)

    override suspend fun confirmCreditPurchase(
        cardId: CardId,
        amount: Money,
        merchant: String,
        effectiveAt: Instant,
        installments: Int,
        operationId: OperationId,
        categoryId: String,
        merchantId: String?,
        note: String?,
    ): Result<Unit> {
        confirmedPurchases += ConfirmedPurchase(cardId, amount, merchant, effectiveAt, installments, categoryId, merchantId, note)
        return Result.success(Unit)
    }

    override suspend fun archiveInstrument(instrumentId: String, isCard: Boolean, operationId: OperationId): Result<Unit> {
        archivedInstruments.add(instrumentId)
        if (activeComputableCount > 0) activeComputableCount--
        return Result.success(Unit)
    }

    override suspend fun reactivateInstrument(instrumentId: String, isCard: Boolean, operationId: OperationId): Result<Unit> {
        reactivatedInstruments.add(instrumentId)
        activeComputableCount++
        return Result.success(Unit)
    }

    override fun observeAccounts(activeOnly: Boolean): Flow<List<Account>> = 
        flowOf(if (activeOnly) createdAccounts.filter { !it.isArchived } else createdAccounts)
    override fun observeAccountById(accountId: AccountId): Flow<Account?> = flowOf(createdAccounts.find { it.id == accountId })
    override fun observeAccountBalance(accountId: AccountId): Flow<Money> = flowOf(Money(0L, Currency.PEN))
    override fun observeCards(activeOnly: Boolean): Flow<List<Card>> = flowOf(emptyList())
    override fun observeCardById(cardId: CardId): Flow<Card?> = flowOf(null)
    override fun observeCardDebt(cardId: CardId): Flow<Money> = flowOf(Money(0L, Currency.PEN))
    override fun observeMovementsByAccount(accountId: AccountId): Flow<List<FinancialMovement>> = flowOf(emptyList())
    override fun observeActiveComputableCount(): Flow<Int> = flowOf(activeComputableCount)
    override suspend fun getActiveComputableCount(): Int = activeComputableCount
    override suspend fun hasCardWithIdentity(issuer: String, network: CardNetwork, lastFourDigits: String): Boolean = false
}

class CreateLiquidAccountTest {

    private lateinit var fakeRepository: FakeFinancialInstrumentsRepository
    private lateinit var createLiquidAccount: CreateLiquidAccount
    private lateinit var recordOpeningAdjustment: RecordOpeningAdjustment
    private lateinit var archiveInstrument: ArchiveInstrument
    private lateinit var reactivateInstrument: ReactivateInstrument
    private lateinit var updateAppearance: UpdateInstrumentAppearance
    private lateinit var confirmCreditPurchase: ConfirmCreditPurchase

    private val testUserId = UserId.generate()

    @Before
    fun setup() {
        fakeRepository = FakeFinancialInstrumentsRepository()
        createLiquidAccount = CreateLiquidAccount(fakeRepository)
        recordOpeningAdjustment = RecordOpeningAdjustment(fakeRepository)
        archiveInstrument = ArchiveInstrument(fakeRepository)
        reactivateInstrument = ReactivateInstrument(fakeRepository)
        updateAppearance = UpdateInstrumentAppearance(fakeRepository)
        confirmCreditPurchase = ConfirmCreditPurchase(fakeRepository)
    }

    @Test
    fun `creating computable account when under quota succeeds`() = runTest {
        fakeRepository.activeComputableCount = 2

        val account = Account(
            id = AccountId.generate(),
            userId = testUserId,
            alias = "Cuenta Sueldo BCP",
            type = AccountType.SAVINGS,
            currency = Currency.PEN,
            preset = AccountPreset.BCP,
            initialBalance = Money(1500_00L, Currency.PEN),
            openedAt = Instant.now(),
        )

        val result = createLiquidAccount(account)
        assertTrue(result.isSuccess)
        assertEquals(1, fakeRepository.createdAccounts.size)
    }

    @Test
    fun `credit liability account cannot be created through liquid account flow`() = runTest {
        val account = Account(
            id = AccountId.generate(),
            userId = testUserId,
            alias = "Internal credit liability",
            type = AccountType.CREDIT_LIABILITY,
            currency = Currency.PEN,
            initialBalance = Money(0L, Currency.PEN),
            openedAt = Instant.now(),
        )

        val result = createLiquidAccount(account)

        assertTrue(result.isFailure)
        assertTrue(fakeRepository.createdAccounts.isEmpty())
    }

    @Test
    fun `credit purchase use case forwards category catalog merchant and note`() = runTest {
        val cardId = CardId.generate()
        val amount = Money(12_500L, Currency.PEN)
        val occurredAt = Instant.parse("2026-09-25T15:00:00Z")

        confirmCreditPurchase(
            cardId = cardId,
            amount = amount,
            merchant = "Tambo",
            effectiveAt = occurredAt,
            installments = 1,
            categoryId = "category-id",
            merchantId = "merchant-id",
            note = "Compra semanal",
        )

        val purchase = fakeRepository.confirmedPurchases.single()
        assertEquals(cardId, purchase.cardId)
        assertEquals(amount, purchase.amount)
        assertEquals(occurredAt, purchase.effectiveAt)
        assertEquals("category-id", purchase.categoryId)
        assertEquals("merchant-id", purchase.merchantId)
        assertEquals("Compra semanal", purchase.note)
    }

    @Test
    fun `creating computable account when quota reached fails with QuotaExceededException`() = runTest {
        fakeRepository.activeComputableCount = 4

        val account = Account(
            id = AccountId.generate(),
            userId = testUserId,
            alias = "Cuenta Ahorros BBVA",
            type = AccountType.BANK,
            currency = Currency.PEN,
            preset = AccountPreset.BBVA,
            initialBalance = Money(500_00L, Currency.PEN),
            openedAt = Instant.now(),
        )

        val result = createLiquidAccount(account)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is QuotaExceededException)
        assertEquals(0, fakeRepository.createdAccounts.size)
    }

    @Test
    fun `creating CASH account when quota is reached succeeds because CASH is exempt`() = runTest {
        fakeRepository.activeComputableCount = 4

        val account = Account(
            id = AccountId.generate(),
            userId = testUserId,
            alias = "Efectivo Billetera",
            type = AccountType.CASH,
            currency = Currency.PEN,
            preset = AccountPreset.CASH,
            initialBalance = Money(80_00L, Currency.PEN),
            openedAt = Instant.now(),
        )

        val result = createLiquidAccount(account)
        assertTrue(result.isSuccess)
        assertEquals(1, fakeRepository.createdAccounts.size)
    }

    @Test
    fun `recording opening adjustment with nonnegative amount succeeds`() = runTest {
        val accountId = AccountId.generate()
        val correctedAmount = Money(200_00L, Currency.PEN)
        val correctedDate = Instant.now()

        val result = recordOpeningAdjustment(accountId, correctedAmount, correctedDate)
        assertTrue(result.isSuccess)
        assertEquals(1, fakeRepository.recordedAdjustments.size)
        assertEquals(accountId, fakeRepository.recordedAdjustments[0].first)
        assertEquals(correctedAmount, fakeRepository.recordedAdjustments[0].second)
    }

    @Test
    fun `recording opening adjustment with negative amount fails`() = runTest {
        val accountId = AccountId.generate()
        val negativeAmount = Money(-50_00L, Currency.PEN)

        val result = recordOpeningAdjustment(accountId, negativeAmount, Instant.now())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, fakeRepository.recordedAdjustments.size)
    }

    @Test
    fun `reactivating computable instrument when quota reached fails`() = runTest {
        fakeRepository.activeComputableCount = 4

        val result = reactivateInstrument("account-123", isCard = false, isComputableForQuota = true)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is QuotaExceededException)
    }

    @Test
    fun `reactivating non-computable instrument when quota reached succeeds`() = runTest {
        fakeRepository.activeComputableCount = 4

        val result = reactivateInstrument("cash-123", isCard = false, isComputableForQuota = false)
        assertTrue(result.isSuccess)
        assertEquals(1, fakeRepository.reactivatedInstruments.size)
    }

    @Test
    fun `updating instrument appearance validates alias`() = runTest {
        val accountId = AccountId.generate()

        val blankResult = updateAppearance(accountId, "   ", null, null, null)
        assertTrue(blankResult.isFailure)

        val longAlias = "a".repeat(81)
        val longResult = updateAppearance(accountId, longAlias, null, null, null)
        assertTrue(longResult.isFailure)

        val validResult = updateAppearance(accountId, "Mi Cuenta", AccountPreset.BCP, null, null)
        assertTrue(validResult.isSuccess)
    }
}
