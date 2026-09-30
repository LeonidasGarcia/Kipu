package com.kipu.app.feature.categories.data

import com.kipu.app.core.database.DatabaseTransactionRunner
import com.kipu.app.core.finance.domain.model.MovementId
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.categories.data.local.CategoryConflictEntity
import com.kipu.app.feature.categories.data.local.CategoryDao
import com.kipu.app.feature.categories.data.local.CategoryEntity
import com.kipu.app.feature.categories.data.local.CategoryPresentationEntity
import com.kipu.app.feature.categories.data.local.CategorySyncOutboxEntity
import com.kipu.app.feature.categories.data.local.MerchantCatalogDao
import com.kipu.app.feature.categories.data.local.MerchantCatalogEntity
import com.kipu.app.feature.categories.data.local.MerchantCategoryFilterRow
import com.kipu.app.feature.categories.data.local.MovementClassificationTuple
import com.kipu.app.feature.categories.data.sync.CategorySyncScheduler
import com.kipu.app.feature.categories.domain.model.Category
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.CategoryOrigin
import com.kipu.app.feature.categories.domain.model.CategoryPresentation
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.domain.model.MovementClassification
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeCategoryDao : CategoryDao {
    val categories = mutableMapOf<String, CategoryEntity>()
    val presentations = mutableMapOf<String, CategoryPresentationEntity>()
    val conflicts = mutableMapOf<String, CategoryConflictEntity>()
    val outbox = mutableListOf<CategorySyncOutboxEntity>()
    val movements = mutableMapOf<String, MovementClassificationTuple>()

    override suspend fun insertCategory(category: CategoryEntity) {
        categories[category.id] = category
    }

    override suspend fun insertCategories(categories: List<CategoryEntity>) {
        categories.forEach { insertCategory(it) }
    }

    override suspend fun insertCategoriesIfAbsent(categories: List<CategoryEntity>) {
        categories.forEach { this.categories.putIfAbsent(it.id, it) }
    }

    override suspend fun updateCategory(category: CategoryEntity) {
        categories[category.id] = category
    }

    override suspend fun getCategoryById(id: String): CategoryEntity? = categories[id]

    override fun observeCategoriesForUser(userId: String): Flow<List<CategoryEntity>> =
        flowOf(categories.values.filter { it.userId == userId || it.userId == null })

    override suspend fun getCategoriesForUser(userId: String): List<CategoryEntity> =
        categories.values.filter { it.userId == userId || it.userId == null }

    override suspend fun countActiveCustomRoots(userId: String): Int =
        categories.values.count { it.userId == userId && it.origin == "CUSTOM" && it.parentId == null && it.isActive }

    override suspend fun countActiveCustomRootsByType(userId: String, categoryType: String): Int =
        categories.values.count { it.userId == userId && it.origin == "CUSTOM" && it.parentId == null && it.isActive && it.categoryType == categoryType }

    override suspend fun insertPresentation(presentation: CategoryPresentationEntity) {
        presentations["${presentation.userId}_${presentation.categoryId}"] = presentation
    }

    override suspend fun insertPresentationsIfAbsent(presentations: List<CategoryPresentationEntity>) {
        presentations.forEach { this.presentations.putIfAbsent("${it.userId}_${it.categoryId}", it) }
    }

    override suspend fun updatePresentation(presentation: CategoryPresentationEntity) {
        presentations["${presentation.userId}_${presentation.categoryId}"] = presentation
    }

    override suspend fun getPresentation(userId: String, categoryId: String): CategoryPresentationEntity? =
        presentations["${userId}_$categoryId"]

    override fun observePresentationsForUser(userId: String): Flow<List<CategoryPresentationEntity>> =
        flowOf(presentations.values.filter { it.userId == userId })

    override suspend fun insertConflict(conflict: CategoryConflictEntity) {
        conflicts[conflict.id] = conflict
    }

    override suspend fun updateConflict(conflict: CategoryConflictEntity) {
        conflicts[conflict.id] = conflict
    }

    override fun observeOpenConflicts(userId: String): Flow<List<CategoryConflictEntity>> =
        flowOf(conflicts.values.filter { it.userId == userId && it.status == "OPEN" })

    override suspend fun getConflict(id: String): CategoryConflictEntity? = conflicts[id]

    override fun observeMovementClassification(movementId: String, userId: String): Flow<MovementClassificationTuple?> =
        flowOf(movements[movementId])

    override suspend fun updateMovementClassification(
        movementId: String,
        userId: String,
        categoryId: String?,
        merchantId: String?,
        provisionalText: String?,
    ): Int {
        movements[movementId] = MovementClassificationTuple(movementId, categoryId, merchantId, provisionalText)
        return 1
    }

    override suspend fun clearCategoryClassification(movementId: String, userId: String): Int {
        val existing = movements[movementId]
        if (existing != null) {
            movements[movementId] = existing.copy(category_id = null)
        }
        return 1
    }

    override suspend fun clearMerchantClassification(movementId: String, userId: String): Int {
        val existing = movements[movementId]
        if (existing != null) {
            movements[movementId] = existing.copy(merchant_id = null, merchant_provisional_text = null)
        }
        return 1
    }

    override suspend fun insertOutboxCommand(command: CategorySyncOutboxEntity) {
        outbox.add(command)
    }

    override suspend fun getPendingOutboxCommands(userId: String): List<CategorySyncOutboxEntity> =
        outbox.filter { it.userId == userId && it.state == "PENDING" }

    override suspend fun updateOutboxCommand(command: CategorySyncOutboxEntity) {
        val idx = outbox.indexOfFirst { it.operationId == command.operationId }
        if (idx >= 0) outbox[idx] = command
    }
}

