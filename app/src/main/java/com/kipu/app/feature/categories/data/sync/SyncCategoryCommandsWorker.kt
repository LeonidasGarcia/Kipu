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
import com.kipu.app.feature.categories.data.local.MerchantRulesDao
import com.kipu.app.feature.categories.data.remote.CategoriesApi
import com.kipu.app.feature.categories.data.remote.CategoryApiResponse
import com.kipu.app.feature.categories.data.remote.CategoryCommandResponseDto
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
    private val merchantRulesDao: MerchantRulesDao,
    private val api: CategoriesApi,
    private val sessionCoordinator: SessionCoordinator,
) : CoroutineWorker(appContext, workerParams) {

    private sealed interface CommandOutcome {
        data object Applied : CommandOutcome
        data object Retry : CommandOutcome
        data class Conflict(val code: String) : CommandOutcome
    }

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
            when (val outcome = processCommand(cmd)) {
                CommandOutcome.Applied -> categoryDao.updateOutboxCommand(cmd.copy(state = "COMPLETED", errorCode = null))
                is CommandOutcome.Conflict -> categoryDao.updateOutboxCommand(
                    cmd.copy(state = "FAILED", errorCode = outcome.code),
                )
                CommandOutcome.Retry -> {
                    if (cmd.attemptCount >= MAX_RETRIES) {
                        markMerchantCommandFailed(cmd)
                        categoryDao.updateOutboxCommand(cmd.copy(state = "FAILED", errorCode = "RETRY_LIMIT"))
                    } else {
                        categoryDao.updateOutboxCommand(
                            cmd.copy(attemptCount = cmd.attemptCount + 1, state = "PENDING"),
                        )
                    }
                }
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

    private suspend fun processCommand(cmd: com.kipu.app.feature.categories.data.local.CategorySyncOutboxEntity): CommandOutcome {
        return try {
            when (cmd.commandType) {
                "CREATE_CATEGORY" -> {
                    val dto = json.decodeFromString<CreateCategoryRequestDto>(cmd.payloadJson)
                    api.createCategory(dto).asCommandOutcome()
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
                                CommandOutcome.Conflict("REVISION_CONFLICT")
                            } else if (!res.data.success) {
                                CommandOutcome.Retry
                            } else {
                                CommandOutcome.Applied
                            }
                        }
                        is CategoryApiResponse.Error -> if (res.statusCode == 409) CommandOutcome.Conflict("REVISION_CONFLICT") else CommandOutcome.Retry
                        is CategoryApiResponse.NetworkFailure -> CommandOutcome.Retry
                    }
                }
                "SET_CATEGORY_ACTIVE" -> {
                    val dto = json.decodeFromString<SetCategoryActiveRequestDto>(cmd.payloadJson)
                    api.setCategoryActive(dto).asCommandOutcome()
                }
                "UPDATE_MOVEMENT_CLASSIFICATION" -> {
                    val dto = json.decodeFromString<UpdateMovementClassificationRequestDto>(cmd.payloadJson)
                    api.updateMovementClassification(dto).asCommandOutcome()
                }
                "PRESERVE_MERCHANT_SOURCE_TEXT" -> {
                    val dto = json.decodeFromString<PreserveMerchantSourceTextRequestDto>(cmd.payloadJson)
                    api.preserveMerchantSourceText(dto).asCommandOutcome()
                }
                "UPSERT_MERCHANT_ALIAS_RULE" -> {
                    val dto = json.decodeFromString<UpsertMerchantAliasRuleRequestDto>(cmd.payloadJson)
                    val outcome = api.upsertMerchantAliasRule(dto).asCommandOutcome()
                    updateAliasSyncState(cmd.userId, dto.ruleId, dto.expectedRevision, outcome)
                    outcome
                }
                "DELETE_MERCHANT_ALIAS_RULE" -> {
                    val dto = json.decodeFromString<DeleteMerchantAliasRuleRequestDto>(cmd.payloadJson)
                    val outcome = api.deleteMerchantAliasRule(dto).asCommandOutcome()
                    updateAliasSyncState(cmd.userId, dto.ruleId, dto.expectedRevision, outcome)
                    outcome
                }
                "UPSERT_MERCHANT_CATEGORY_PREFERENCE" -> {
                    val dto = json.decodeFromString<UpsertMerchantCategoryPreferenceRequestDto>(cmd.payloadJson)
                    val outcome = api.upsertMerchantCategoryPreference(dto).asCommandOutcome()
                    updatePreferenceSyncState(cmd.userId, dto.merchantId, dto.expectedRevision, outcome)
                    outcome
                }
                "DELETE_MERCHANT_CATEGORY_PREFERENCE" -> {
                    val dto = json.decodeFromString<DeleteMerchantCategoryPreferenceRequestDto>(cmd.payloadJson)
                    val merchantId = merchantRulesDao.getPreference(cmd.userId, cmd.aggregateId)?.merchantId ?: cmd.aggregateId
                    val outcome = api.deleteMerchantCategoryPreference(dto).asCommandOutcome()
                    updatePreferenceSyncState(cmd.userId, merchantId, dto.expectedRevision, outcome)
                    outcome
                }
                "RESOLVE_CONFLICT" -> {
                    val dto = json.decodeFromString<ResolveCategoryConflictRequestDto>(cmd.payloadJson)
                    api.resolveConflict(dto).asCommandOutcome()
                }
                else -> CommandOutcome.Applied
            }
        } catch (e: Exception) {
            if (cmd.commandType in setOf("PRESERVE_MERCHANT_SOURCE_TEXT", "UPSERT_MERCHANT_ALIAS_RULE", "DELETE_MERCHANT_ALIAS_RULE", "UPSERT_MERCHANT_CATEGORY_PREFERENCE", "DELETE_MERCHANT_CATEGORY_PREFERENCE")) {
                SecureLog.e("SyncCategoryCommandsWorker", "Failed to sync a private merchant command")
            } else {
                SecureLog.e("SyncCategoryCommandsWorker", "Error processing command ${cmd.commandType}", e)
            }
            CommandOutcome.Retry
        }
    }

    private fun CategoryApiResponse<CategoryCommandResponseDto>.asCommandOutcome(): CommandOutcome = when (this) {
        is CategoryApiResponse.Success -> when {
            data.status == "CONFLICT" || data.status == "REJECTED" -> CommandOutcome.Conflict(data.code ?: "REVISION_CONFLICT")
            data.success -> CommandOutcome.Applied
            else -> CommandOutcome.Retry
        }
        is CategoryApiResponse.Error -> when {
            statusCode == 409 -> CommandOutcome.Conflict("REVISION_CONFLICT")
            statusCode in 400..499 && statusCode !in setOf(401, 408, 429) -> CommandOutcome.Conflict("COMMAND_REJECTED")
            else -> CommandOutcome.Retry
        }
        is CategoryApiResponse.NetworkFailure -> CommandOutcome.Retry
    }

    private suspend fun updateAliasSyncState(
        userId: String,
        ruleId: String,
        expectedRevision: Long?,
        outcome: CommandOutcome,
    ) {
        val state = when (outcome) {
            CommandOutcome.Applied -> "SYNCED"
            is CommandOutcome.Conflict -> "CONFLICT"
            CommandOutcome.Retry -> "PENDING"
        }
        merchantRulesDao.setAliasSyncState(
            userId = userId,
            ruleId = ruleId,
            state = state,
            revision = if (outcome == CommandOutcome.Applied) expectedRevision?.plus(1L) ?: 1L else null,
            error = (outcome as? CommandOutcome.Conflict)?.code,
        )
    }

    private suspend fun updatePreferenceSyncState(
        userId: String,
        merchantId: String,
        expectedRevision: Long?,
        outcome: CommandOutcome,
    ) {
        val state = when (outcome) {
            CommandOutcome.Applied -> "SYNCED"
            is CommandOutcome.Conflict -> "CONFLICT"
            CommandOutcome.Retry -> "PENDING"
        }
        merchantRulesDao.setPreferenceSyncState(
            userId = userId,
            merchantId = merchantId,
            state = state,
            revision = if (outcome == CommandOutcome.Applied) expectedRevision?.plus(1L) ?: 1L else null,
            error = (outcome as? CommandOutcome.Conflict)?.code,
        )
    }

    private suspend fun markMerchantCommandFailed(cmd: com.kipu.app.feature.categories.data.local.CategorySyncOutboxEntity) {
        when (cmd.commandType) {
            "UPSERT_MERCHANT_ALIAS_RULE", "DELETE_MERCHANT_ALIAS_RULE" ->
                merchantRulesDao.setAliasSyncState(cmd.userId, cmd.aggregateId, "FAILED", null, "RETRY_LIMIT")
            "UPSERT_MERCHANT_CATEGORY_PREFERENCE", "DELETE_MERCHANT_CATEGORY_PREFERENCE" ->
                merchantRulesDao.setPreferenceSyncState(cmd.userId, cmd.aggregateId, "FAILED", null, "RETRY_LIMIT")
        }
    }
}
