package com.kipu.app.feature.categories.domain.usecase

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.model.Category
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.CategoryOrigin
import com.kipu.app.feature.categories.domain.model.CategoryPresentation
import javax.inject.Inject

class CreateCategory @Inject constructor(
    private val repository: CategoriesRepository,
) {
    suspend operator fun invoke(
        ownerId: UserId,
        name: String,
        icon: String,
        color: String,
        parentId: CategoryId? = null,
    ): Result<Category> {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Category name cannot be blank"))
        }

        val categoryId = CategoryId.generate()
        val category = Category(
            id = categoryId,
            ownerId = ownerId,
            parentId = parentId,
            origin = CategoryOrigin.CUSTOM,
            isActive = true,
        )
        val presentation = CategoryPresentation(
            categoryId = categoryId,
            ownerId = ownerId,
            name = trimmedName,
            icon = icon.ifBlank { "category" },
            color = color.ifBlank { "#757575" },
        )

        return repository.createCategory(category, presentation)
    }
}
