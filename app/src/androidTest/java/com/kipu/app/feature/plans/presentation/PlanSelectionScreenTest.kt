package com.kipu.app.feature.plans.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.plans.domain.model.CommercialOption
import com.kipu.app.feature.plans.domain.model.TrialEligibilitySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlanSelectionScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsStitchCardsSixFreeRowsAndTwoActions() {
        setScreen()

        compose.onNodeWithText("Selecciona tu Plan").assertIsDisplayed()
        compose.onNodeWithText("Configuración inicial de cuenta y suscripción").assertIsDisplayed()
        compose.onNodeWithTag("plan-free-card").assertHasNoClickAction()
        compose.onNodeWithTag("plan-trial-card").assertExists()
        compose.onAllNodes(hasText("Confirmar Plan") and hasClickAction()).assertCountEquals(1)
        compose.onAllNodes(hasText("Continuar con Plan Free") and hasClickAction()).assertCountEquals(1)
        listOf(
            "Núcleo manual de registro",
            "Hasta 4 instrumentos financieros",
            "Hasta 5 categorías personalizadas",
            "Hasta 2 deudas activas",
            "Hasta 2 metas de ahorro",
            "Hasta 2 presupuestos mensuales",
        ).forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("No requiere método de pago").assertExists()
        compose.onAllNodes(hasContentDescription("Incluido")).assertCountEquals(6)
    }

    @Test
    fun annualIsPreselectedAndPremiumCardsFollowStitchOrder() {
        var selected = CommercialOption.ANNUAL
        setScreen(selected = selected, onSelect = { selected = it })

        val annual = compose.onNodeWithTag("plan-option-ANNUAL").assertIsSelected().fetchSemanticsNode().positionInRoot.y
        val monthly = compose.onNodeWithTag("plan-option-MONTHLY").assertIsNotSelected().fetchSemanticsNode().positionInRoot.y
        val lifetime = compose.onNodeWithTag("plan-option-LIFETIME").assertIsNotSelected().fetchSemanticsNode().positionInRoot.y
        assertTrue(annual < monthly)
        assertTrue(monthly < lifetime)

        compose.onNodeWithTag("plan-option-MONTHLY").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(CommercialOption.MONTHLY, selected) }
    }

    @Test
    fun staticTrialAndApprovedCommercialCopyIgnoreEligibility() {
        val rendered = mutableStateOf(state(eligibility = TrialEligibilitySnapshot.UNKNOWN))
        compose.setContent { PlanSelectionScreen(rendered.value, {}, {}, {}) }

        assertStaticTrialCopy()
        compose.runOnIdle { rendered.value = state(eligibility = ineligible()) }
        assertStaticTrialCopy()

        compose.onNodeWithText("Recomendado").assertExists()
        compose.onNodeWithText("Ahorro equivalente a 50%").assertExists()
        compose.onNodeWithText("Validez fiscal y operativa", substring = true).performScrollTo().assertExists()
    }

    @Test
    fun pricesIncludeFrequencyAndTabularTags() {
        setScreen()

        listOf(
            "ANNUAL" to "S/ 29.99 / año",
            "MONTHLY" to "S/ 4.99 / mes",
            "LIFETIME" to "S/ 49.99 pago único",
        ).forEach { (option, price) ->
            compose.onNodeWithText(price).performScrollTo().assertExists()
            compose.onNodeWithTag("plan-price-$option", useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun controlsAndIconsAreAccessibleAndAtLeast48Dp() {
        setScreen()

        listOf("ANNUAL", "MONTHLY", "LIFETIME").forEach { option ->
            val bounds = compose.onNodeWithTag("plan-option-$option").performScrollTo().assertHasClickAction()
                .fetchSemanticsNode().boundsInRoot
            assertMinimumTouchTarget(bounds)
        }
        compose.onNodeWithContentDescription("Información del periodo de prueba").performScrollTo().assertExists()
        assertMinimumTouchTarget(compose.onNodeWithText("Confirmar Plan").performScrollTo().fetchSemanticsNode().boundsInRoot)
        assertMinimumTouchTarget(compose.onNodeWithText("Continuar con Plan Free").performScrollTo().fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun twoHundredPercentFontScaleScrollsThroughFooterAndBothActions() {
        compose.setContent {
            val density = LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                PlanSelectionScreen(state(), {}, {}, {})
            }
        }

        compose.onNodeWithText("Confirmar Plan").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Continuar con Plan Free").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Validez fiscal y operativa", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun topAndBottomBarsRemainAbsent() {
        setScreen()
        compose.onNodeWithText("KipuTopAppBar", substring = true).assertDoesNotExist()
        compose.onNodeWithText("KipuBottomBar", substring = true).assertDoesNotExist()
    }

    private fun assertStaticTrialCopy() {
        compose.onNodeWithText("Prueba Premium Gratis por 7 días").assertExists()
        compose.onNodeWithText("Completo").assertExists()
        compose.onNodeWithText("Periodo de prueba voluntario sin cobro inmediato").assertExists()
        compose.onNodeWithText("El cobro de S/ 29.99 se realizará", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText("sujeta a verificación", substring = true, ignoreCase = true).assertDoesNotExist()
    }

    private fun setScreen(
        selected: CommercialOption = CommercialOption.ANNUAL,
        eligibility: TrialEligibilitySnapshot = TrialEligibilitySnapshot.UNKNOWN,
        onSelect: (CommercialOption) -> Unit = {},
    ) {
        compose.setContent { PlanSelectionScreen(state(selected, eligibility), onSelect, {}, {}) }
    }

    private fun state(
        selected: CommercialOption = CommercialOption.ANNUAL,
        eligibility: TrialEligibilitySnapshot = TrialEligibilitySnapshot.UNKNOWN,
    ) = PlanSelectionUiState(
        selectedOption = selected,
        eligibility = eligibility,
        isLoadingEligibility = false,
        isConfirming = false,
        errorMessage = null,
    )

    private fun ineligible() = TrialEligibilitySnapshot.ineligible(
        verifiedAt = java.time.Instant.parse("2026-09-14T10:00:00Z"),
    )

    private fun assertMinimumTouchTarget(bounds: Rect) {
        compose.runOnIdle {
            with(compose.density) {
                assertTrue("width was ${bounds.width.toDp()}", bounds.width.toDp() >= 48.dp)
                assertTrue("height was ${bounds.height.toDp()}", bounds.height.toDp() >= 48.dp)
            }
        }
    }
}
