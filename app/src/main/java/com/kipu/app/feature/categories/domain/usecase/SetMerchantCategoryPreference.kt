package com.kipu.app.feature.categories.domain.usecase

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.CategoryRules
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.MerchantCategoryPreference
import com.kipu.app.feature.categories.domain.model.MerchantId
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class SetMerchantCategoryPreference @Inject constructor(
    private val repository: CategoriesRepository,
) {
    suspend operator fun invoke(
        ownerId: UserId,
        merchantId: MerchantId,
        categoryId: CategoryId,
    ): Result<MerchantCategoryPreference> = runCatching {
        val categories = repository.observeCategories(ownerId).first()
        val byId = categories.associateBy { it.id }
        val selected = byId[categoryId] ?: error("Category is unavailable")
        require(selected.ownerId == null || selected.ownerId == ownerId) { "Category belongs to another owner" }
        val root = selected.parentId?.let(byId::get)
        require(CategoryRules.isEligibleForAssignment(selected, root)) { "Category or root is inactive" }
        require(!selected.isPlanLocked && root?.isPlanLocked != true) { "Category is blocked by the Free plan selection" }
        require(selected.isSystem || selected.ownerId == ownerId) { "Category is unavailable to this owner" }

        val previous = repository.observeMerchantCategoryPreferences(ownerId).first()
            .firstOrNull { it.ownerId == ownerId && it.merchantId == merchantId }
        val preference = MerchantCategoryPreference(
            ownerId = ownerId,
            merchantId = merchantId,
            categoryId = categoryId,
            revision = (previous?.revision ?: 0L) + 1L,
            id = previous?.id ?: java.util.UUID.randomUUID().toString(),
        )
        repository.saveMerchantCategoryPreference(preference, previous?.revision).getOrThrow()
        preference
    }
}
