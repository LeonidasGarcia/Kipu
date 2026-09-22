package com.kipu.app.feature.categories.domain.usecase

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.model.CategoryConflict
import com.kipu.app.feature.categories.domain.model.ConflictId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ResolveCategoryConflict @Inject constructor(
    private val repository: CategoriesRepository,
) {
    fun observeConflicts(userId: UserId): Flow<List<CategoryConflict>> {
        return repository.observeConflicts(userId)
    }

    suspend fun resolve(conflictId: ConflictId, chosenVersion: String): Result<Unit> {
        require(chosenVersion.isNotBlank()) { "La versión elegida no puede estar vacía" }
        return repository.resolveConflict(conflictId, chosenVersion)
    }
}
