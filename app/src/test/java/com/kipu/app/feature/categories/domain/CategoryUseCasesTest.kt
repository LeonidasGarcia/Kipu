package com.kipu.app.feature.categories.domain

import com.kipu.app.core.database.DatabaseTransactionRunner
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.feature.categories.data.FakeCategoryDao
import com.kipu.app.feature.categories.data.FakeCategorySyncScheduler
import com.kipu.app.feature.categories.data.FakeMerchantCatalogDao
import com.kipu.app.feature.categories.data.FakeSessionCoordinator
import com.kipu.app.feature.categories.data.OfflineFirstCategoriesRepository
import com.kipu.app.feature.categories.data.local.CategoryEntity
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.usecase.CreateCategory
import com.kipu.app.feature.categories.domain.usecase.ObserveCategories
import com.kipu.app.feature.categories.domain.usecase.SetCategoryActive
import com.kipu.app.feature.plans.domain.FeatureAccessPolicy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CategoryUseCasesTest {

    private lateinit var categoryDao: FakeCategoryDao
    private lateinit var merchantDao: FakeMerchantCatalogDao
    private lateinit var sessionCoordinator: FakeSessionCoordinator
    private lateinit var syncScheduler: FakeCategorySyncScheduler
    private lateinit var repository: OfflineFirstCategoriesRepository

    private lateinit var observeCategories: ObserveCategories
    private lateinit var createCategory: CreateCategory
    private lateinit var setCategoryActive: SetCategoryActive

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
            featureAccessPolicy = FeatureAccessPolicy(),
        )

        observeCategories = ObserveCategories(repository)
        createCategory = CreateCategory(repository)
        setCategoryActive = SetCategoryActive(repository)
    }

    @Test
    fun `createCategory creates custom root successfully`() = runTest {
        val result = createCategory(
            ownerId = testUserId,
            name = "Entretenimiento",
            icon = "movie",
            color = "#9C27B0",
            parentId = null,
        )

        assertTrue(result.isSuccess)
        val created = result.getOrNull()
        assertNotNull(created)
        assertTrue(created!!.isRoot)
        assertTrue(created.isCustom)

        val items = observeCategories(testUserId).first()
        assertEquals(1, items.size)
        assertEquals("Entretenimiento", items.first().displayName)
        assertEquals("movie", items.first().icon)
    }

    @Test
    fun `createCategory creates subcategory under root`() = runTest {
        val rootResult = createCategory(
            ownerId = testUserId,
            name = "Entretenimiento",
            icon = "movie",
            color = "#9C27B0",
            parentId = null,
        )
        val root = rootResult.getOrThrow()

        val subResult = createCategory(
            ownerId = testUserId,
            name = "Streaming",
            icon = "tv",
            color = "#E91E63",
            parentId = root.id,
        )
        assertTrue(subResult.isSuccess)
        val sub = subResult.getOrThrow()
        assertTrue(sub.isSubcategory)
        assertEquals(root.id, sub.parentId)

        val items = observeCategories(testUserId).first()
        assertEquals(1, items.size)
        assertEquals(1, items.first().subcategories.size)
        assertEquals("Streaming", items.first().subcategories.first().displayName)
    }

    @Test
    fun `createCategory rejects blank name`() = runTest {
        val result = createCategory(
            ownerId = testUserId,
            name = "   ",
            icon = "star",
            color = "#000000",
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `createCategory rejects third level category`() = runTest {
        val root = createCategory(testUserId, "Root", "icon", "#000").getOrThrow()
        val sub1 = createCategory(testUserId, "Sub1", "icon", "#000", root.id).getOrThrow()

        val sub2Result = createCategory(testUserId, "Sub2", "icon", "#000", sub1.id)
        assertTrue(sub2Result.isFailure)
        assertTrue(sub2Result.exceptionOrNull()?.message?.contains("third level") == true)
    }

    @Test
    fun `createCategory blocks 6th active custom root under Free limit`() = runTest {
        for (i in 1..5) {
            val res = createCategory(testUserId, "Cat $i", "icon", "#000")
            assertTrue("Expected cat $i to succeed", res.isSuccess)
        }

        val sixthResult = createCategory(testUserId, "Cat 6", "icon", "#000")
        assertTrue(sixthResult.isFailure)
        assertTrue(sixthResult.exceptionOrNull()?.message?.contains("Free plan limit reached") == true)
    }

    @Test
    fun `subcategories do not consume Free custom root quota`() = runTest {
        val root = createCategory(testUserId, "Root 1", "icon", "#000").getOrThrow()

        // Create 3 subcategories under root 1
        for (i in 1..3) {
            val subRes = createCategory(testUserId, "Sub $i", "icon", "#000", root.id)
            assertTrue(subRes.isSuccess)
        }

        // We can still create 4 more roots (total 5 roots)
        for (i in 2..5) {
            val res = createCategory(testUserId, "Root $i", "icon", "#000")
            assertTrue("Expected root $i to succeed", res.isSuccess)
        }

        // 6th root should fail
        val sixthRoot = createCategory(testUserId, "Root 6", "icon", "#000")
        assertTrue(sixthRoot.isFailure)
    }

    @Test
    fun `setCategoryActive inactivate and reactivate works`() = runTest {
        val root = createCategory(testUserId, "Root", "icon", "#000").getOrThrow()

        val deactivateRes = setCategoryActive(root.id, false)
        assertTrue(deactivateRes.isSuccess)
        assertFalse(categoryDao.getCategoryById(root.id.value)!!.isActive)

        val reactivateRes = setCategoryActive(root.id, true)
        assertTrue(reactivateRes.isSuccess)
        assertTrue(categoryDao.getCategoryById(root.id.value)!!.isActive)
    }

    @Test
    fun `setCategoryActive reactivating 6th active custom root fails under Free limit`() = runTest {
        // Create 5 active custom roots
        val roots = (1..5).map {
            createCategory(testUserId, "Cat $it", "icon", "#000").getOrThrow()
        }

        // Inactivate first root
        setCategoryActive(roots[0].id, false)

        // Now we can create a 6th category (since active count is 4)
        val newRoot = createCategory(testUserId, "Cat 6", "icon", "#000").getOrThrow()

        // Now trying to reactivate roots[0] would make 6 active roots, which must fail
        val reactivateRes = setCategoryActive(roots[0].id, true)
        assertTrue(reactivateRes.isFailure)
        assertTrue(reactivateRes.exceptionOrNull()?.message?.contains("Free plan limit reached") == true)
    }
}
