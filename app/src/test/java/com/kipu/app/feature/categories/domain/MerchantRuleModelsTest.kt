package com.kipu.app.feature.categories.domain

import com.kipu.app.core.finance.domain.model.MovementId
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.MerchantAliasRule
import com.kipu.app.feature.categories.domain.model.MerchantAliasRuleId
import com.kipu.app.feature.categories.domain.model.MerchantCategoryPreference
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.domain.model.MovementClassification
import com.kipu.app.feature.categories.domain.model.SourceMerchantText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class MerchantRuleModelsTest {
    private val ownerA = UserId.generate()
    private val ownerB = UserId.generate()
    private val merchant = MerchantId.generate()
    private val category = CategoryId.generate()

    @Test
    fun `source merchant text preserves the exact input independently of normalization`() {
        val raw = "  IZIPAY* TÁMBO\tLIMA  "
        val source = SourceMerchantText(raw)
        val classification = MovementClassification(
            movementId = MovementId.generate(),
            merchantRawText = source.value,
        )

        assertEquals(raw, source.value)
        assertEquals(raw, classification.merchantRawText)
    }

    @Test
    fun `alias identity survives revision and tombstone updates`() {
        val original = MerchantAliasRule(
            id = MerchantAliasRuleId("rule-01"),
            ownerId = ownerA,
            normalizedPattern = "izipay*tambo lima",
            merchantId = merchant,
        )
        val removed = original.copy(revision = 2, deletedAt = Instant.parse("2026-10-08T10:00:00Z"))

        assertEquals(original.id, removed.id)
        assertEquals(ownerA, removed.ownerId)
        assertEquals(2, removed.revision)
        assertNotNull(removed.deletedAt)
    }

    @Test
    fun `alias revisions must be positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            MerchantAliasRule(
                id = MerchantAliasRuleId("rule-02"),
                ownerId = ownerA,
                normalizedPattern = "tambo",
                merchantId = merchant,
                revision = 0,
            )
        }
    }

    @Test
    fun `merchant category preference is scoped to its owner`() {
        val preferenceA = MerchantCategoryPreference(ownerA, merchant, category)
        val preferenceB = MerchantCategoryPreference(ownerB, merchant, CategoryId.generate())
        val changedPreference = preferenceA.copy(revision = 2, deletedAt = Instant.parse("2026-10-08T10:00:00Z"))

        assertEquals(merchant, preferenceA.merchantId)
        assertEquals(ownerA, preferenceA.ownerId)
        assertEquals(preferenceA.id, changedPreference.id)
        assertNotEquals(preferenceA.categoryId, preferenceB.categoryId)
        assertNotEquals(preferenceA.ownerId, preferenceB.ownerId)
    }
}
