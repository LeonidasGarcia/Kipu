package com.kipu.app.feature.movements.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class QuickMovementScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun typeTabsShowConditionalFieldsAndAccessibleSaveAction() {
        val initialState = QuickMovementUiState(
            availableCategories = listOf(
                CategoryOption("expense-food", "Alimentación", "restaurant", CategoryType.EXPENSE),
                CategoryOption("income-salary", "Salario", "work", CategoryType.INCOME),
            ),
        )

        compose.setContent {
            KipuTheme {
                val state = remember { mutableStateOf(initialState) }
                QuickMovementContent(
                    uiState = state.value,
                    onTypeSelected = { type -> state.value = state.value.copy(type = type) },
                    onAmountChanged = {},
                    onSourceAccountSelected = {},
                    onDestinationAccountSelected = {},
                    onCategorySelected = {},
                    onOpenMerchantPicker = {},
                    onClearMerchant = {},
                    onNoteChanged = {},
                    onOpenDatePicker = {},
                    onToggleMoreDetails = {},
                    onSave = {},
                    onClose = {},
                )
            }
        }

        compose.onNodeWithTag("selector_source_account").assertIsDisplayed()
        compose.onNodeWithTag("selector_destination_account").assertDoesNotExist()
        compose.onNodeWithText("Categoría *").assertIsDisplayed()
        compose.onNodeWithTag("chip_cat_expense-food").assertIsDisplayed()

        compose.onNodeWithTag("tab_income").performClick()
        compose.onNodeWithText("Categoría (opcional)").assertIsDisplayed()
        compose.onNodeWithTag("selector_destination_account").assertDoesNotExist()

        compose.onNodeWithTag("tab_transfer").performClick()
        compose.onNodeWithTag("selector_source_account").assertIsDisplayed()
        compose.onNodeWithTag("selector_destination_account").assertIsDisplayed()
        compose.onNodeWithText("Categoría *").assertDoesNotExist()
        compose.onNodeWithTag("chip_cat_expense-food").assertDoesNotExist()

        val saveBounds = compose.onNodeWithContentDescription("Guardar transacción")
            .assertHasClickAction()
            .fetchSemanticsNode().boundsInRoot
        with(compose.density) {
            assertTrue("Save action height must meet the 48dp touch target", saveBounds.height.toDp() >= 48.dp)
        }
    }

    @Test
    fun eachMovementTabHasAnAccessibleClickTarget() {
        compose.setContent {
            KipuTheme {
                QuickMovementContent(
                    uiState = QuickMovementUiState(),
                    onTypeSelected = {},
                    onAmountChanged = {},
                    onSourceAccountSelected = {},
                    onDestinationAccountSelected = {},
                    onCategorySelected = {},
                    onOpenMerchantPicker = {},
                    onClearMerchant = {},
                    onNoteChanged = {},
                    onOpenDatePicker = {},
                    onToggleMoreDetails = {},
                    onSave = {},
                    onClose = {},
                )
            }
        }

        listOf(MovementType.EXPENSE, MovementType.INCOME, MovementType.TRANSFER).forEach { type ->
            compose.onNodeWithTag("tab_${type.name.lowercase()}").assertHasClickAction()
        }
    }
}
