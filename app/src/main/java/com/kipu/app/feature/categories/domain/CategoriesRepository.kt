package com.kipu.app.feature.categories.domain

import com.kipu.app.core.finance.domain.model.MovementId
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.model.Category
import com.kipu.app.feature.categories.domain.model.CategoryConflict
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.CategoryPresentation
import com.kipu.app.feature.categories.domain.model.ConflictId
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.MerchantCategoryFilter
import com.kipu.app.feature.categories.domain.model.MovementClassification
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow

interface CategoriesRepository {
    suspend fun ensureInitialCatalog(userId: UserId): Result<Unit> =
        Result.failure(UnsupportedOperationException("Initial category catalog is unavailable"))
    fun observeCategories(userId: UserId): Flow<List<Category>>
    fun observeCategoryPresentations(userId: UserId): Flow<List<CategoryPresentation>>
    suspend fun getCategory(categoryId: CategoryId): Category?
    suspend fun createCategory(category: Category, presentation: CategoryPresentation): Result<Category>
    suspend fun setCategoryActive(categoryId: CategoryId, isActive: Boolean): Result<Unit>
    suspend fun updateCategoryPresentation(presentation: CategoryPresentation, expectedRevision: Long): Result<Unit>
    fun observeSelectedFreeCategoryRoots(userId: UserId): Flow<Set<CategoryId>> = flowOf(emptySet())
    suspend fun saveSelectedFreeCategoryRoots(userId: UserId, categoryIds: Set<CategoryId>): Result<Unit> =
        Result.failure(UnsupportedOperationException("Free category selection is unavailable"))

    fun searchMerchants(query: String): Flow<List<MerchantCatalogEntry>>
    fun observeMerchantCatalog(): Flow<List<MerchantCatalogEntry>> = flowOf(emptyList())
    fun observeMerchantCategoryFilters(): Flow<List<MerchantCategoryFilter>> = flowOf(emptyList())
    fun observeMovementClassification(movementId: MovementId): Flow<MovementClassification?>
    suspend fun updateMovementClassification(classification: MovementClassification): Result<Unit>
    suspend fun clearCategoryClassification(movementId: MovementId): Result<Unit>
    suspend fun clearMerchantClassification(movementId: MovementId): Result<Unit>

    fun observeConflicts(userId: UserId): Flow<List<CategoryConflict>>
    suspend fun resolveConflict(conflictId: ConflictId, chosenVersion: String): Result<Unit>
}
