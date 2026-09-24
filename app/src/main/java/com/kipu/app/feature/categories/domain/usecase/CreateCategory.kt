package com.kipu.app.feature.categories.domain.usecase

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.model.Category
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.CategoryOrigin
import com.kipu.app.feature.categories.domain.model.CategoryPresentation
import com.kipu.app.feature.categories.domain.model.CategoryType
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
        categoryType: CategoryType? = null,
    ): Result<Category> {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Category name cannot be blank"))
        }

        val resolvedType = if (parentId == null) {
            categoryType ?: CategoryType.GENERAL
        } else {
            val parent = repository.getCategory(parentId)
                ?: return Result.failure(IllegalArgumentException("Parent category does not exist: $parentId"))
            if (parent.isSubcategory) {
                return Result.failure(IllegalArgumentException("Cannot create a third level. Parent $parentId is already a subcategory."))
            }
            if (categoryType != null && categoryType != parent.categoryType) {
                return Result.failure(IllegalArgumentException("Subcategory type must match its root category"))
            }
            parent.categoryType
        }

        val categoryId = CategoryId.generate()
        val category = Category(
            id = categoryId,
            ownerId = ownerId,
            parentId = parentId,
            origin = CategoryOrigin.CUSTOM,
            isActive = true,
            categoryType = resolvedType,
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
