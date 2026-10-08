package com.kipu.app.feature.categories.domain.usecase

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.MerchantAliasRules
import com.kipu.app.feature.categories.domain.model.MerchantAliasRule
import com.kipu.app.feature.categories.domain.model.MerchantAliasRuleId
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.domain.model.SourceMerchantText
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class SaveMerchantAliasRule @Inject constructor(
    private val repository: CategoriesRepository,
) {
    suspend operator fun invoke(
        ownerId: UserId,
        sourceText: SourceMerchantText,
        merchant: MerchantCatalogEntry,
        confirmedMerchantId: MerchantId,
        existingRule: MerchantAliasRule? = null,
    ): Result<MerchantAliasRule> = runCatching {
        require(merchant.isActive) { "Canonical merchant is not active" }
        require(merchant.id == confirmedMerchantId) { "Confirm the canonical merchant before saving the alias" }
        require(existingRule == null || existingRule.ownerId == ownerId) { "Alias rule belongs to another owner" }
        require(existingRule == null || existingRule.deletedAt == null) { "Deleted alias rules cannot be edited" }

        val normalized = MerchantAliasRules.normalize(sourceText.value)
        require(normalized.isNotEmpty()) { "Alias source text cannot be blank" }
        if (existingRule == null) {
            require(repository.observePremiumVerified(ownerId).first()) { "Premium is required to create a merchant alias" }
        }

        val saved = MerchantAliasRule(
            id = existingRule?.id ?: MerchantAliasRuleId.generate(),
            ownerId = ownerId,
            normalizedPattern = normalized,
            merchantId = merchant.id,
            revision = (existingRule?.revision ?: 0L) + 1L,
        )
        repository.saveMerchantAliasRule(saved, existingRule?.revision).getOrThrow()
        saved
    }
}
