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
import com.kipu.app.feature.categories.data.local.MerchantCatalogDao
import com.kipu.app.feature.categories.data.remote.CreateCategoryRequestDto
import com.kipu.app.feature.categories.data.remote.ResolveCategoryConflictRequestDto
import com.kipu.app.feature.categories.data.remote.SetCategoryActiveRequestDto
import com.kipu.app.feature.categories.data.remote.UpdateCategoryPresentationRequestDto
import com.kipu.app.feature.categories.data.remote.UpdateMovementClassificationRequestDto
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
import com.kipu.app.feature.categories.domain.model.ConflictId
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.domain.model.MovementClassification
import com.kipu.app.feature.plans.domain.FeatureAccessPolicy
import com.kipu.app.feature.plans.domain.model.Capability
import com.kipu.app.feature.plans.domain.model.FeatureAccessDecision
import com.kipu.app.feature.plans.domain.model.FeatureAccessRequest
import com.kipu.app.feature.plans.domain.model.FreePlanLimits
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class OfflineFirstCategoriesRepository @Inject constructor(
    private val transactionRunner: DatabaseTransactionRunner,
    private val categoryDao: CategoryDao,
    private val merchantDao: MerchantCatalogDao,
    private val sessionCoordinator: SessionCoordinator,
    private val syncScheduler: CategorySyncScheduler,
    private val featureAccessPolicy: FeatureAccessPolicy = FeatureAccessPolicy(),
) : CategoriesRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private fun sha256(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(content.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun currentUserId(): String? {
        return (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId
    }

    override fun observeCategories(userId: UserId): Flow<List<Category>> {
        return categoryDao.observeCategoriesForUser(userId.value).map { entities ->
            entities.map { entity ->
                Category(
                    id = CategoryId(entity.id),
                    ownerId = entity.userId?.let { UserId(it) },
                    parentId = entity.parentId?.let { CategoryId(it) },
                    origin = CategoryOrigin.valueOf(entity.origin),
                    isActive = entity.isActive,
                    revision = entity.remoteRevision,
                )
            }
        }
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
            )
        }
    }

    override suspend fun createCategory(
        category: Category,
        presentation: CategoryPresentation
    ): Result<Category> {
        val userId = currentUserId() ?: return Result.failure(IllegalStateException("No active owner session"))
        val now = System.currentTimeMillis()
        val operationId = UUID.randomUUID().toString()

        // Validate hierarchy
        val existing = categoryDao.getCategoriesForUser(userId).associate {
            CategoryId(it.id) to Category(
                id = CategoryId(it.id),
                ownerId = it.userId?.let { uid -> UserId(uid) },
                parentId = it.parentId?.let { pid -> CategoryId(pid) },
                origin = CategoryOrigin.valueOf(it.origin),
                isActive = it.isActive,
                revision = it.remoteRevision
            )
        }
        val hierarchyCheck = CategoryRules.validateHierarchy(category.id, category.parentId, existing)
        if (hierarchyCheck.isFailure) {
            return Result.failure(hierarchyCheck.exceptionOrNull()!!)
        }

        // Validate Free quota if custom root
        if (category.isRoot && category.isCustom) {
            val activeCount = categoryDao.countActiveCustomRoots(userId)
            val decision = featureAccessPolicy.evaluate(
                FeatureAccessRequest(
                    capability = Capability.CustomCategories,
                    currentUsage = activeCount,
                    freeLimits = FreePlanLimits(),
                    effectiveEntitlement = null,
                )
            )
            if (decision is FeatureAccessDecision.Denied) {
                return Result.failure(IllegalStateException("Free plan limit reached: maximum 5 active custom roots"))
            }
        }

        val payloadDto = CreateCategoryRequestDto(
            operationId = operationId,
            categoryId = category.id.value,
            parentId = category.parentId?.value,
            name = presentation.name,
            icon = presentation.icon,
            color = presentation.color,
            payloadHash = sha256("$operationId:${category.id.value}:${presentation.name}")
        )
        val payloadJson = json.encodeToString(payloadDto)

        transactionRunner {
            categoryDao.insertCategory(
                CategoryEntity(
                    id = category.id.value,
                    userId = userId,
                    parentId = category.parentId?.value,
                    origin = category.origin.name,
                    isActive = category.isActive,
                    remoteRevision = 1L,
                    createdAt = now,
                    updatedAt = now
                )
            )

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
        }

        syncScheduler.scheduleSync(userId)
        return Result.success(category)
    }

    override suspend fun setCategoryActive(categoryId: CategoryId, isActive: Boolean): Result<Unit> {
        val userId = currentUserId() ?: return Result.failure(IllegalStateException("No active owner session"))
        val now = System.currentTimeMillis()
        val operationId = UUID.randomUUID().toString()

        val existing = categoryDao.getCategoryById(categoryId.value)
            ?: return Result.failure(IllegalArgumentException("Category not found: ${categoryId.value}"))

        if (existing.parentId == null && existing.origin == "CUSTOM" && isActive && !existing.isActive) {
            val activeCount = categoryDao.countActiveCustomRoots(userId)
            val decision = featureAccessPolicy.evaluate(
                FeatureAccessRequest(
                    capability = Capability.CustomCategories,
                    currentUsage = activeCount,
                    freeLimits = FreePlanLimits(),
                    effectiveEntitlement = null,
                )
            )
            if (decision is FeatureAccessDecision.Denied) {
                return Result.failure(IllegalStateException("Free plan limit reached: maximum 5 active custom roots"))
            }
        }

        val payloadDto = SetCategoryActiveRequestDto(
            operationId = operationId,
            categoryId = categoryId.value,
            isActive = isActive,
            expectedRevision = existing.remoteRevision,
            payloadHash = sha256("$operationId:${categoryId.value}:$isActive")
        )
        val payloadJson = json.encodeToString(payloadDto)

        transactionRunner {
            categoryDao.updateCategory(
                existing.copy(
                    isActive = isActive,
                    updatedAt = now
                )
            )

            categoryDao.insertOutboxCommand(
                CategorySyncOutboxEntity(
                    operationId = operationId,
                    userId = userId,
                    commandType = "SET_CATEGORY_ACTIVE",
                    aggregateType = "CATEGORY",
                    aggregateId = categoryId.value,
                    expectedRevision = existing.remoteRevision,
                    payloadJson = payloadJson,
                    payloadHash = payloadDto.payloadHash,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        syncScheduler.scheduleSync(userId)
        return Result.success(Unit)
    }

    override suspend fun updateCategoryPresentation(
        presentation: CategoryPresentation,
        expectedRevision: Long
    ): Result<Unit> {
        val userId = currentUserId() ?: return Result.failure(IllegalStateException("No active owner session"))
        val now = System.currentTimeMillis()
        val operationId = UUID.randomUUID().toString()

        val existing = categoryDao.getPresentation(userId, presentation.categoryId.value)

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

        transactionRunner {
            if (existing != null && existing.remoteRevision != expectedRevision) {
                // Conflict: save conflict record
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
        }

        syncScheduler.scheduleSync(userId)
        return Result.success(Unit)
    }

    override fun searchMerchants(query: String): Flow<List<MerchantCatalogEntry>> {
        val normalized = CategoryRules.normalizeText(query)
        return merchantDao.searchMerchants(normalized).map { entities ->
            entities.map {
                MerchantCatalogEntry(
                    id = MerchantId(it.id),
                    name = it.name,
                    normalizedName = it.normalizedName,
                    isActive = it.isActive
                )
            }
        }
    }

    override fun observeMovementClassification(movementId: MovementId): Flow<MovementClassification?> {
        val userId = currentUserId() ?: return kotlinx.coroutines.flow.flowOf(null)
        return categoryDao.observeMovementClassification(movementId.value, userId).map { tuple ->
            tuple?.let {
                MovementClassification(
                    movementId = MovementId(it.id),
                    categoryId = it.category_id?.let { cid -> CategoryId(cid) },
                    merchantId = it.merchant_id?.let { mid -> MerchantId(mid) },
                    merchantProvisionalText = it.merchant_provisional_text
                )
            }
        }
    }

    override suspend fun updateMovementClassification(classification: MovementClassification): Result<Unit> {
        val userId = currentUserId() ?: return Result.failure(IllegalStateException("No active owner session"))
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
            categoryDao.updateMovementClassification(
                movementId = classification.movementId.value,
                userId = userId,
                categoryId = classification.categoryId?.value,
                merchantId = classification.merchantId?.value,
                provisionalText = classification.merchantProvisionalText
            )

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
        }

        syncScheduler.scheduleSync(userId)
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

        val payloadDto = ResolveCategoryConflictRequestDto(
            operationId = operationId,
            conflictId = conflictId.value,
            chosenVersion = chosenVersion,
            payloadHash = sha256("$operationId:${conflictId.value}")
        )
        val payloadJson = json.encodeToString(payloadDto)

        transactionRunner {
            categoryDao.updateConflict(
                conflict.copy(
                    status = "RESOLVED",
                    resolutionOperationId = operationId
                )
            )

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
        }

        syncScheduler.scheduleSync(userId)
        return Result.success(Unit)
    }
}
