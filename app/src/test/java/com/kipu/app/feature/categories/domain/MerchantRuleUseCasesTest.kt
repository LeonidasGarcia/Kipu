package com.kipu.app.feature.categories.domain

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.model.Category
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.CategoryOrigin
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.categories.domain.model.MerchantAliasRule
import com.kipu.app.feature.categories.domain.model.MerchantAliasRuleId
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.MerchantCategoryPreference
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.domain.model.SourceMerchantText
import com.kipu.app.feature.categories.domain.usecase.SaveMerchantAliasRule
import com.kipu.app.feature.categories.domain.usecase.ResolvePreferredCategory
import com.kipu.app.feature.categories.domain.usecase.SetMerchantCategoryPreference
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantRuleUseCasesTest {
    private val repository = mockk<CategoriesRepository>(relaxed = true)
    private val owner = UserId.generate()
    private val merchant = MerchantId.generate()
    private val canonicalMerchant = MerchantCatalogEntry(
        id = merchant,
        name = "Tambo",
        normalizedName = "tambo",
    )

    @Test
    fun `new alias requires a currently verified Premium entitlement`() = runTest {
        every { repository.observePremiumVerified(owner) } returns flowOf(false)
        val save = SaveMerchantAliasRule(repository)

        val result = save(
            ownerId = owner,
            sourceText = SourceMerchantText("IZIPAY*TAMBO"),
            merchant = canonicalMerchant,
            confirmedMerchantId = merchant,
        )

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { repository.saveMerchantAliasRule(any(), any()) }
    }

    @Test
    fun `new alias requires explicit canonical merchant confirmation`() = runTest {
        every { repository.observePremiumVerified(owner) } returns flowOf(true)
        coEvery { repository.saveMerchantAliasRule(any(), any()) } returns Result.success(Unit)

        val result = SaveMerchantAliasRule(repository)(
            ownerId = owner,
            sourceText = SourceMerchantText("IZIPAY*TAMBO"),
            merchant = canonicalMerchant,
            confirmedMerchantId = MerchantId.generate(),
        )

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { repository.saveMerchantAliasRule(any(), any()) }
    }

    @Test
    fun `confirmed alias stores normalized source and retains punctuation`() = runTest {
        every { repository.observePremiumVerified(owner) } returns flowOf(true)
        coEvery { repository.saveMerchantAliasRule(any(), any()) } returns Result.success(Unit)

        val result = SaveMerchantAliasRule(repository)(
            ownerId = owner,
            sourceText = SourceMerchantText("  Izipay* TÁMBO  "),
            merchant = canonicalMerchant,
            confirmedMerchantId = merchant,
        )

        assertTrue(result.isSuccess)
        assertEquals("izipay* tambo", result.getOrThrow().normalizedPattern)
        assertEquals(merchant, result.getOrThrow().merchantId)
        coVerify(exactly = 1) { repository.saveMerchantAliasRule(any(), null) }
    }

    @Test
    fun `editing an existing alias does not reapply the new rule Premium gate`() = runTest {
        every { repository.observePremiumVerified(owner) } returns flowOf(false)
        coEvery { repository.saveMerchantAliasRule(any(), any()) } returns Result.success(Unit)
        val existing = MerchantAliasRule(
            id = MerchantAliasRuleId.generate(),
            ownerId = owner,
            normalizedPattern = "old pattern",
            merchantId = merchant,
            revision = 4,
        )

        val result = SaveMerchantAliasRule(repository)(
            ownerId = owner,
            sourceText = SourceMerchantText("NEW PATTERN"),
            merchant = canonicalMerchant,
            confirmedMerchantId = merchant,
            existingRule = existing,
        )

        assertTrue(result.isSuccess)
        assertEquals(5L, result.getOrThrow().revision)
        assertEquals(existing.id, result.getOrThrow().id)
        coVerify(exactly = 0) { repository.observePremiumVerified(owner) }
        coVerify(exactly = 1) { repository.saveMerchantAliasRule(any(), 4L) }
    }

    @Test
    fun `future preference can be saved for Free without changing existing movement history`() = runTest {
        val category = Category(
            id = CategoryId.generate(), ownerId = owner, parentId = null,
            origin = CategoryOrigin.CUSTOM, isActive = true, categoryType = CategoryType.EXPENSE,
        )
        every { repository.observeCategories(owner) } returns flowOf(listOf(category))
        every { repository.observeMerchantCategoryPreferences(owner) } returns flowOf(emptyList())
        coEvery { repository.saveMerchantCategoryPreference(any(), any()) } returns Result.success(Unit)

        val result = SetMerchantCategoryPreference(repository)(owner, merchant, category.id)

        assertTrue(result.isSuccess)
        assertEquals(category.id, result.getOrThrow().categoryId)
        coVerify(exactly = 0) { repository.observePremiumVerified(owner) }
        coVerify(exactly = 1) { repository.saveMerchantCategoryPreference(any(), null) }
    }

    @Test
    fun `preference rejects inactive or plan blocked categories`() = runTest {
        val inactive = Category(
            id = CategoryId.generate(), ownerId = owner, parentId = null,
            origin = CategoryOrigin.CUSTOM, isActive = false, categoryType = CategoryType.EXPENSE,
        )
        every { repository.observeCategories(owner) } returns flowOf(listOf(inactive))
        every { repository.observeMerchantCategoryPreferences(owner) } returns flowOf(emptyList())
        coEvery { repository.saveMerchantCategoryPreference(any(), any()) } returns Result.success(Unit)

        val result = SetMerchantCategoryPreference(repository)(owner, merchant, inactive.id)

        assertFalse(result.isSuccess)
        coVerify(exactly = 0) { repository.saveMerchantCategoryPreference(any(), any()) }
    }

    @Test
    fun `preference update preserves stable id and advances revision`() = runTest {
        val category = Category(
            id = CategoryId.generate(), ownerId = owner, parentId = null,
            origin = CategoryOrigin.CUSTOM, isActive = true, categoryType = CategoryType.GENERAL,
        )
        val current = MerchantCategoryPreference(owner, merchant, CategoryId.generate(), revision = 7, id = "pref-id")
        every { repository.observeCategories(owner) } returns flowOf(listOf(category))
        every { repository.observeMerchantCategoryPreferences(owner) } returns flowOf(listOf(current))
        coEvery { repository.saveMerchantCategoryPreference(any(), any()) } returns Result.success(Unit)

        val result = SetMerchantCategoryPreference(repository)(owner, merchant, category.id)

        assertTrue(result.isSuccess)
        assertEquals("pref-id", result.getOrThrow().id)
        assertEquals(8L, result.getOrThrow().revision)
        coVerify(exactly = 1) { repository.saveMerchantCategoryPreference(any(), 7L) }
    }

    @Test
    fun `preference resolution uses owner choice only when category matches future movement type`() = runTest {
        val expense = Category(
            id = CategoryId.generate(), ownerId = owner, parentId = null,
            origin = CategoryOrigin.CUSTOM, isActive = true, categoryType = CategoryType.EXPENSE,
        )
        val income = Category(
            id = CategoryId.generate(), ownerId = owner, parentId = null,
            origin = CategoryOrigin.CUSTOM, isActive = true, categoryType = CategoryType.INCOME,
        )
        every { repository.observeCategories(owner) } returns flowOf(listOf(expense, income))
        every { repository.observeMerchantCategoryPreferences(owner) } returns flowOf(
            listOf(MerchantCategoryPreference(owner, merchant, expense.id)),
        )

        val result = ResolvePreferredCategory(repository)(
            ownerId = owner,
            merchantId = merchant,
            movementType = com.kipu.app.feature.movements.domain.model.MovementType.INCOME,
            generalSuggestion = income.id,
        )

        assertEquals(MerchantPreferenceResolution.NeedsNewChoice, result)
    }
}
