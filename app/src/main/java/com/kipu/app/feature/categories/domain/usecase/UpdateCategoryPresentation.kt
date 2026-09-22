package com.kipu.app.feature.categories.domain.usecase

import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.model.CategoryPresentation
import javax.inject.Inject

class UpdateCategoryPresentation @Inject constructor(
    private val repository: CategoriesRepository,
) {
    suspend operator fun invoke(
        presentation: CategoryPresentation,
        expectedRevision: Long,
    ): Result<Unit> {
        val trimmed = presentation.name.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("El nombre de la categoría no puede estar vacío"))
        }
        val sanitized = presentation.copy(
            name = trimmed,
            icon = presentation.icon.ifBlank { "category" },
            color = presentation.color.ifBlank { "#757575" },
        )
        return repository.updateCategoryPresentation(sanitized, expectedRevision)
    }
}
