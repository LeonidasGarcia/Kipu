package com.kipu.app.feature.categories

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.database.DatabaseTransactionRunner
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.finance.domain.model.MovementId
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.accounts.data.local.FinancialMovementEntity
import com.kipu.app.feature.categories.data.OfflineFirstCategoriesRepository
import com.kipu.app.feature.categories.data.local.CategoryDao
import com.kipu.app.feature.categories.data.local.MerchantCatalogDao
import com.kipu.app.feature.categories.data.local.MerchantCatalogEntity
import com.kipu.app.feature.categories.data.sync.CategorySyncScheduler
import com.kipu.app.feature.categories.domain.model.Category
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.CategoryOrigin
import com.kipu.app.feature.categories.domain.model.CategoryPresentation
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.domain.model.MovementClassification
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpCcoEndToEndTest {

    private lateinit var database: KipuDatabase
    private lateinit var categoryDao: CategoryDao
    private lateinit var merchantDao: MerchantCatalogDao
    private lateinit var syncScheduler: CategorySyncScheduler
    private lateinit var repository: OfflineFirstCategoriesRepository

    private val testUserId = UserId.generate()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        categoryDao = database.categoryDao()
        merchantDao = database.merchantCatalogDao()
        val sessionCoordinator = object : SessionCoordinator {
            override val remoteSession = MutableStateFlow<RemoteSession>(RemoteSession.Absent)
            override val localAccess = MutableStateFlow<LocalAccess>(
                LocalAccess.Available(testUserId.value, RemoteSession.Absent)
            )
            override val currentOwner = LocalOwner(testUserId.value)
            override suspend fun setActiveOwner(userId: String) {}
            override suspend fun clearActiveOwner(explicit: Boolean) {}
            override suspend fun updateRemoteSession(session: RemoteSession) {}
            override suspend fun updateLockState(isLocked: Boolean, reason: String) {}
        }
        syncScheduler = object : CategorySyncScheduler {
            override fun scheduleSync(userId: String) {}
            override fun cancelSync(userId: String) {}
        }

        val transactionRunner = object : DatabaseTransactionRunner {
            override suspend operator fun <R> invoke(block: suspend () -> R): R = database.withTransaction { block() }
        }

        repository = OfflineFirstCategoriesRepository(
            transactionRunner = transactionRunner,
            categoryDao = categoryDao,
            merchantDao = merchantDao,
            sessionCoordinator = sessionCoordinator,
            syncScheduler = syncScheduler,
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    private suspend fun seedMovement(movementId: MovementId) {
        val accountId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        database.accountDao().insert(
            AccountEntity(
                id = accountId,
                userId = testUserId.value,
                creationOperationId = UUID.randomUUID().toString(),
                alias = "Cuenta de prueba",
                type = "SAVINGS",
                currency = "PEN",
                presetId = null,
                color = null,
                icon = null,
                initialBalanceMinorUnits = 0L,
                openedAt = now,
                createdAt = now,
                updatedAt = now,
            )
        )
        database.financialMovementDao().insert(
            FinancialMovementEntity(
                id = movementId.value,
                operationId = UUID.randomUUID().toString(),
                operationSequence = 0,
                userId = testUserId.value,
                kind = "EXPENSE",
                amountMinorUnits = -100L,
                currency = "PEN",
                accountId = accountId,
                effectiveAt = now,
                createdAt = now,
            )
        )
    }

    @Test
    fun offlineCreateAndClassifyFlow() = runTest {
        // 1. Create custom root offline
        val root = Category(
            id = CategoryId.generate(),
            ownerId = testUserId,
            parentId = null,
            origin = CategoryOrigin.CUSTOM,
            isActive = true,
        )
        val rootPres = CategoryPresentation(
            categoryId = root.id,
            ownerId = testUserId,
            name = "Transporte Urbano",
            icon = "directions_bus",
            color = "#2196F3",
        )
        val createRes = repository.createCategory(root, rootPres)
        assertTrue(createRes.isSuccess)

        // Verify committed locally and outbox queued
        val localCat = categoryDao.getCategoryById(root.id.value)
        assertNotNull(localCat)
        assertEquals("Transporte Urbano", categoryDao.getPresentation(testUserId.value, root.id.value)?.name)
        val outbox = categoryDao.getPendingOutboxCommands(testUserId.value)
        assertEquals(1, outbox.size)
        assertEquals("CREATE_CATEGORY", outbox.first().commandType)

        // 2. Classify a movement offline with category and provisional text
        val movementId = MovementId.generate()
        seedMovement(movementId)
        val classification = MovementClassification(
            movementId = movementId,
            categoryId = root.id,
            merchantId = null,
            merchantProvisionalText = "Micro Colectivo 73",
        )
        val classifyRes = repository.updateMovementClassification(classification)
        assertTrue(classifyRes.isSuccess)

        // Verify movement updated locally and outbox updated
        val storedMovement = categoryDao.observeMovementClassification(movementId.value, testUserId.value).first()
        assertEquals(root.id.value, storedMovement?.category_id)
        assertEquals("Micro Colectivo 73", storedMovement?.merchant_provisional_text)
        assertEquals(2, categoryDao.getPendingOutboxCommands(testUserId.value).size)
    }

    @Test
    fun catalogSearchAndAssignFlow() = runTest {
        // Seed catalog
        merchantDao.insertMerchants(
            listOf(
                MerchantCatalogEntity("m1", "Tambo+", "tambo+", true, 1L, 1000L),
                MerchantCatalogEntity("m2", "Oxxo", "oxxo", true, 1L, 1000L),
            )
        )

        // Search catalog
        val searchResults = repository.searchMerchants("tam").first()
        assertEquals(1, searchResults.size)
        assertEquals("Tambo+", searchResults.first().name)

        // Assign catalog merchant
        val movementId = MovementId.generate()
        seedMovement(movementId)
        val classification = MovementClassification(
            movementId = movementId,
            categoryId = null,
            merchantId = MerchantId("m1"),
            merchantProvisionalText = null,
        )
        val assignRes = repository.updateMovementClassification(classification)
        assertTrue(assignRes.isSuccess)

        val stored = categoryDao.observeMovementClassification(movementId.value, testUserId.value).first()
        assertEquals("m1", stored?.merchant_id)
    }
}