class FakeMerchantCatalogDao : MerchantCatalogDao {
    val merchants = mutableMapOf<String, MerchantCatalogEntity>()

    override suspend fun insertMerchants(merchants: List<MerchantCatalogEntity>) {
        merchants.forEach { this.merchants[it.id] = it }
    }

    override fun searchMerchants(query: String): Flow<List<MerchantCatalogEntity>> =
        flowOf(merchants.values.filter { it.isActive && it.normalizedName.contains(query, ignoreCase = true) })

    override fun getAllActiveMerchants(): Flow<List<MerchantCatalogEntity>> =
        flowOf(merchants.values.filter { it.isActive })

    override fun getActiveMerchantsByCategory(categoryId: String): Flow<List<MerchantCatalogEntity>> =
        flowOf(merchants.values.filter { it.isActive && it.defaultCategoryId == categoryId })

    override fun searchMerchantsByCategory(query: String, categoryId: String): Flow<List<MerchantCatalogEntity>> =
        flowOf(merchants.values.filter {
            it.isActive && it.defaultCategoryId == categoryId && it.normalizedName.contains(query, ignoreCase = true)
        })

    override fun observeMerchantCategoryFilters(userId: String): Flow<List<MerchantCategoryFilterRow>> =
        flowOf(emptyList())

    override fun observeMerchants(): Flow<List<MerchantCatalogEntity>> = flowOf(merchants.values.toList())

    override suspend fun getMerchantById(id: String): MerchantCatalogEntity? = merchants[id]

    override suspend fun countActiveMerchants(): Int = merchants.values.count { it.isActive }

    override suspend fun getLatestVersion(): Long? = merchants.values.maxOfOrNull { it.version }
}

class FakeSessionCoordinator(
    initialAccess: LocalAccess = LocalAccess.NoOwner,
) : SessionCoordinator {
    override val localAccess = MutableStateFlow<LocalAccess>(initialAccess)
    override val remoteSession = MutableStateFlow<RemoteSession>(RemoteSession.Absent)
    override val currentOwner: LocalOwner? = null

    override suspend fun setActiveOwner(userId: String) {
        localAccess.value = LocalAccess.Available(userId, RemoteSession.Valid(userId, Instant.now().plusSeconds(3600)))
    }

    override suspend fun clearActiveOwner(explicit: Boolean) {
        localAccess.value = LocalAccess.NoOwner
    }

    override suspend fun updateRemoteSession(session: RemoteSession) {
        remoteSession.value = session
    }

    override suspend fun updateLockState(isLocked: Boolean, reason: String) {
        val current = localAccess.value
        if (isLocked && current is LocalAccess.Available) {
            localAccess.value = LocalAccess.Protected(current.userId, reason)
        }
    }
}

class FakeCategorySyncScheduler : CategorySyncScheduler {
    val scheduledUsers = mutableListOf<String>()

    override fun scheduleSync(userId: String) {
        scheduledUsers.add(userId)
    }

    override fun cancelSync(userId: String) {
        scheduledUsers.remove(userId)
    }
}

class OfflineFirstCategoriesRepositoryTest {

    private lateinit var categoryDao: FakeCategoryDao
    private lateinit var merchantDao: FakeMerchantCatalogDao
    private lateinit var sessionCoordinator: FakeSessionCoordinator
    private lateinit var syncScheduler: FakeCategorySyncScheduler
    private lateinit var quotaSelectionDao: FakeQuotaSelectionDao
    private lateinit var repository: OfflineFirstCategoriesRepository

    private val testUserId = UserId.generate()

