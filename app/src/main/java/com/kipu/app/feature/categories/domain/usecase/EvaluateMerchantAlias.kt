package com.kipu.app.feature.categories.domain.usecase

import com.kipu.app.feature.categories.domain.MerchantAliasEvaluation
import com.kipu.app.feature.categories.domain.MerchantAliasRules
import com.kipu.app.feature.categories.domain.model.CaptureCandidate
import com.kipu.app.feature.categories.domain.model.MerchantAliasRule
import com.kipu.app.feature.categories.domain.model.MerchantId
import javax.inject.Inject

class EvaluateMerchantAlias @Inject constructor() {
    operator fun invoke(
        candidate: CaptureCandidate,
        rules: Iterable<MerchantAliasRule>,
        activeMerchantIds: Set<MerchantId>,
    ): MerchantAliasEvaluation = MerchantAliasRules.evaluate(
        ownerId = candidate.ownerId,
        rawText = candidate.sourceText.value,
        rules = rules,
        activeMerchantIds = activeMerchantIds,
    )
}
