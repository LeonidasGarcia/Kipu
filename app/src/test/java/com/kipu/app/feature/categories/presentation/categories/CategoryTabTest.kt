package com.kipu.app.feature.categories.presentation.categories

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.model.Category
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.CategoryOrigin
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.categories.domain.usecase.CategoryItem
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryTabTest {
    @Test
    fun expenseAndIncomeTabsFilterOppositeTypesAndKeepGeneralCategories() {
        val owner = UserId.generate()
        val expense = item(owner, "expense", CategoryType.EXPENSE)
        val income = item(owner, "income", CategoryType.INCOME)
        val general = item(owner, "general", CategoryType.GENERAL)
        val categories = listOf(expense, income, general)

        assertEquals(
            setOf("expense", "general"),
            CategoryTab.EXPENSE.filterCategories(categories).map { it.category.id.value }.toSet(),
        )
        assertEquals(
            setOf("income", "general"),
            CategoryTab.INCOME.filterCategories(categories).map { it.category.id.value }.toSet(),
        )
    }

    private fun item(owner: UserId, id: String, type: CategoryType) = CategoryItem(
        category = Category(
            id = CategoryId(id),
            ownerId = owner,
            parentId = null,
            origin = CategoryOrigin.CUSTOM,
            isActive = true,
            categoryType = type,
        ),
        presentation = null,
    )
}
