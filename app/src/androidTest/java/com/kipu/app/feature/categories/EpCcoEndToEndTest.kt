package com.kipu.app.feature.categories

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.database.DatabaseTransactionRunner
import com.kipu.app.core.finance.domain.model.MovementId
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.feature.categories.data.FakeCategoryDao
import com.kipu.app.feature.categories.data.FakeCategorySyncScheduler
import com.kipu.app.feature.categories.data.FakeMerchantCatalogDao
import com.kipu.app.feature.categories.data.FakeSessionCoordinator
import com.kipu.app.feature.categories.data.OfflineFirstCategoriesRepository
import com.kipu.app.feature.categories.data.local.MerchantCatalogEntity
import com.kipu.app.feature.categories.domain.model.Category
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.CategoryOrigin
import com.kipu.app.feature.categories.domain.model.CategoryPresentation
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.domain.model.MovementClassification
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpCcoEndToEndTest {

    private lateinit var categoryDao: FakeCategoryDao
    private lateinit var merchantDao: FakeMerchantCatalogDao
    private lateinit var sessionCoordinator: FakeSessionCoordinator
    private lateinit var syncScheduler: FakeCategorySyncScheduler
    private lateinit var repository: OfflineFirstCategoriesRepository

    private val testUserId = UserId.generate()

    @Before
    fun setup() {
        categoryDao = FakeCategoryDao()
        merchantDao = FakeMerchantCatalogDao()
        sessionCoordinator = FakeSessionCoordinator(
            LocalAccess.Available(testUserId.value, RemoteSession.Absent)
        )
        syncScheduler = FakeCategorySyncScheduler()

        val transactionRunner = object : DatabaseTransactionRunner {
            override suspend operator fun <R> invoke(block: suspend () -> R): R = block()
        }

        repository = OfflineFirstCategoriesRepository(
            transactionRunner = transactionRunner,
            categoryDao = categoryDao,
            merchantDao = merchantDao,
            sessionCoordinator = sessionCoordinator,
            syncScheduler = syncScheduler,
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
        val classification = MovementClassification(
            movementId = movementId,
            categoryId = root.id,
            merchantId = null,
            merchantProvisionalText = "Micro Colectivo 73",
        )
        val classifyRes = repository.updateMovementClassification(classification)
        assertTrue(classifyRes.isSuccess)

        // Verify movement updated locally and outbox updated
        val storedMovement = categoryDao.movements[movementId.value]
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
        val classification = MovementClassification(
            movementId = movementId,
            categoryId = null,
            merchantId = MerchantId("m1"),
            merchantProvisionalText = null,
        )
        val assignRes = repository.updateMovementClassification(classification)
        assertTrue(assignRes.isSuccess)

        val stored = categoryDao.movements[movementId.value]
        assertEquals("m1", stored?.merchant_id)
    }
}
