package com.kipu.app.feature.categories

import com.kipu.app.feature.categories.domain.model.Category
import com.kipu.app.feature.categories.domain.model.CategoryPresentation
import com.kipu.app.feature.categories.domain.model.MerchantAliasRule
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.MerchantCategoryPreference
import com.kipu.app.feature.categories.domain.model.MovementClassification
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScopeBoundaryTest {
    private val forbiddenTagTerms = listOf("tag", "etiqueta", "semantic")

    @Test
    fun `movement classification preserves source text without storing rules or preferences`() {
        val fields = MovementClassification::class.java.declaredFields.map { it.name.lowercase() }
        forbiddenTagTerms.forEach { term ->
            assertFalse("MovementClassification must not contain $term", fields.any { it.contains(term) })
        }
        assertTrue(fields.containsAll(listOf("movementid", "categoryid", "merchantid", "merchantprovisionaltext", "merchantrawtext")))
        assertFalse(fields.any { it.contains("alias") || it.contains("preference") })
    }

    @Test
    fun `category model remains free of tags and merchant rules`() {
        val fields = Category::class.java.declaredFields.map { it.name.lowercase() }
        forbiddenTagTerms.forEach { term ->
            assertFalse("Category must not contain $term", fields.any { it.contains(term) })
        }
        assertFalse(fields.any { it.contains("alias") || it.contains("preference") })
    }

    @Test
    fun `merchant aliases and category preferences are separate owner scoped models`() {
        val aliasFields = MerchantAliasRule::class.java.declaredFields.map { it.name.lowercase() }
        val preferenceFields = MerchantCategoryPreference::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue(aliasFields.containsAll(listOf("ownerid", "normalizedpattern", "merchantid", "revision", "deletedat")))
        assertTrue(preferenceFields.containsAll(listOf("ownerid", "merchantid", "categoryid", "revision", "deletedat")))

        val catalogFields = MerchantCatalogEntry::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(catalogFields.any { it.contains("owner") || it.contains("alias") || it.contains("preference") })
    }

    @Test
    fun `category presentation has no tags aliases or preference fields`() {
        val fields = CategoryPresentation::class.java.declaredFields.map { it.name.lowercase() }
        forbiddenTagTerms.forEach { term ->
            assertFalse("CategoryPresentation must not contain $term", fields.any { it.contains(term) })
        }
        assertFalse(fields.any { it.contains("alias") || it.contains("preference") })
    }
}
