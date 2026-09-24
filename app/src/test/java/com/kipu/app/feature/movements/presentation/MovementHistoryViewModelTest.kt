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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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

    private class TestSessionCoordinator(initialAccess: LocalAccess) : SessionCoordinator {
        override val remoteSession = MutableStateFlow<RemoteSession>(RemoteSession.Absent)
        override val localAccess = MutableStateFlow(initialAccess)
        override val currentOwner: LocalOwner? = null
        override suspend fun setActiveOwner(userId: String) = Unit
        override suspend fun clearActiveOwner(explicit: Boolean) = Unit
        override suspend fun updateRemoteSession(session: RemoteSession) = Unit
        override suspend fun updateLockState(isLocked: Boolean, reason: String) = Unit
    }

    private class RecordingMovementRepository : MovementRepository {
        val observedOwners = mutableListOf<String>()

        override fun observeTransactions(userId: String): Flow<List<TransactionItem>> {
            observedOwners += userId
            return flowOf(emptyList())
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