    @Before
    fun setup() {
        categoryDao = FakeCategoryDao()
        merchantDao = FakeMerchantCatalogDao()
        sessionCoordinator = FakeSessionCoordinator(LocalAccess.NoOwner)
        syncScheduler = FakeCategorySyncScheduler()
        quotaSelectionDao = FakeQuotaSelectionDao()

        val transactionRunner = object : DatabaseTransactionRunner {
            override suspend operator fun <R> invoke(block: suspend () -> R): R = block()
        }

        repository = OfflineFirstCategoriesRepository(
            transactionRunner = transactionRunner,
            categoryDao = categoryDao,
            merchantDao = merchantDao,
            sessionCoordinator = sessionCoordinator,
            syncScheduler = syncScheduler,
            quotaSelectionDao = quotaSelectionDao,
            featureAccessCacheDao = FakeFeatureAccessCacheDao(),
            quotaPolicy = com.kipu.app.feature.plans.domain.PlanQuotaPolicy(),
        )
    }

    @Test
    fun `createCategory fails when session has NoOwner`() = runTest {
        sessionCoordinator.localAccess.value = LocalAccess.NoOwner

        val category = Category(
            id = CategoryId.generate(),
            ownerId = testUserId,
            parentId = null,
            origin = CategoryOrigin.CUSTOM,
            isActive = true,
        )
        val presentation = CategoryPresentation(
            categoryId = category.id,
            ownerId = testUserId,
            name = "Gimnasio",
            icon = "fitness",
            color = "#FF0000",
        )

        val result = repository.createCategory(category, presentation)
        assertTrue(result.isFailure)
        assertEquals("No active owner session", result.exceptionOrNull()?.message)
    }

    @Test
    fun `createCategory succeeds and schedules sync when session is Available`() = runTest {
        sessionCoordinator.localAccess.value = LocalAccess.Available(testUserId.value, RemoteSession.Absent)

        val category = Category(
            id = CategoryId.generate(),
            ownerId = testUserId,
            parentId = null,
            origin = CategoryOrigin.CUSTOM,
            isActive = true,
        )
        val presentation = CategoryPresentation(
            categoryId = category.id,
            ownerId = testUserId,
            name = "Gimnasio",
            icon = "fitness",
            color = "#FF0000",
        )

        val result = repository.createCategory(category, presentation)
        assertTrue(result.isSuccess)

        // Verify entity persisted in Room
        val savedCategory = categoryDao.getCategoryById(category.id.value)
        assertNotNull(savedCategory)
        assertEquals("Gimnasio", categoryDao.getPresentation(testUserId.value, category.id.value)?.name)

        // Verify outbox queued
        val pendingOutbox = categoryDao.getPendingOutboxCommands(testUserId.value)
        assertEquals(1, pendingOutbox.size)
        assertEquals("CREATE_CATEGORY", pendingOutbox.first().commandType)

        // Verify sync scheduled
        assertTrue(syncScheduler.scheduledUsers.contains(testUserId.value))
    }

    @Test
    fun `createCategory fails when free quota of 5 active custom roots is reached`() = runTest {
        sessionCoordinator.localAccess.value = LocalAccess.Available(testUserId.value, RemoteSession.Absent)

        // Seed 5 active custom roots
        for (i in 1..5) {
            val root = CategoryEntity(
                id = "root-$i",
                userId = testUserId.value,
                parentId = null,
                origin = "CUSTOM",
                isActive = true,
                remoteRevision = 1L,
                createdAt = 1000L,
                updatedAt = 1000L,
            )
            categoryDao.insertCategory(root)
        }

        val category = Category(
            id = CategoryId.generate(),
            ownerId = testUserId,
            parentId = null,
            origin = CategoryOrigin.CUSTOM,
            isActive = true,
        )
        val presentation = CategoryPresentation(
            categoryId = category.id,
            ownerId = testUserId,
            name = "Sexta Categoria",
            icon = "tag",
            color = "#00FF00",
        )

        val result = repository.createCategory(category, presentation)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Free plan limit reached") == true)
    }

