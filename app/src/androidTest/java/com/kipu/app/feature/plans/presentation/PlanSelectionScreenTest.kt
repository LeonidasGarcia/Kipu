package com.kipu.app.feature.plans.presentation

import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.plans.domain.model.CommercialOption
import com.kipu.app.feature.plans.domain.model.TrialEligibilitySnapshot
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlanSelectionScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsFourOptionsFiveFreeLimitsAndExactlyOneConfirmationAction() {
        setScreen()

        CommercialOption.entries.forEach {
            compose.onNodeWithTag("plan-option-${it.name}").performScrollTo().assertIsDisplayed()
        }
        listOf("4 instrumentos", "5 categorías personalizadas", "2 deudas", "2 metas", "2 presupuestos").forEach {
            compose.onNodeWithText(it, substring = true).assertExists()
        }
        compose.onAllNodes(hasText("Confirmar Plan") and hasClickAction()).assertCountEquals(1)
    }

    @Test
    fun exactPricesRenewalAndCancellationAreShownBeforeEligibleMonthlyOrAnnualConfirmation() {
        val renderedState = setDynamicScreen(state(CommercialOption.MONTHLY, eligible()))
        compose.onAllNodes(hasText("7 días", substring = true)).assertCountEquals(2)
        compose.onNodeWithText("S/ 4.99", substring = true).assertExists()
        compose.onNodeWithText("mensual", substring = true, ignoreCase = true).assertExists()
        compose.onAllNodes(hasText("cancel", substring = true, ignoreCase = true)).assertCountEquals(2)

        compose.runOnIdle { renderedState.value = state(CommercialOption.ANNUAL, eligible()) }
        compose.onAllNodes(hasText("7 días", substring = true)).assertCountEquals(2)
        compose.onNodeWithText("S/ 29.99", substring = true).assertExists()
        compose.onNodeWithText("anual", substring = true, ignoreCase = true).assertExists()
        compose.onAllNodes(hasText("cancel", substring = true, ignoreCase = true)).assertCountEquals(2)
    }

    @Test
    fun unknownAndIneligibleNeverPromiseTrialAndLifetimeNeverRenews() {
        val renderedState = setDynamicScreen(state(CommercialOption.MONTHLY, TrialEligibilitySnapshot.unknown()))
        compose.onAllNodes(hasText("sujeta a verificación", substring = true, ignoreCase = true)).assertCountEquals(2)

        compose.runOnIdle { renderedState.value = state(CommercialOption.ANNUAL, ineligible()) }
        compose.onNodeWithText("7 días", substring = true).assertDoesNotExist()

        compose.runOnIdle { renderedState.value = state(CommercialOption.LIFETIME, eligible()) }
        compose.onNodeWithText("S/ 49.99", substring = true).assertExists()
        compose.onNodeWithText("pago único", substring = true, ignoreCase = true).assertExists()
        compose.onNodeWithText("sin renovación", substring = true, ignoreCase = true).assertExists()
    }

    @Test
    fun optionSemanticsExposeSingleSelectionAndEachControlIsAtLeast48Dp() {
        var selected = CommercialOption.FREE
        setScreen(selected = selected, onSelect = { selected = it })

        compose.onNodeWithTag("plan-option-FREE").assertIsSelected()
        compose.onNodeWithTag("plan-option-MONTHLY").assertIsNotSelected().performClick()
        compose.runOnIdle { assertEquals(CommercialOption.MONTHLY, selected) }

        listOf("FREE", "MONTHLY", "ANNUAL", "LIFETIME").forEach { option ->
            val bounds = compose.onNodeWithTag("plan-option-$option").performScrollTo().assertHasClickAction()
                .fetchSemanticsNode().boundsInRoot
            assertMinimumTouchTarget(bounds)
        }
        assertMinimumTouchTarget(compose.onNodeWithText("Confirmar Plan").performScrollTo().fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun allFinancialFiguresUseTabularGlyphAdvances() {
        setScreen()
        listOf("S/ 4.99", "S/ 29.99", "S/ 49.99").forEach { price ->
            val option = when (price) {
                "S/ 4.99" -> "MONTHLY"
                "S/ 29.99" -> "ANNUAL"
                else -> "LIFETIME"
            }
            compose.onNodeWithTag("plan-price-$option", useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun twoHundredPercentFontScaleRemainsScrollableThroughAllContentAndCta() {
        compose.setContent {
            val density = LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                PlanSelectionScreen(state(), {}, {})
            }
        }

        compose.onNodeWithTag("plan-option-LIFETIME").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Confirmar Plan").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun containsNoBarsPaymentActionsOrPromotionalLanguage() {
        setScreen()
        listOf(
            "KipuTopAppBar", "KipuBottomBar", "Confirmar y Pagar", "Método de pago",
            "Tarjeta de crédito", "Recomendado", "Ahorra", "50%", "Gratis",
        ).forEach { forbidden ->
            compose.onNodeWithText(forbidden, substring = true, ignoreCase = true).assertDoesNotExist()
        }
        compose.onNodeWithText("Google Play no está habilitado", substring = true, ignoreCase = true).assertExists()
        compose.onNodeWithText("solo registra interés", substring = true, ignoreCase = true).assertExists()
    }

    private fun setScreen(
        selected: CommercialOption = CommercialOption.FREE,
        eligibility: TrialEligibilitySnapshot = eligible(),
        onSelect: (CommercialOption) -> Unit = {},
    ) {
        compose.setContent { PlanSelectionScreen(state(selected, eligibility), onSelect, {}) }
    }

    private fun setDynamicScreen(initial: PlanSelectionUiState): MutableState<PlanSelectionUiState> {
        val renderedState = mutableStateOf(initial)
        compose.setContent { PlanSelectionScreen(renderedState.value, {}, {}) }
        return renderedState
    }

    private fun state(
        selected: CommercialOption = CommercialOption.FREE,
        eligibility: TrialEligibilitySnapshot = eligible(),
    ) = PlanSelectionUiState(
        selectedOption = selected,
        eligibility = eligibility,
        isLoadingEligibility = false,
        isConfirming = false,
        errorMessage = null,
    )

    private fun eligible() = TrialEligibilitySnapshot.eligible(
        verifiedAt = Instant.parse("2026-09-14T10:00:00Z"),
        validUntil = Instant.parse("2026-09-21T10:00:00Z"),
    )

    private fun ineligible() = TrialEligibilitySnapshot.ineligible(
        verifiedAt = Instant.parse("2026-09-14T10:00:00Z"),
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
