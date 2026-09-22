package com.kipu.app.feature.categories.domain.usecase

import com.kipu.app.core.finance.domain.model.MovementId
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.domain.model.MovementClassification
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class UpdateMovementClassification @Inject constructor(
    private val repository: CategoriesRepository,
) {
    operator fun invoke(movementId: MovementId): Flow<MovementClassification?> {
        return repository.observeMovementClassification(movementId)
    }

    suspend fun assignClassification(
        movementId: MovementId,
        categoryId: CategoryId?,
        merchantId: MerchantId?,
        provisionalText: String?,
    ): Result<Unit> {
        val sanitizedProvisional = provisionalText?.trim()?.takeIf { it.isNotBlank() }

        if (merchantId != null && sanitizedProvisional != null) {
            return Result.failure(IllegalArgumentException("Merchant ID and provisional text cannot coexist"))
        }

        val classification = MovementClassification(
            movementId = movementId,
            categoryId = categoryId,
            merchantId = merchantId,
            merchantProvisionalText = sanitizedProvisional,
        )

        return repository.updateMovementClassification(classification)
    }

    suspend fun clearCategory(movementId: MovementId): Result<Unit> {
        return repository.clearCategoryClassification(movementId)
    }

    suspend fun clearMerchant(movementId: MovementId): Result<Unit> {
        return repository.clearMerchantClassification(movementId)
    }
}
