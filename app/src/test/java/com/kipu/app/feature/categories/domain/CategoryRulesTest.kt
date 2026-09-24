package com.kipu.app.feature.categories.domain

import com.kipu.app.core.finance.domain.model.MovementId
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.model.Category
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.CategoryOrigin
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.domain.model.MovementClassification
import com.kipu.app.feature.categories.domain.usecase.CategoryItem
import com.kipu.app.feature.categories.presentation.categories.CategoryTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryRulesTest {

    private val user1 = UserId.generate()

    @Test
    fun `valid root category passes hierarchy validation`() {
        val rootId = CategoryId.generate()
        val result = CategoryRules.validateHierarchy(rootId, null, emptyMap())
        assertTrue(result.isSuccess)
    }

    @Test
    fun `valid subcategory with root parent passes hierarchy validation`() {
        val rootId = CategoryId.generate()
        val subId = CategoryId.generate()
        val root = Category(rootId, user1, null, CategoryOrigin.CUSTOM, true)

        val result = CategoryRules.validateHierarchy(subId, rootId, mapOf(rootId to root))
        assertTrue(result.isSuccess)
    }

    @Test
    fun `subcategory type must match its root`() {
        val rootId = CategoryId.generate()
        val subId = CategoryId.generate()
        val root = Category(
            rootId, user1, null, CategoryOrigin.CUSTOM, true,
            categoryType = CategoryType.INCOME,
        )

        assertTrue(CategoryRules.validateCategoryType(CategoryType.INCOME, rootId, mapOf(rootId to root)).isSuccess)
        assertTrue(CategoryRules.validateCategoryType(CategoryType.EXPENSE, rootId, mapOf(rootId to root)).isFailure)
        assertTrue(CategoryRules.validateCategoryType(CategoryType.INCOME, subId, mapOf(rootId to root)).isFailure)
    }

    @Test
    fun `expense and income tabs include general categories but exclude the opposite type`() {
        val expense = Category(CategoryId.generate(), user1, null, CategoryOrigin.CUSTOM, true, categoryType = CategoryType.EXPENSE)
        val income = Category(CategoryId.generate(), user1, null, CategoryOrigin.CUSTOM, true, categoryType = CategoryType.INCOME)
        val general = Category(CategoryId.generate(), user1, null, CategoryOrigin.CUSTOM, true, categoryType = CategoryType.GENERAL)
        val items = listOf(expense, income, general).map { CategoryItem(it, null) }

        assertEquals(listOf(expense.id, general.id), CategoryTab.EXPENSE.filterCategories(items).map { it.category.id })
        assertEquals(listOf(income.id, general.id), CategoryTab.INCOME.filterCategories(items).map { it.category.id })
    }

    @Test
    fun `category cannot be its own parent`() {
        val catId = CategoryId.generate()
        val result = CategoryRules.validateHierarchy(catId, catId, emptyMap())
        assertTrue(result.isFailure)
    }

    @Test
    fun `third level hierarchy is rejected`() {
        val rootId = CategoryId.generate()
        val sub1Id = CategoryId.generate()
        val sub2Id = CategoryId.generate()

        val root = Category(rootId, user1, null, CategoryOrigin.CUSTOM, true)
        val sub1 = Category(sub1Id, user1, rootId, CategoryOrigin.CUSTOM, true)

        val categories = mapOf(rootId to root, sub1Id to sub1)
        val result = CategoryRules.validateHierarchy(sub2Id, sub1Id, categories)
        assertTrue(result.isFailure)
    }

    @Test
    fun `eligibility respects root and subcategory active states`() {
        val rootId = CategoryId.generate()
        val subId = CategoryId.generate()

        val activeRoot = Category(rootId, user1, null, CategoryOrigin.CUSTOM, true)
        val inactiveRoot = Category(rootId, user1, null, CategoryOrigin.CUSTOM, false)

        val activeSub = Category(subId, user1, rootId, CategoryOrigin.CUSTOM, true)
        val inactiveSub = Category(subId, user1, rootId, CategoryOrigin.CUSTOM, false)

        // Active root is eligible
        assertTrue(CategoryRules.isEligibleForAssignment(activeRoot, null))
        // Inactive root is not eligible
        assertFalse(CategoryRules.isEligibleForAssignment(inactiveRoot, null))

        // Active sub with active root is eligible
        assertTrue(CategoryRules.isEligibleForAssignment(activeSub, activeRoot))
        // Active sub with inactive root is NOT eligible
        assertFalse(CategoryRules.isEligibleForAssignment(activeSub, inactiveRoot))
        // Inactive sub with active root is NOT eligible
        assertFalse(CategoryRules.isEligibleForAssignment(inactiveSub, activeRoot))
    }

    @Test
    fun `free plan limits active custom roots to five`() {
        assertFalse(CategoryRules.canActivateCustomRoot(activeCustomRootCount = 5, isPremium = false))
        assertTrue(CategoryRules.canActivateCustomRoot(activeCustomRootCount = 4, isPremium = false))
        assertTrue(CategoryRules.canActivateCustomRoot(activeCustomRootCount = 5, isPremium = true))
    }

    @Test
    fun `countActiveCustomRoots ignores subcategories and system categories`() {
        val root1 = Category(CategoryId.generate(), user1, null, CategoryOrigin.CUSTOM, true)
        val root2 = Category(CategoryId.generate(), user1, null, CategoryOrigin.CUSTOM, true)
        val inactiveRoot = Category(CategoryId.generate(), user1, null, CategoryOrigin.CUSTOM, false)
        val sub1 = Category(CategoryId.generate(), user1, root1.id, CategoryOrigin.CUSTOM, true)
        val systemRoot = Category(CategoryId.generate(), null, null, CategoryOrigin.SYSTEM, true)

        val categories = listOf(root1, root2, inactiveRoot, sub1, systemRoot)
        val count = CategoryRules.countActiveCustomRoots(categories)
        assertEquals(2, count)
    }

    @Test
    fun `provisional text cannot coexist with catalog merchant in MovementClassification`() {
        val movementId = MovementId.generate()
        val merchantId = MerchantId.generate()

        // Valid with merchant only
        MovementClassification(movementId, null, merchantId, null)

        // Valid with provisional text only
        MovementClassification(movementId, null, null, "Bodega Don Pepe")

        // Invalid with both
        var caught = false
        try {
            MovementClassification(movementId, null, merchantId, "Bodega Don Pepe")
        } catch (e: IllegalArgumentException) {
            caught = true
        }
        assertTrue("Expected IllegalArgumentException when both merchantId and provisional text are provided", caught)
    }

    @Test
    fun `search normalization strips diacritics and handles case and whitespace`() {
        assertEquals("tambo", CategoryRules.normalizeText("  Támbô  "))
        assertEquals("starbucks coffee", CategoryRules.normalizeText("Stárbücks   Cöfféé"))

        assertTrue(CategoryRules.matchesNormalized("Tambo", "tam"))
        assertTrue(CategoryRules.matchesNormalized("Starbucks Coffee", "cöf"))
        assertFalse(CategoryRules.matchesNormalized("Tambo", "oxxo"))
        assertFalse(CategoryRules.matchesNormalized("Tambo", "   "))
    }
}
