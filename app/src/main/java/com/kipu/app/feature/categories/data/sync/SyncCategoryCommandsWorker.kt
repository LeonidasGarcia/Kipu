package com.kipu.app.feature.categories.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kipu.app.core.logging.SecureLog
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.categories.data.local.CategoryConflictEntity
import com.kipu.app.feature.categories.data.local.CategoryDao
import com.kipu.app.feature.categories.data.local.CategoryEntity
import com.kipu.app.feature.categories.data.local.CategoryPresentationEntity
import com.kipu.app.feature.categories.data.local.MerchantCatalogDao
import com.kipu.app.feature.categories.data.local.MerchantCatalogEntity
import com.kipu.app.feature.categories.data.remote.CategoriesApi
import com.kipu.app.feature.categories.data.remote.CategoryApiResponse
import com.kipu.app.feature.categories.data.remote.CreateCategoryRequestDto
import com.kipu.app.feature.categories.data.remote.ResolveCategoryConflictRequestDto
import com.kipu.app.feature.categories.data.remote.SetCategoryActiveRequestDto
import com.kipu.app.feature.categories.data.remote.UpdateCategoryPresentationRequestDto
import com.kipu.app.feature.categories.data.remote.UpdateMovementClassificationRequestDto
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json


@HiltWorker
class SyncCategoryCommandsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val categoryDao: CategoryDao,
    private val merchantDao: MerchantCatalogDao,
    private val api: CategoriesApi,
    private val sessionCoordinator: SessionCoordinator,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_USER_ID = "key_user_id"
        private const val MAX_RETRIES = 5
    }

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        val userId = inputData.getString(KEY_USER_ID) ?: return Result.failure()
        val access = sessionCoordinator.localAccess.value as? LocalAccess.Available
        if (access?.userId != userId) {
            SecureLog.w("SyncCategoryCommandsWorker", "Skipping category sync for an inactive owner")
            return Result.success()
        }

        // Fill missing server categories without overwriting pending local edits.
        hydrateCategories(userId)

        // 1. Hydrate / refresh merchant catalog
        hydrateMerchantCatalog()

        // 2. Process pending outbox commands
        val pendingCommands = categoryDao.getPendingOutboxCommands(userId)
        for (cmd in pendingCommands) {
            val success = processCommand(cmd)
            if (!success) {
                if (cmd.attemptCount >= MAX_RETRIES) {
                    categoryDao.updateOutboxCommand(cmd.copy(state = "FAILED"))
                } else {
                    categoryDao.updateOutboxCommand(
                        cmd.copy(
                            attemptCount = cmd.attemptCount + 1,
                            state = "PENDING"
                        )
                    )
                }
            } else {
                categoryDao.updateOutboxCommand(cmd.copy(state = "COMPLETED"))
            }
        }

        return Result.success()
    }

    private suspend fun hydrateCategories(userId: String) {
        try {
            val pendingCommands = categoryDao.getPendingOutboxCommands(userId)
            val pendingCategoryIds = pendingCommands.map { it.aggregateId }.toSet()

            val categories = when (val response = api.fetchCategories()) {
                is CategoryApiResponse.Success -> {
                    val visible = response.data.filter { it.userId == null || it.userId == userId }
                    for (dto in visible) {
                        val existing = categoryDao.getCategoryById(dto.id)
                        val entity = CategoryEntity(
                            id = dto.id,
                            userId = dto.userId,
                            parentId = dto.parentId,
                            origin = dto.origin,
                            categoryType = dto.categoryType,
                            isActive = dto.isActive,
                            remoteRevision = dto.remoteRevision,
                            createdAt = Instant.parse(dto.createdAt).toEpochMilli(),
                            updatedAt = Instant.parse(dto.updatedAt).toEpochMilli(),
                        )
                        if (existing == null) {
                            categoryDao.insertCategory(entity)
                        } else if (dto.id !in pendingCategoryIds && dto.remoteRevision >= existing.remoteRevision) {
                            categoryDao.updateCategory(entity)
                        }
                    }
                    visible
                }
                else -> {
                    SecureLog.w("SyncCategoryCommandsWorker", "Failed to load categories")
                    emptyList()
                }
            }

            when (val response = api.fetchCategoryPresentations()) {
                is CategoryApiResponse.Success -> {
                    val userPresentations = response.data.filter { it.userId == userId }
                    for (dto in userPresentations) {
                        val existing = categoryDao.getPresentation(userId, dto.categoryId)
                        val entity = CategoryPresentationEntity(
                            categoryId = dto.categoryId,
                            userId = dto.userId,
                            name = dto.name,
                            icon = dto.icon,
                            color = dto.color,
                            remoteRevision = dto.remoteRevision,
                            updatedAt = Instant.parse(dto.updatedAt).toEpochMilli(),
                        )
                        if (existing == null) {
                            categoryDao.insertPresentation(entity)
                        } else if (dto.categoryId !in pendingCategoryIds && dto.remoteRevision >= existing.remoteRevision) {
                            categoryDao.insertPresentation(entity)
                        }
                    }
                }
                else -> SecureLog.w("SyncCategoryCommandsWorker", "Failed to load category presentations")
            }

            // System categories have a server name but no user presentation until customized.
            categoryDao.insertPresentationsIfAbsent(categories.filter { it.origin == "SYSTEM" }.map { dto ->
                CategoryPresentationEntity(
                    categoryId = dto.id, userId = userId, name = dto.name,
                    icon = dto.iconKey ?: "category", color = "#757575",
                    remoteRevision = dto.remoteRevision,
                    updatedAt = Instant.parse(dto.updatedAt).toEpochMilli(),
                )
            })
        } catch (e: Exception) {
            SecureLog.e("SyncCategoryCommandsWorker", "Error loading categories", e)
        }
    }

    private suspend fun hydrateMerchantCatalog() {
        try {
            val latestVersion = merchantDao.getLatestVersion() ?: 0L
            when (val response = api.fetchMerchantCatalog(sinceVersion = latestVersion)) {
                is CategoryApiResponse.Success -> {
                    val now = System.currentTimeMillis()
                    val entities = response.data.map { dto ->
                        MerchantCatalogEntity(
                            id = dto.id,
                            name = dto.name,
                            normalizedName = com.kipu.app.feature.categories.domain.CategoryRules.normalizeText(dto.normalizedName),
                            isActive = dto.isActive,
                            version = dto.version,
                            lastSyncedAt = now,
                            defaultCategoryId = dto.defaultCategoryId,
                            priority = dto.priority,
                            logoKey = dto.logoKey,
                            brandColor = dto.brandColor,
                        )
                    }
                    if (entities.isNotEmpty()) {
                        merchantDao.insertMerchants(entities)
                    }
                }
                else -> {
                    SecureLog.w("SyncCategoryCommandsWorker", "Failed to refresh merchant catalog")
                }
            }
        } catch (e: Exception) {
            SecureLog.e("SyncCategoryCommandsWorker", "Error hydrating merchant catalog", e)
        }
    }

    private suspend fun processCommand(cmd: com.kipu.app.feature.categories.data.local.CategorySyncOutboxEntity): Boolean {
        return try {
            when (cmd.commandType) {
                "CREATE_CATEGORY" -> {
                    val dto = json.decodeFromString<CreateCategoryRequestDto>(cmd.payloadJson)
                    when (val res = api.createCategory(dto)) {
                        is CategoryApiResponse.Success -> res.data.success
                        else -> false
                    }
                }
                "UPDATE_PRESENTATION" -> {
                    val dto = json.decodeFromString<UpdateCategoryPresentationRequestDto>(cmd.payloadJson)
                    when (val res = api.updatePresentation(dto)) {
                        is CategoryApiResponse.Success -> {
                            if (res.data.status == "CONFLICT") {
                                // Conflict record was generated remotely; ensure local conflict state
                                categoryDao.insertConflict(
                                    CategoryConflictEntity(
                                        id = java.util.UUID.randomUUID().toString(),
                                        categoryId = dto.categoryId,
                                        userId = cmd.userId,
                                        conflictType = "PRESENTATION",
                                        localVersion = cmd.payloadJson,
                                        remoteVersion = "CONFLICT_DETECTED",
                                        status = "OPEN",
                                        createdAt = System.currentTimeMillis()
                                    )
                                )
                            }
                            res.data.success
                        }
                        else -> false
                    }
                }
                "SET_CATEGORY_ACTIVE" -> {
                    val dto = json.decodeFromString<SetCategoryActiveRequestDto>(cmd.payloadJson)
                    when (val res = api.setCategoryActive(dto)) {
                        is CategoryApiResponse.Success -> res.data.success
                        else -> false
                    }
                }
                "UPDATE_MOVEMENT_CLASSIFICATION" -> {
                    val dto = json.decodeFromString<UpdateMovementClassificationRequestDto>(cmd.payloadJson)
                    when (val res = api.updateMovementClassification(dto)) {
                        is CategoryApiResponse.Success -> res.data.success
                        else -> false
                    }
                }
                "RESOLVE_CONFLICT" -> {
                    val dto = json.decodeFromString<ResolveCategoryConflictRequestDto>(cmd.payloadJson)
                    when (val res = api.resolveConflict(dto)) {
                        is CategoryApiResponse.Success -> res.data.success
                        else -> false
                    }
                }
                else -> true
            }
        } catch (e: Exception) {
            SecureLog.e("SyncCategoryCommandsWorker", "Error processing command ${cmd.commandType}", e)
            false
        }
    }
}
