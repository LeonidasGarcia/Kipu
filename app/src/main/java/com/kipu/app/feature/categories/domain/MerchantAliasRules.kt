package com.kipu.app.feature.categories.domain

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.model.MerchantAliasRule
import com.kipu.app.feature.categories.domain.model.MerchantId
import java.text.Normalizer
import java.util.Locale

sealed interface MerchantAliasEvaluation {
    data object NoMatch : MerchantAliasEvaluation
    data class Match(val merchantId: MerchantId) : MerchantAliasEvaluation
    data class NeedsReview(val merchantIds: Set<MerchantId>) : MerchantAliasEvaluation
}

object MerchantAliasRules {
    /** Fold case, accents and whitespace; punctuation remains part of the exact match key. */
    fun normalize(rawText: String): String {
        val collapsed = rawText.trim().replace(Regex("[\\s\\p{Z}]+"), " ")
        return Normalizer.normalize(collapsed, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
    }

    fun evaluate(
        ownerId: UserId,
        rawText: String,
        rules: Iterable<MerchantAliasRule>,
        activeMerchantIds: Set<MerchantId>,
    ): MerchantAliasEvaluation {
        val key = normalize(rawText)
        if (key.isEmpty()) return MerchantAliasEvaluation.NoMatch

        val matches = rules.asSequence()
            .filter { it.ownerId == ownerId && it.deletedAt == null }
            .filter { it.merchantId in activeMerchantIds && it.normalizedPattern == key }
            .map { it.merchantId }
            .toSet()

        return when (matches.size) {
            0 -> MerchantAliasEvaluation.NoMatch
            1 -> MerchantAliasEvaluation.Match(matches.single())
            else -> MerchantAliasEvaluation.NeedsReview(matches)
        }
    }
}
