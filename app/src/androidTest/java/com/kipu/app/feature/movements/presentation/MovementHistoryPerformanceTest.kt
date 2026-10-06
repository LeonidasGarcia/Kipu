package com.kipu.app.feature.movements.presentation

import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.movements.data.OfflineFirstMovementRepository
import com.kipu.app.feature.movements.data.local.BalanceProjectionStore
import com.kipu.app.feature.movements.data.local.MovementLocalDataSource
import com.kipu.app.feature.movements.data.local.TransactionEntity
import com.kipu.app.feature.movements.data.sync.MovementSyncScheduler
import com.kipu.app.feature.movements.domain.MovementRevisionRequestHasher
import com.kipu.app.feature.movements.domain.TransactionRequestHasher
import com.kipu.app.feature.movements.domain.model.MovementHistoryCursor
import com.kipu.app.feature.movements.domain.model.MovementHistoryQuery
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.Transaction
import com.kipu.app.feature.movements.domain.model.TransactionItem
import com.kipu.app.ui.theme.KipuTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MovementHistoryPerformanceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tenThousandMovementsRenderLazilyAndRemainScrollable() {
        val transactions = List(10_000) { index ->
            TransactionItem(
                transaction = Transaction(
                    id = "performance-$index",
                    userId = "performance-user",
                    type = MovementType.EXPENSE,
                    amountMinor = 1_000L + index,
                    currency = "PEN",
                    sourceAccountId = "performance-account",
                    categoryId = "performance-category",
                    occurredAt = System.currentTimeMillis(),
                ),
                sourceAccountAlias = "Cuenta principal",
                categoryName = "Alimentación",
            )
        }
        val renderedState = MutableStateFlow(
            MovementHistoryUiState(
                isLoading = false,
                allTransactions = transactions,
                filteredTransactions = mapOf("Hoy" to transactions),
            ),
        )
        val viewModel = mockk<MovementHistoryViewModel>(relaxed = true) {
            every { uiState } returns renderedState
        }

        val startedAt = SystemClock.elapsedRealtime()
        compose.setContent {
            KipuTheme {
                MovementHistoryRoute(viewModel = viewModel)
            }
        }
        compose.waitForIdle()
        val initialRenderMillis = SystemClock.elapsedRealtime() - startedAt

        assertTrue(
            "10,000-row history should compose its initial viewport in under 5 seconds; took ${initialRenderMillis}ms",
            initialRenderMillis < 5_000L,
        )
        compose.onNodeWithTag("tx_row_performance-0").assertIsDisplayed()
        compose.onNodeWithTag("tx_row_performance-9999").assertDoesNotExist()
        compose.onNodeWithTag("list_movements")
            // Jump near the end; scanning every viewport measures the test driver, not lazy rendering.
            .performScrollToIndex(transactions.lastIndex)
        compose.onNodeWithTag("tx_row_performance-9999").assertIsDisplayed()
    }

    @Test
    fun tenThousandMovementsLocalQueryP95LatencyUnder300ms() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.movementDao()
            val projectionStore = BalanceProjectionStore(dao)
            val source = MovementLocalDataSource(db, dao, projectionStore)
            val syncScheduler = MovementSyncScheduler(context)
            val repository = OfflineFirstMovementRepository(
                localDataSource = source,
                balanceProjectionStore = projectionStore,
                hasher = TransactionRequestHasher(),
                revisionHasher = MovementRevisionRequestHasher(),
                syncScheduler = syncScheduler,
                accountDao = db.accountDao(),
                cardDao = db.cardDao(),
                categoryDao = db.categoryDao(),
                merchantDao = db.merchantCatalogDao(),
            )

            val userId = "perf-user"
            val now = System.currentTimeMillis()
            db.accountDao().insert(
                AccountEntity(
                    id = "acc-main",
                    userId = userId,
                    creationOperationId = "perf-account-create",
                    alias = "Cuenta principal",
                    type = "BANK",
                    currency = "PEN",
                    presetId = null,
                    color = null,
                    icon = null,
                    initialBalanceMinorUnits = 0L,
                    openedAt = now,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            val entities = List(10_000) { i ->
                TransactionEntity(
                    id = "tx-perf-$i",
                    userId = userId,
                    type = "EXPENSE",
                    amountMinor = 1_000L + (i % 100),
                    currencyCode = "PEN",
                    sourceAccountId = "acc-main",
                    destinationAccountId = null,
                    categoryId = "cat-food",
                    merchantId = null,
                    merchantProvisionalText = null,
                    occurredAt = now - i * 60_000L,
                    note = "Performance item $i",
                    status = "ACTIVE",
                    revision = 1L,
                    currentRevisionId = "rev-$i",
                    syncStatus = "CONFIRMED",
                    createdAt = now - i * 60_000L,
                    updatedAt = now - i * 60_000L,
                    legacyKind = null,
                )
            }
            entities.chunked(500).forEach { chunk ->
                dao.insertTransactions(chunk)
            }

            // Warm up
            repository.queryHistory(userId, MovementHistoryQuery(limit = 50))

            // Perform 20 paginated queries and measure latencies
            val latencies = mutableListOf<Long>()
            var cursor: MovementHistoryCursor? = null
            val collectedIds = mutableListOf<String>()

            for (p in 0 until 20) {
                val start = SystemClock.elapsedRealtime()
                val page = repository.queryHistory(userId, MovementHistoryQuery(limit = 50, cursor = cursor))
                val elapsed = SystemClock.elapsedRealtime() - start
                latencies.add(elapsed)
                collectedIds.addAll(page.items.map { it.transaction.id })
                cursor = page.nextCursor
                if (!page.hasMore) break
            }

            latencies.sort()
            val p95Index = ((latencies.size * 0.95).toInt()).coerceAtMost(latencies.size - 1)
            val p95 = latencies[p95Index]

            assertTrue("Query p95 latency should be < 300ms, actual: ${p95}ms (all: $latencies)", p95 < 300L)
            assertEquals(1000, collectedIds.size)
            assertEquals(collectedIds.toSet().size, collectedIds.size)
        } finally {
            db.close()
        }
    }
}
