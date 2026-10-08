package com.kipu.app.feature.categories.domain

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.model.Category
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.CategoryOrigin
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.categories.domain.model.MerchantCategoryPreference
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.movements.domain.model.MovementType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class MerchantCategoryPreferenceTest {
    private val ownerA = UserId.generate()
    private val ownerB = UserId.generate()
    private val merchant = MerchantId.generate()

    @Test
    fun `owners keep independent preferences and preference outranks a general suggestion`() {
        val preferred = category(ownerA)
        val general = Category(
            id = CategoryId.generate(), ownerId = null, parentId = null,
            origin = CategoryOrigin.SYSTEM, isActive = true,
            categoryType = CategoryType.EXPENSE,
        )
        val preference = MerchantCategoryPreference(ownerA, merchant, preferred.id)

        assertEquals(
            MerchantPreferenceResolution.Preferred(preferred.id),
            MerchantCategoryPreferenceRules.resolve(
                ownerA, merchant, listOf(preference), listOf(preferred, general),
                MovementType.EXPENSE, general.id,
            ),
        )
        assertEquals(
            MerchantPreferenceResolution.Suggested(general.id),
            MerchantCategoryPreferenceRules.resolve(
                ownerB, merchant, listOf(preference), listOf(preferred, general),
                MovementType.EXPENSE, general.id,
            ),
        )
    }

    @Test
    fun `inactive plan blocked and incompatible preferences request a new choice`() {
        val inactive = category(ownerA, isActive = false)
        val locked = category(ownerA, isPlanLocked = true)
        val income = category(ownerA, type = CategoryType.INCOME)

        listOf(inactive, locked, income).forEach { selected ->
            assertEquals(
                MerchantPreferenceResolution.NeedsNewChoice,
                MerchantCategoryPreferenceRules.resolve(
                    ownerA, merchant,
                    listOf(MerchantCategoryPreference(ownerA, merchant, selected.id)),
                    listOf(selected), MovementType.EXPENSE, null,
                ),
            )
        }
    }

    @Test
    fun `inactive root makes a child preference ineligible and a removed preference is ignored`() {
        val root = category(ownerA, isActive = false)
        val child = Category(
            id = CategoryId.generate(), ownerId = ownerA, parentId = root.id,
            origin = CategoryOrigin.CUSTOM, isActive = true,
            categoryType = CategoryType.EXPENSE,
        )
        val preference = MerchantCategoryPreference(ownerA, merchant, child.id)

        assertEquals(
            MerchantPreferenceResolution.NeedsNewChoice,
            MerchantCategoryPreferenceRules.resolve(
                ownerA, merchant, listOf(preference), listOf(root, child), MovementType.EXPENSE, null,
            ),
        )
        assertEquals(
            MerchantPreferenceResolution.Unclassified,
            MerchantCategoryPreferenceRules.resolve(
                ownerA, merchant,
                listOf(preference.copy(revision = 2, deletedAt = Instant.parse("2026-10-08T10:00:00Z"))),
                listOf(root, child), MovementType.EXPENSE, null,
            ),
        )
    }

    private fun category(
        owner: UserId,
        type: CategoryType = CategoryType.EXPENSE,
        isActive: Boolean = true,
        isPlanLocked: Boolean = false,
    ) = Category(
        id = CategoryId.generate(), ownerId = owner, parentId = null,
        origin = CategoryOrigin.CUSTOM, isActive = isActive,
        isPlanLocked = isPlanLocked, categoryType = type,
    )
}
