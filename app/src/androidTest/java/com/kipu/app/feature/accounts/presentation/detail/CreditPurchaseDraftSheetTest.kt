package com.kipu.app.feature.accounts.presentation.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.presentation.PurchaseCategoryOption
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.domain.usecase.SearchMerchantCatalog
import com.kipu.app.feature.categories.presentation.components.MerchantPickerViewModel
import com.kipu.app.ui.theme.KipuTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CreditPurchaseDraftSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun catalogMerchantAndExpenseCategoryReachExplicitPurchaseConfirmation() {
        val merchant = MerchantCatalogEntry(
            id = MerchantId("merchant-plaza-vea"),
            name = "Plaza Vea",
            normalizedName = "plaza vea",
        )
        val repository = mockk<CategoriesRepository>(relaxed = true) {
            every { observeMerchantCatalog() } returns flowOf(listOf(merchant))
            every { observeMerchantCategoryFilters() } returns flowOf(emptyList())
        }
        val merchantPicker = MerchantPickerViewModel(SearchMerchantCatalog(repository), repository)
        val category = PurchaseCategoryOption(id = "category-groceries", name = "Supermercado")
        var confirmedCandidate: com.kipu.app.feature.accounts.domain.model.PurchaseCandidate? = null
        var confirmedInstallments: Int? = null

        compose.setContent {
            KipuTheme {
                CreditPurchaseDraftSheet(
                    card = testCard(),
                    categories = listOf(category),
                    onDismiss = {},
                    onConfirmPurchase = { candidate, installments ->
                        confirmedCandidate = candidate
                        confirmedInstallments = installments
                    },
                    merchantPickerViewModel = merchantPicker,
                )
            }
        }

        compose.waitUntil(5_000) { merchantPicker.uiState.value.catalogEntries.isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextInput("125.50")
        compose.onNodeWithText("Seleccionar comercio").performClick()
        compose.onNodeWithText("Plaza Vea").performClick()
        compose.onNodeWithText("Seleccionar categoría").performClick()
        compose.onNodeWithText("Supermercado").performClick()
        compose.onNodeWithText("Ver simulación").assertIsEnabled().performClick()
        compose.onNodeWithText("Cronograma proyectado").assertIsDisplayed()
        compose.onAllNodesWithText("Plaza Vea").assertCountEquals(1)
        compose.onNodeWithText("Confirmar compra").assertIsEnabled().performClick()

        compose.runOnIdle {
            assertEquals("Plaza Vea", confirmedCandidate?.merchant)
            assertEquals("merchant-plaza-vea", confirmedCandidate?.merchantId)
            assertEquals("category-groceries", confirmedCandidate?.categoryId)
            assertEquals(12_550L, confirmedCandidate?.amount?.minorUnits)
            assertEquals(3, confirmedInstallments)
        }
    }

    @Test
    fun categorySearchFindsSpanishNamesWithoutTypingAccents() {
        val repository = mockk<CategoriesRepository>(relaxed = true) {
            every { observeMerchantCatalog() } returns flowOf(emptyList())
            every { observeMerchantCategoryFilters() } returns flowOf(emptyList())
        }
        val merchantPicker = MerchantPickerViewModel(SearchMerchantCatalog(repository), repository)

        compose.setContent {
            KipuTheme {
                CreditPurchaseDraftSheet(
                    card = testCard(),
                    categories = listOf(
                        PurchaseCategoryOption(id = "food", name = "Alimentación"),
                        PurchaseCategoryOption(id = "transport", name = "Transporte"),
                    ),
                    onDismiss = {},
                    onConfirmPurchase = { _, _ -> },
                    merchantPickerViewModel = merchantPicker,
                )
            }
        }

        compose.onNodeWithText("Seleccionar categoría").performClick()
        compose.onNodeWithTag("credit_purchase_category_search").performTextInput("Alimentacion")
        compose.onNodeWithText("Alimentación").assertIsDisplayed()
        compose.onNodeWithText("No se encontraron categorías.").assertDoesNotExist()
    }

    private fun testCard() = CreditCard(
        id = CardId.generate(),
        userId = UserId.generate(),
        alias = "Visa principal",
        issuer = "BCP",
        network = CardNetwork.VISA,
        lastFourDigits = "1234",
        currency = Currency.PEN,
        creditLimitMinorUnits = 500_000L,
        billingDay = 20,
        dueDay = 26,
    )
}
