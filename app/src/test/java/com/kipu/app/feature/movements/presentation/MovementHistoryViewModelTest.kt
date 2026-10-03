package com.kipu.app.feature.movements.presentation

import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.movements.domain.MovementRepository
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.RegisterTransactionCommand
import com.kipu.app.feature.movements.domain.model.RegisterTransactionResult
import com.kipu.app.feature.movements.domain.model.Transaction
import com.kipu.app.feature.movements.domain.model.TransactionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MovementHistoryViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()
    private lateinit var repository: RecordingMovementRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        repository = RecordingMovementRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun noOwnerNeverQueriesSharedPlaceholderHistory() = runTest {
        val viewModel = MovementHistoryViewModel(
            movementRepository = repository,
            sessionCoordinator = TestSessionCoordinator(LocalAccess.NoOwner),
        )
        val state = async { viewModel.uiState.first { !it.isLoading } }

        advanceUntilIdle()

        assertTrue(state.await().allTransactions.isEmpty())
        assertEquals(emptyList<String>(), repository.observedOwners)
    }

    @Test
    fun transferConfirmationClearsSearchAndReturnsHistoryToAllTypes() = runTest {
        val viewModel = MovementHistoryViewModel(
            movementRepository = repository,
            sessionCoordinator = TestSessionCoordinator(LocalAccess.NoOwner),
        )
        viewModel.onFilterTypeSelected(MovementType.EXPENSE)
        viewModel.onSearchQueryChanged("no coincide")

        viewModel.showAllTransactionsAfterTransfer()

        assertEquals(null, viewModel.uiState.value.selectedFilterType)
        assertEquals("", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun deepLinkArgumentsPopulateQueryAndFilters() = runTest {
        val handle = androidx.lifecycle.SavedStateHandle(
            mapOf(
                "query" to "farmacia",
                "accountId" to "acc-test-1",
                "categoryId" to "cat-test-2",
            )
        )
        val viewModel = MovementHistoryViewModel(
            movementRepository = repository,
            sessionCoordinator = TestSessionCoordinator(LocalAccess.Available("user-alice", RemoteSession.Absent)),
            savedStateHandle = handle,
        )

        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertEquals("farmacia", viewModel.uiState.value.searchQuery)
        assertEquals(setOf("acc-test-1"), viewModel.uiState.value.selectedAccountIds)
        assertEquals(setOf("cat-test-2"), viewModel.uiState.value.selectedCategoryIds)
        assertTrue(viewModel.uiState.value.hasActiveAdvancedFilters)
    }

    @Test
    fun advancedFiltersFilterTransactionsAccurately() = runTest {
        val tx1 = createTransactionItem(
            id = "tx-1",
            type = MovementType.EXPENSE,
            amountMinor = 1_500L,
            sourceAccountId = "acc-wallet",
            categoryId = "cat-food",
            occurredAt = 1_000_000L,
            status = com.kipu.app.feature.movements.domain.model.TransactionStatus.ACTIVE,
        )
        val tx2 = createTransactionItem(
            id = "tx-2",
            type = MovementType.EXPENSE,
            amountMinor = 5_000L,
            sourceAccountId = "acc-bank",
            categoryId = "cat-pharmacy",
            occurredAt = 2_000_000L,
            status = com.kipu.app.feature.movements.domain.model.TransactionStatus.ACTIVE,
        )
        val tx3 = createTransactionItem(
            id = "tx-3",
            type = MovementType.TRANSFER,
            amountMinor = 10_000L,
            sourceAccountId = "acc-bank",
            destinationAccountId = "acc-wallet",
            occurredAt = 3_000_000L,
            status = com.kipu.app.feature.movements.domain.model.TransactionStatus.VOIDED,
        )

        val repoWithData = RecordingMovementRepository(listOf(tx1, tx2, tx3))
        val viewModel = MovementHistoryViewModel(
            movementRepository = repoWithData,
            sessionCoordinator = TestSessionCoordinator(LocalAccess.Available("user-alice", RemoteSession.Absent)),
        )

        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.allTransactions.size)

        // Filter by account
        viewModel.onAccountFilterToggled("acc-wallet")
        advanceUntilIdle()
        var currentItems = viewModel.uiState.value.filteredTransactions.values.flatten()
        assertEquals(2, currentItems.size) // tx1 (source acc-wallet) and tx3 (dest acc-wallet)

        // Filter by amount range
        viewModel.onAmountRangeChanged(min = 1_000L, max = 2_000L)
        advanceUntilIdle()
        currentItems = viewModel.uiState.value.filteredTransactions.values.flatten()
        assertEquals(1, currentItems.size)
        assertEquals("tx-1", currentItems.first().transaction.id)

        // Filter by financial state VOIDED
        viewModel.onClearFilters()
        viewModel.onFinancialStateToggled(com.kipu.app.feature.movements.domain.model.MovementFinancialState.VOIDED)
        advanceUntilIdle()
        currentItems = viewModel.uiState.value.filteredTransactions.values.flatten()
        assertEquals(1, currentItems.size)
        assertEquals("tx-3", currentItems.first().transaction.id)

        // Filter by date range
        viewModel.onClearFilters()
        viewModel.onDateRangeSelected(from = 1_500_000L, to = 2_500_000L)
        advanceUntilIdle()
        currentItems = viewModel.uiState.value.filteredTransactions.values.flatten()
        assertEquals(1, currentItems.size)
        assertEquals("tx-2", currentItems.first().transaction.id)
    }

    @Test
    fun clearAndResetAdvancedFiltersBehaveCorrectly() = runTest {
        val viewModel = MovementHistoryViewModel(
            movementRepository = repository,
            sessionCoordinator = TestSessionCoordinator(LocalAccess.Available("user-alice", RemoteSession.Absent)),
        )

        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onToggleAdvancedFilterPanel(true)
        viewModel.onSearchQueryChanged("test query")
        viewModel.onFilterTypeSelected(MovementType.INCOME)
        viewModel.onAccountFilterToggled("acc-1")
        viewModel.onAmountRangeChanged(100L, 500L)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showAdvancedFilterPanel)
        assertTrue(viewModel.uiState.value.hasActiveAdvancedFilters)

        // Reset advanced filters: keeps panel open, clears criteria
        viewModel.onResetAdvancedFilters()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showAdvancedFilterPanel)
        org.junit.Assert.assertFalse(viewModel.uiState.value.hasActiveAdvancedFilters)
        assertEquals("test query", viewModel.uiState.value.searchQuery)
        assertEquals(MovementType.INCOME, viewModel.uiState.value.selectedFilterType)

        // Clear all filters: resets everything
        viewModel.onClearFilters()
        advanceUntilIdle()
        assertEquals("", viewModel.uiState.value.searchQuery)
        assertEquals(null, viewModel.uiState.value.selectedFilterType)
        org.junit.Assert.assertFalse(viewModel.uiState.value.hasActiveAdvancedFilters)
    }

    @Test
    fun authenticatedSessionDerivesOwnerFromCoordinator() = runTest {
        val coordinator = TestSessionCoordinator(LocalAccess.Available("owner-42", RemoteSession.Absent))
        val viewModel = MovementHistoryViewModel(
            movementRepository = repository,
            sessionCoordinator = coordinator,
        )

        val state = async { viewModel.uiState.first { !it.isLoading } }
        advanceUntilIdle()
        state.await()

        assertEquals(listOf("owner-42"), repository.observedOwners)
    }

    private fun createTransactionItem(
        id: String,
        type: MovementType,
        amountMinor: Long,
        sourceAccountId: String,
        destinationAccountId: String? = null,
        categoryId: String? = "cat-default",
        occurredAt: Long = 1_000_000L,
        status: com.kipu.app.feature.movements.domain.model.TransactionStatus = com.kipu.app.feature.movements.domain.model.TransactionStatus.ACTIVE,
    ) = TransactionItem(
        transaction = Transaction(
            id = id,
            userId = "user-alice",
            type = type,
            amountMinor = amountMinor,
            currency = "PEN",
            sourceAccountId = sourceAccountId,
            destinationAccountId = destinationAccountId,
            categoryId = categoryId,
            occurredAt = occurredAt,
            status = status,
        ),
        categoryName = categoryId,
        sourceAccountAlias = sourceAccountId,
    )

    private class TestSessionCoordinator(initialAccess: LocalAccess) : SessionCoordinator {
        override val remoteSession = MutableStateFlow<RemoteSession>(RemoteSession.Absent)
        override val localAccess = MutableStateFlow(initialAccess)
        override val currentOwner: LocalOwner? = null
        override suspend fun setActiveOwner(userId: String) = Unit
        override suspend fun clearActiveOwner(explicit: Boolean) = Unit
        override suspend fun updateRemoteSession(session: RemoteSession) = Unit
        override suspend fun updateLockState(isLocked: Boolean, reason: String) = Unit
    }

    private class RecordingMovementRepository(
        private val initialTransactions: List<TransactionItem> = emptyList(),
    ) : MovementRepository {
        val observedOwners = mutableListOf<String>()

        override fun observeTransactions(userId: String): Flow<List<TransactionItem>> {
            observedOwners += userId
            return flowOf(initialTransactions)
        }

        override fun observeRecentTransactions(userId: String, limit: Int): Flow<List<TransactionItem>> = emptyFlow()
        override suspend fun getTransactionById(userId: String, transactionId: String): Transaction? = null
        override suspend fun registerTransaction(command: RegisterTransactionCommand): RegisterTransactionResult =
            RegisterTransactionResult.Failure("Not used by this test")

        override suspend fun findSimilarTransactions(
            userId: String,
            sourceAccountId: String,
            type: MovementType,
            amountMinor: Long,
            currency: String,
            occurredAt: Long,
            windowMillis: Long,
        ): List<Transaction> = emptyList()

        override fun observeBalance(userId: String, accountId: String): Flow<Long?> = flowOf(null)
    }
}
