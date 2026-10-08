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
import com.kipu.app.feature.categories.data.local.MerchantAliasRuleEntity
import com.kipu.app.feature.categories.data.local.MerchantCategoryPreferenceEntity
import com.kipu.app.feature.categories.data.local.MerchantRulesDao
import com.kipu.app.feature.categories.data.local.MerchantCategoryFilterRow
import com.kipu.app.feature.categories.data.local.MovementClassificationTuple
import com.kipu.app.feature.categories.data.sync.CategorySyncScheduler
import com.kipu.app.feature.categories.domain.model.Category
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.CategoryOrigin
import com.kipu.app.feature.categories.domain.model.CategoryPresentation
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.domain.model.MerchantAliasRule
import com.kipu.app.feature.categories.domain.model.MerchantAliasRuleId
import com.kipu.app.feature.categories.domain.model.MerchantCategoryPreference
import com.kipu.app.feature.categories.domain.model.SourceMerchantText
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
    val merchantSourceTexts = mutableMapOf<Pair<String, String>, String>()

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

    override suspend fun getMerchantRawText(movementId: String, userId: String): String? =
        merchantSourceTexts[userId to movementId]

    override suspend fun setMerchantRawTextIfAbsent(movementId: String, userId: String, rawText: String): Int {
        if (movements[movementId] == null) return 0
        if (merchantSourceTexts[userId to movementId] != null) return 0
        merchantSourceTexts[userId to movementId] = rawText
        val existing = movements[movementId]!!
        movements[movementId] = existing.copy(merchant_raw_text = rawText)
        return 1
    }

    override suspend fun updateMovementClassification(
        movementId: String,
        userId: String,
        categoryId: String?,
        merchantId: String?,
        provisionalText: String?,
    ): Int {
        movements[movementId] = MovementClassificationTuple(
            movementId, categoryId, merchantId, provisionalText, movements[movementId]?.merchant_raw_text,
        )
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

class FakeMerchantRulesDao : MerchantRulesDao {
    val aliases = mutableMapOf<Pair<String, String>, MerchantAliasRuleEntity>()
    val preferences = mutableMapOf<Pair<String, String>, MerchantCategoryPreferenceEntity>()

    override suspend fun upsertAliasRule(rule: MerchantAliasRuleEntity) { aliases[rule.userId to rule.id] = rule }
    override suspend fun updateAliasRule(rule: MerchantAliasRuleEntity) { aliases[rule.userId to rule.id] = rule }
    override suspend fun getAliasRule(userId: String, ruleId: String): MerchantAliasRuleEntity? = aliases[userId to ruleId]
    override fun observeAliasRules(userId: String): Flow<List<MerchantAliasRuleEntity>> =
        flowOf(aliases.values.filter { it.userId == userId })
    override suspend fun setAliasSyncState(userId: String, ruleId: String, state: String, revision: Long?, error: String?): Int {
        val key = userId to ruleId
        val row = aliases[key] ?: return 0
        aliases[key] = row.copy(syncState = state, syncError = error, remoteRevision = revision ?: row.remoteRevision)
        return 1
    }
    override suspend fun upsertPreference(preference: MerchantCategoryPreferenceEntity) {
        preferences[preference.userId to preference.merchantId] = preference
    }
    override suspend fun getPreference(userId: String, merchantId: String): MerchantCategoryPreferenceEntity? =
        preferences[userId to merchantId]
    override fun observePreferences(userId: String): Flow<List<MerchantCategoryPreferenceEntity>> =
        flowOf(preferences.values.filter { it.userId == userId })
    override suspend fun setPreferenceSyncState(userId: String, merchantId: String, state: String, revision: Long?, error: String?): Int {
        val key = userId to merchantId
        val row = preferences[key] ?: return 0
        preferences[key] = row.copy(syncState = state, syncError = error, remoteRevision = revision ?: row.remoteRevision)
        return 1
    }
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
    private lateinit var merchantRulesDao: FakeMerchantRulesDao
    private lateinit var repository: OfflineFirstCategoriesRepository

    private val testUserId = UserId.generate()

    @Before
    fun setup() {
        categoryDao = FakeCategoryDao()
        merchantDao = FakeMerchantCatalogDao()
        sessionCoordinator = FakeSessionCoordinator(LocalAccess.NoOwner)
        syncScheduler = FakeCategorySyncScheduler()
        quotaSelectionDao = FakeQuotaSelectionDao()
        merchantRulesDao = FakeMerchantRulesDao()

        val transactionRunner = object : DatabaseTransactionRunner {
            override suspend operator fun <R> invoke(block: suspend () -> R): R = block()
        }

        repository = OfflineFirstCategoriesRepository(
            transactionRunner = transactionRunner,
            categoryDao = categoryDao,
            merchantDao = merchantDao,
            merchantRulesDao = merchantRulesDao,
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
    fun `initial catalog is available from an empty local cache and preserves existing presentations`() = runTest {
        sessionCoordinator.localAccess.value = LocalAccess.Available(testUserId.value, RemoteSession.Absent)

        repository.ensureInitialCatalog(testUserId).getOrThrow()
        assertEquals(
            setOf("Alimentación", "Transporte", "Servicios"),
            categoryDao.presentations.values
                .filter { it.userId == testUserId.value }
                .map { it.name }
                .toSet(),
        )
        categoryDao.insertPresentation(
            CategoryPresentationEntity(
                categoryId = "00000000-0000-0000-0000-000000000001",
                userId = testUserId.value,
                name = "Comida",
                icon = "restaurant",
                color = "#0F766E",
                remoteRevision = 2L,
                updatedAt = 2L,
            ),
        )
        repository.ensureInitialCatalog(testUserId).getOrThrow()

        val categories = categoryDao.getCategoriesForUser(testUserId.value)
        assertEquals(3, categories.count { it.origin == "SYSTEM" })
        assertEquals("Comida", categoryDao.getPresentation(testUserId.value, "00000000-0000-0000-0000-000000000001")?.name)
    }

    @Test
    fun `createCategory fails when five active custom roots of a type are reached`() = runTest {
        sessionCoordinator.localAccess.value = LocalAccess.Available(testUserId.value, RemoteSession.Absent)

        // Seed 5 active custom roots for INCOME
        for (i in 1..5) {
            val root = CategoryEntity(
                id = "root-$i",
                userId = testUserId.value,
                parentId = null,
                origin = "CUSTOM",
                isActive = true,
                categoryType = "INCOME",
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
            categoryType = com.kipu.app.feature.categories.domain.model.CategoryType.INCOME,
        )
        val presentation = CategoryPresentation(
            categoryId = category.id,
            ownerId = testUserId,
            name = "Sexta Categoria Income",
            icon = "tag",
            color = "#00FF00",
        )

        val result = repository.createCategory(category, presentation)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Free plan limit reached") == true)

        // But creating an EXPENSE root is still allowed (5+5 quota isolation)
        val expenseCat = Category(
            id = CategoryId.generate(),
            ownerId = testUserId,
            parentId = null,
            origin = CategoryOrigin.CUSTOM,
            isActive = true,
            categoryType = com.kipu.app.feature.categories.domain.model.CategoryType.EXPENSE,
        )
        val expensePresentation = CategoryPresentation(
            categoryId = expenseCat.id,
            ownerId = testUserId,
            name = "Primera Categoria Expense",
            icon = "tag",
            color = "#00FF00",
        )
        val expenseResult = repository.createCategory(expenseCat, expensePresentation)
        assertTrue(expenseResult.isSuccess)
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
    fun `merchant source alias and preference persist offline with owner scoped tombstones`() = runTest {
        sessionCoordinator.localAccess.value = LocalAccess.Available(testUserId.value, RemoteSession.Absent)
        val otherOwner = UserId.generate()
        val merchantId = MerchantId("merchant-1")
        merchantDao.insertMerchants(listOf(MerchantCatalogEntity("merchant-1", "Tambo", "tambo", true, 1L, 1000L)))
        val movementId = MovementId.generate()
        val originalClassification = MovementClassificationTuple(movementId.value, "category-old", "merchant-1", null, null)
        categoryDao.movements[movementId.value] = originalClassification
        val rawText = "  IZIPAY*TÁMBO\tLIMA  "

        repository.preserveMerchantSourceText(movementId, SourceMerchantText(rawText)).getOrThrow()
        repository.preserveMerchantSourceText(movementId, SourceMerchantText(rawText)).getOrThrow()
        assertEquals(rawText, categoryDao.movements[movementId.value]?.merchant_raw_text)
        assertEquals(1, categoryDao.outbox.count { it.commandType == "PRESERVE_MERCHANT_SOURCE_TEXT" })
        assertTrue(repository.preserveMerchantSourceText(movementId, SourceMerchantText("changed source")).isFailure)

        val aliasId = MerchantAliasRuleId("rule-1")
        val alias = MerchantAliasRule(aliasId, testUserId, "izipay*tambo", merchantId)
        repository.saveMerchantAliasRule(alias, expectedRevision = null).getOrThrow()
        val editedAlias = alias.copy(normalizedPattern = "izipay*tambo lima", revision = 2)
        repository.saveMerchantAliasRule(editedAlias, expectedRevision = 1).getOrThrow()
        repository.deleteMerchantAliasRule(aliasId, expectedRevision = 2).getOrThrow()
        val storedAlias = merchantRulesDao.getAliasRule(testUserId.value, aliasId.value)
        assertEquals(3L, storedAlias?.remoteRevision)
        assertTrue(storedAlias?.deletedAt != null)

        val categoryA = CategoryEntity("category-a", null, null, "SYSTEM", true, 1L, 1000L, 1000L, "GENERAL")
        val categoryB = CategoryEntity("category-b", null, null, "SYSTEM", true, 1L, 1000L, 1000L, "GENERAL")
        categoryDao.insertCategories(listOf(categoryA, categoryB))
        val preference = MerchantCategoryPreference(
            ownerId = testUserId,
            merchantId = merchantId,
            categoryId = CategoryId("category-a"),
            id = "preference-1",
        )
        repository.saveMerchantCategoryPreference(preference, expectedRevision = null).getOrThrow()
        val updatedPreference = preference.copy(categoryId = CategoryId("category-b"), revision = 2)
        repository.saveMerchantCategoryPreference(updatedPreference, expectedRevision = 1).getOrThrow()
        repository.deleteMerchantCategoryPreference(merchantId, expectedRevision = 2).getOrThrow()
        val storedPreference = merchantRulesDao.getPreference(testUserId.value, merchantId.value)
        assertEquals("category-b", storedPreference?.categoryId)
        assertEquals(3L, storedPreference?.remoteRevision)
        assertTrue(storedPreference?.deletedAt != null)

        assertEquals(originalClassification.copy(merchant_raw_text = rawText), categoryDao.movements[movementId.value])
        assertEquals(2, categoryDao.outbox.count { it.commandType == "UPSERT_MERCHANT_ALIAS_RULE" })
        assertEquals(1, categoryDao.outbox.count { it.commandType == "DELETE_MERCHANT_ALIAS_RULE" })
        assertEquals(2, categoryDao.outbox.count { it.commandType == "UPSERT_MERCHANT_CATEGORY_PREFERENCE" })
        assertEquals(1, categoryDao.outbox.count { it.commandType == "DELETE_MERCHANT_CATEGORY_PREFERENCE" })

        sessionCoordinator.localAccess.value = LocalAccess.Available(otherOwner.value, RemoteSession.Absent)
        assertTrue(repository.observeMerchantAliasRules(testUserId).first().isEmpty())
        assertTrue(repository.observeMerchantCategoryPreferences(testUserId).first().isEmpty())
        assertTrue(repository.observeMerchantAliasRules(otherOwner).first().isEmpty())
        assertTrue(repository.observeMerchantCategoryPreferences(otherOwner).first().isEmpty())
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
    fun `free category selection allows five roots per type and rejects a sixth in a type`() = runTest {
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

        // 5 expense + 5 income succeeds (10 total roots across types)
        val validSelection = expenseIds.take(5).toSet() + incomeIds.toSet()
        repository.saveSelectedFreeCategoryRoots(testUserId, validSelection).getOrThrow()
        assertEquals(10, quotaSelectionDao.getSelectedResourceIds(testUserId.value, "CUSTOM_CATEGORIES").size)

        // Attempting 6 expense roots fails
        val overLimit = repository.saveSelectedFreeCategoryRoots(testUserId, expenseIds.toSet() + incomeIds.take(2).toSet())
        assertTrue(overLimit.isFailure)
    }
}
