package com.kipu.app.feature.movements.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.categories.data.local.CategoryEntity
import com.kipu.app.feature.categories.data.local.CategoryPresentationEntity
import com.kipu.app.feature.categories.data.local.MerchantCatalogEntity
import com.kipu.app.feature.movements.data.OfflineFirstMovementRepository
import com.kipu.app.feature.movements.data.sync.MovementSyncScheduler
import com.kipu.app.feature.movements.domain.MovementRevisionRequestHasher
import com.kipu.app.feature.movements.domain.TransactionRequestHasher
import com.kipu.app.feature.movements.domain.model.AdvancedHistoryCriteria
import com.kipu.app.feature.movements.domain.model.MovementFinancialState
import com.kipu.app.feature.movements.domain.model.MovementHistoryQuery
import com.kipu.app.feature.movements.domain.model.MovementType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MovementHistoryQueryTest {
    private lateinit var database: KipuDatabase
    private lateinit var dao: MovementDao
    private lateinit var repository: OfflineFirstMovementRepository
    private val userId = UUID.randomUUID().toString()
    private val bcpAccountId = "acc-bcp"
    private val bbvaAccountId = "acc-bbva"
    private val foodCategoryId = "cat-food"
    private val transportCategoryId = "cat-transport"
    private val tamboMerchantId = "merch-tambo"

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.movementDao()
        val projectionStore = BalanceProjectionStore(dao)
        val source = MovementLocalDataSource(database, dao, projectionStore)

        val syncScheduler = MovementSyncScheduler(context)
        repository = OfflineFirstMovementRepository(
            localDataSource = source,
            balanceProjectionStore = projectionStore,
            hasher = TransactionRequestHasher(),
            revisionHasher = MovementRevisionRequestHasher(),
            syncScheduler = syncScheduler,
            accountDao = database.accountDao(),
            cardDao = database.cardDao(),
            categoryDao = database.categoryDao(),
            merchantDao = database.merchantCatalogDao(),
        )

        // Seed accounts
        database.accountDao().insert(
            AccountEntity(
                id = bcpAccountId,
                userId = userId,
                creationOperationId = UUID.randomUUID().toString(),
                alias = "BCP Soles",
                type = "SAVINGS",
                currency = "PEN",
                presetId = null,
                color = null,
                icon = null,
                initialBalanceMinorUnits = 100_000L,
                openedAt = 1000L,
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )
        database.accountDao().insert(
            AccountEntity(
                id = bbvaAccountId,
                userId = userId,
                creationOperationId = UUID.randomUUID().toString(),
                alias = "BBVA Soles",
                type = "SAVINGS",
                currency = "PEN",
                presetId = null,
                color = null,
                icon = null,
                initialBalanceMinorUnits = 50_000L,
                openedAt = 1000L,
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )

        // Seed categories and presentations
        database.categoryDao().insertCategory(
            CategoryEntity(
                id = foodCategoryId,
                userId = userId,
                parentId = null,
                origin = "CUSTOM",
                isActive = true,
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )
        database.categoryDao().insertPresentation(
            CategoryPresentationEntity(
                categoryId = foodCategoryId,
                userId = userId,
                name = "Alimentación",
                icon = "food_icon",
                color = "#FF5722",
                updatedAt = 1000L,
            )
        )

        database.categoryDao().insertCategory(
            CategoryEntity(
                id = transportCategoryId,
                userId = userId,
                parentId = null,
                origin = "CUSTOM",
                isActive = true,
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )
        database.categoryDao().insertPresentation(
            CategoryPresentationEntity(
                categoryId = transportCategoryId,
                userId = userId,
                name = "Transporte",
                icon = "car_icon",
                color = "#2196F3",
                updatedAt = 1000L,
            )
        )

        // Seed merchant catalog
        database.merchantCatalogDao().insertMerchants(
            listOf(
                MerchantCatalogEntity(
                    id = tamboMerchantId,
                    name = "Tambo+",
                    normalizedName = "TAMBO",
                    isActive = true,
                    version = 1L,
                    lastSyncedAt = 1000L,
                )
            )
        )

        // Seed transactions
        // Tx 1: Expense, PEN 20.00, BCP, Food, Tambo, occurredAt = 10_000L, note = "Snacks"
        dao.insertTransaction(
            TransactionEntity(
                id = "tx-1",
                userId = userId,
                type = "EXPENSE",
                amountMinor = 2_000L,
                currencyCode = "PEN",
                sourceAccountId = bcpAccountId,
                categoryId = foodCategoryId,
                merchantId = tamboMerchantId,
                merchantProvisionalText = "Tambo",
                occurredAt = 10_000L,
                note = "Snacks de la tarde",
                status = "CONFIRMED",
                syncStatus = "SYNCED",
                revision = 1L,
                createdAt = 10_000L,
                updatedAt = 10_000L,
            )
        )

        // Tx 2: Expense, PEN 50.00, BBVA, Transport, occurredAt = 20_000L, note = "Gasolina"
        dao.insertTransaction(
            TransactionEntity(
                id = "tx-2",
                userId = userId,
                type = "EXPENSE",
                amountMinor = 5_000L,
                currencyCode = "PEN",
                sourceAccountId = bbvaAccountId,
                categoryId = transportCategoryId,
                occurredAt = 20_000L,
                note = "Gasolina 95",
                status = "ACTIVE",
                syncStatus = "PENDING",
                revision = 1L,
                createdAt = 20_000L,
                updatedAt = 20_000L,
            )
        )

        // Tx 3: Income, PEN 3000.00, BCP, occurredAt = 30_000L, note = "Sueldo mensual"
        dao.insertTransaction(
            TransactionEntity(
                id = "tx-3",
                userId = userId,
                type = "INCOME",
                amountMinor = 300_000L,
                currencyCode = "PEN",
                sourceAccountId = bcpAccountId,
                occurredAt = 30_000L,
                note = "Sueldo mensual",
                status = "CONFIRMED",
                syncStatus = "SYNCED",
                revision = 1L,
                createdAt = 30_000L,
                updatedAt = 30_000L,
            )
        )

        // Tx 4: Transfer, PEN 100.00, BCP -> BBVA, occurredAt = 40_000L
        dao.insertTransaction(
            TransactionEntity(
                id = "tx-4",
                userId = userId,
                type = "TRANSFER",
                amountMinor = 10_000L,
                currencyCode = "PEN",
                sourceAccountId = bcpAccountId,
                destinationAccountId = bbvaAccountId,
                occurredAt = 40_000L,
                note = "Ahorro programado",
                status = "ACTIVE",
                syncStatus = "PENDING",
                revision = 1L,
                createdAt = 40_000L,
                updatedAt = 40_000L,
            )
        )

        // Tx 5: Expense, PEN 15.00, BCP, Food, occurredAt = 50_000L, status = "VOIDED"
        dao.insertTransaction(
            TransactionEntity(
                id = "tx-5",
                userId = userId,
                type = "EXPENSE",
                amountMinor = 1_500L,
                currencyCode = "PEN",
                sourceAccountId = bcpAccountId,
                categoryId = foodCategoryId,
                occurredAt = 50_000L,
                note = "Café anulado",
                status = "VOIDED",
                syncStatus = "SYNCED",
                revision = 2L,
                createdAt = 50_000L,
                updatedAt = 50_000L,
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun basicTextQueryMatchesMerchantAndNote() = runBlocking {
        // Query "Tambo" should match Tx 1
        val resultTambo = repository.queryHistory(userId, MovementHistoryQuery(queryText = "Tambo"))
        assertEquals(1, resultTambo.items.size)
        assertEquals("tx-1", resultTambo.items.first().transaction.id)

        // Query "Gasolina" should match Tx 2
        val resultGasolina = repository.queryHistory(userId, MovementHistoryQuery(queryText = "Gasolina"))
        assertEquals(1, resultGasolina.items.size)
        assertEquals("tx-2", resultGasolina.items.first().transaction.id)

        // Query "Alimentación" matches category name on Tx 1 and Tx 5
        val resultFood = repository.queryHistory(userId, MovementHistoryQuery(queryText = "Alimentación"))
        assertEquals(2, resultFood.items.size)
        assertTrue(resultFood.items.any { it.transaction.id == "tx-1" })
        assertTrue(resultFood.items.any { it.transaction.id == "tx-5" })
    }

    @Test
    fun basicDateRangeQueryFiltersCorrectly() = runBlocking {
        // from 20_000 inclusive to 40_000 exclusive: matches Tx 2 (20_000) and Tx 3 (30_000)
        val result = repository.queryHistory(
            userId,
            MovementHistoryQuery(fromInclusive = 20_000L, toExclusive = 40_000L)
        )
        assertEquals(2, result.items.size)
        // Ordered DESC by occurredAt: Tx 3 (30_000) first, then Tx 2 (20_000)
        assertEquals("tx-3", result.items[0].transaction.id)
        assertEquals("tx-2", result.items[1].transaction.id)
    }

    @Test
    fun basicTypeFilterReturnsOnlySelectedTypes() = runBlocking {
        val resultIncome = repository.queryHistory(
            userId,
            MovementHistoryQuery(types = setOf(MovementType.INCOME))
        )
        assertEquals(1, resultIncome.items.size)
        assertEquals("tx-3", resultIncome.items.first().transaction.id)

        val resultTransfer = repository.queryHistory(
            userId,
            MovementHistoryQuery(types = setOf(MovementType.TRANSFER))
        )
        assertEquals(1, resultTransfer.items.size)
        assertEquals("tx-4", resultTransfer.items.first().transaction.id)
    }

    @Test
    fun advancedCriteriaFiltersByAccountCategoryAndAmount() = runBlocking {
        // Filter by BBVA account: matches Tx 2 (source) and Tx 4 (destination)
        val resultBbva = repository.queryHistory(
            userId,
            MovementHistoryQuery(
                advancedCriteria = AdvancedHistoryCriteria(accountIds = setOf(bbvaAccountId))
            )
        )
        assertEquals(2, resultBbva.items.size)
        assertTrue(resultBbva.items.any { it.transaction.id == "tx-2" })
        assertTrue(resultBbva.items.any { it.transaction.id == "tx-4" })

        // Filter by Food category and amount >= 18.00 (1800 minor): matches Tx 1 (2000 minor), excludes Tx 5 (1500 minor)
        val resultFoodAmount = repository.queryHistory(
            userId,
            MovementHistoryQuery(
                advancedCriteria = AdvancedHistoryCriteria(
                    categoryIds = setOf(foodCategoryId),
                    minAmountMinor = 1_800L,
                    currency = "PEN",
                )
            )
        )
        assertEquals(1, resultFoodAmount.items.size)
        assertEquals("tx-1", resultFoodAmount.items.first().transaction.id)
    }

    @Test
    fun voidedTransactionsArePreservedAndRetrievable() = runBlocking {
        // Query specifically for VOIDED financial state
        val resultVoided = repository.queryHistory(
            userId,
            MovementHistoryQuery(
                advancedCriteria = AdvancedHistoryCriteria(
                    financialStates = setOf(MovementFinancialState.VOIDED)
                )
            )
        )
        assertEquals(1, resultVoided.items.size)
        val voidedItem = resultVoided.items.first()
        assertEquals("tx-5", voidedItem.transaction.id)
        assertEquals(com.kipu.app.feature.movements.domain.model.TransactionStatus.VOIDED, voidedItem.transaction.status)
    }

    @Test
    fun keysetPaginationTraversesPagesWithoutGapsOrDuplicates() = runBlocking {
        // Total 5 transactions: Tx 5 (50k), Tx 4 (40k), Tx 3 (30k), Tx 2 (20k), Tx 1 (10k)
        // Page 1: limit = 2
        val page1 = repository.queryHistory(userId, MovementHistoryQuery(limit = 2))
        assertEquals(2, page1.items.size)
        assertEquals("tx-5", page1.items[0].transaction.id)
        assertEquals("tx-4", page1.items[1].transaction.id)
        assertTrue(page1.hasMore)
        assertNotNull(page1.nextCursor)

        // Page 2: with cursor from page 1
        val page2 = repository.queryHistory(userId, MovementHistoryQuery(limit = 2, cursor = page1.nextCursor))
        assertEquals(2, page2.items.size)
        assertEquals("tx-3", page2.items[0].transaction.id)
        assertEquals("tx-2", page2.items[1].transaction.id)
        assertTrue(page2.hasMore)
        assertNotNull(page2.nextCursor)

        // Page 3: with cursor from page 2
        val page3 = repository.queryHistory(userId, MovementHistoryQuery(limit = 2, cursor = page2.nextCursor))
        assertEquals(1, page3.items.size)
        assertEquals("tx-1", page3.items[0].transaction.id)
        assertFalse(page3.hasMore)
        assertNull(page3.nextCursor)

        // Verify all 5 items collected with zero duplicates
        val allIds = (page1.items + page2.items + page3.items).map { it.transaction.id }
        assertEquals(listOf("tx-5", "tx-4", "tx-3", "tx-2", "tx-1"), allIds)
    }
}
