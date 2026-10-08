package com.kipu.app.feature.categories.domain.usecase

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.model.MerchantAliasRuleId
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class DeleteMerchantAliasRule @Inject constructor(
    private val repository: CategoriesRepository,
) {
    suspend operator fun invoke(ownerId: UserId, ruleId: MerchantAliasRuleId): Result<Unit> = runCatching {
        val rule = repository.observeMerchantAliasRules(ownerId).first().firstOrNull { it.id == ruleId }
            ?: error("Alias rule is unavailable")
        require(rule.ownerId == ownerId) { "Alias rule belongs to another owner" }
        require(rule.deletedAt == null) { "Alias rule is already deleted" }
        repository.deleteMerchantAliasRule(ruleId, rule.revision).getOrThrow()
    }
}
