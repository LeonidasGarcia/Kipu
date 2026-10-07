package com.kipu.app.feature.categories.domain.usecase

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.CategoriesRepository
import javax.inject.Inject

class EnsureInitialCategoryCatalog @Inject constructor(
    private val repository: CategoriesRepository,
) {
    suspend operator fun invoke(userId: UserId): Result<Unit> = repository.ensureInitialCatalog(userId)
}
