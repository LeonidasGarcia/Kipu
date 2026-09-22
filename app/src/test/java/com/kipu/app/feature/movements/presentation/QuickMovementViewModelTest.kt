package com.kipu.app.feature.movements.presentation

import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.domain.FinancialInstrumentsRepository
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.Card
import com.kipu.app.feature.accounts.domain.usecase.ObserveInstruments
import com.kipu.app.feature.movements.domain.MovementRepository
import com.kipu.app.feature.movements.domain.RegisterTransaction
import com.kipu.app.feature.movements.domain.RegisterTransactionValidator
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.RegisterTransactionCommand
import com.kipu.app.feature.movements.domain.model.RegisterTransactionResult
import com.kipu.app.feature.movements.domain.model.Transaction
import com.kipu.app.feature.movements.domain.model.TransactionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class QuickMovementViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testAccountId = AccountId.generate()
    private val testUserId = UserId.generate()
    private lateinit var fakeMovementRepo: FakeMovementRepo
    private lateinit var fakeInstrumentsRepo: FakeInstrumentsRepo
    private lateinit var fakeSessionCoordinator: FakeSessionCoordinator
    private lateinit var viewModel: QuickMovementViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeMovementRepo = FakeMovementRepo()
        fakeInstrumentsRepo = FakeInstrumentsRepo(testAccountId, testUserId)
        fakeSessionCoordinator = FakeSessionCoordinator(testUserId.value)

        val validator = RegisterTransactionValidator()
        val registerUseCase = RegisterTransaction(fakeMovementRepo, validator)
        val observeInstruments = ObserveInstruments(fakeInstrumentsRepo)

        viewModel = QuickMovementViewModel(
            registerTransactionUseCase = registerUseCase,
            observeInstruments = observeInstruments,
            sessionCoordinator = fakeSessionCoordinator,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state defaults to expense with active accounts`() = runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(MovementType.EXPENSE, state.type)
        assertEquals(testAccountId.value, state.selectedSourceAccountId)
    }

    @Test
    fun `changing type clears previous errors`() = runTest {
        advanceUntilIdle()
        viewModel.onAmountChanged("")
        viewModel.onSave()
        assertNotNull(viewModel.uiState.value.amountError)

        viewModel.onTypeSelected(MovementType.INCOME)
        assertEquals(null, viewModel.uiState.value.amountError)
        assertEquals(MovementType.INCOME, viewModel.uiState.value.type)
    }

    @Test
    fun `saving without amount sets amountError`() = runTest {
        advanceUntilIdle()
        viewModel.onAmountChanged("0")
        viewModel.onSave()
        assertNotNull(viewModel.uiState.value.amountError)
    }

    @Test
    fun `saving expense without category sets categoryError`() = runTest {
        advanceUntilIdle()
        viewModel.onAmountChanged("50.00")
        viewModel.onSave()
        assertNotNull(viewModel.uiState.value.categoryError)
    }

    @Test
    fun `valid expense registration succeeds`() = runTest {
        advanceUntilIdle()
        viewModel.onAmountChanged("25.50")
        viewModel.onCategorySelected(CategoryOption("cat-food", "Alimentación", "restaurant"))
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(1, fakeMovementRepo.registeredCommands.size)
        assertEquals(2550L, fakeMovementRepo.registeredCommands.first().amountMinor)
    }

    @Test
    fun `duplicate warning shows dialog and confirm registers with new key`() = runTest {
        advanceUntilIdle()
        fakeMovementRepo.triggerDuplicateWarning = true

        viewModel.onAmountChanged("30.00")
        viewModel.onCategorySelected(CategoryOption("cat-food", "Alimentación", "restaurant"))
        viewModel.onSave()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showDuplicateWarning)
        val initialKey = viewModel.uiState.value.pendingCommandForDuplicate?.idempotencyKey
        assertNotNull(initialKey)

        fakeMovementRepo.triggerDuplicateWarning = false
        viewModel.onConfirmDuplicate()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.showDuplicateWarning)
        assertEquals(1, fakeMovementRepo.registeredCommands.size)
        assertTrue(fakeMovementRepo.registeredCommands.first().ignoreSimilarityWarning)
    }

    private class FakeMovementRepo : MovementRepository {
        val registeredCommands = mutableListOf<RegisterTransactionCommand>()
        var triggerDuplicateWarning = false

        override fun observeTransactions(userId: String): Flow<List<TransactionItem>> = flowOf(emptyList())
        override fun observeRecentTransactions(userId: String, limit: Int): Flow<List<TransactionItem>> = flowOf(emptyList())
        override suspend fun getTransactionById(userId: String, transactionId: String): Transaction? = null

        override suspend fun registerTransaction(command: RegisterTransactionCommand): RegisterTransactionResult {
            registeredCommands.add(command)
            return RegisterTransactionResult.Success(
                Transaction(
                    id = UUID.randomUUID().toString(),
                    userId = command.userId,
                    type = command.type,
                    amountMinor = command.amountMinor,
                    currency = command.currency,
                    sourceAccountId = command.sourceAccountId,
                    destinationAccountId = command.destinationAccountId,
                    categoryId = command.categoryId,
                    occurredAt = command.occurredAt,
                )
            )
        }

        override suspend fun findSimilarTransactions(
            userId: String,
            sourceAccountId: String,
            type: MovementType,
            amountMinor: Long,
            currency: String,
            occurredAt: Long,
            windowMillis: Long,
        ): List<Transaction> {
            if (triggerDuplicateWarning) {
                return listOf(
                    Transaction(
                        id = "tx_sim",
                        userId = userId,
                        type = type,
                        amountMinor = amountMinor,
                        currency = currency,
                        sourceAccountId = sourceAccountId,
                        categoryId = "cat-food",
                        occurredAt = occurredAt - 1000L,
                    )
                )
            }
            return emptyList()
        }

        override fun observeBalance(userId: String, accountId: String): Flow<Long?> = flowOf(0L)
    }

    private class FakeInstrumentsRepo(val accountId: AccountId, val userId: UserId) : FinancialInstrumentsRepository {
        val accounts = listOf(
            Account(
                id = accountId,
                userId = userId,
                alias = "BCP Sueldo",
                type = AccountType.SAVINGS,
                currency = Currency.PEN,
                initialBalance = Money(100000L, Currency.PEN),
                openedAt = java.time.Instant.now(),
            )
        )

        override fun observeAccounts(activeOnly: Boolean): Flow<List<Account>> = flowOf(accounts)
        override fun observeCards(activeOnly: Boolean): Flow<List<Card>> = flowOf(emptyList())
        override fun observeActiveComputableCount(): Flow<Int> = flowOf(1)
        override fun observeAccountById(accountId: AccountId): Flow<Account?> = flowOf(accounts.find { it.id == accountId })
        override fun observeAccountBalance(accountId: AccountId): Flow<Money> = flowOf(Money(100000L, Currency.PEN))
        override fun observeCardById(cardId: CardId): Flow<Card?> = flowOf(null)
        override fun observeCardDebt(cardId: CardId): Flow<Money> = flowOf(Money(0L, Currency.PEN))
        override fun observeMovementsByAccount(accountId: AccountId): Flow<List<com.kipu.app.core.finance.domain.model.FinancialMovement>> = flowOf(emptyList())
        override suspend fun getActiveComputableCount(): Int = 1
        override suspend fun hasCardWithIdentity(issuer: String, network: com.kipu.app.feature.accounts.domain.model.CardNetwork, lastFourDigits: String): Boolean = false
        override suspend fun createLiquidAccount(account: Account, operationId: com.kipu.app.core.finance.domain.model.OperationId): Result<Account> = Result.success(account)
        override suspend fun recordOpeningAdjustment(accountId: AccountId, correctedAmount: Money, correctedDate: java.time.Instant, operationId: com.kipu.app.core.finance.domain.model.OperationId): Result<Unit> = Result.success(Unit)
        override suspend fun updateAccountAppearance(accountId: AccountId, alias: String, preset: com.kipu.app.feature.accounts.domain.model.AccountPreset?, colorToken: String?, iconToken: String?, operationId: com.kipu.app.core.finance.domain.model.OperationId): Result<Unit> = Result.success(Unit)
        override suspend fun registerDebitCard(card: com.kipu.app.feature.accounts.domain.model.DebitCard, operationId: com.kipu.app.core.finance.domain.model.OperationId): Result<com.kipu.app.feature.accounts.domain.model.DebitCard> = Result.success(card)
        override suspend fun registerCreditCard(card: com.kipu.app.feature.accounts.domain.model.CreditCard, operationId: com.kipu.app.core.finance.domain.model.OperationId): Result<com.kipu.app.feature.accounts.domain.model.CreditCard> = Result.success(card)
        override suspend fun updateCardAppearance(cardId: CardId, alias: String?, preset: com.kipu.app.feature.accounts.domain.model.CardPreset?, colorToken: String?, iconToken: String?, operationId: com.kipu.app.core.finance.domain.model.OperationId): Result<Unit> = Result.success(Unit)
        override suspend fun deleteUnusedCard(cardId: CardId, operationId: com.kipu.app.core.finance.domain.model.OperationId): Result<Unit> = Result.success(Unit)
        override suspend fun payCreditCard(cardId: CardId, sourceAccountId: AccountId, paymentAmount: Money, effectiveAt: java.time.Instant, operationId: com.kipu.app.core.finance.domain.model.OperationId): Result<Unit> = Result.success(Unit)
        override suspend fun confirmCreditPurchase(cardId: CardId, amount: Money, merchant: String, effectiveAt: java.time.Instant, installments: Int, operationId: com.kipu.app.core.finance.domain.model.OperationId): Result<Unit> = Result.success(Unit)
        override suspend fun archiveInstrument(instrumentId: String, isCard: Boolean, operationId: com.kipu.app.core.finance.domain.model.OperationId): Result<Unit> = Result.success(Unit)
        override suspend fun reactivateInstrument(instrumentId: String, isCard: Boolean, operationId: com.kipu.app.core.finance.domain.model.OperationId): Result<Unit> = Result.success(Unit)
    }

    private class FakeSessionCoordinator(val userId: String) : SessionCoordinator {
        override val remoteSession: StateFlow<RemoteSession> = MutableStateFlow(RemoteSession.Valid(userId, java.time.Instant.now()))
        override val localAccess: StateFlow<LocalAccess> = MutableStateFlow(LocalAccess.Available(userId, RemoteSession.Valid(userId, java.time.Instant.now())))
        override val currentOwner: LocalOwner = LocalOwner(userId)
        override suspend fun setActiveOwner(userId: String) {}
        override suspend fun clearActiveOwner(explicit: Boolean) {}
        override suspend fun updateRemoteSession(session: RemoteSession) {}
        override suspend fun updateLockState(isLocked: Boolean, reason: String) {}
    }
}
