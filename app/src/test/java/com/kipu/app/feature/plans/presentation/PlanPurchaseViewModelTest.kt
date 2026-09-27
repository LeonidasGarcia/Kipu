package com.kipu.app.feature.plans.presentation

import android.app.Activity
import com.kipu.app.feature.plans.domain.model.BillingProduct
import com.kipu.app.feature.plans.domain.model.BillingProductKind
import com.kipu.app.feature.plans.domain.model.BillingVerificationResult
import com.kipu.app.feature.plans.domain.model.CommercialOption
import com.kipu.app.feature.plans.domain.model.LocalizedBillingOffer
import com.kipu.app.feature.plans.domain.model.GooglePlayPurchaseState
import com.kipu.app.feature.plans.domain.model.PurchaseLifecycle
import com.kipu.app.feature.plans.purchase.BillingPurchaseRepository
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlanPurchaseViewModelTest {
    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Annual is selected by default and catalog price comes from Play`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val repository = FakeRepository(listOf(annualOffer()))
        val viewModel = PlanPurchaseViewModel(repository)
        advanceUntilIdle()

        assertEquals(CommercialOption.ANNUAL, viewModel.uiState.value.selectedOffer?.product?.option)
        assertEquals("S/ 27,10 al año", viewModel.uiState.value.selectedOffer?.formattedPrice)
        assertFalse(viewModel.uiState.value.effectivePremium)
    }

    @Test
    fun `purchase callback alone never unlocks Premium`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val repository = FakeRepository(listOf(annualOffer()))
        val viewModel = PlanPurchaseViewModel(repository)
        advanceUntilIdle()

        viewModel.startPurchase(mockk<Activity>(relaxed = true))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.effectivePremium)
        assertEquals(PlanPurchaseStatus.LAUNCHING, viewModel.uiState.value.status)
    }

    @Test
    fun `pending and retryable remain distinct and retryable preserves verified access`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val repository = FakeRepository(listOf(annualOffer()))
        val viewModel = PlanPurchaseViewModel(repository)
        advanceUntilIdle()

        repository.results.emit(BillingVerificationResult.Verified("kipu_pro_annual", GooglePlayPurchaseState.PURCHASED, PurchaseLifecycle.ACTIVE, 1_900_000_000_000, true, false))
        advanceUntilIdle()
        assertEquals(GooglePlayPurchaseState.PURCHASED, viewModel.uiState.value.providerPurchaseState)
        repository.results.emit(BillingVerificationResult.Retryable("PROVIDER_UNAVAILABLE"))
        advanceUntilIdle()
        assertEquals(PlanPurchaseStatus.RETRYABLE, viewModel.uiState.value.status)
        assertTrue(viewModel.uiState.value.effectivePremium)

        repository.results.emit(BillingVerificationResult.PaymentPending("kipu_pro_annual"))
        advanceUntilIdle()
        assertEquals(PlanPurchaseStatus.PENDING, viewModel.uiState.value.status)
        assertEquals(GooglePlayPurchaseState.PENDING, viewModel.uiState.value.providerPurchaseState)
        assertTrue(viewModel.uiState.value.effectivePremium)
    }

    @Test
    fun `pending payment from a Free account never grants Premium`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val repository = FakeRepository(listOf(annualOffer()))
        val viewModel = PlanPurchaseViewModel(repository)
        advanceUntilIdle()

        repository.results.emit(BillingVerificationResult.PaymentPending("kipu_pro_annual"))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.effectivePremium)
        assertEquals(GooglePlayPurchaseState.PENDING, viewModel.uiState.value.providerPurchaseState)
    }

    private fun annualOffer() = LocalizedBillingOffer(
        product = BillingProduct("annual", "kipu_pro_annual", "annual", "Kipu Pro Anual", CommercialOption.ANNUAL, BillingProductKind.SUBSCRIPTION),
        formattedPrice = "S/ 27,10 al año",
        currencyCode = "PEN",
        offerToken = "play-offer-token",
        trialPeriodDays = null,
        renewalPeriod = "P1Y",
    )

    private class FakeRepository(offers: List<LocalizedBillingOffer>) : BillingPurchaseRepository {
        val results = MutableSharedFlow<BillingVerificationResult>(extraBufferCapacity = 8)
        private val catalog = offers
        override val purchaseResults = results
        override suspend fun loadOffers() = catalog
        override suspend fun startPurchase(activity: Activity, offer: LocalizedBillingOffer) = Unit
        override suspend fun refreshPurchases() = Unit
    }
}
