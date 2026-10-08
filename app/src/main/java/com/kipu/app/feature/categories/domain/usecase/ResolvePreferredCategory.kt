package com.kipu.app.feature.categories.domain.usecase

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.MerchantCategoryPreferenceRules
import com.kipu.app.feature.categories.domain.MerchantPreferenceResolution
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.movements.domain.model.MovementType
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class ResolvePreferredCategory @Inject constructor(
    private val repository: CategoriesRepository,
) {
    suspend operator fun invoke(
        ownerId: UserId,
        merchantId: MerchantId,
        movementType: MovementType,
        generalSuggestion: CategoryId?,
    ): MerchantPreferenceResolution = MerchantCategoryPreferenceRules.resolve(
        ownerId = ownerId,
        merchantId = merchantId,
        preferences = repository.observeMerchantCategoryPreferences(ownerId).first(),
        categories = repository.observeCategories(ownerId).first(),
        movementType = movementType,
        generalSuggestion = generalSuggestion,
    )
}
