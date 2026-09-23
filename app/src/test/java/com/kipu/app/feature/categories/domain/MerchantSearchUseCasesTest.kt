package com.kipu.app.feature.categories.domain

import com.kipu.app.core.database.DatabaseTransactionRunner
import com.kipu.app.core.finance.domain.model.MovementId
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.feature.categories.data.FakeCategoryDao
import com.kipu.app.feature.categories.data.FakeQuotaSelectionDao
import com.kipu.app.feature.categories.data.FakeFeatureAccessCacheDao
import com.kipu.app.feature.categories.data.FakeCategorySyncScheduler
import com.kipu.app.feature.categories.data.FakeMerchantCatalogDao
import com.kipu.app.feature.categories.data.FakeSessionCoordinator
import com.kipu.app.feature.categories.data.OfflineFirstCategoriesRepository
import com.kipu.app.feature.categories.data.local.MerchantCatalogEntity
import com.kipu.app.feature.categories.data.local.CategoryEntity
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.domain.usecase.SearchMerchantCatalog
import com.kipu.app.feature.categories.domain.usecase.UpdateMovementClassification
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MerchantSearchUseCasesTest {

    private lateinit var categoryDao: FakeCategoryDao
    private lateinit var merchantDao: FakeMerchantCatalogDao
    private lateinit var sessionCoordinator: FakeSessionCoordinator
    private lateinit var syncScheduler: FakeCategorySyncScheduler
    private lateinit var repository: OfflineFirstCategoriesRepository

    private lateinit var searchMerchantCatalog: SearchMerchantCatalog
    private lateinit var updateMovementClassification: UpdateMovementClassification

    private val testUserId = UserId.generate()

    private suspend fun seedActiveRoot(categoryId: CategoryId) {
        categoryDao.insertCategory(CategoryEntity(categoryId.value, testUserId.value, null, "CUSTOM", true, 1L, 1L, 1L))
    }

    @Before
    fun setup() = runTest {
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
            quotaSelectionDao = FakeQuotaSelectionDao(),
            featureAccessCacheDao = FakeFeatureAccessCacheDao(),
            quotaPolicy = com.kipu.app.feature.plans.domain.PlanQuotaPolicy(),
        )

        searchMerchantCatalog = SearchMerchantCatalog(repository)
        updateMovementClassification = UpdateMovementClassification(repository)

        // Seed merchant catalog entries
        merchantDao.insertMerchants(
            listOf(
                MerchantCatalogEntity("m1", "Tambo+", "tambo+", true, 1L, 1000L),
                MerchantCatalogEntity("m2", "Café Martínez", "cafe martinez", true, 1L, 1000L),
                MerchantCatalogEntity("m3", "Supermercados Metro", "supermercados metro", true, 1L, 1000L),
                MerchantCatalogEntity("m4", "Plaza Vea", "plaza vea", true, 1L, 1000L),
                MerchantCatalogEntity("m5", "Inkafarma", "inkafarma", true, 1L, 1000L),
            )
        )
    }

    @Test
    fun `search with normalized substring matches merchant`() = runTest {
        val results = searchMerchantCatalog("tam").first()
        assertEquals(1, results.size)
        assertEquals("Tambo+", results.first().name)
    }

    @Test
    fun `search ignores accents and diacritics`() = runTest {
        val results = searchMerchantCatalog("cafe").first()
        assertEquals(1, results.size)
        assertEquals("Café Martínez", results.first().name)
    }

    @Test
    fun `search is case-insensitive`() = runTest {
        val results = searchMerchantCatalog("METRO").first()
        assertEquals(1, results.size)
        assertEquals("Supermercados Metro", results.first().name)
    }

    @Test
    fun `search trims and collapses multiple whitespaces`() = runTest {
        val results = searchMerchantCatalog("   plaza    vea   ").first()
        assertEquals(1, results.size)
        assertEquals("Plaza Vea", results.first().name)
    }

    @Test
    fun `search with blank query returns empty results`() = runTest {
        val results = searchMerchantCatalog("   ").first()
        assertTrue(results.isEmpty())
    }

    @Test
    fun `search with no match returns empty results`() = runTest {
        val results = searchMerchantCatalog("comercio_desconocido").first()
        assertTrue(results.isEmpty())
    }

    @Test
    fun `assignClassification with catalog merchant succeeds`() = runTest {
        val movementId = MovementId.generate()
        val categoryId = CategoryId.generate()
        val merchantId = MerchantId("m1")
        seedActiveRoot(categoryId)

        val result = updateMovementClassification.assignClassification(
            movementId = movementId,
            categoryId = categoryId,
            merchantId = merchantId,
            provisionalText = null,
        )
        assertTrue(result.isSuccess)

        val classification = updateMovementClassification(movementId).first()
        assertNotNull(classification)
        assertEquals(categoryId, classification!!.categoryId)
        assertEquals(merchantId, classification.merchantId)
        assertNull(classification.merchantProvisionalText)
    }

    @Test
    fun `assignClassification with provisional text succeeds when no merchant is selected`() = runTest {
        val movementId = MovementId.generate()
        val categoryId = CategoryId.generate()
        seedActiveRoot(categoryId)

        val result = updateMovementClassification.assignClassification(
            movementId = movementId,
            categoryId = categoryId,
            merchantId = null,
            provisionalText = "Bodega Don Pepe",
        )
        assertTrue(result.isSuccess)

        val classification = updateMovementClassification(movementId).first()
        assertNotNull(classification)
        assertEquals(categoryId, classification!!.categoryId)
        assertNull(classification.merchantId)
        assertEquals("Bodega Don Pepe", classification.merchantProvisionalText)
    }

    @Test
    fun `assignClassification rejects coexisting merchantId and provisionalText`() = runTest {
        val movementId = MovementId.generate()
        val merchantId = MerchantId("m1")

        val result = updateMovementClassification.assignClassification(
            movementId = movementId,
            categoryId = null,
            merchantId = merchantId,
            provisionalText = "Texto Provisional",
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `clearCategory leaves merchant intact`() = runTest {
        val movementId = MovementId.generate()
        val categoryId = CategoryId.generate()
        val merchantId = MerchantId("m1")
        seedActiveRoot(categoryId)

        updateMovementClassification.assignClassification(
            movementId = movementId,
            categoryId = categoryId,
            merchantId = merchantId,
            provisionalText = null,
        )

        val clearRes = updateMovementClassification.clearCategory(movementId)
        assertTrue(clearRes.isSuccess)

        val saved = categoryDao.movements[movementId.value]
        assertNull(saved?.category_id)
        assertEquals("m1", saved?.merchant_id)
    }

    @Test
    fun `clearMerchant leaves category intact`() = runTest {
        val movementId = MovementId.generate()
        val categoryId = CategoryId.generate()
        val merchantId = MerchantId("m1")
        seedActiveRoot(categoryId)

        updateMovementClassification.assignClassification(
            movementId = movementId,
            categoryId = categoryId,
            merchantId = merchantId,
            provisionalText = null,
        )

        val clearRes = updateMovementClassification.clearMerchant(movementId)
        assertTrue(clearRes.isSuccess)

        val saved = categoryDao.movements[movementId.value]
        assertEquals(categoryId.value, saved?.category_id)
        assertNull(saved?.merchant_id)
        assertNull(saved?.merchant_provisional_text)
    }
}
