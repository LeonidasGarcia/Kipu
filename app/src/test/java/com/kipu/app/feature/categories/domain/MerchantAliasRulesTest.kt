package com.kipu.app.feature.categories.domain

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.model.MerchantAliasRule
import com.kipu.app.feature.categories.domain.model.MerchantAliasRuleId
import com.kipu.app.feature.categories.domain.model.MerchantId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantAliasRulesTest {
    private val ownerA = UserId.generate()
    private val ownerB = UserId.generate()
    private val tambo = MerchantId.generate()
    private val plin = MerchantId.generate()

    @Test
    fun `normalization folds case accents and whitespace while retaining punctuation`() {
        assertEquals("izipay* tambo", MerchantAliasRules.normalize("  Izipay* TÁMBO\t"))
    }

    @Test
    fun `alias match uses exact normalized equality and never prefix matching`() {
        val rules = listOf(rule(ownerA, "IZIPAY*TAMBO", tambo))

        assertEquals(
            MerchantAliasEvaluation.Match(tambo),
            MerchantAliasRules.evaluate(ownerA, " izipay*tambo ", rules, setOf(tambo)),
        )
        assertEquals(
            MerchantAliasEvaluation.NoMatch,
            MerchantAliasRules.evaluate(ownerA, "IZIPAY*TAMBO LIMA", rules, setOf(tambo)),
        )
    }

    @Test
    fun `matching rules for different merchants require human review`() {
        val rules = listOf(
            rule(ownerA, "IZIPAY*TAMBO", tambo),
            rule(ownerA, "izipay*tambo", plin),
        )

        assertEquals(
            MerchantAliasEvaluation.NeedsReview(setOf(tambo, plin)),
            MerchantAliasRules.evaluate(ownerA, "IZIPAY*TAMBO", rules, setOf(tambo, plin)),
        )
    }

    @Test
    fun `another owners alias cannot influence this owners result`() {
        val rules = listOf(rule(ownerB, "TAMBO", tambo))

        assertEquals(
            MerchantAliasEvaluation.NoMatch,
            MerchantAliasRules.evaluate(ownerA, "TAMBO", rules, setOf(tambo)),
        )
        assertTrue(MerchantAliasRules.evaluate(ownerB, "TAMBO", rules, setOf(tambo)) is MerchantAliasEvaluation.Match)
    }

    private fun rule(owner: UserId, pattern: String, merchant: MerchantId) = MerchantAliasRule(
        id = MerchantAliasRuleId.generate(),
        ownerId = owner,
        normalizedPattern = MerchantAliasRules.normalize(pattern),
        merchantId = merchant,
    )
}
