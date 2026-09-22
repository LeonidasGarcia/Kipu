package com.kipu.app.feature.categories.domain.usecase

import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.CategoryRules
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class SearchMerchantCatalog @Inject constructor(
    private val repository: CategoriesRepository,
) {
    operator fun invoke(query: String): Flow<List<MerchantCatalogEntry>> {
        val normalized = CategoryRules.normalizeText(query)
        if (normalized.isBlank()) {
            return flowOf(emptyList())
        }
        return repository.searchMerchants(normalized)
    }
}
