package com.kipu.app.feature.categories

import com.kipu.app.feature.categories.domain.model.Category
import com.kipu.app.feature.categories.domain.model.CategoryPresentation
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.MovementClassification
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScopeBoundaryTest {

    private val forbiddenTerms = listOf(
        "tag",
        "etiqueta",
        "alias",
        "originalText",
        "personalMerchant",
        "semantic",
    )

    @Test
    fun `movement classification has no tags, aliases or original text fields`() {
        val fields = MovementClassification::class.java.declaredFields.map { it.name.lowercase() }
        forbiddenTerms.forEach { term ->
            assertFalse(
                "MovementClassification must not contain forbidden scope term: $term",
                fields.any { it.contains(term.lowercase()) }
            )
        }
        // Assert exact allowed fields
        assertTrue(fields.contains("movementid"))
        assertTrue(fields.contains("categoryid"))
        assertTrue(fields.contains("merchantid"))
        assertTrue(fields.contains("merchantprovisionaltext"))
    }

    @Test
    fun `category model has no tags or aliases fields`() {
        val fields = Category::class.java.declaredFields.map { it.name.lowercase() }
        forbiddenTerms.forEach { term ->
            assertFalse(
                "Category must not contain forbidden scope term: $term",
                fields.any { it.contains(term.lowercase()) }
            )
        }
    }

    @Test
    fun `merchant catalog entry has no user ownership or personal preference fields`() {
        val fields = MerchantCatalogEntry::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(
            "MerchantCatalogEntry must be global and read-only, cannot have user_id",
            fields.contains("userid") || fields.contains("ownerid")
        )
        forbiddenTerms.forEach { term ->
            assertFalse(
                "MerchantCatalogEntry must not contain forbidden scope term: $term",
                fields.any { it.contains(term.lowercase()) }
            )
        }
    }

    @Test
    fun `category presentation has no tags or aliases fields`() {
        val fields = CategoryPresentation::class.java.declaredFields.map { it.name.lowercase() }
        forbiddenTerms.forEach { term ->
            assertFalse(
                "CategoryPresentation must not contain forbidden scope term: $term",
                fields.any { it.contains(term.lowercase()) }
            )
        }
    }
}
