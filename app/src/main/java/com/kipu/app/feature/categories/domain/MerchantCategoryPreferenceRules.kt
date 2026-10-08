package com.kipu.app.feature.categories.domain

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.model.Category
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.categories.domain.model.MerchantCategoryPreference
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.movements.domain.model.MovementType

sealed interface MerchantPreferenceResolution {
    data class Preferred(val categoryId: CategoryId) : MerchantPreferenceResolution
    data class Suggested(val categoryId: CategoryId) : MerchantPreferenceResolution
    data object NeedsNewChoice : MerchantPreferenceResolution
    data object Unclassified : MerchantPreferenceResolution
}

object MerchantCategoryPreferenceRules {
    fun resolve(
        ownerId: UserId,
        merchantId: MerchantId,
        preferences: Iterable<MerchantCategoryPreference>,
        categories: Iterable<Category>,
        movementType: MovementType,
        generalSuggestion: CategoryId?,
    ): MerchantPreferenceResolution {
        if (movementType == MovementType.TRANSFER) return MerchantPreferenceResolution.Unclassified

        val categoriesById = categories.associateBy(Category::id)
        val preference = preferences.singleOrNull {
            it.ownerId == ownerId && it.merchantId == merchantId && it.deletedAt == null
        }
        if (preference != null) {
            val selected = categoriesById[preference.categoryId]
                ?: return MerchantPreferenceResolution.NeedsNewChoice
            return if (isEligible(ownerId, selected, categoriesById, movementType)) {
                MerchantPreferenceResolution.Preferred(selected.id)
            } else {
                MerchantPreferenceResolution.NeedsNewChoice
            }
        }

        val suggestion = generalSuggestion?.let(categoriesById::get)
            ?: return MerchantPreferenceResolution.Unclassified
        return if (isEligible(ownerId, suggestion, categoriesById, movementType)) {
            MerchantPreferenceResolution.Suggested(suggestion.id)
        } else {
            MerchantPreferenceResolution.Unclassified
        }
    }

    private fun isEligible(
        ownerId: UserId,
        category: Category,
        categories: Map<CategoryId, Category>,
        movementType: MovementType,
    ): Boolean {
        if (category.ownerId != null && category.ownerId != ownerId) return false
        val parent = category.parentId?.let(categories::get)
        if (!CategoryRules.isEligibleForAssignment(category, parent)) return false
        if (category.isPlanLocked || parent?.isPlanLocked == true) return false
        val typeMatches = when (category.categoryType) {
            CategoryType.GENERAL -> true
            CategoryType.EXPENSE -> movementType == MovementType.EXPENSE
            CategoryType.INCOME -> movementType == MovementType.INCOME
        }
        return typeMatches
    }
}