    @Test
    fun `createCategory fails when third level hierarchy is attempted`() = runTest {
        sessionCoordinator.localAccess.value = LocalAccess.Available(testUserId.value, RemoteSession.Absent)

        val rootId = CategoryId.generate()
        val sub1Id = CategoryId.generate()

        val root = CategoryEntity(rootId.value, testUserId.value, null, "CUSTOM", true, 1L, 1000L, 1000L)
        val sub1 = CategoryEntity(sub1Id.value, testUserId.value, rootId.value, "CUSTOM", true, 1L, 1000L, 1000L)

        categoryDao.insertCategory(root)
        categoryDao.insertCategory(sub1)

        val sub2 = Category(
            id = CategoryId.generate(),
            ownerId = testUserId,
            parentId = sub1Id, // attempting 3rd level
            origin = CategoryOrigin.CUSTOM,
            isActive = true,
        )
        val presentation = CategoryPresentation(
            categoryId = sub2.id,
            ownerId = testUserId,
            name = "Sub-sub",
            icon = "tag",
            color = "#0000FF",
        )

        val result = repository.createCategory(sub2, presentation)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("third level") == true)
    }

    @Test
    fun `setCategoryActive updates category and schedules sync`() = runTest {
        sessionCoordinator.localAccess.value = LocalAccess.Available(testUserId.value, RemoteSession.Absent)

        val catId = CategoryId.generate()
        val cat = CategoryEntity(catId.value, testUserId.value, null, "CUSTOM", false, 1L, 1000L, 1000L)
        categoryDao.insertCategory(cat)

        val result = repository.setCategoryActive(catId, true)
        assertTrue(result.isSuccess)
        assertTrue(categoryDao.getCategoryById(catId.value)?.isActive == true)
        assertTrue(syncScheduler.scheduledUsers.contains(testUserId.value))
    }

    @Test
    fun `updateMovementClassification updates classification and schedules sync`() = runTest {
        sessionCoordinator.localAccess.value = LocalAccess.Available(testUserId.value, RemoteSession.Absent)
        val categoryId = CategoryId.generate()
        categoryDao.insertCategory(CategoryEntity(categoryId.value, testUserId.value, null, "CUSTOM", true, 1L, 1L, 1L))

        val classification = MovementClassification(
            movementId = MovementId.generate(),
            categoryId = categoryId,
            merchantId = MerchantId("merchant-1"),
            merchantProvisionalText = null,
        )

        val result = repository.updateMovementClassification(classification)
        assertTrue(result.isSuccess)
        assertEquals(
            "merchant-1",
            categoryDao.movements[classification.movementId.value]?.merchant_id
        )
        assertTrue(syncScheduler.scheduledUsers.contains(testUserId.value))
    }

    @Test
    fun `free selection locks unselected roots without deactivating them or assigning them`() = runTest {
        sessionCoordinator.localAccess.value = LocalAccess.Available(testUserId.value, RemoteSession.Absent)
        val ids = (1..6).map { CategoryId.generate() }
        ids.forEachIndexed { index, id ->
            categoryDao.insertCategory(
                CategoryEntity(id.value, testUserId.value, null, "CUSTOM", true, 1L, index.toLong(), index.toLong())
            )
        }

        repository.saveSelectedFreeCategoryRoots(testUserId, ids.take(5).toSet()).getOrThrow()
        val observed = com.kipu.app.feature.categories.domain.usecase.ObserveCategories(repository)(testUserId).first()
        val locked = observed.single { it.category.id == ids.last() }

        assertTrue(locked.category.isPlanLocked)
        assertTrue(categoryDao.getCategoryById(ids.last().value)?.isActive == true)
        val result = repository.updateMovementClassification(
            MovementClassification(MovementId.generate(), categoryId = ids.last())
        )
        assertTrue(result.isFailure)
        assertTrue(categoryDao.movements.isEmpty())
    }

    @Test
    fun `free category selection allows five per type but rejects a sixth of one type`() = runTest {
        sessionCoordinator.localAccess.value = LocalAccess.Available(testUserId.value, RemoteSession.Absent)
        val expenseIds = (1..6).map { CategoryId.generate() }
        val incomeIds = (1..5).map { CategoryId.generate() }

        expenseIds.forEachIndexed { index, id ->
            categoryDao.insertCategory(
                CategoryEntity(
                    id = id.value,
                    userId = testUserId.value,
                    parentId = null,
                    origin = "CUSTOM",
                    createdAt = index.toLong(),
                    updatedAt = index.toLong(),
                    categoryType = "EXPENSE",
                ),
            )
        }
        incomeIds.forEachIndexed { index, id ->
            categoryDao.insertCategory(
                CategoryEntity(
                    id = id.value,
                    userId = testUserId.value,
                    parentId = null,
                    origin = "CUSTOM",
                    createdAt = index.toLong(),
                    updatedAt = index.toLong(),
                    categoryType = "INCOME",
                ),
            )
        }

        repository.saveSelectedFreeCategoryRoots(testUserId, expenseIds.take(5).toSet() + incomeIds.toSet()).getOrThrow()
        val overExpenseLimit = repository.saveSelectedFreeCategoryRoots(testUserId, expenseIds.toSet())

        assertTrue(overExpenseLimit.isFailure)
        assertEquals(10, quotaSelectionDao.getSelectedResourceIds(testUserId.value, "CUSTOM_CATEGORIES").size)
    }
}
