package com.kipu.app.feature.plans.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.plans.domain.model.BillingProduct
import com.kipu.app.feature.plans.domain.model.BillingProductKind
import com.kipu.app.feature.plans.domain.model.CommercialOption
import com.kipu.app.feature.plans.domain.model.LocalizedBillingOffer
import com.kipu.app.feature.plans.domain.model.PurchaseLifecycle
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlanPurchaseScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rendersLocalizedPlayPricesAnnualBadgeAndLifetimeOneTimeTerms() {
        setScreen()

        compose.onNodeWithText("S/ 29,99 al año").assertIsDisplayed()
        compose.onNodeWithText("S/ 4,99 al mes").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("S/ 49,99 pago único").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Más popular").assertExists()
        compose.onNodeWithText("Pago único para siempre").assertExists()
        compose.onNodeWithText("Un solo pago. Sin renovación y sin periodo de prueba.").assertExists()
    }

    @Test
    fun unavailableCatalogHasOneRetryActionAndNoPurchaseButton() {
        compose.setContent {
            KipuTheme {
                PlanPurchaseScreen(
                    state = PlanPurchaseUiState(status = PlanPurchaseStatus.UNAVAILABLE),
                    onOfferSelected = {}, onPurchase = {}, onRetryVerification = {}, onReloadCatalog = {},
                    onManageSubscriptions = {}, onNavigateBack = {},
                )
            }
        }
        compose.onNodeWithTag("purchase-unavailable").assertIsDisplayed()
        compose.onNodeWithText("Volver a cargar ofertas").assertHasClickAction()
        compose.onNodeWithTag("purchase-cta").assertDoesNotExist()
    }

    @Test
    fun selectedSemanticsAndAllPurchaseActionsMeetFortyEightDpTouchTarget() {
        var selected = "kipu_pro_annual"
        val state = mutableStateOf(readyState(selected))
        var manageCount = 0
        compose.setContent {
            KipuTheme {
                PlanPurchaseScreen(
                    state = state.value,
                    onOfferSelected = { id -> selected = id; state.value = readyState(id) },
                    onPurchase = {}, onRetryVerification = {}, onReloadCatalog = {},
                    onManageSubscriptions = { manageCount++ }, onNavigateBack = {},
                )
            }
        }

        compose.onNodeWithTag("purchase-option-annual").assertIsSelected()
        compose.onNodeWithTag("purchase-option-monthly").assertIsNotSelected().performScrollTo().performClick()
        compose.runOnIdle { assertEquals("kipu_pro_monthly", selected) }
        compose.onNodeWithTag("purchase-option-monthly").assertIsSelected()
        listOf("purchase-option-annual", "purchase-option-monthly", "purchase-option-lifetime", "purchase-cta", "purchase-manage-link")
            .forEach { tag ->
                val bounds = compose.onNodeWithTag(tag).performScrollTo().assertHasClickAction().fetchSemanticsNode().boundsInRoot
                assertTrue("$tag must be at least 48dp high", with(compose.density) { bounds.height.toDp() >= 48.dp })
            }
        compose.onNodeWithTag("purchase-manage-link").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, manageCount) }
    }

    @Test
    fun trialDateAppearsOnlyWhenGooglePlayReturnedAnEligibleTrial() {
        compose.setContent {
            KipuTheme {
                PlanPurchaseScreen(
                    state = readyState("kipu_pro_annual", annualTrialDays = 7),
                    onOfferSelected = {}, onPurchase = {}, onRetryVerification = {}, onReloadCatalog = {},
                    onManageSubscriptions = {}, onNavigateBack = {},
                )
            }
        }
        compose.onNodeWithText("Prueba ofrecida por Google Play para esta cuenta.").assertExists()
        compose.onNodeWithText("Primer cobro estimado:", substring = true).assertExists()
    }

    @Test
    fun paymentPendingAndVerificationRetryableHaveDifferentCopyAndKeepPriorAccess() {
        val state = mutableStateOf(readyState("kipu_pro_annual").copy(
            status = PlanPurchaseStatus.RETRYABLE,
            effectivePremium = true,
            lifecycle = PurchaseLifecycle.ACTIVE,
        ))
        compose.setContent {
            KipuTheme {
                PlanPurchaseScreen(
                    state = state.value,
                    onOfferSelected = {}, onPurchase = {}, onRetryVerification = {}, onReloadCatalog = {},
                    onManageSubscriptions = {}, onNavigateBack = {},
                )
            }
        }
        compose.onNodeWithText("No pudimos confirmar el resultado ahora. Se conserva el acceso que ya tenías. Puedes volver a intentar.").assertIsDisplayed()
        compose.onNodeWithText("Reintentar verificación").assertHasClickAction()

        compose.runOnIdle {
            state.value = state.value.copy(status = PlanPurchaseStatus.PENDING)
        }
        compose.onNodeWithText("Completa el pago en Google Play. Premium se habilitará cuando Google confirme el cobro.").assertIsDisplayed()
        compose.onNodeWithText("Reintentar verificación").assertDoesNotExist()
    }

    @Test
    fun verifiedSubscriptionAndLifetimeLifecycleStatesHaveClearDisclosure() {
        val state = mutableStateOf(readyState("kipu_pro_annual"))
        compose.setContent {
            KipuTheme {
                PlanPurchaseScreen(
                    state = state.value,
                    onOfferSelected = {}, onPurchase = {}, onRetryVerification = {}, onReloadCatalog = {},
                    onManageSubscriptions = {}, onNavigateBack = {},
                )
            }
        }
        val disclosures = listOf(
            PurchaseLifecycle.ACTIVE to "Premium activo",
            PurchaseLifecycle.IN_GRACE_PERIOD to "Periodo de gracia: Google Play está intentando procesar el pago.",
            PurchaseLifecycle.ACCOUNT_HOLD to "La cuenta está en pausa por un problema de pago y no tiene acceso Premium.",
            PurchaseLifecycle.CANCELED_ACTIVE to "No se renovará. Conservas Premium hasta",
            PurchaseLifecycle.EXPIRED to "Premium venció el",
            PurchaseLifecycle.REVOKED to "Google Play revocó esta compra. No concede acceso.",
            PurchaseLifecycle.PAUSED to "La suscripción está pausada y no concede acceso.",
        )
        disclosures.forEach { (lifecycle, text) ->
            compose.runOnIdle {
                state.value = readyState("kipu_pro_annual").copy(
                    status = PlanPurchaseStatus.VERIFIED,
                    effectivePremium = lifecycle in setOf(PurchaseLifecycle.ACTIVE, PurchaseLifecycle.IN_GRACE_PERIOD, PurchaseLifecycle.CANCELED_ACTIVE),
                    lifecycle = lifecycle,
                    expiresAtEpochMillis = System.currentTimeMillis() + 86_400_000,
                )
            }
            compose.onNodeWithText(text, substring = true).assertIsDisplayed()
        }
        compose.runOnIdle {
            state.value = readyState("kipu_pro_lifetime").copy(
                status = PlanPurchaseStatus.VERIFIED,
                effectivePremium = true,
                lifecycle = PurchaseLifecycle.ACTIVE,
                expiresAtEpochMillis = null,
            )
        }
        compose.onNodeWithText("Kipu Lifetime está activo para siempre.").assertIsDisplayed()
    }

    @Test
    fun processingToVerifiedKeepsStatusCardAnchoredAndPriceShimmerReservesSpace() {
        val state = mutableStateOf(readyState("kipu_pro_annual").copy(status = PlanPurchaseStatus.VERIFYING))
        compose.setContent {
            KipuTheme {
                PlanPurchaseScreen(
                    state = state.value,
                    onOfferSelected = {}, onPurchase = {}, onRetryVerification = {}, onReloadCatalog = {},
                    onManageSubscriptions = {}, onNavigateBack = {}, reduceMotion = true,
                )
            }
        }
        val before = compose.onNodeWithTag("purchase-status-verifying").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle {
            state.value = state.value.copy(status = PlanPurchaseStatus.VERIFIED, effectivePremium = true)
        }
        val after = compose.onNodeWithTag("purchase-status-verified").fetchSemanticsNode().boundsInRoot.top
        assertEquals(before, after, 1f)
        compose.onNodeWithText("Compra verificada").assertIsDisplayed()

        compose.runOnIdle { state.value = PlanPurchaseUiState(status = PlanPurchaseStatus.LOADING) }
        val shimmer = compose.onNodeWithTag("purchase-price-shimmer").fetchSemanticsNode().size
        assertEquals(132.dp, with(compose.density) { shimmer.width.toDp() })
        assertEquals(24.dp, with(compose.density) { shimmer.height.toDp() })
        compose.mainClock.advanceTimeBy(1000)
        val afterAnimation = compose.onNodeWithTag("purchase-price-shimmer").fetchSemanticsNode().size
        assertEquals(shimmer, afterAnimation)
    }

    @Test
    fun lifetimeNeverRendersSubscriptionTrialDisclosure() {
        setScreen()
        compose.onAllNodes(hasText("Prueba ofrecida por Google Play para esta cuenta.")).assertCountEquals(0)
    }

    private fun setScreen() {
        compose.setContent {
            KipuTheme {
                PlanPurchaseScreen(
                    state = readyState("kipu_pro_annual"),
                    onOfferSelected = {}, onPurchase = {}, onRetryVerification = {}, onReloadCatalog = {},
                    onManageSubscriptions = {}, onNavigateBack = {},
                )
            }
        }
    }

    private fun readyState(selected: String, annualTrialDays: Int? = null) = PlanPurchaseUiState(
        offers = listOf(annualOffer(annualTrialDays), monthlyOffer(), lifetimeOffer()),
        selectedProductId = selected,
        status = PlanPurchaseStatus.READY,
    )

    private fun annualOffer(trialDays: Int? = null) = offer(
        "annual", "kipu_pro_annual", "annual", "Plan anual", CommercialOption.ANNUAL,
        BillingProductKind.SUBSCRIPTION, "S/ 29,99 al año", trialDays, "P1Y",
    )

    private fun monthlyOffer() = offer(
        "monthly", "kipu_pro_monthly", "monthly", "Plan mensual", CommercialOption.MONTHLY,
        BillingProductKind.SUBSCRIPTION, "S/ 4,99 al mes", null, "P1M",
    )

    private fun lifetimeOffer() = offer(
        "lifetime", "kipu_pro_lifetime", null, "Kipu Lifetime", CommercialOption.LIFETIME,
        BillingProductKind.ONE_TIME, "S/ 49,99 pago único", null, null,
    )

    private fun offer(
        id: String,
        storeId: String,
        basePlanId: String?,
        name: String,
        option: CommercialOption,
        kind: BillingProductKind,
        price: String,
        trialDays: Int?,
        renewal: String?,
    ) = LocalizedBillingOffer(
        product = BillingProduct(id, storeId, basePlanId, name, option, kind),
        formattedPrice = price,
        currencyCode = "PEN",
        offerToken = if (kind == BillingProductKind.SUBSCRIPTION) "play-offer-$id" else null,
        trialPeriodDays = trialDays,
        renewalPeriod = renewal,
    )
}
