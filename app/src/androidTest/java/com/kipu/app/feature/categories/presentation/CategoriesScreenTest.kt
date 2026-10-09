package com.kipu.app.feature.categories.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kipu.app.ui.theme.KipuTheme
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.model.Category
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.CategoryOrigin
import com.kipu.app.feature.categories.domain.model.CategoryPresentation
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.categories.domain.usecase.CategoryItem
import com.kipu.app.feature.categories.presentation.categories.CategoriesUiState
import com.kipu.app.feature.categories.presentation.categories.CategoriesViewModel
import com.kipu.app.feature.categories.presentation.categories.CategoryRootCard
import com.kipu.app.feature.categories.presentation.categories.CategoryFormContent
import com.kipu.app.feature.categories.presentation.categories.CategoriesScreen
import com.kipu.app.feature.categories.presentation.categories.CategoryTab
import com.kipu.app.feature.categories.presentation.categories.QuotaBanner
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.abs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CategoriesScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testUserId = UserId.generate()

    @Test
    fun displaysHierarchyWithRootAndSubcategories() {
        val rootId = CategoryId.generate()
        val subId = CategoryId.generate()

        val subItem = CategoryItem(
            category = Category(
                id = subId,
                ownerId = testUserId,
                parentId = rootId,
                origin = CategoryOrigin.CUSTOM,
                isActive = true,
            ),
            presentation = CategoryPresentation(
                categoryId = subId,
                ownerId = testUserId,
                name = "Restaurantes",
                icon = "restaurant",
                color = "#E91E63",
            ),
        )

        val rootItem = CategoryItem(
            category = Category(
                id = rootId,
                ownerId = testUserId,
                parentId = null,
                origin = CategoryOrigin.CUSTOM,
                isActive = true,
            ),
            presentation = CategoryPresentation(
                categoryId = rootId,
                ownerId = testUserId,
                name = "Alimentación",
                icon = "food",
                color = "#4CAF50",
            ),
            subcategories = listOf(subItem),
        )

        composeTestRule.setContent {
            CategoryRootCard(
                item = rootItem,
                onToggleActive = {},
                onAddSubcategory = {},
                onToggleSubcategoryActive = { _, _ -> },
            )
        }

        composeTestRule.onNodeWithText("Alimentación").assertIsDisplayed()
        composeTestRule.onNodeWithText("Restaurantes").assertIsDisplayed()
        composeTestRule.onNodeWithText("Personalizada").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Personalizada · consume cupo Free").assertCountEquals(0)
        composeTestRule.onNodeWithText("Agregar subcategoría").assertIsDisplayed()
    }

    @Test
    fun editButtonsAlignWithRootAndSubcategoryNames() {
        val rootId = CategoryId.generate()
        val subId = CategoryId.generate()
        val subItem = CategoryItem(
            category = Category(
                id = subId,
                ownerId = testUserId,
                parentId = rootId,
                origin = CategoryOrigin.CUSTOM,
                isActive = true,
            ),
            presentation = CategoryPresentation(
                categoryId = subId,
                ownerId = testUserId,
                name = "Restaurantes",
                icon = "restaurant",
                color = "#E91E63",
            ),
        )
        val rootItem = CategoryItem(
            category = Category(
                id = rootId,
                ownerId = testUserId,
                parentId = null,
                origin = CategoryOrigin.CUSTOM,
                isActive = true,
            ),
            presentation = CategoryPresentation(
                categoryId = rootId,
                ownerId = testUserId,
                name = "Alimentación",
                icon = "food",
                color = "#4CAF50",
            ),
            subcategories = listOf(subItem),
        )

        composeTestRule.setContent { CategoryRootCard(item = rootItem) }

        val rootNameY = composeTestRule.onNodeWithText("Alimentación")
            .fetchSemanticsNode().boundsInRoot.center.y
        val rootEditY = composeTestRule.onNodeWithContentDescription("Editar Alimentación")
            .fetchSemanticsNode().boundsInRoot.center.y
        val subcategoryNameY = composeTestRule.onNodeWithText("Restaurantes")
            .fetchSemanticsNode().boundsInRoot.center.y
        val subcategoryEditY = composeTestRule.onNodeWithContentDescription("Editar Restaurantes")
            .fetchSemanticsNode().boundsInRoot.center.y

        assertTrue("Root edit should align with the root title", abs(rootEditY - rootNameY) < 16f)
        assertTrue("Subcategory edit should align with its label", abs(subcategoryEditY - subcategoryNameY) < 8f)
    }

    @Test
    fun displaysDisabledStateWhenRootIsInactive() {
        val rootId = CategoryId.generate()
        val subId = CategoryId.generate()

        val subItem = CategoryItem(
            category = Category(
                id = subId,
                ownerId = testUserId,
                parentId = rootId,
                origin = CategoryOrigin.CUSTOM,
                isActive = true,
            ),
            presentation = CategoryPresentation(
                categoryId = subId,
                ownerId = testUserId,
                name = "Subcat",
                icon = "label",
                color = "#757575",
            ),
        )

        val inactiveRoot = CategoryItem(
            category = Category(
                id = rootId,
                ownerId = testUserId,
                parentId = null,
                origin = CategoryOrigin.CUSTOM,
                isActive = false,
            ),
            presentation = CategoryPresentation(
                categoryId = rootId,
                ownerId = testUserId,
                name = "Categoría Inactiva",
                icon = "folder",
                color = "#757575",
            ),
            subcategories = listOf(subItem),
        )

        composeTestRule.setContent {
            CategoryRootCard(
                item = inactiveRoot,
                onToggleActive = {},
                onAddSubcategory = {},
                onToggleSubcategoryActive = { _, _ -> },
            )
        }

        composeTestRule.onNodeWithText("Categoría Inactiva").assertIsDisplayed()
        composeTestRule.onNodeWithText("Inactiva · Bloquea nuevas asignaciones").assertIsDisplayed()
        composeTestRule.onNodeWithText("Inactiva por categoría padre").assertIsDisplayed()
    }

    @Test
    fun displaysQuotaBannerAndLimitReachedWarning() {
        composeTestRule.setContent {
            QuotaBanner(
                activeCount = 5,
                maxCount = 5,
                isLimitReached = true,
            )
        }

        composeTestRule.onNodeWithText("5/5").assertIsDisplayed()
        composeTestRule.onNodeWithText("0/5").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Cupo completo · libera una categoría para activar otra."
        ).assertIsDisplayed()
    }

    @Test
    fun rendersCleanlyUnder200PercentTextScaling() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 2.0f, fontScale = 2.0f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    QuotaBanner(
                        activeCount = 3,
                        maxCount = 5,
                        isLimitReached = false,
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("3/5").assertIsDisplayed()
        composeTestRule.onNodeWithText("0/5").assertIsDisplayed()
    }

    @Test
    fun customIconAndColorStayVisibleAsSelectedAndSubcategoryUsesCategoryConcepts() {
        composeTestRule.setContent {
            CategoryFormContent(
                name = "",
                icon = "restaurant",
                color = "#AA3311",
                parentId = CategoryId.generate(),
                availableRoots = emptyList(),
                onNameChange = {},
                onIconChange = {},
                onColorChange = {},
                onParentIdChange = {},
                onDismiss = {},
                onBack = {},
                onConfirm = {},
            )
        }

        composeTestRule.onNodeWithTag("more_icons_button").assertIsSelected()
        composeTestRule.onNodeWithTag("custom_color_picker_button").assertIsSelected()
        composeTestRule.onNodeWithText("Ej. Restaurantes, Cafeterías...").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Recordar comercio frecuente").assertCountEquals(0)
    }

    @Test
    fun expenseAndIncomeTabsShowMatchingRootsAndGeneralCategories() {
        val categories = listOf(
            categoryItem("expense-root", "Alimentación", CategoryType.EXPENSE),
            categoryItem("income-root", "Salario", CategoryType.INCOME),
            categoryItem("general-root", "Sin tipo histórico", CategoryType.GENERAL),
        )
        val state = MutableStateFlow(
            CategoriesUiState(isLoading = false, categories = categories, selectedTab = CategoryTab.EXPENSE),
        )
        val viewModel = mockk<CategoriesViewModel>(relaxed = true) {
            every { uiState } returns state
            every { onTabSelected(any()) } answers {
                state.value = state.value.copy(selectedTab = firstArg())
            }
        }

        composeTestRule.setContent {
            KipuTheme {
                CategoriesScreen(viewModel = viewModel, onNavigateBack = {})
            }
        }

        composeTestRule.onNodeWithText("Alimentación").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sin tipo histórico").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Agregar subcategoría").assertCountEquals(0)
        assertFalse(composeTestRule.onAllNodesWithText("Salario").fetchSemanticsNodes().isNotEmpty())

        composeTestRule.onNodeWithContentDescription("Expandir Alimentación").performClick()
        composeTestRule.onNodeWithText("Agregar subcategoría").assertIsDisplayed()

        composeTestRule.onNodeWithText("Ingresos").performClick()
        composeTestRule.onNodeWithText("Salario").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sin tipo histórico").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Agregar subcategoría").assertCountEquals(0)
        composeTestRule.onNodeWithContentDescription("Expandir Salario").performClick()
        composeTestRule.onNodeWithText("Agregar subcategoría").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithText("Alimentación").fetchSemanticsNodes().isEmpty())
    }

    private fun categoryItem(id: String, name: String, type: CategoryType) = CategoryItem(
        category = Category(
            id = CategoryId(id),
            ownerId = testUserId,
            parentId = null,
            origin = CategoryOrigin.CUSTOM,
            isActive = true,
            categoryType = type,
        ),
        presentation = CategoryPresentation(
            categoryId = CategoryId(id),
            ownerId = testUserId,
            name = name,
            icon = "category",
            color = "#0F766E",
        ),
    )
}
