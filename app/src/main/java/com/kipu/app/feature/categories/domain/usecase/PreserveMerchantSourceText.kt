package com.kipu.app.feature.categories.domain.usecase

import com.kipu.app.core.finance.domain.model.MovementId
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.model.SourceMerchantText
import javax.inject.Inject

class PreserveMerchantSourceText @Inject constructor(
    private val repository: CategoriesRepository,
) {
    suspend operator fun invoke(movementId: MovementId, rawText: String) =
        repository.preserveMerchantSourceText(movementId, SourceMerchantText(rawText))
}
