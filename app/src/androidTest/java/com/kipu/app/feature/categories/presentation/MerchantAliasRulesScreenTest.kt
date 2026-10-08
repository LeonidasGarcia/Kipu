package com.kipu.app.feature.categories.presentation

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.presentation.merchantrules.MerchantAliasRulesContent
import com.kipu.app.feature.categories.presentation.merchantrules.MerchantAliasRulesUiState
import org.junit.Rule
import org.junit.Test

class MerchantAliasRulesScreenTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun freeUserCanReviewManualFlowButCannotSaveANewRule() {
        val tambo = MerchantCatalogEntry(MerchantId.generate(), "Tambo", "tambo")
        composeTestRule.setContent {
            MerchantAliasRulesContent(
                state = MerchantAliasRulesUiState(
                    isPremiumVerified = false,
                    sourceText = "IZIPAY*TAMBO",
                    merchantQuery = "Tambo",
                    merchants = listOf(tambo),
                    selectedMerchant = tambo,
                ),
                onSourceTextChange = {},
                onMerchantQueryChange = {},
                onSelectMerchant = {},
                onSave = {},
                onDelete = {},
            )
        }

        composeTestRule.onNodeWithText("Texto fuente exacto").assertIsDisplayed()
        composeTestRule.onNodeWithText("Comercio confirmado").assertIsDisplayed()
        composeTestRule.onNode(hasText("Tambo") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHasClickAction()
        composeTestRule.onNodeWithText("Premium necesario para crear una regla nueva").assertIsDisplayed()
        composeTestRule.onNodeWithText("Si Premium o el consentimiento no están vigentes, puedes registrar el movimiento manualmente.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sin coincidencia, el comercio no se asigna y el texto queda disponible para revisión.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Si las reglas apuntan a comercios distintos, la decisión requiere revisión manual.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Guardar alias").assertIsNotEnabled().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun aliasExplainsExactMatchAndKeepsSourceAndCanonicalMerchantSeparate() {
        val tambo = MerchantCatalogEntry(MerchantId.generate(), "Tambo", "tambo")
        composeTestRule.setContent {
            MerchantAliasRulesContent(
                state = MerchantAliasRulesUiState(
                    isPremiumVerified = true,
                    sourceText = " IZIPAY*TÁMBO ",
                    merchantQuery = "Tambo",
                    merchants = listOf(tambo),
                    selectedMerchant = tambo,
                ),
                onSourceTextChange = {},
                onMerchantQueryChange = {},
                onSelectMerchant = {},
                onSave = {},
                onDelete = {},
            )
        }

        composeTestRule.onNodeWithText(" IZIPAY*TÁMBO ").assertIsDisplayed()
        composeTestRule.onNodeWithText("Comercio canónico: Tambo").assertIsDisplayed()
        composeTestRule.onNodeWithText("Coincidencia exacta normalizada; la puntuación se conserva.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Guardar alias").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    }
}
