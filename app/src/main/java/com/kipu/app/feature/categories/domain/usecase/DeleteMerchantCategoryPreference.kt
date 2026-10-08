package com.kipu.app.feature.categories.domain.usecase

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.model.MerchantId
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class DeleteMerchantCategoryPreference @Inject constructor(
    private val repository: CategoriesRepository,
) {
    suspend operator fun invoke(ownerId: UserId, merchantId: MerchantId): Result<Unit> = runCatching {
        val preference = repository.observeMerchantCategoryPreferences(ownerId).first()
            .firstOrNull { it.ownerId == ownerId && it.merchantId == merchantId && it.deletedAt == null }
            ?: error("Merchant category preference is unavailable")
        repository.deleteMerchantCategoryPreference(merchantId, preference.revision).getOrThrow()
    }
}
