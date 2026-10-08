package com.kipu.app.feature.categories.data

import com.kipu.app.core.database.DatabaseTransactionRunner
import com.kipu.app.core.finance.domain.model.MovementId
import com.kipu.app.core.finance.domain.model.OperationId
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.categories.data.local.CategoryConflictEntity
import com.kipu.app.feature.categories.data.local.CategoryDao
import com.kipu.app.feature.categories.data.local.CategoryEntity
import com.kipu.app.feature.categories.data.local.CategoryPresentationEntity
import com.kipu.app.feature.categories.data.local.CategorySyncOutboxEntity
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheDao
import com.kipu.app.feature.plans.data.entitlement.DenyUnverifiedEntitlementEvaluator
import com.kipu.app.feature.plans.data.entitlement.EffectiveEntitlementEvaluator
import com.kipu.app.feature.plans.data.entitlement.offlineEntitlementRefreshTicker
import com.kipu.app.feature.plans.domain.model.OfflineEntitlementLeaseDecision
import com.kipu.app.feature.plans.data.local.PlanQuotaSelectionDao
import com.kipu.app.feature.categories.data.local.MerchantCatalogDao
import com.kipu.app.feature.categories.data.local.MerchantCatalogEntity
import com.kipu.app.feature.categories.data.local.MerchantAliasRuleEntity
import com.kipu.app.feature.categories.data.local.MerchantCategoryPreferenceEntity
import com.kipu.app.feature.categories.data.local.MerchantRulesDao
import com.kipu.app.feature.categories.data.remote.CreateCategoryRequestDto
import com.kipu.app.feature.categories.data.remote.DeleteMerchantAliasRuleRequestDto
import com.kipu.app.feature.categories.data.remote.DeleteMerchantCategoryPreferenceRequestDto
import com.kipu.app.feature.categories.data.remote.ResolveCategoryConflictRequestDto
import com.kipu.app.feature.categories.data.remote.SetCategoryActiveRequestDto
import com.kipu.app.feature.categories.data.remote.UpdateCategoryPresentationRequestDto
import com.kipu.app.feature.categories.data.remote.UpdateMovementClassificationRequestDto
import com.kipu.app.feature.categories.data.remote.PreserveMerchantSourceTextRequestDto
import com.kipu.app.feature.categories.data.remote.UpsertMerchantAliasRuleRequestDto
import com.kipu.app.feature.categories.data.remote.UpsertMerchantCategoryPreferenceRequestDto
import com.kipu.app.feature.categories.data.sync.CategorySyncScheduler
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.CategoryRules
import com.kipu.app.feature.categories.domain.model.Category
import com.kipu.app.feature.categories.domain.model.CategoryConflict
import com.kipu.app.feature.categories.domain.model.CategoryConflictStatus
import com.kipu.app.feature.categories.domain.model.CategoryConflictType
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.CategoryOrigin
import com.kipu.app.feature.categories.domain.model.CategoryPresentation
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.categories.domain.model.ConflictId
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.MerchantAliasRule
import com.kipu.app.feature.categories.domain.model.MerchantAliasRuleId
import com.kipu.app.feature.categories.domain.model.MerchantCategoryFilter
import com.kipu.app.feature.categories.domain.model.MerchantCategoryPreference
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.domain.model.MerchantRuleSyncState
import com.kipu.app.feature.categories.domain.model.MovementClassification
import com.kipu.app.feature.categories.domain.model.SourceMerchantText
import com.kipu.app.feature.plans.domain.FeatureAccessPolicy
import com.kipu.app.feature.plans.domain.model.Capability
import com.kipu.app.feature.plans.domain.model.FeatureAccessDecision
import com.kipu.app.feature.plans.domain.model.FeatureAccessRequest
import com.kipu.app.feature.plans.domain.model.FreePlanLimits
import com.kipu.app.feature.plans.domain.PlanQuotaPolicy
import com.kipu.app.feature.plans.domain.model.QuotaGroup
import com.kipu.app.feature.plans.domain.model.EffectiveEntitlement
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class OfflineFirstCategoriesRepository @Inject constructor(
    private val transactionRunner: DatabaseTransactionRunner,
    private val categoryDao: CategoryDao,
    private val merchantDao: MerchantCatalogDao,
    private val merchantRulesDao: MerchantRulesDao,
    private val sessionCoordinator: SessionCoordinator,
    private val syncScheduler: CategorySyncScheduler,
    private val featureAccessPolicy: FeatureAccessPolicy = FeatureAccessPolicy(),
    private val quotaSelectionDao: PlanQuotaSelectionDao,
    private val featureAccessCacheDao: FeatureAccessCacheDao,
    private val quotaPolicy: PlanQuotaPolicy,
    private val planQuotaSyncScheduler: com.kipu.app.feature.plans.data.sync.PlanQuotaSyncScheduler? = null,
    private val entitlementEvaluator: EffectiveEntitlementEvaluator = DenyUnverifiedEntitlementEvaluator,
) : CategoriesRepository {

    private data class InitialSystemCategory(
        val id: String,
        val name: String,
    )

    private companion object {
        val initialSystemCategories = listOf(
            InitialSystemCategory("00000000-0000-0000-0000-000000000001", "Alimentación"),
            InitialSystemCategory("00000000-0000-0000-0000-000000000002", "Transporte"),
            InitialSystemCategory("00000000-0000-0000-0000-000000000003", "Servicios"),
        )
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun sha256(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(content.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun currentUserId(): String? {
        return (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId
    }

    override suspend fun ensureInitialCatalog(userId: UserId): Result<Unit> = runCatching {
        require(currentUserId() == userId.value) { "No active owner session" }
        val now = System.currentTimeMillis()
        transactionRunner {
            categoryDao.insertCategoriesIfAbsent(initialSystemCategories.map { category ->
                CategoryEntity(
                    id = category.id,
                    userId = null,
                    parentId = null,
                    origin = "SYSTEM",
                    categoryType = CategoryType.GENERAL.name,
                    isActive = true,
                    remoteRevision = 1L,
                    createdAt = now,
                    updatedAt = now,
                )
            })
            categoryDao.insertPresentationsIfAbsent(initialSystemCategories.map { category ->
                CategoryPresentationEntity(
                    categoryId = category.id,
                    userId = userId.value,
                    name = category.name,
                    icon = "category",
                    color = "#757575",
                    remoteRevision = 1L,
                    updatedAt = now,
                )
            })
        }
    }

    override fun observeCategories(userId: UserId): Flow<List<Category>> {
        val selectedIds = quotaSelectionDao.observeSelectedResourceIds(userId.value, QuotaGroup.CUSTOM_CATEGORIES.name)
        val access = featureAccessCacheDao.observe(UUID.fromString(userId.value))
        return combine(categoryDao.observeCategoriesForUser(userId.value), selectedIds, access, offlineEntitlementRefreshTicker()) { entities, selected, cache, _ ->
            val isPremium = entitlementEvaluator.evaluate(userId.value, cache) is OfflineEntitlementLeaseDecision.Allowed
            val quota = CategoryRules.evaluateCustomRootQuota(
                categories = entities.asSequence()
                    .filter { it.userId == userId.value && it.parentId == null && it.origin == "CUSTOM" && it.isActive }
                    .map { it.toDomainCategory() }
                    .toList(),
                selectedRootIds = selected.mapTo(mutableSetOf()) { CategoryId(it) },
                limitPerType = FreePlanLimits().customCategories,
                premiumVerified = isPremium,
            )
            val lockedRoots = quota.planLockedRootIds.mapTo(mutableSetOf()) { it.value }
            entities.map { entity ->
                val rootId = entity.parentId ?: entity.id
                Category(
                    id = CategoryId(entity.id),
                    ownerId = entity.userId?.let { UserId(it) },
                    parentId = entity.parentId?.let { CategoryId(it) },
                    origin = CategoryOrigin.valueOf(entity.origin),
                    isActive = entity.isActive,
                    revision = entity.remoteRevision,
                    isPlanLocked = rootId in lockedRoots,
                    categoryType = CategoryType.fromStorage(entity.categoryType),
                )
            }
        }
    }

    override fun observePremiumVerified(userId: UserId): Flow<Boolean> = combine(
        featureAccessCacheDao.observe(UUID.fromString(userId.value)),
        offlineEntitlementRefreshTicker(),
    ) { cache, _ ->
        entitlementEvaluator.evaluate(userId.value, cache) is OfflineEntitlementLeaseDecision.Allowed
    }.distinctUntilChanged()

    override fun observeSelectedFreeCategoryRoots(userId: UserId): Flow<Set<CategoryId>> =
        quotaSelectionDao.observeSelectedResourceIds(userId.value, QuotaGroup.CUSTOM_CATEGORIES.name)
            .map { ids -> ids.mapTo(linkedSetOf(), ::CategoryId) }

    override suspend fun saveSelectedFreeCategoryRoots(userId: UserId, categoryIds: Set<CategoryId>): Result<Unit> = runCatching {
        require(currentUserId() == userId.value) { "No active owner session" }
        val activeRoots = categoryDao.getCategoriesForUser(userId.value)
            .filter { it.userId == userId.value && it.parentId == null && it.origin == "CUSTOM" && it.isActive }
        require(currentUserId() == userId.value) { "Owner changed while loading categories" }
        val evaluation = CategoryRules.evaluateCustomRootQuota(
            categories = activeRoots.map { it.toDomainCategory() },
            selectedRootIds = categoryIds,
            limitPerType = FreePlanLimits().customCategories,
        )
        require(evaluation.selectedRootIds == categoryIds) { "Selection includes unavailable categories" }
        require(evaluation.selectedWithinLimit) {
            "Free category selection exceeds the per-type limit (max ${evaluation.limitPerType} each)"
        }
        transactionRunner {
            require(currentUserId() == userId.value) { "Owner changed before saving category selection" }
            quotaSelectionDao.replaceSelection(
                userId = userId.value,
                featureKey = QuotaGroup.CUSTOM_CATEGORIES.name,
                resourceType = "CATEGORY_ROOT",
                resourceIds = categoryIds.map { it.value },
                now = System.currentTimeMillis(),
            )
            require(currentUserId() == userId.value) { "Owner changed while saving category selection" }
        }
        planQuotaSyncScheduler?.scheduleSync(userId.value)
    }

    override fun observeCategoryPresentations(userId: UserId): Flow<List<CategoryPresentation>> {
        return categoryDao.observePresentationsForUser(userId.value).map { entities ->
            entities.map { entity ->
                CategoryPresentation(
                    categoryId = CategoryId(entity.categoryId),
                    ownerId = UserId(entity.userId),
                    name = entity.name,
                    icon = entity.icon,
                    color = entity.color,
                    revision = entity.remoteRevision,
                )
            }
        }
    }

    override suspend fun getCategory(categoryId: CategoryId): Category? {
        return categoryDao.getCategoryById(categoryId.value)?.let { entity ->
            Category(
                id = CategoryId(entity.id),
                ownerId = entity.userId?.let { UserId(it) },
                parentId = entity.parentId?.let { CategoryId(it) },
                origin = CategoryOrigin.valueOf(entity.origin),
                isActive = entity.isActive,
                revision = entity.remoteRevision,
                categoryType = CategoryType.fromStorage(entity.categoryType),
            )
        }
    }

    override suspend fun createCategory(
        category: Category,
        presentation: CategoryPresentation
    ): Result<Category> {
        val userId = currentUserId() ?: return Result.failure(IllegalStateException("No active owner session"))
        if (category.ownerId?.value != userId || presentation.ownerId.value != userId) {
            return Result.failure(IllegalStateException("Category owner does not match the active owner"))
        }
        if (!category.isCustom) {
            return Result.failure(IllegalArgumentException("Only custom categories can be created locally"))
        }
        val now = System.currentTimeMillis()
        val operationId = UUID.randomUUID().toString()

        val payloadDto = CreateCategoryRequestDto(
            operationId = operationId,
            categoryId = category.id.value,
            parentId = category.parentId?.value,
            categoryType = category.categoryType.name,
            name = presentation.name,
            icon = presentation.icon,
            color = presentation.color,
            payloadHash = sha256("$operationId:${category.id.value}:${category.categoryType.name}:${presentation.name}")
        )
        val payloadJson = json.encodeToString(payloadDto)

        val writeFailure = try {
            transactionRunner {
                require(currentUserId() == userId) { "Owner changed before creating category" }
                val entities = categoryDao.getCategoriesForUser(userId)
                require(currentUserId() == userId) { "Owner changed while loading categories" }
                require(categoryDao.getCategoryById(category.id.value) == null) { "Category already exists" }
                require(currentUserId() == userId) { "Owner changed while checking category ID" }

                val existing = entities.associate { CategoryId(it.id) to it.toDomainCategory() }
                CategoryRules.validateHierarchy(category.id, category.parentId, existing).getOrThrow()
                CategoryRules.validateCategoryType(category.categoryType, category.parentId, existing).getOrThrow()

                if (category.isRoot) {
                    val cache = featureAccessCacheDao.get(UUID.fromString(userId))
                    require(currentUserId() == userId) { "Owner changed while checking entitlement" }
                    val premiumVerified = entitlementEvaluator.evaluate(userId, cache) is OfflineEntitlementLeaseDecision.Allowed
                    val quota = CategoryRules.evaluateCustomRootQuota(
                        categories = entities.map { it.toDomainCategory() },
                        selectedRootIds = emptySet(),
                        limitPerType = FreePlanLimits().customCategories,
                        premiumVerified = premiumVerified,
                    )
                    check(quota.canAdd(category.categoryType)) {
                        "Free plan limit reached: maximum ${quota.limitPerType} active custom roots per category type"
                    }
                }

                require(currentUserId() == userId) { "Owner changed before writing category" }
                categoryDao.insertCategory(
                    CategoryEntity(
                        id = category.id.value,
                        userId = userId,
                        parentId = category.parentId?.value,
                        origin = category.origin.name,
                        categoryType = category.categoryType.name,
                        isActive = category.isActive,
                        remoteRevision = 1L,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                require(currentUserId() == userId) { "Owner changed while writing category" }

                categoryDao.insertPresentation(
                    CategoryPresentationEntity(
                        categoryId = category.id.value,
                        userId = userId,
                        name = presentation.name,
                        icon = presentation.icon,
                        color = presentation.color,
                        remoteRevision = 1L,
                        updatedAt = now
                    )
                )
                require(currentUserId() == userId) { "Owner changed while writing category presentation" }

                categoryDao.insertOutboxCommand(
                    CategorySyncOutboxEntity(
                        operationId = operationId,
                        userId = userId,
                        commandType = "CREATE_CATEGORY",
                        aggregateType = "CATEGORY",
                        aggregateId = category.id.value,
                        payloadJson = payloadJson,
                        payloadHash = payloadDto.payloadHash,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                require(currentUserId() == userId) { "Owner changed while writing category command" }
            }
            null
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            error
        }

        writeFailure?.let { return Result.failure(it) }

        if (currentUserId() == userId) syncScheduler.scheduleSync(userId)
        return Result.success(category)
    }

    override suspend fun setCategoryActive(categoryId: CategoryId, isActive: Boolean): Result<Unit> {
        val userId = currentUserId() ?: return Result.failure(IllegalStateException("No active owner session"))
        val now = System.currentTimeMillis()
        val operationId = UUID.randomUUID().toString()

        val existing = categoryDao.getCategoryById(categoryId.value)
            ?: return Result.failure(IllegalArgumentException("Category not found: ${categoryId.value}"))
        if (existing.userId != userId) {
            return Result.failure(IllegalStateException("Category does not belong to the active owner"))
        }

        val payloadDto = SetCategoryActiveRequestDto(
            operationId = operationId,
            categoryId = categoryId.value,
            isActive = isActive,
            expectedRevision = existing.remoteRevision,
            payloadHash = sha256("$operationId:${categoryId.value}:$isActive")
        )
        val payloadJson = json.encodeToString(payloadDto)

        val writeFailure = try {
            transactionRunner {
                require(currentUserId() == userId) { "Owner changed before updating category" }
                val current = categoryDao.getCategoryById(categoryId.value)
                    ?: error("Category not found: ${categoryId.value}")
                require(currentUserId() == userId) { "Owner changed while loading category" }
                require(current.userId == userId) { "Category does not belong to the active owner" }

                if (current.parentId == null && current.origin == "CUSTOM" && isActive && !current.isActive) {
                    val cache = featureAccessCacheDao.get(UUID.fromString(userId))
                    require(currentUserId() == userId) { "Owner changed while checking entitlement" }
                    val premiumVerified = entitlementEvaluator.evaluate(userId, cache) is OfflineEntitlementLeaseDecision.Allowed
                    val categories = categoryDao.getCategoriesForUser(userId)
                    require(currentUserId() == userId) { "Owner changed while counting active categories" }
                    val quota = CategoryRules.evaluateCustomRootQuota(
                        categories = categories.map { it.toDomainCategory() },
                        selectedRootIds = emptySet(),
                        limitPerType = FreePlanLimits().customCategories,
                        premiumVerified = premiumVerified,
                    )
                    check(quota.canAdd(CategoryType.fromStorage(current.categoryType))) {
                        "Free plan limit reached: maximum ${quota.limitPerType} active custom roots per category type"
                    }
                }

                require(currentUserId() == userId) { "Owner changed before writing category status" }
                categoryDao.updateCategory(current.copy(isActive = isActive, updatedAt = now))
                require(currentUserId() == userId) { "Owner changed while writing category status" }
                categoryDao.insertOutboxCommand(
                    CategorySyncOutboxEntity(
                        operationId = operationId,
                        userId = userId,
                        commandType = "SET_CATEGORY_ACTIVE",
                        aggregateType = "CATEGORY",
                        aggregateId = categoryId.value,
                        expectedRevision = current.remoteRevision,
                        payloadJson = payloadJson,
                        payloadHash = payloadDto.payloadHash,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                require(currentUserId() == userId) { "Owner changed while writing category command" }
            }
            null
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            error
        }

        writeFailure?.let { return Result.failure(it) }
        if (currentUserId() == userId) syncScheduler.scheduleSync(userId)
        return Result.success(Unit)
    }

    override suspend fun updateCategoryPresentation(
        presentation: CategoryPresentation,
        expectedRevision: Long
    ): Result<Unit> {
        val userId = currentUserId() ?: return Result.failure(IllegalStateException("No active owner session"))
        if (presentation.ownerId.value != userId) {
            return Result.failure(IllegalStateException("Presentation owner does not match the active owner"))
        }
        val now = System.currentTimeMillis()
        val operationId = UUID.randomUUID().toString()

        val payloadDto = UpdateCategoryPresentationRequestDto(
            operationId = operationId,
            categoryId = presentation.categoryId.value,
            name = presentation.name,
            icon = presentation.icon,
            color = presentation.color,
            expectedRevision = expectedRevision,
            payloadHash = sha256("$operationId:${presentation.categoryId.value}:${presentation.name}")
        )
        val payloadJson = json.encodeToString(payloadDto)

        val writeFailure = try {
            transactionRunner {
                require(currentUserId() == userId) { "Owner changed before updating presentation" }
                val category = categoryDao.getCategoryById(presentation.categoryId.value)
                    ?: error("Category not found: ${presentation.categoryId.value}")
                require(currentUserId() == userId) { "Owner changed while loading category" }
                require(category.userId == null || category.userId == userId) {
                    "Category does not belong to the active owner"
                }
                val existing = categoryDao.getPresentation(userId, presentation.categoryId.value)
                require(currentUserId() == userId) { "Owner changed while loading presentation" }

                if (existing != null && existing.remoteRevision != expectedRevision) {
                    categoryDao.insertConflict(
                        CategoryConflictEntity(
                            id = UUID.randomUUID().toString(),
                            categoryId = presentation.categoryId.value,
                            userId = userId,
                            conflictType = "PRESENTATION",
                            localVersion = payloadJson,
                            remoteVersion = json.encodeToString(existing),
                            status = "OPEN",
                            createdAt = now
                        )
                    )
                } else {
                    categoryDao.insertPresentation(
                        CategoryPresentationEntity(
                            categoryId = presentation.categoryId.value,
                            userId = userId,
                            name = presentation.name,
                            icon = presentation.icon,
                            color = presentation.color,
                            remoteRevision = expectedRevision + 1,
                            updatedAt = now
                        )
                    )
                }
                require(currentUserId() == userId) { "Owner changed while writing presentation" }
                categoryDao.insertOutboxCommand(
                    CategorySyncOutboxEntity(
                        operationId = operationId,
                        userId = userId,
                        commandType = "UPDATE_PRESENTATION",
                        aggregateType = "CATEGORY_PRESENTATION",
                        aggregateId = presentation.categoryId.value,
                        expectedRevision = expectedRevision,
                        payloadJson = payloadJson,
                        payloadHash = payloadDto.payloadHash,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                require(currentUserId() == userId) { "Owner changed while writing presentation command" }
            }
            null
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            error
        }

        writeFailure?.let { return Result.failure(it) }
        if (currentUserId() == userId) syncScheduler.scheduleSync(userId)
        return Result.success(Unit)
    }

    override fun searchMerchants(query: String): Flow<List<MerchantCatalogEntry>> {
        val normalized = CategoryRules.normalizeText(query)
        return merchantDao.searchMerchants(normalized).map { entities ->
            entities.map(::toMerchantCatalogEntry)
        }
    }

    override fun observeMerchantCatalog(): Flow<List<MerchantCatalogEntry>> =
        merchantDao.getAllActiveMerchants().map { entities -> entities.map(::toMerchantCatalogEntry) }

    override fun observeMerchantCategoryFilters(): Flow<List<MerchantCategoryFilter>> {
        val userId = currentUserId() ?: return flowOf(emptyList())
        return merchantDao.observeMerchantCategoryFilters(userId).map { filters ->
            filters.map { MerchantCategoryFilter(CategoryId(it.categoryId), it.name) }
        }
    }

    private fun toMerchantCatalogEntry(entity: MerchantCatalogEntity) = MerchantCatalogEntry(
        id = MerchantId(entity.id),
        name = entity.name,
        normalizedName = entity.normalizedName,
        isActive = entity.isActive,
        defaultCategoryId = entity.defaultCategoryId?.let(::CategoryId),
        priority = entity.priority,
        logoKey = entity.logoKey,
        brandColor = entity.brandColor,
    )

    override fun observeMerchantAliasRules(userId: UserId): Flow<List<MerchantAliasRule>> {
        if (currentUserId() != userId.value) return flowOf(emptyList())
        return merchantRulesDao.observeAliasRules(userId.value).map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun saveMerchantAliasRule(
        rule: MerchantAliasRule,
        expectedRevision: Long?,
    ): Result<Unit> = runCatching {
        val userId = rule.ownerId.value
        require(currentUserId() == userId) { "No active owner session" }
        require(rule.deletedAt == null) { "Use the tombstone command to remove an alias" }
        require(rule.normalizedPattern == com.kipu.app.feature.categories.domain.MerchantAliasRules.normalize(rule.normalizedPattern)) {
            "Alias pattern is not normalized"
        }
        require(rule.normalizedPattern.length <= 160) { "Alias pattern is too long" }
        val merchant = merchantDao.getMerchantById(rule.merchantId.value)
        require(merchant?.isActive == true) { "Canonical merchant is unavailable" }
        require(currentUserId() == userId) { "Owner changed while validating alias" }

        val operationId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val payloadHash = sha256("$operationId:${rule.id.value}:${rule.normalizedPattern}:${rule.merchantId.value}:${expectedRevision ?: "new"}")
        val dto = UpsertMerchantAliasRuleRequestDto(
            operationId = operationId,
            ruleId = rule.id.value,
            normalizedPattern = rule.normalizedPattern,
            merchantId = rule.merchantId.value,
            expectedRevision = expectedRevision,
            payloadHash = payloadHash,
        )
        transactionRunner {
            require(currentUserId() == userId) { "Owner changed before saving alias" }
            val existing = merchantRulesDao.getAliasRule(userId, rule.id.value)
            if (expectedRevision == null) {
                require(existing == null) { "Alias already exists; refresh before editing" }
            } else {
                require(existing?.remoteRevision == expectedRevision) { "Alias revision changed; refresh before editing" }
            }
            merchantRulesDao.upsertAliasRule(
                MerchantAliasRuleEntity(
                    id = rule.id.value,
                    userId = userId,
                    normalizedPattern = rule.normalizedPattern,
                    merchantId = rule.merchantId.value,
                    remoteRevision = rule.revision,
                    deletedAt = null,
                    updatedAt = now,
                    syncState = MerchantRuleSyncState.PENDING.name,
                ),
            )
            categoryDao.insertOutboxCommand(
                CategorySyncOutboxEntity(
                    operationId = operationId,
                    userId = userId,
                    commandType = "UPSERT_MERCHANT_ALIAS_RULE",
                    aggregateType = "MERCHANT_ALIAS_RULE",
                    aggregateId = rule.id.value,
                    expectedRevision = expectedRevision,
                    payloadJson = json.encodeToString(dto),
                    payloadHash = payloadHash,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            require(currentUserId() == userId) { "Owner changed while saving alias command" }
        }
        if (currentUserId() == userId) syncScheduler.scheduleSync(userId)
    }

    override suspend fun deleteMerchantAliasRule(
        ruleId: MerchantAliasRuleId,
        expectedRevision: Long,
    ): Result<Unit> = runCatching {
        val userId = currentUserId() ?: error("No active owner session")
        val existing = merchantRulesDao.getAliasRule(userId, ruleId.value)
            ?: error("Alias rule is unavailable")
        require(existing.remoteRevision == expectedRevision && existing.deletedAt == null) {
            "Alias revision changed; refresh before removing"
        }
        val operationId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val payloadHash = sha256("$operationId:${ruleId.value}:$expectedRevision:delete")
        val dto = DeleteMerchantAliasRuleRequestDto(operationId, ruleId.value, expectedRevision, payloadHash)
        transactionRunner {
            require(currentUserId() == userId) { "Owner changed before removing alias" }
            merchantRulesDao.upsertAliasRule(
                existing.copy(
                    remoteRevision = expectedRevision + 1L,
                    deletedAt = now,
                    updatedAt = now,
                    syncState = MerchantRuleSyncState.PENDING.name,
                    syncError = null,
                ),
            )
            categoryDao.insertOutboxCommand(
                CategorySyncOutboxEntity(
                    operationId = operationId,
                    userId = userId,
                    commandType = "DELETE_MERCHANT_ALIAS_RULE",
                    aggregateType = "MERCHANT_ALIAS_RULE",
                    aggregateId = ruleId.value,
                    expectedRevision = expectedRevision,
                    payloadJson = json.encodeToString(dto),
                    payloadHash = payloadHash,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            require(currentUserId() == userId) { "Owner changed while removing alias" }
        }
        if (currentUserId() == userId) syncScheduler.scheduleSync(userId)
    }

    override fun observeMerchantCategoryPreferences(userId: UserId): Flow<List<MerchantCategoryPreference>> {
        if (currentUserId() != userId.value) return flowOf(emptyList())
        return merchantRulesDao.observePreferences(userId.value).map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun saveMerchantCategoryPreference(
        preference: MerchantCategoryPreference,
        expectedRevision: Long?,
    ): Result<Unit> = runCatching {
        val userId = preference.ownerId.value
        require(currentUserId() == userId) { "No active owner session" }
        val merchant = merchantDao.getMerchantById(preference.merchantId.value)
        require(merchant?.isActive == true) { "Canonical merchant is unavailable" }
        val category = categoryDao.getCategoryById(preference.categoryId.value)
            ?: error("Preference category is unavailable")
        require(category.userId == null || category.userId == userId) { "Preference category belongs to another owner" }
        val root = category.parentId?.let { parentId -> categoryDao.getCategoryById(parentId) }
        require(CategoryRules.isEligibleForAssignment(category.toDomainCategory(), root?.toDomainCategory())) {
            "Preference category or root is inactive"
        }
        val rootId = category.parentId ?: category.id
        require(rootId !in planLockedCategoryRootIds(userId)) { "Preference category is blocked by the Free plan selection" }

        val operationId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val payloadHash = sha256("$operationId:${preference.id}:${preference.merchantId.value}:${preference.categoryId.value}:${expectedRevision ?: "new"}")
        val dto = UpsertMerchantCategoryPreferenceRequestDto(
            operationId = operationId,
            preferenceId = preference.id,
            merchantId = preference.merchantId.value,
            categoryId = preference.categoryId.value,
            expectedRevision = expectedRevision,
            payloadHash = payloadHash,
        )
        transactionRunner {
            require(currentUserId() == userId) { "Owner changed before saving preference" }
            val existing = merchantRulesDao.getPreference(userId, preference.merchantId.value)
            if (expectedRevision == null) {
                require(existing == null) { "Preference already exists; refresh before editing" }
            } else {
                require(existing?.remoteRevision == expectedRevision && existing.id == preference.id) {
                    "Preference revision changed; refresh before editing"
                }
            }
            merchantRulesDao.upsertPreference(
                MerchantCategoryPreferenceEntity(
                    userId = userId,
                    merchantId = preference.merchantId.value,
                    id = preference.id,
                    categoryId = preference.categoryId.value,
                    remoteRevision = preference.revision,
                    deletedAt = null,
                    updatedAt = now,
                    syncState = MerchantRuleSyncState.PENDING.name,
                ),
            )
            categoryDao.insertOutboxCommand(
                CategorySyncOutboxEntity(
                    operationId = operationId,
                    userId = userId,
                    commandType = "UPSERT_MERCHANT_CATEGORY_PREFERENCE",
                    aggregateType = "MERCHANT_CATEGORY_PREFERENCE",
                    aggregateId = preference.merchantId.value,
                    expectedRevision = expectedRevision,
                    payloadJson = json.encodeToString(dto),
                    payloadHash = payloadHash,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            require(currentUserId() == userId) { "Owner changed while saving preference" }
        }
        if (currentUserId() == userId) syncScheduler.scheduleSync(userId)
    }

    override suspend fun deleteMerchantCategoryPreference(
        merchantId: MerchantId,
        expectedRevision: Long,
    ): Result<Unit> = runCatching {
        val userId = currentUserId() ?: error("No active owner session")
        val existing = merchantRulesDao.getPreference(userId, merchantId.value)
            ?: error("Merchant category preference is unavailable")
        require(existing.remoteRevision == expectedRevision && existing.deletedAt == null) {
            "Preference revision changed; refresh before removing"
        }
        val operationId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val payloadHash = sha256("$operationId:${existing.id}:$expectedRevision:delete")
        val dto = DeleteMerchantCategoryPreferenceRequestDto(operationId, existing.id, expectedRevision, payloadHash)
        transactionRunner {
            require(currentUserId() == userId) { "Owner changed before removing preference" }
            merchantRulesDao.upsertPreference(
                existing.copy(
                    remoteRevision = expectedRevision + 1L,
                    deletedAt = now,
                    updatedAt = now,
                    syncState = MerchantRuleSyncState.PENDING.name,
                    syncError = null,
                ),
            )
            categoryDao.insertOutboxCommand(
                CategorySyncOutboxEntity(
                    operationId = operationId,
                    userId = userId,
                    commandType = "DELETE_MERCHANT_CATEGORY_PREFERENCE",
                    aggregateType = "MERCHANT_CATEGORY_PREFERENCE",
                    aggregateId = merchantId.value,
                    expectedRevision = expectedRevision,
                    payloadJson = json.encodeToString(dto),
                    payloadHash = payloadHash,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            require(currentUserId() == userId) { "Owner changed while removing preference" }
        }
        if (currentUserId() == userId) syncScheduler.scheduleSync(userId)
    }

    override fun observeMovementClassification(movementId: MovementId): Flow<MovementClassification?> {
        val userId = currentUserId() ?: return kotlinx.coroutines.flow.flowOf(null)
        return categoryDao.observeMovementClassification(movementId.value, userId).map { tuple ->
            tuple?.let {
                MovementClassification(
                    movementId = MovementId(it.id),
                    categoryId = it.category_id?.let { cid -> CategoryId(cid) },
                    merchantId = it.merchant_id?.let { mid -> MerchantId(mid) },
                    merchantProvisionalText = it.merchant_provisional_text,
                    merchantRawText = it.merchant_raw_text,
                )
            }
        }
    }

    override suspend fun preserveMerchantSourceText(
        movementId: MovementId,
        sourceText: SourceMerchantText,
    ): Result<Unit> = runCatching {
        val userId = currentUserId() ?: error("No active owner session")
        require(sourceText.value.isNotEmpty()) { "Merchant source text cannot be empty" }
        require(currentUserId() == userId) { "Owner changed before preserving merchant source text" }
        transactionRunner {
            val existing = categoryDao.getMerchantRawText(movementId.value, userId)
                ?: run {
                    val written = categoryDao.setMerchantRawTextIfAbsent(movementId.value, userId, sourceText.value)
                    require(written == 1) { "Movement does not belong to the active owner" }
                    null
                }
            if (existing != null) {
                require(existing == sourceText.value) { "Merchant source text is immutable once captured" }
                return@transactionRunner
            }
            require(currentUserId() == userId) { "Owner changed while preserving merchant source text" }
            val operationId = UUID.randomUUID().toString()
            val hash = sha256("$operationId:${movementId.value}:${sourceText.value}")
            val dto = PreserveMerchantSourceTextRequestDto(
                operationId = operationId,
                movementId = movementId.value,
                merchantRawText = sourceText.value,
                payloadHash = hash,
            )
            categoryDao.insertOutboxCommand(
                CategorySyncOutboxEntity(
                    operationId = operationId,
                    userId = userId,
                    commandType = "PRESERVE_MERCHANT_SOURCE_TEXT",
                    aggregateType = "MOVEMENT_SOURCE_TEXT",
                    aggregateId = movementId.value,
                    payloadJson = json.encodeToString(dto),
                    payloadHash = hash,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            require(currentUserId() == userId) { "Owner changed while saving merchant source command" }
        }
        if (currentUserId() == userId) syncScheduler.scheduleSync(userId)
    }

    override suspend fun updateMovementClassification(classification: MovementClassification): Result<Unit> {
        val userId = currentUserId() ?: return Result.failure(IllegalStateException("No active owner session"))
        classification.categoryId?.let { categoryId ->
            val category = categoryDao.getCategoryById(categoryId.value)
                ?: return Result.failure(IllegalArgumentException("Category is unavailable"))
            if (category.userId != null && category.userId != userId) {
                return Result.failure(IllegalStateException("Category does not belong to the active owner"))
            }
            val parent = category.parentId?.let { parentId -> categoryDao.getCategoryById(parentId) }
            if (!CategoryRules.isEligibleForAssignment(category.toDomainCategory(), parent?.toDomainCategory())) {
                return Result.failure(IllegalStateException("Inactive category or root cannot be assigned"))
            }
            val lockedRootIds = planLockedCategoryRootIds(userId)
            val rootId = category.parentId ?: category.id
            if (rootId in lockedRootIds) {
                return Result.failure(IllegalStateException("Category is blocked by the Free plan selection"))
            }
        }
        if (currentUserId() != userId) return Result.failure(IllegalStateException("Owner changed while validating classification"))
        val now = System.currentTimeMillis()
        val operationId = UUID.randomUUID().toString()

        val payloadDto = UpdateMovementClassificationRequestDto(
            operationId = operationId,
            movementId = classification.movementId.value,
            categoryId = classification.categoryId?.value,
            merchantId = classification.merchantId?.value,
            merchantProvisionalText = classification.merchantProvisionalText,
            payloadHash = sha256("$operationId:${classification.movementId.value}")
        )
        val payloadJson = json.encodeToString(payloadDto)

        transactionRunner {
            require(currentUserId() == userId) { "Owner changed before saving classification" }
            categoryDao.updateMovementClassification(
                movementId = classification.movementId.value,
                userId = userId,
                categoryId = classification.categoryId?.value,
                merchantId = classification.merchantId?.value,
                provisionalText = classification.merchantProvisionalText
            )
            require(currentUserId() == userId) { "Owner changed while saving classification" }

            categoryDao.insertOutboxCommand(
                CategorySyncOutboxEntity(
                    operationId = operationId,
                    userId = userId,
                    commandType = "UPDATE_MOVEMENT_CLASSIFICATION",
                    aggregateType = "MOVEMENT_CLASSIFICATION",
                    aggregateId = classification.movementId.value,
                    payloadJson = payloadJson,
                    payloadHash = payloadDto.payloadHash,
                    createdAt = now,
                    updatedAt = now
                )
            )
            require(currentUserId() == userId) { "Owner changed while saving classification command" }
        }

        if (currentUserId() == userId) syncScheduler.scheduleSync(userId)
        return Result.success(Unit)
    }

    override suspend fun clearCategoryClassification(movementId: MovementId): Result<Unit> {
        val userId = currentUserId() ?: return Result.failure(IllegalStateException("No active owner session"))
        categoryDao.clearCategoryClassification(movementId.value, userId)
        return Result.success(Unit)
    }

    override suspend fun clearMerchantClassification(movementId: MovementId): Result<Unit> {
        val userId = currentUserId() ?: return Result.failure(IllegalStateException("No active owner session"))
        categoryDao.clearMerchantClassification(movementId.value, userId)
        return Result.success(Unit)
    }

    override fun observeConflicts(userId: UserId): Flow<List<CategoryConflict>> {
        return categoryDao.observeOpenConflicts(userId.value).map { entities ->
            entities.map { entity ->
                CategoryConflict(
                    id = ConflictId(entity.id),
                    categoryId = CategoryId(entity.categoryId),
                    ownerId = UserId(entity.userId),
                    conflictType = CategoryConflictType.valueOf(entity.conflictType),
                    localVersion = entity.localVersion,
                    remoteVersion = entity.remoteVersion,
                    status = CategoryConflictStatus.valueOf(entity.status),
                    resolutionOperationId = entity.resolutionOperationId
                )
            }
        }
    }

    override suspend fun resolveConflict(conflictId: ConflictId, chosenVersion: String): Result<Unit> {
        val userId = currentUserId() ?: return Result.failure(IllegalStateException("No active owner session"))
        val now = System.currentTimeMillis()
        val operationId = UUID.randomUUID().toString()

        val conflict = categoryDao.getConflict(conflictId.value)
            ?: return Result.failure(IllegalArgumentException("Conflict not found"))
        if (conflict.userId != userId) {
            return Result.failure(IllegalStateException("Conflict does not belong to the active owner"))
        }

        val payloadDto = ResolveCategoryConflictRequestDto(
            operationId = operationId,
            conflictId = conflictId.value,
            chosenVersion = chosenVersion,
            payloadHash = sha256("$operationId:${conflictId.value}")
        )
        val payloadJson = json.encodeToString(payloadDto)

        transactionRunner {
            require(currentUserId() == userId) { "Owner changed before resolving conflict" }
            val currentConflict = categoryDao.getConflict(conflictId.value)
                ?: error("Conflict not found: ${conflictId.value}")
            require(currentUserId() == userId) { "Owner changed while loading conflict" }
            require(currentConflict.userId == userId) { "Conflict does not belong to the active owner" }
            categoryDao.updateConflict(
                currentConflict.copy(
                    status = "RESOLVED",
                    resolutionOperationId = operationId
                )
            )
            require(currentUserId() == userId) { "Owner changed while writing conflict" }

            categoryDao.insertOutboxCommand(
                CategorySyncOutboxEntity(
                    operationId = operationId,
                    userId = userId,
                    commandType = "RESOLVE_CONFLICT",
                    aggregateType = "CATEGORY_CONFLICT",
                    aggregateId = conflictId.value,
                    payloadJson = payloadJson,
                    payloadHash = payloadDto.payloadHash,
                    createdAt = now,
                    updatedAt = now
                )
            )
            require(currentUserId() == userId) { "Owner changed while writing conflict command" }
        }

        if (currentUserId() == userId) syncScheduler.scheduleSync(userId)
        return Result.success(Unit)
    }

    private suspend fun planLockedCategoryRootIds(userId: String): Set<String> {
        val categories = categoryDao.getCategoriesForUser(userId)
        val roots = categories.filter {
            it.userId == userId && it.parentId == null && it.origin == "CUSTOM" && it.isActive
        }
        val selected = quotaSelectionDao.getSelectedResourceIds(userId, QuotaGroup.CUSTOM_CATEGORIES.name)
        val premiumVerified = entitlementEvaluator.evaluate(userId, featureAccessCacheDao.get(UUID.fromString(userId))) is OfflineEntitlementLeaseDecision.Allowed
        return CategoryRules.evaluateCustomRootQuota(
            categories = roots.map { it.toDomainCategory() },
            selectedRootIds = selected.mapTo(mutableSetOf()) { CategoryId(it) },
            limitPerType = FreePlanLimits().customCategories,
            premiumVerified = premiumVerified,
        ).planLockedRootIds.mapTo(mutableSetOf()) { it.value }
    }

    private fun CategoryEntity.toDomainCategory() = Category(
        id = CategoryId(id),
        ownerId = userId?.let(::UserId),
        parentId = parentId?.let(::CategoryId),
        origin = CategoryOrigin.valueOf(origin),
        isActive = isActive,
        revision = remoteRevision,
        categoryType = CategoryType.fromStorage(categoryType),
    )

    private fun MerchantAliasRuleEntity.toDomain() = MerchantAliasRule(
        id = MerchantAliasRuleId(id),
        ownerId = UserId(userId),
        normalizedPattern = normalizedPattern,
        merchantId = MerchantId(merchantId),
        revision = remoteRevision,
        deletedAt = deletedAt?.let(java.time.Instant::ofEpochMilli),
        syncState = MerchantRuleSyncState.entries.firstOrNull { it.name == syncState } ?: MerchantRuleSyncState.FAILED,
    )

    private fun MerchantCategoryPreferenceEntity.toDomain() = MerchantCategoryPreference(
        ownerId = UserId(userId),
        merchantId = MerchantId(merchantId),
        categoryId = CategoryId(categoryId),
        revision = remoteRevision,
        deletedAt = deletedAt?.let(java.time.Instant::ofEpochMilli),
        id = id,
        syncState = MerchantRuleSyncState.entries.firstOrNull { it.name == syncState } ?: MerchantRuleSyncState.FAILED,
    )

    private suspend fun effectiveEntitlement(userId: String): EffectiveEntitlement? {
        val cache = featureAccessCacheDao.get(UUID.fromString(userId))
        if (entitlementEvaluator.evaluate(userId, cache) !is OfflineEntitlementLeaseDecision.Allowed) return null
        return EffectiveEntitlement(verified = true, expiresAtEpochMillis = cache?.entitlementExpiresAt?.toEpochMilli())
    }
}
