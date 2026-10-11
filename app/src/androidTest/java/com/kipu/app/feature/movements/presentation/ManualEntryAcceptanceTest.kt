package com.kipu.app.feature.movements.presentation

import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class ManualEntryAcceptanceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun preparedExpenseCanBeEnteredAndAcceptedWithinTenSeconds() {
        val accountId = AccountId.generate()
        val account = Account(
            id = accountId,
            userId = UserId.generate(),
            alias = "Cuenta principal",
            type = AccountType.SAVINGS,
            currency = Currency.PEN,
            initialBalance = Money(100_000L, Currency.PEN),
            openedAt = Instant.parse("2026-09-24T12:00:00Z"),
        )
        val initialState = QuickMovementUiState(
            selectedSourceAccountId = account.id.value,
            availableAccounts = listOf(account),
            availableCategories = listOf(
                CategoryOption("acceptance-food", "Alimentación", "restaurant", CategoryType.EXPENSE),
            ),
        )
        var acceptedAmount = ""
        var acceptedCategoryId: String? = null
        val startedAt = SystemClock.elapsedRealtime()

        compose.setContent {
            KipuTheme {
                val state = remember { mutableStateOf(initialState) }
                QuickMovementContent(
                    uiState = state.value,
                    onTypeSelected = { type -> state.value = state.value.copy(type = type) },
                    onAmountChanged = { amount -> state.value = state.value.copy(amountText = amount) },
                    onSourceAccountSelected = { id -> state.value = state.value.copy(selectedSourceAccountId = id) },
                    onSourceCardSelected = {},
                    onDestinationAccountSelected = { id -> state.value = state.value.copy(selectedDestinationAccountId = id) },
                    onCategorySelected = { category ->
                        state.value = state.value.copy(
                            selectedCategoryId = category.id,
                            selectedCategoryName = category.name,
                        )
                    },
                    onOpenMerchantPicker = {},
                    onClearMerchant = {},
                    onNoteChanged = { note -> state.value = state.value.copy(note = note) },
                    onOpenDatePicker = {},
                    onToggleMoreDetails = {},
                    onSave = {
                        acceptedAmount = state.value.amountText
                        acceptedCategoryId = state.value.selectedCategoryId
                    },
                    onClose = {},
                    accountBalances = emptyMap(),
                    creditCardDebts = emptyMap(),
                    creditCardAvailableCredits = emptyMap(),
                    mostUsedAccountId = null,
                    onNavigateToNewAccount = {},
                    onCreateSubcategory = { _, _, _, _, _ ->
                        Result.failure(UnsupportedOperationException("Subcategory creation is not part of this test"))
                    },
                    isCreatingSubcategory = false,
                    categoryCreationError = null,
                )
            }
        }

        compose.onNodeWithTag("input_amount").performTextInput("12.50")
        compose.onNodeWithTag("chip_cat_acceptance-food").performScrollTo().performClick()
        compose.onNodeWithTag("btn_save_transaction").performScrollTo().performClick()
        compose.waitForIdle()
        val elapsedMillis = SystemClock.elapsedRealtime() - startedAt

        assertEquals("12.50", acceptedAmount)
        assertEquals("acceptance-food", acceptedCategoryId)
        assertTrue("Prepared manual entry should complete within 10 seconds; took ${elapsedMillis}ms", elapsedMillis < 10_000L)
    }
}
