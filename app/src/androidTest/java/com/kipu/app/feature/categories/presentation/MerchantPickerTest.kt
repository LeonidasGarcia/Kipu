package com.kipu.app.feature.categories.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.presentation.components.CatalogStatus
import com.kipu.app.feature.categories.presentation.components.MerchantPicker
import com.kipu.app.feature.categories.presentation.components.MerchantPickerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MerchantPickerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysCatalogSearchResults() {
        val entry1 = MerchantCatalogEntry(MerchantId("m1"), "Tambo+", "tambo+")
        val entry2 = MerchantCatalogEntry(MerchantId("m2"), "Supermercados Metro", "supermercados metro")

        var selectedEntry: MerchantCatalogEntry? = null

        composeTestRule.setContent {
            MerchantPicker(
                state = MerchantPickerState(
                    query = "tam",
                    searchResults = listOf(entry1, entry2),
                ),
                onQueryChange = {},
                onSelectMerchant = { selectedEntry = it },
                onSetProvisionalText = {},
                onClearSelection = {},
            )
        }

        composeTestRule.onNodeWithText("Resultados del catálogo:").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tambo+").assertIsDisplayed()
        composeTestRule.onNodeWithText("Supermercados Metro").assertIsDisplayed()

        composeTestRule.onNodeWithText("Tambo+").performClick()
        assertEquals(entry1, selectedEntry)
    }

    @Test
    fun displaysEmptyResultAndProvisionalOption() {
        var provisionalAssigned: String? = null

        composeTestRule.setContent {
            MerchantPicker(
                state = MerchantPickerState(
                    query = "Bodeguita Linda",
                    searchResults = emptyList(),
                    isSearching = false,
                ),
                onQueryChange = {},
                onSelectMerchant = {},
                onSetProvisionalText = { provisionalAssigned = it },
                onClearSelection = {},
            )
        }

        composeTestRule.onNodeWithText("No se encontró \"Bodeguita Linda\" en el catálogo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("use_provisional_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("use_provisional_button").performClick()
        assertEquals("Bodeguita Linda", provisionalAssigned)
    }

    @Test
    fun displaysSelectedMerchantWithClearAction() {
        var clearClicked = false
        val entry = MerchantCatalogEntry(MerchantId("m1"), "Plaza Vea", "plaza vea")

        composeTestRule.setContent {
            MerchantPicker(
                state = MerchantPickerState(
                    selectedMerchant = entry,
                ),
                onQueryChange = {},
                onSelectMerchant = {},
                onSetProvisionalText = {},
                onClearSelection = { clearClicked = true },
            )
        }

        composeTestRule.onNodeWithText("Comercio del catálogo").assertIsDisplayed()
        composeTestRule.onNodeWithText("Plaza Vea").assertIsDisplayed()
        composeTestRule.onNodeWithTag("clear_merchant_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("clear_merchant_button").performClick()
        assertTrue(clearClicked)
    }

    @Test
    fun displaysProvisionalTextWithClearAction() {
        var clearClicked = false

        composeTestRule.setContent {
            MerchantPicker(
                state = MerchantPickerState(
                    provisionalText = "Puesto de Frutas",
                ),
                onQueryChange = {},
                onSelectMerchant = {},
                onSetProvisionalText = {},
                onClearSelection = { clearClicked = true },
            )
        }

        composeTestRule.onNodeWithText("Texto provisional").assertIsDisplayed()
        composeTestRule.onNodeWithText("Puesto de Frutas").assertIsDisplayed()
        composeTestRule.onNodeWithTag("clear_merchant_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("clear_merchant_button").performClick()
        assertTrue(clearClicked)
    }

    @Test
    fun displaysStaleAndUnavailableCatalogStatusBanners() {
        composeTestRule.setContent {
            MerchantPicker(
                state = MerchantPickerState(
                    catalogStatus = CatalogStatus.STALE,
                ),
                onQueryChange = {},
                onSelectMerchant = {},
                onSetProvisionalText = {},
                onClearSelection = {},
            )
        }

        composeTestRule.onNodeWithText(
            "Catálogo sin sincronizar recientemente. Los resultados locales pueden estar desactualizados."
        ).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Estado del catálogo").assertIsDisplayed()
    }
}
