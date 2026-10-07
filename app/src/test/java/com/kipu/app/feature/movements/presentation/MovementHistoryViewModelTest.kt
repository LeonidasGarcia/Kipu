package com.kipu.app.feature.movements.presentation

import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.movements.domain.MovementHistoryQueryRepository
import com.kipu.app.feature.movements.domain.MovementRepository
import com.kipu.app.feature.movements.domain.MovementEntitlementEvidence
import com.kipu.app.feature.movements.domain.MovementEntitlementProvider
import com.kipu.app.feature.movements.domain.MovementHistoryAccessPolicy
import com.kipu.app.feature.movements.domain.QueryMovementHistory
import com.kipu.app.feature.movements.domain.model.MovementFinancialState
import com.kipu.app.feature.movements.domain.model.MovementHistoryAccessDecision
import com.kipu.app.feature.movements.domain.model.MovementHistoryCursor
import com.kipu.app.feature.movements.domain.model.MovementHistoryPage
import com.kipu.app.feature.movements.domain.model.MovementHistoryQuery
import com.kipu.app.feature.movements.domain.model.MovementHistorySummary
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.Transaction
import com.kipu.app.feature.movements.domain.model.TransactionItem
import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.coVerify
import java.math.BigInteger
import androidx.lifecycle.SavedStateHandle
import com.kipu.app.feature.plans.domain.model.BillingVerificationResult
import com.kipu.app.feature.plans.purchase.BillingPurchaseRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MovementHistoryViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(mainDispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun initialLoadBuildsPagedQueryAndUsesRepositorySummary() = runTest {
        val repository = FakeHistoryRepository { query ->
            MovementHistoryPage(listOf(item("one", 20)), null, false, MovementHistoryAccessDecision.Allowed)
        }.apply { summary = MovementHistorySummary(99, mapOf("PEN" to BigInteger.TEN), "cash") }
        val viewModel = viewModel(repository)

        advanceUntilIdle()

        assertEquals(1, repository.queries.size)
        assertEquals(99L, viewModel.uiState.value.totalFilteredCount)
        assertEquals(BigInteger.TEN, viewModel.uiState.value.netByCurrency["PEN"])
        assertEquals("cash", viewModel.uiState.value.mostUsedAccountId)
    }

    @Test
    fun nextPageAppendsInStableOrderWithoutDuplicates() = runTest {
        val cursor = MovementHistoryCursor(20, "one")
        val repository = FakeHistoryRepository { query ->
            if (query.cursor == null) MovementHistoryPage(
                listOf(item("one", 20), item("two", 10)), cursor, true, MovementHistoryAccessDecision.Allowed,
            ) else MovementHistoryPage(
                listOf(item("two", 10), item("three", 5)), null, false, MovementHistoryAccessDecision.Allowed,
            )
        }
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onLoadMore()
        advanceUntilIdle()

        assertEquals(listOf("one", "two", "three"), viewModel.uiState.value.allTransactions.map { it.transaction.id })
        assertEquals(2, repository.queries.size)
        assertEquals(cursor, repository.queries.last().cursor)
    }

    @Test
    fun filterChangeDiscardsPreviouslyLoadedPages() = runTest {
        val cursor = MovementHistoryCursor(20, "old")
        val repository = FakeHistoryRepository { query ->
            val id = if (query.types.isEmpty()) "old" else "expense"
            MovementHistoryPage(listOf(item(id, 20)), cursor, true, MovementHistoryAccessDecision.Allowed)
        }
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.onLoadMore()
        advanceUntilIdle()

        viewModel.onFilterTypeSelected(MovementType.EXPENSE)
        advanceUntilIdle()

        assertEquals(listOf("expense"), viewModel.uiState.value.allTransactions.map { it.transaction.id })
        assertTrue(repository.queries.last().types.contains(MovementType.EXPENSE))
    }

    @Test
    fun fallbackPagesContinueWithFallbackCursorAndQuery() = runTest {
        val cursor = MovementHistoryCursor(20, "one")
        val repository = FakeHistoryRepository { query ->
            if (query.cursor == null) MovementHistoryPage(listOf(item("one", 20)), cursor, true, MovementHistoryAccessDecision.Allowed)
            else MovementHistoryPage(listOf(item("two", 10)), null, false, MovementHistoryAccessDecision.Allowed)
        }
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onAccountFilterToggled("cash")
        advanceUntilIdle()
        viewModel.onLoadMore()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.fallbackUsed)
        assertEquals(listOf("one", "two"), viewModel.uiState.value.allTransactions.map { it.transaction.id })
        assertTrue(repository.queries.takeLast(2).all { it.advancedCriteria == null })
        assertEquals(cursor, repository.queries.last().cursor)
    }

    @Test
    fun summaryIsNotDerivedFromLoadedPageCount() = runTest {
        val repository = FakeHistoryRepository { MovementHistoryPage(listOf(item("one", 20)), null, false, MovementHistoryAccessDecision.Allowed) }
            .apply { summary = MovementHistorySummary(500L, emptyMap(), null) }
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.allTransactions.size)
        assertEquals(500L, viewModel.uiState.value.totalFilteredCount)
        assertFalse(viewModel.uiState.value.queryError)
    }

    @Test
    fun noOwnerNeverQueriesSharedPlaceholderHistory() = runTest {
        val repository = FakeHistoryRepository()
        val viewModel = viewModel(repository, LocalAccess.NoOwner)
        val state = async { viewModel.uiState.first { !it.isLoading } }

        advanceUntilIdle()

        assertTrue(state.await().allTransactions.isEmpty())
        assertEquals(emptyList<MovementHistoryQuery>(), repository.queries)
    }

    @Test
    fun transferConfirmationClearsSearchAndReturnsHistoryToAllTypes() = runTest {
        val viewModel = viewModel(FakeHistoryRepository())
        viewModel.onFilterTypeSelected(MovementType.EXPENSE)
        viewModel.onSearchQueryChanged("no coincide")

        viewModel.showAllTransactionsAfterTransfer()

        assertEquals(null, viewModel.uiState.value.selectedFilterType)
        assertEquals("", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun deepLinkArgumentsPopulateQueryAndFilters() = runTest {
        val viewModel = MovementHistoryViewModel(
            movementRepository = mockk(relaxed = true),
            sessionCoordinator = TestSessionCoordinator(),
            queryMovementHistoryUseCase = QueryMovementHistory(FakeHistoryRepository()),
            savedStateHandle = SavedStateHandle(mapOf("query" to "farmacia", "accountId" to "acc-test-1", "categoryId" to "cat-test-2")),
        )

        advanceUntilIdle()

        assertEquals("farmacia", viewModel.uiState.value.searchQuery)
        assertEquals(setOf("acc-test-1"), viewModel.uiState.value.selectedAccountIds)
        assertEquals(setOf("cat-test-2"), viewModel.uiState.value.selectedCategoryIds)
        assertTrue(viewModel.uiState.value.hasActiveAdvancedFilters)
    }

    @Test
    fun advancedFiltersFilterTransactionsAccurately() = runTest {
        val tx1 = item("tx-1", 1_000_000).copy(transaction = item("tx-1", 1_000_000).transaction.copy(amountMinor = 1_500, sourceAccountId = "acc-wallet", categoryId = "cat-food"))
        val tx2 = item("tx-2", 2_000_000).copy(transaction = item("tx-2", 2_000_000).transaction.copy(amountMinor = 5_000, sourceAccountId = "acc-bank", categoryId = "cat-pharmacy"))
        val tx3 = item("tx-3", 3_000_000).copy(transaction = item("tx-3", 3_000_000).transaction.copy(type = MovementType.TRANSFER, amountMinor = 10_000, sourceAccountId = "acc-bank", destinationAccountId = "acc-wallet", status = com.kipu.app.feature.movements.domain.model.TransactionStatus.VOIDED))
        val repository = filteringRepository(tx1, tx2, tx3)
        val viewModel = viewModel(repository, useCase = allowedUseCase(repository))
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.allTransactions.size)

        viewModel.onAccountFilterToggled("acc-wallet")
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.allTransactions.size)
        viewModel.onAmountRangeChanged(1_000, 2_000)
        advanceUntilIdle()
        assertEquals(listOf("tx-1"), viewModel.uiState.value.allTransactions.map { it.transaction.id })
        viewModel.onClearFilters()
        viewModel.onFinancialStateToggled(MovementFinancialState.VOIDED)
        advanceUntilIdle()
        assertEquals(listOf("tx-3"), viewModel.uiState.value.allTransactions.map { it.transaction.id })
        viewModel.onClearFilters()
        viewModel.onDateRangeSelected(1_500_000, 2_500_000)
        advanceUntilIdle()
        assertEquals(listOf("tx-2"), viewModel.uiState.value.allTransactions.map { it.transaction.id })
    }

    @Test
    fun clearAndResetAdvancedFiltersBehaveCorrectly() = runTest {
        val viewModel = viewModel(FakeHistoryRepository())
        viewModel.onToggleAdvancedFilterPanel(true)
        viewModel.onSearchQueryChanged("test query")
        viewModel.onFilterTypeSelected(MovementType.INCOME)
        viewModel.onAccountFilterToggled("acc-1")
        viewModel.onAmountRangeChanged(100, 500)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showAdvancedFilterPanel)
        assertTrue(viewModel.uiState.value.hasActiveAdvancedFilters)

        viewModel.onResetAdvancedFilters()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showAdvancedFilterPanel)
        assertFalse(viewModel.uiState.value.hasActiveAdvancedFilters)
        assertEquals("test query", viewModel.uiState.value.searchQuery)
        assertEquals(MovementType.INCOME, viewModel.uiState.value.selectedFilterType)

        viewModel.onClearFilters()
        advanceUntilIdle()
        assertEquals("", viewModel.uiState.value.searchQuery)
        assertEquals(null, viewModel.uiState.value.selectedFilterType)
        assertFalse(viewModel.uiState.value.hasActiveAdvancedFilters)
    }

    @Test
    fun premiumRequiredFallsBackToFreeFiltersAndShowsPlanAction() = runTest {
        val tx = item("tx-food", 1_000_000).copy(transaction = item("tx-food", 1_000_000).transaction.copy(categoryId = "cat-food"))
        val viewModel = viewModel(filteringRepository(tx))
        advanceUntilIdle()
        viewModel.onSearchQueryChanged("cat-food")
        viewModel.onAccountFilterToggled("acc-not-matching")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.accessStatus is MovementHistoryAccessDecision.PremiumRequired)
        assertTrue(state.fallbackUsed)
        assertEquals("PREMIUM_REQUIRED", state.accessMessage)
        assertEquals(listOf("tx-food"), state.allTransactions.map { it.transaction.id })
    }

    @Test
    fun expiredLeaseKeepsDateAndTextFiltersButDropsAdvancedAmountFilter() = runTest {
        val tx = item("tx-food", 1_000_000).copy(transaction = item("tx-food", 1_000_000).transaction.copy(amountMinor = 1_500, categoryId = "cat-food"))
        val viewModel = viewModel(filteringRepository(tx), useCase = accessUseCase(filteringRepository(tx), true))
        advanceUntilIdle()
        viewModel.onSearchQueryChanged("cat-food")
        viewModel.onDateRangeSelected(900_000, 1_100_000)
        viewModel.onAmountRangeChanged(2_000, null)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.accessStatus is MovementHistoryAccessDecision.RevalidationRequired)
        assertTrue(state.fallbackUsed)
        assertEquals("REVALIDATION_REQUIRED", state.accessMessage)
        assertEquals(listOf("tx-food"), state.allTransactions.map { it.transaction.id })
    }

    @Test
    fun authenticatedSessionDerivesOwnerFromCoordinator() = runTest {
        val repository = FakeHistoryRepository()
        val viewModel = viewModel(repository, LocalAccess.Available("owner-42", RemoteSession.Absent))
        val state = async { viewModel.uiState.first { !it.isLoading } }
        advanceUntilIdle()
        state.await()
        assertEquals(1, repository.queries.size)
    }

    @Test
    fun validatedDraftAppliesOnceAndIndividualRemovalKeepsOtherSelections() = runTest {
        val viewModel = viewModel(FakeHistoryRepository())
        advanceUntilIdle()
        assertTrue(viewModel.onApplyFilterDraft(MovementFilterDraft(accountIds = setOf("cash", "bank"), currency = "PEN", minAmount = "15,50")).errors.isEmpty())
        advanceUntilIdle()
        assertEquals(1550L, viewModel.uiState.value.minAmountMinor)
        viewModel.onRemoveFilter("account:cash")
        advanceUntilIdle()
        assertEquals(setOf("bank"), viewModel.uiState.value.selectedAccountIds)
        assertEquals(1550L, viewModel.uiState.value.minAmountMinor)
        assertFalse(viewModel.onApplyFilterDraft(MovementFilterDraft(minAmount = "abc")).errors.isEmpty())
        assertEquals(1550L, viewModel.uiState.value.minAmountMinor)
    }

    @Test
    fun ownerChangeClearsFiltersSearchAndDetail() = runTest {
        val coordinator = TestSessionCoordinator()
        val tx = item("one", 1_000)
        val repository = filteringRepository(tx).apply { allowedUsers = setOf("user") }
        val viewModel = viewModel(repository, coordinator = coordinator)
        advanceUntilIdle()
        viewModel.onOpenDetail(tx)
        viewModel.onSearchQueryChanged("cash")
        viewModel.onApplyFilterDraft(MovementFilterDraft(accountIds = setOf("cash")))
        advanceUntilIdle()
        coordinator.localAccess.value = LocalAccess.Available("user-bob", RemoteSession.Absent)
        advanceUntilIdle()
        assertEquals("", viewModel.uiState.value.searchQuery)
        assertTrue(viewModel.uiState.value.selectedAccountIds.isEmpty())
        assertEquals(null, viewModel.uiState.value.selectedDetail)
        assertTrue(viewModel.uiState.value.allTransactions.isEmpty())
    }

    @Test
    fun recoveryRejectsDuplicatePressesAndHasNoPurchaseTerminalState() = runTest {
        val result = CompletableDeferred<BillingVerificationResult>()
        val billing = mockk<BillingPurchaseRepository>()
        coEvery { billing.restoreAndVerifyAccess() } coAnswers { result.await() }
        val viewModel = MovementHistoryViewModel(
            movementRepository = mockk(relaxed = true),
            sessionCoordinator = TestSessionCoordinator(),
            queryMovementHistoryUseCase = QueryMovementHistory(FakeHistoryRepository()),
            billingRepository = billing,
        )
        runCurrent()
        viewModel.onRevalidateAccess()
        viewModel.onRevalidateAccess()
        runCurrent()
        assertEquals(HistoryAccessRecovery.VERIFYING, viewModel.uiState.value.recovery)
        result.complete(BillingVerificationResult.Retryable("NO_RECOVERABLE_PURCHASE"))
        advanceUntilIdle()
        assertEquals(HistoryAccessRecovery.NO_PURCHASE, viewModel.uiState.value.recovery)
        coVerify(exactly = 1) { billing.restoreAndVerifyAccess() }
    }

    @Test
    fun historyReadFailureIsDistinctFromEmptyAndCanBeRetried() = runTest {
        val repository = FakeHistoryRepository { throw IllegalStateException("read failed") }
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.queryError)
        repository.page = { MovementHistoryPage(emptyList(), null, false, MovementHistoryAccessDecision.Allowed) }
        viewModel.onRetryHistory()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.queryError)
        assertTrue(viewModel.uiState.value.allTransactions.isEmpty())
    }

    private fun viewModel(
        repository: FakeHistoryRepository,
        access: LocalAccess = LocalAccess.Available("user", RemoteSession.Absent),
        coordinator: TestSessionCoordinator = TestSessionCoordinator(access),
        useCase: QueryMovementHistory = QueryMovementHistory(repository),
    ): MovementHistoryViewModel = MovementHistoryViewModel(
        movementRepository = mockk<MovementRepository>(relaxed = true),
        sessionCoordinator = coordinator,
        queryMovementHistoryUseCase = useCase,
    )

    private fun item(id: String, occurredAt: Long) = TransactionItem(
        transaction = Transaction(
            id = id,
            userId = "user",
            type = MovementType.EXPENSE,
            amountMinor = 100,
            currency = "PEN",
            sourceAccountId = "cash",
            categoryId = "category",
            occurredAt = occurredAt,
        ),
        sourceAccountAlias = "cash",
    )

    private class FakeHistoryRepository(
        var page: (MovementHistoryQuery) -> MovementHistoryPage = {
            MovementHistoryPage(emptyList(), null, false, MovementHistoryAccessDecision.Allowed)
        },
    ) : MovementHistoryQueryRepository {
        val queries = mutableListOf<MovementHistoryQuery>()
        var summary = MovementHistorySummary(0, emptyMap(), null)
        var allowedUsers: Set<String>? = null
        override suspend fun queryHistory(userId: String, query: MovementHistoryQuery): MovementHistoryPage {
            queries += query
            val users = allowedUsers
            return if (users == null || userId in users) page(query)
            else MovementHistoryPage(emptyList(), null, false, MovementHistoryAccessDecision.Allowed)
        }
        override suspend fun getHistorySummary(userId: String, query: MovementHistoryQuery) = summary
        override fun observeHistoryInvalidations() = emptyFlow<Unit>()
    }

    private fun filteringRepository(vararg items: TransactionItem) = FakeHistoryRepository { query ->
        val criteria = query.advancedCriteria
        val matching = items.filter { item ->
            val transaction = item.transaction
            val text = query.queryText.orEmpty()
            val textMatches = text.isBlank() || listOfNotNull(
                item.categoryName,
                item.sourceAccountAlias,
                transaction.categoryId,
                transaction.sourceAccountId,
            ).any { it.contains(text, ignoreCase = true) }
            val accountMatches = criteria?.accountIds.isNullOrEmpty() ||
                transaction.sourceAccountId in criteria!!.accountIds || transaction.destinationAccountId in criteria.accountIds
            val categoryMatches = criteria?.categoryIds.isNullOrEmpty() || transaction.categoryId in criteria!!.categoryIds
            val amountMatches = (criteria?.minAmountMinor == null || transaction.amountMinor >= criteria.minAmountMinor) &&
                (criteria?.maxAmountMinor == null || transaction.amountMinor <= criteria.maxAmountMinor)
            val stateMatches = criteria?.financialStates.isNullOrEmpty() ||
                transaction.status.name in criteria!!.financialStates.map { it.name }
            textMatches &&
                (query.types.isEmpty() || transaction.type in query.types) &&
                (query.fromInclusive == null || transaction.occurredAt >= query.fromInclusive) &&
                (query.toExclusive == null || transaction.occurredAt < query.toExclusive) &&
                accountMatches && categoryMatches && amountMatches && stateMatches
        }.sortedWith(compareByDescending<TransactionItem> { it.transaction.occurredAt }.thenByDescending { it.transaction.id })
        MovementHistoryPage(matching, null, false, MovementHistoryAccessDecision.Allowed)
    }

    private fun accessUseCase(repository: FakeHistoryRepository, revalidationRequired: Boolean) = QueryMovementHistory(
        repository = repository,
        entitlementProvider = object : MovementEntitlementProvider {
            override suspend fun getEffectiveEntitlement(userId: String) = MovementEntitlementEvidence(
                verified = false,
                verifiedServerTimeMillis = 0L,
                entitlementExpiresAtMillis = null,
                revalidationRequired = revalidationRequired,
            )
        },
    )

    private fun allowedUseCase(repository: FakeHistoryRepository) = QueryMovementHistory(
        repository = repository,
        accessPolicy = object : MovementHistoryAccessPolicy {
            override fun evaluate(
                userId: String?,
                query: MovementHistoryQuery,
                evidence: MovementEntitlementEvidence?,
                requiresAdvancedAccess: Boolean,
            ) = MovementHistoryAccessDecision.Allowed
        },
    )

    private class TestSessionCoordinator(initialAccess: LocalAccess = LocalAccess.Available("user", RemoteSession.Absent)) : SessionCoordinator {
        override val remoteSession = MutableStateFlow<RemoteSession>(RemoteSession.Absent)
        override val localAccess = MutableStateFlow(initialAccess)
        override val currentOwner: LocalOwner? = null
        override suspend fun setActiveOwner(userId: String) = Unit
        override suspend fun clearActiveOwner(explicit: Boolean) = Unit
        override suspend fun updateRemoteSession(session: RemoteSession) = Unit
        override suspend fun updateLockState(isLocked: Boolean, reason: String) = Unit
    }
}
