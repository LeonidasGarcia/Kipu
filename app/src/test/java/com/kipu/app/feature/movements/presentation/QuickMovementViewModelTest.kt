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
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.usecase.ConfirmCreditPurchase
import com.kipu.app.feature.accounts.domain.usecase.ObserveInstruments
import com.kipu.app.feature.accounts.domain.usecase.ObserveFinancialDashboard
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.model.Category
import com.kipu.app.feature.categories.domain.model.CategoryConflict
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.CategoryOrigin
import com.kipu.app.feature.categories.domain.model.CategoryPresentation
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.categories.domain.model.ConflictId
import com.kipu.app.feature.categories.domain.model.MerchantCategoryPreference
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.domain.model.MovementClassification
import com.kipu.app.feature.categories.domain.usecase.ObserveCategories
import com.kipu.app.feature.categories.domain.usecase.CreateCategory
import com.kipu.app.feature.categories.domain.usecase.EnsureInitialCategoryCatalog
import com.kipu.app.feature.categories.domain.usecase.ResolvePreferredCategory
import com.kipu.app.feature.categories.data.sync.CategorySyncScheduler
import com.kipu.app.core.finance.domain.model.MovementId
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
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class QuickMovementViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testAccountId = AccountId.generate()
    private val usdAccountId = AccountId.generate()
    private val testUserId = UserId.generate()
    private val testCategoryId = CategoryId.generate()
    private val testIncomeCategoryId = CategoryId.generate()
    private lateinit var fakeCategoriesRepo: FakeCategoriesRepo
    private val transferAccountId = AccountId.generate()
    private lateinit var fakeMovementRepo: FakeMovementRepo
    private lateinit var fakeInstrumentsRepo: FakeInstrumentsRepo
    private lateinit var fakeSessionCoordinator: FakeSessionCoordinator
    private lateinit var viewModel: QuickMovementViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeMovementRepo = FakeMovementRepo()
        fakeInstrumentsRepo = FakeInstrumentsRepo(testAccountId, usdAccountId, transferAccountId, testUserId)
        fakeSessionCoordinator = FakeSessionCoordinator(testUserId.value)
        fakeCategoriesRepo = FakeCategoriesRepo(testUserId, testCategoryId, testIncomeCategoryId)

        val validator = RegisterTransactionValidator()
        val registerUseCase = RegisterTransaction(fakeMovementRepo, validator)
        val observeInstruments = ObserveInstruments(fakeInstrumentsRepo)

        viewModel = QuickMovementViewModel(
            registerTransactionUseCase = registerUseCase,
            confirmCreditPurchaseUseCase = ConfirmCreditPurchase(fakeInstrumentsRepo),
            observeInstruments = observeInstruments,
            observeFinancialDashboard = ObserveFinancialDashboard(fakeInstrumentsRepo),
            observeCategories = ObserveCategories(fakeCategoriesRepo),
            resolvePreferredCategory = ResolvePreferredCategory(fakeCategoriesRepo),
            createCategoryUseCase = CreateCategory(fakeCategoriesRepo),
            ensureInitialCategoryCatalog = EnsureInitialCategoryCatalog(fakeCategoriesRepo),
            categorySyncScheduler = object : CategorySyncScheduler {
                override fun scheduleSync(userId: String) = Unit
                override fun cancelSync(userId: String) = Unit
            },
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
        assertTrue(state.availableCategories.any { it.id == testCategoryId.value })
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
    fun `category selector filters by income and hides categories for transfers`() = runTest {
        advanceUntilIdle()
        viewModel.onTypeSelected(MovementType.INCOME)

        assertEquals(listOf(testIncomeCategoryId.value), viewModel.uiState.value.availableCategories.map { it.id })

        viewModel.onTypeSelected(MovementType.TRANSFER)
        assertTrue(viewModel.uiState.value.availableCategories.isEmpty())
        assertEquals(null, viewModel.uiState.value.selectedCategoryId)
    }

    @Test
    fun `transfer command never carries a category`() = runTest {
        advanceUntilIdle()
        viewModel.onTypeSelected(MovementType.TRANSFER)
        viewModel.onAmountChanged("12.00")
        viewModel.onDestinationAccountSelected(transferAccountId.value)
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(null, fakeMovementRepo.registeredCommands.single().categoryId)
    }

    @Test
    fun `successful transfer event identifies transfer for immediate history visibility`() = runTest {
        advanceUntilIdle()
        val event = async { viewModel.events.first() }
        viewModel.onTypeSelected(MovementType.TRANSFER)
        viewModel.onAmountChanged("12.00")
        viewModel.onDestinationAccountSelected(transferAccountId.value)
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(MovementType.TRANSFER, (event.await() as QuickMovementUiEvent.TransactionSaved).movementType)
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
        viewModel.onCategorySelected(viewModel.uiState.value.availableCategories.first { it.id == testCategoryId.value })
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(1, fakeMovementRepo.registeredCommands.size)
        assertEquals(2550L, fakeMovementRepo.registeredCommands.first().amountMinor)
    }

    @Test
    fun `active credit card can be selected for expense without registering a cash transaction`() = runTest {
        advanceUntilIdle()
        val card = fakeInstrumentsRepo.creditCard
        assertEquals(listOf(card.id.value), viewModel.uiState.value.availableCreditCards.map { it.id.value })

        viewModel.onSourceCardSelected(card.id.value)
        assertNull(viewModel.uiState.value.selectedSourceAccountId)
        viewModel.onAmountChanged("25.50")
        viewModel.onCategorySelected(viewModel.uiState.value.availableCategories.first { it.id == testCategoryId.value })
        viewModel.onMerchantProvisionalText("Bodega")
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(card.id.value, fakeInstrumentsRepo.confirmedPurchases.single().cardId.value)
        assertEquals(2550L, fakeInstrumentsRepo.confirmedPurchases.single().amount.minorUnits)
        assertEquals("Bodega", fakeInstrumentsRepo.confirmedPurchases.single().merchant)
        assertEquals(testCategoryId.value, fakeInstrumentsRepo.confirmedPurchases.single().categoryId)
        assertTrue(fakeMovementRepo.registeredCommands.isEmpty())
    }

    @Test
    fun `leaving expense clears selected credit card source`() = runTest {
        advanceUntilIdle()
        val card = fakeInstrumentsRepo.creditCard

        viewModel.onSourceCardSelected(card.id.value)
        viewModel.onTypeSelected(MovementType.INCOME)

        assertNull(viewModel.uiState.value.selectedSourceCardId)
        assertEquals(testAccountId.value, viewModel.uiState.value.selectedSourceAccountId)
    }

    @Test
    fun `duplicate warning shows dialog and confirm registers with new key`() = runTest {
        advanceUntilIdle()
        fakeMovementRepo.triggerDuplicateWarning = true

        viewModel.onAmountChanged("30.00")
        viewModel.onCategorySelected(viewModel.uiState.value.availableCategories.first { it.id == testCategoryId.value })
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

    @Test
    fun `selecting a USD account updates the movement currency`() = runTest {
        advanceUntilIdle()
        viewModel.onSourceAccountSelected(usdAccountId.value)
        viewModel.onAmountChanged("25.50")
        viewModel.onCategorySelected(viewModel.uiState.value.availableCategories.first { it.id == testCategoryId.value })
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals("USD", fakeMovementRepo.registeredCommands.single().currency)
    }

    @Test
    fun `provisional merchant is not sent as a catalog UUID`() = runTest {
        advanceUntilIdle()
        viewModel.onAmountChanged("25.50")
        viewModel.onCategorySelected(viewModel.uiState.value.availableCategories.first { it.id == testCategoryId.value })
        viewModel.onMerchantProvisionalText("Bodega del barrio")
        viewModel.onSave()
        advanceUntilIdle()

        val command = fakeMovementRepo.registeredCommands.single()
        assertEquals(null, command.merchantId)
        assertEquals("Bodega del barrio", command.merchantProvisionalText)
    }

    @Test
    fun `selected occurrence date is saved and success reports pending local sync`() = runTest {
        advanceUntilIdle()
        val selectedDate = java.time.Instant.parse("2026-09-18T14:30:00Z").toEpochMilli()
        val event = async { viewModel.events.first() }

        viewModel.onAmountChanged("25.50")
        viewModel.onCategorySelected(viewModel.uiState.value.availableCategories.first { it.id == testCategoryId.value })
        viewModel.onOccurredAtChanged(selectedDate)
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(selectedDate, fakeMovementRepo.registeredCommands.single().occurredAt)
        assertEquals(
            QuickMovementUiEvent.TransactionSaved("Guardado en este dispositivo · Pendiente de sincronización"),
            event.await(),
        )
    }

    @Test
    fun `catalog merchant uses its UUID and clears provisional text`() = runTest {
        advanceUntilIdle()
        viewModel.onAmountChanged("25.50")
        viewModel.onCategorySelected(viewModel.uiState.value.availableCategories.first { it.id == testCategoryId.value })
        viewModel.onMerchantProvisionalText("Bodega del barrio")
        val merchantId = MerchantId.generate()
        viewModel.onMerchantSelected(MerchantCatalogEntry(merchantId, "Tambo", "tambo"))
        viewModel.onSave()
        advanceUntilIdle()

        val command = fakeMovementRepo.registeredCommands.single()
        assertEquals(merchantId.value, command.merchantId)
        assertEquals(null, command.merchantProvisionalText)
    }

    @Test
    fun `merchant preference is applied to a future movement`() = runTest {
        advanceUntilIdle()
        val merchant = MerchantCatalogEntry(
            MerchantId.generate(), "Tambo", "tambo", defaultCategoryId = testCategoryId,
        )
        fakeCategoriesRepo.merchantPreferences = listOf(
            MerchantCategoryPreference(testUserId, merchant.id, fakeCategoriesRepo.preferenceCategoryId)
        )

        viewModel.onMerchantSelected(merchant)
        advanceUntilIdle()

        assertEquals(fakeCategoriesRepo.preferenceCategoryId.value, viewModel.uiState.value.selectedCategoryId)
    }

    @Test
    fun `merchant preference does not override an explicit category choice`() = runTest {
        advanceUntilIdle()
        val merchant = MerchantCatalogEntry(
            MerchantId.generate(), "Tambo", "tambo", defaultCategoryId = testCategoryId,
        )
        fakeCategoriesRepo.merchantPreferences = listOf(
            MerchantCategoryPreference(testUserId, merchant.id, fakeCategoriesRepo.preferenceCategoryId)
        )
        val explicitlySelected = viewModel.uiState.value.availableCategories.first { it.id == testCategoryId.value }
        viewModel.onCategorySelected(explicitlySelected)
        viewModel.onTypeSelected(MovementType.EXPENSE)

        viewModel.onMerchantSelected(merchant)
        advanceUntilIdle()

        assertEquals(explicitlySelected.id, viewModel.uiState.value.selectedCategoryId)
    }

    @Test
    fun `saving waits for the merchant preference before using the category`() = runTest {
        advanceUntilIdle()
        val merchant = MerchantCatalogEntry(
            MerchantId.generate(), "Tambo", "tambo", defaultCategoryId = testCategoryId,
        )
        fakeCategoriesRepo.merchantPreferences = listOf(
            MerchantCategoryPreference(testUserId, merchant.id, fakeCategoriesRepo.preferenceCategoryId)
        )
        fakeCategoriesRepo.preferenceLookupDelayMillis = 100L

        viewModel.onMerchantSelected(merchant)
        viewModel.onAmountChanged("25.50")
        viewModel.onSave()

        assertTrue(viewModel.uiState.value.isSaving)
        assertTrue(fakeMovementRepo.registeredCommands.isEmpty())
        advanceTimeBy(50L)
        assertTrue(fakeMovementRepo.registeredCommands.isEmpty())
        advanceUntilIdle()

        assertEquals(fakeCategoriesRepo.preferenceCategoryId.value, fakeMovementRepo.registeredCommands.single().categoryId)
    }

    @Test
    fun `failed preference lookup does not save the general category`() = runTest {
        advanceUntilIdle()
        val merchant = MerchantCatalogEntry(
            MerchantId.generate(), "Tambo", "tambo", defaultCategoryId = testCategoryId,
        )
        fakeCategoriesRepo.preferenceLookupFailure = IllegalStateException("local read failed")

        viewModel.onMerchantSelected(merchant)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedCategoryId)
        assertEquals("No se pudo validar la preferencia del comercio.", viewModel.uiState.value.categoryError)
    }

    @Test
    fun `createCategoryOrSubcategory creates root category when parent is null and selects it`() = runTest {
        advanceUntilIdle()
        val result = viewModel.createCategoryOrSubcategory(
            parentCategoryId = null,
            name = "Suscripciones",
            icon = "tv",
            color = "#8B5CF6",
            rememberFrequentMerchant = false,
        )
        advanceUntilIdle()

        assertTrue(result.isSuccess)
        val created = result.getOrThrow()
        assertEquals("Suscripciones", created.name)
        assertNull(created.parentCategoryId)
        assertEquals(created.id, viewModel.uiState.value.selectedCategoryId)
        assertEquals("Suscripciones", viewModel.uiState.value.selectedCategoryName)
    }

    @Test
    fun `createCategoryOrSubcategory creates subcategory when parent is provided and selects it`() = runTest {
        advanceUntilIdle()
        val parent = viewModel.uiState.value.availableCategories.first { it.id == testCategoryId.value }
        val result = viewModel.createCategoryOrSubcategory(
            parentCategoryId = parent.id,
            name = "Supermercado",
            icon = "shopping_cart",
            color = "#06B6D4",
            rememberFrequentMerchant = false,
        )
        advanceUntilIdle()

        assertTrue(result.isSuccess)
        val created = result.getOrThrow()
        assertEquals("Supermercado", created.name)
        assertEquals(parent.id, created.parentCategoryId)
        assertEquals(created.id, viewModel.uiState.value.selectedCategoryId)
        assertEquals("${parent.name} > Supermercado", viewModel.uiState.value.selectedCategoryName)
    }

    private class FakeCategoriesRepo(
        private val userId: UserId,
        private val categoryId: CategoryId,
        private val incomeCategoryId: CategoryId,
    ) : CategoriesRepository {
        val preferenceCategoryId = CategoryId.generate()
        var merchantPreferences: List<MerchantCategoryPreference> = emptyList()
        var preferenceLookupDelayMillis = 0L
        var preferenceLookupFailure: Exception? = null

        private fun categories() = listOf(
            Category(categoryId, null, null, CategoryOrigin.SYSTEM, true, categoryType = CategoryType.EXPENSE),
            Category(incomeCategoryId, null, null, CategoryOrigin.SYSTEM, true, categoryType = CategoryType.INCOME),
            Category(
                id = preferenceCategoryId,
                ownerId = userId,
                parentId = null,
                origin = CategoryOrigin.CUSTOM,
                isActive = true,
                categoryType = CategoryType.EXPENSE,
            ),
        )

        override fun observeCategories(userId: UserId): Flow<List<Category>> = flowOf(categories())
        override fun observeCategoryPresentations(userId: UserId): Flow<List<CategoryPresentation>> = flowOf(listOf(
            CategoryPresentation(categoryId, this.userId, "Alimentación", "restaurant", "#ffffff"),
            CategoryPresentation(incomeCategoryId, this.userId, "Salario", "work", "#ffffff"),
            CategoryPresentation(preferenceCategoryId, this.userId, "Compras", "shopping_cart", "#ffffff"),
        ))
        override suspend fun getCategory(categoryId: CategoryId): Category? = categories().firstOrNull { it.id == categoryId }
        override suspend fun createCategory(category: Category, presentation: CategoryPresentation): Result<Category> = Result.success(category)
        override suspend fun setCategoryActive(categoryId: CategoryId, isActive: Boolean): Result<Unit> = Result.failure(UnsupportedOperationException())
        override suspend fun updateCategoryPresentation(presentation: CategoryPresentation, expectedRevision: Long): Result<Unit> = Result.failure(UnsupportedOperationException())
        override fun searchMerchants(query: String): Flow<List<MerchantCatalogEntry>> = flowOf(emptyList())
        override fun observeMerchantCategoryPreferences(userId: UserId): Flow<List<MerchantCategoryPreference>> = flow {
            if (preferenceLookupDelayMillis > 0) delay(preferenceLookupDelayMillis)
            preferenceLookupFailure?.let { throw it }
            emit(merchantPreferences)
        }
        override fun observeMovementClassification(movementId: MovementId): Flow<MovementClassification?> = flowOf(null)
        override suspend fun updateMovementClassification(classification: MovementClassification): Result<Unit> = Result.failure(UnsupportedOperationException())
        override suspend fun clearCategoryClassification(movementId: MovementId): Result<Unit> = Result.failure(UnsupportedOperationException())
        override suspend fun clearMerchantClassification(movementId: MovementId): Result<Unit> = Result.failure(UnsupportedOperationException())
        override fun observeConflicts(userId: UserId): Flow<List<CategoryConflict>> = flowOf(emptyList())
        override suspend fun resolveConflict(conflictId: ConflictId, chosenVersion: String): Result<Unit> = Result.failure(UnsupportedOperationException())
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

    private class FakeInstrumentsRepo(
        val accountId: AccountId,
        val usdAccountId: AccountId,
        val transferAccountId: AccountId,
        val userId: UserId,
    ) : FinancialInstrumentsRepository {
        val creditCard = CreditCard(
            id = CardId.generate(),
            userId = userId,
            alias = "BCP Visa",
            issuer = "BCP",
            network = CardNetwork.VISA,
            lastFourDigits = "7548",
            currency = Currency.PEN,
            creditLimitMinorUnits = 500000L,
            billingDay = 15,
            dueDay = 5,
        )
        data class Purchase(
            val cardId: CardId,
            val amount: Money,
            val merchant: String,
            val categoryId: String?,
        )
        val confirmedPurchases = mutableListOf<Purchase>()

        val accounts = listOf(
            Account(
                id = accountId,
                userId = userId,
                alias = "BCP Sueldo",
                type = AccountType.SAVINGS,
                currency = Currency.PEN,
                initialBalance = Money(100000L, Currency.PEN),
                openedAt = java.time.Instant.now(),
            ),
            Account(
                id = usdAccountId,
                userId = userId,
                alias = "Dólares",
                type = AccountType.SAVINGS,
                currency = Currency.USD,
                initialBalance = Money(100000L, Currency.USD),
                openedAt = java.time.Instant.now(),
            ),
            Account(
                id = transferAccountId,
                userId = userId,
                alias = "Cuenta secundaria",
                type = AccountType.SAVINGS,
                currency = Currency.PEN,
                initialBalance = Money(100000L, Currency.PEN),
                openedAt = java.time.Instant.now(),
            ),
        )

        override fun observeAccounts(activeOnly: Boolean): Flow<List<Account>> = flowOf(accounts)
        override fun observeCards(activeOnly: Boolean): Flow<List<Card>> = flowOf(listOf(creditCard))
        override fun observeActiveComputableCount(): Flow<Int> = flowOf(1)
        override fun observeAccountById(accountId: AccountId): Flow<Account?> = flowOf(accounts.find { it.id == accountId })
        override fun observeAccountBalance(accountId: AccountId): Flow<Money> {
            val account = accounts.firstOrNull { it.id == accountId }
            return flowOf(Money(100000L, account?.currency ?: Currency.PEN))
        }
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
        override suspend fun confirmCreditPurchase(cardId: CardId, amount: Money, merchant: String, effectiveAt: java.time.Instant, installments: Int, operationId: com.kipu.app.core.finance.domain.model.OperationId, categoryId: String, merchantId: String?, note: String?): Result<Unit> {
            confirmedPurchases += Purchase(cardId, amount, merchant, categoryId)
            return Result.success(Unit)
        }
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
