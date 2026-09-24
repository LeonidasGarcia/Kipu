package com.kipu.app.feature.categories.domain.usecase

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.model.Category
import com.kipu.app.feature.categories.domain.model.CategoryPresentation
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class CategoryItem(
    val category: Category,
    val presentation: CategoryPresentation?,
    val subcategories: List<CategoryItem> = emptyList(),
) {
    val displayName: String get() = presentation?.name ?: "Sin nombre"
    val icon: String get() = presentation?.icon ?: "category"
    val color: String get() = presentation?.color ?: "#757575"
}

class ObserveCategories @Inject constructor(
    private val repository: CategoriesRepository,
) {
    operator fun invoke(userId: UserId): Flow<List<CategoryItem>> {
        return combine(
            repository.observeCategories(userId),
            repository.observeCategoryPresentations(userId),
        ) { categories, presentations ->
            val presentationMap = presentations.associateBy { it.categoryId }

            val subcategoriesByParent = categories
                .filter { it.parentId != null }
                .groupBy { it.parentId!! }

            categories
                .filter { it.isRoot }
                .map { root ->
                    val subs = (subcategoriesByParent[root.id] ?: emptyList()).map { sub ->
                        CategoryItem(
                            category = sub,
                            presentation = presentationMap[sub.id],
                            subcategories = emptyList(),
                        )
                    }
                    CategoryItem(
                        category = root,
                        presentation = presentationMap[root.id],
                        subcategories = subs,
                    )
                }
        }
    }
}

class ObserveSelectedFreeCategoryRoots @Inject constructor(
    private val repository: CategoriesRepository,
) {
    operator fun invoke(userId: UserId): Flow<Set<com.kipu.app.feature.categories.domain.model.CategoryId>> =
        repository.observeSelectedFreeCategoryRoots(userId)
}

class SaveSelectedFreeCategoryRoots @Inject constructor(
    private val repository: CategoriesRepository,
) {
    suspend operator fun invoke(
        userId: UserId,
        categoryIds: Set<com.kipu.app.feature.categories.domain.model.CategoryId>,
    ): Result<Unit> = repository.saveSelectedFreeCategoryRoots(userId, categoryIds)
}
