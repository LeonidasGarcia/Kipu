package com.kipu.app.feature.categories.domain.usecase

import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.model.CategoryId
import javax.inject.Inject

class SetCategoryActive @Inject constructor(
    private val repository: CategoriesRepository,
) {
    suspend operator fun invoke(categoryId: CategoryId, isActive: Boolean): Result<Unit> {
        return repository.setCategoryActive(categoryId, isActive)
    }
}
