package com.kipu.app.feature.categories.domain

import com.kipu.app.core.database.DatabaseTransactionRunner
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
import com.kipu.app.feature.categories.data.local.CategoryEntity
import com.kipu.app.feature.categories.data.local.CategoryPresentationEntity
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.CategoryPresentation
import com.kipu.app.feature.categories.domain.model.ConflictId
import com.kipu.app.feature.categories.domain.usecase.ObserveCategories
import com.kipu.app.feature.categories.domain.usecase.ResolveCategoryConflict
import com.kipu.app.feature.categories.domain.usecase.UpdateCategoryPresentation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CategoryPresentationUseCasesTest {

    private lateinit var categoryDao: FakeCategoryDao
    private lateinit var merchantDao: FakeMerchantCatalogDao
    private lateinit var sessionCoordinator: FakeSessionCoordinator
    private lateinit var syncScheduler: FakeCategorySyncScheduler
    private lateinit var repository: OfflineFirstCategoriesRepository

    private lateinit var updateCategoryPresentation: UpdateCategoryPresentation
    private lateinit var resolveCategoryConflict: ResolveCategoryConflict
    private lateinit var observeCategories: ObserveCategories

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
            quotaSelectionDao = FakeQuotaSelectionDao(),
            featureAccessCacheDao = FakeFeatureAccessCacheDao(),
            quotaPolicy = com.kipu.app.feature.plans.domain.PlanQuotaPolicy(),
        )

        updateCategoryPresentation = UpdateCategoryPresentation(repository)
        resolveCategoryConflict = ResolveCategoryConflict(repository)
        observeCategories = ObserveCategories(repository)
    }

    @Test
    fun `updateCategoryPresentation succeeds when revision matches`() = runTest {
        val catId = CategoryId.generate()
        categoryDao.insertCategory(
            com.kipu.app.feature.categories.data.local.CategoryEntity(
                id = catId.value,
                userId = testUserId.value,
                parentId = null,
                origin = "CUSTOM",
                isActive = true,
                remoteRevision = 1L,
                categoryType = "EXPENSE",
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )
        val initialPres = CategoryPresentationEntity(
            categoryId = catId.value,
            userId = testUserId.value,
            name = "Comida",
            icon = "restaurant",
            color = "#4CAF50",
            remoteRevision = 1L,
            updatedAt = 1000L,
        )
        categoryDao.insertPresentation(initialPres)

        val updated = CategoryPresentation(
            categoryId = catId,
            ownerId = testUserId,
            name = "Alimentos y Bebidas",
            icon = "fastfood",
            color = "#FF9800",
        )

        val result = updateCategoryPresentation(updated, expectedRevision = 1L)
        assertTrue(result.isSuccess)

        val saved = categoryDao.getPresentation(testUserId.value, catId.value)
        assertNotNull(saved)
        assertEquals("Alimentos y Bebidas", saved!!.name)
        assertEquals("fastfood", saved.icon)
        assertEquals(2L, saved.remoteRevision)
        assertTrue(syncScheduler.scheduledUsers.contains(testUserId.value))
    }

    @Test
    fun `updateCategoryPresentation rejects blank name`() = runTest {
        val catId = CategoryId.generate()
        val pres = CategoryPresentation(
            categoryId = catId,
            ownerId = testUserId,
            name = "   ",
            icon = "category",
            color = "#000",
        )

        val result = updateCategoryPresentation(pres, expectedRevision = 1L)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `updateCategoryPresentation records conflict when revision does not match`() = runTest {
        val catId = CategoryId.generate()
        categoryDao.insertCategory(
            com.kipu.app.feature.categories.data.local.CategoryEntity(
                id = catId.value,
                userId = testUserId.value,
                parentId = null,
                origin = "CUSTOM",
                isActive = true,
                remoteRevision = 2L,
                categoryType = "EXPENSE",
                createdAt = 1000L,
                updatedAt = 2000L,
            )
        )
        val currentRemotePres = CategoryPresentationEntity(
            categoryId = catId.value,
            userId = testUserId.value,
            name = "Comida (servidor)",
            icon = "restaurant",
            color = "#4CAF50",
            remoteRevision = 2L, // server is ahead at rev 2
            updatedAt = 2000L,
        )
        categoryDao.insertPresentation(currentRemotePres)

        // Local client attempts update expecting rev 1
        val localPres = CategoryPresentation(
            categoryId = catId,
            ownerId = testUserId,
            name = "Comida (local)",
            icon = "dinner_dining",
            color = "#E91E63",
        )

        val result = updateCategoryPresentation(localPres, expectedRevision = 1L)
        assertTrue(result.isSuccess) // Locally accepted and conflict recorded

        // Verify conflict created
        val conflicts = resolveCategoryConflict.observeConflicts(testUserId).first()
        assertEquals(1, conflicts.size)
        assertEquals(catId, conflicts.first().categoryId)
        assertEquals("OPEN", conflicts.first().status.name)
    }

    @Test
    fun `resolveConflict marks conflict as resolved and queues sync`() = runTest {
        val catId = CategoryId.generate()
        val conflictId = ConflictId.generate()

        val conflictEntity = com.kipu.app.feature.categories.data.local.CategoryConflictEntity(
            id = conflictId.value,
            categoryId = catId.value,
            userId = testUserId.value,
            conflictType = "PRESENTATION",
            localVersion = "{\"name\":\"Local\"}",
            remoteVersion = "{\"name\":\"Remote\"}",
            status = "OPEN",
            createdAt = 1000L,
        )
        categoryDao.insertConflict(conflictEntity)

        val result = resolveCategoryConflict.resolve(conflictId, chosenVersion = "LOCAL")
        assertTrue(result.isSuccess)

        val updatedConflict = categoryDao.getConflict(conflictId.value)
        assertNotNull(updatedConflict)
        assertEquals("RESOLVED", updatedConflict!!.status)
        assertNotNull(updatedConflict.resolutionOperationId)
        assertTrue(syncScheduler.scheduledUsers.contains(testUserId.value))
    }

    @Test
    fun `system category presentation overlay preserves category identity`() = runTest {
        val systemCatId = CategoryId("sys-alimentacion")
        val systemCategory = CategoryEntity(
            id = systemCatId.value,
            userId = null, // system category has null userId
            parentId = null,
            origin = "SYSTEM",
            isActive = true,
            remoteRevision = 1L,
            createdAt = 1000L,
            updatedAt = 1000L,
        )
        categoryDao.insertCategory(systemCategory)

        // User personalizes the system category presentation
        val customPres = CategoryPresentation(
            categoryId = systemCatId,
            ownerId = testUserId,
            name = "Mis Comidas",
            icon = "restaurant",
            color = "#FF5722",
        )

        val result = updateCategoryPresentation(customPres, expectedRevision = 0L)
        assertTrue(result.isSuccess)

        // The system category identity is intact
        val cat = categoryDao.getCategoryById(systemCatId.value)
        assertNotNull(cat)
        assertEquals("SYSTEM", cat!!.origin)
        assertEquals("sys-alimentacion", cat.id)

        // The presentation overlay for the user is saved
        val pres = categoryDao.getPresentation(testUserId.value, systemCatId.value)
        assertNotNull(pres)
        assertEquals("Mis Comidas", pres!!.name)
        assertEquals(testUserId.value, pres.userId)
    }
}
