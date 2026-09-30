package com.kipu.app.feature.categories.domain

import com.kipu.app.feature.categories.domain.model.Category
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.CategoryOrigin
import com.kipu.app.feature.categories.domain.model.CategoryType
import java.text.Normalizer
import java.util.Locale

object CategoryRules {

    const val MAX_FREE_ACTIVE_CUSTOM_ROOTS = 5

    /**
     * Validates that adding or editing a category does not violate hierarchy constraints:
     * 1. No self-parenting.
     * 2. Max 2 levels: parent must be a root (parent.parentId == null).
     * 3. No cycles.
     */
    fun validateHierarchy(
        categoryId: CategoryId,
        parentId: CategoryId?,
        existingCategories: Map<CategoryId, Category>
    ): Result<Unit> {
        if (parentId == null) {
            return Result.success(Unit) // Valid root
        }

        if (categoryId == parentId) {
            return Result.failure(IllegalArgumentException("Category cannot be its own parent: $categoryId"))
        }

        val parent = existingCategories[parentId]
            ?: return Result.failure(IllegalArgumentException("Parent category does not exist: $parentId"))

        if (parent.parentId != null) {
            return Result.failure(IllegalArgumentException("Cannot create a third level. Parent $parentId is already a subcategory."))
        }

        // Cycle check: verify that parent does not point to categoryId (transitively)
        var current: Category? = parent
        while (current != null) {
            if (current.id == categoryId) {
                return Result.failure(IllegalStateException("Cycle detected in category hierarchy"))
            }
            current = current.parentId?.let { existingCategories[it] }
        }

        return Result.success(Unit)
    }

    /** Subcategories must use the exact type of their root; GENERAL roots stay GENERAL. */
    fun validateCategoryType(
        categoryType: CategoryType,
        parentId: CategoryId?,
        existingCategories: Map<CategoryId, Category>,
    ): Result<Unit> {
        if (parentId == null) return Result.success(Unit)

        val parent = existingCategories[parentId]
            ?: return Result.failure(IllegalArgumentException("Parent category does not exist: $parentId"))
        if (parent.parentId != null) {
            return Result.failure(IllegalArgumentException("Cannot create a third level. Parent $parentId is already a subcategory."))
        }
        if (categoryType != parent.categoryType) {
            return Result.failure(IllegalArgumentException("Subcategory type must match its root category"))
        }
        return Result.success(Unit)
    }

    /**
     * Checks if a category is eligible for new movement assignments.
     * - A root must be active.
     * - A subcategory is eligible only if both it and its root parent are active.
     */
    fun isEligibleForAssignment(
        category: Category,
        parent: Category?
    ): Boolean {
        if (!category.isActive) return false
        if (category.isSubcategory) {
            return parent != null && parent.isActive && parent.id == category.parentId
        }
        return true
    }

    /**
     * Checks if a user under Free plan can activate or create a custom root category.
     * Free plan allows at most [MAX_FREE_ACTIVE_CUSTOM_ROOTS] active custom roots.
     */
    fun canActivateCustomRoot(
        activeCustomRootCount: Int,
        isPremium: Boolean
    ): Boolean {
        if (isPremium) return true
        return activeCustomRootCount < MAX_FREE_ACTIVE_CUSTOM_ROOTS
    }

    /**
     * Counts active custom roots from a list of categories.
     */
    fun countActiveCustomRoots(categories: List<Category>): Int {
        return categories.count { it.origin == CategoryOrigin.CUSTOM && it.isRoot && it.isActive }
    }

    /**
     * Normalizes search queries for merchant and category lookup:
     * - Converts to lowercase.
     * - Strips accents / diacritics (NFD decomposition).
     * - Removes punctuation so names such as McDonald's, Listo!, and iCloud+ match
     *   canonical normalized merchant names.
     * - Trims and collapses multiple whitespace.
     */
    fun normalizeText(text: String): String {
        val nfdNormalized = Normalizer.normalize(text, Normalizer.Form.NFD)
        val pattern = "\\p{InCombiningDiacriticalMarks}+".toRegex()
        val withoutDiacritics = pattern.replace(nfdNormalized, "")
        return "[^\\p{L}\\p{N}\\s]".toRegex().replace(withoutDiacritics, "")
            .lowercase(Locale.ROOT)
            .trim()
            .replace("\\s+".toRegex(), " ")
    }

    /**
     * Evaluates whether a query matches a merchant name based on normalized substring evidence.
     * Returns true only if the normalized query is a non-empty substring of the normalized name.
     */
    fun matchesNormalized(candidateName: String, query: String): Boolean {
        val normalizedCandidate = normalizeText(candidateName)
        val normalizedQuery = normalizeText(query)
        if (normalizedQuery.isBlank()) return false
        return normalizedCandidate.contains(normalizedQuery)
    }
}
