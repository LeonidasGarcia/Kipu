package com.kipu.app.feature.plans.presentation

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.feature.plans.domain.model.BillingVerificationResult
import com.kipu.app.feature.plans.domain.model.CommercialOption
import com.kipu.app.feature.plans.purchase.BillingPurchaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PlanPurchaseViewModel @Inject constructor(
    private val repository: BillingPurchaseRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PlanPurchaseUiState())
    val uiState = mutableState.asStateFlow()

    init {
        viewModelScope.launch { repository.purchaseResults.collect(::applyVerificationResult) }
        refreshCatalog()
    }

    fun selectOffer(productId: String) {
        mutableState.update { state ->
            if (state.offers.any { it.product.storeProductId == productId }) {
                state.copy(selectedProductId = productId, status = if (state.effectivePremium) PlanPurchaseStatus.VERIFIED else PlanPurchaseStatus.READY, errorCode = null)
            } else state
        }
    }

    fun startPurchase(activity: Activity) {
        val state = mutableState.value
        val offer = state.selectedOffer ?: run {
            mutableState.update { it.copy(status = PlanPurchaseStatus.UNAVAILABLE) }
            return
        }
        if (state.status == PlanPurchaseStatus.LAUNCHING || state.status == PlanPurchaseStatus.VERIFYING) return
        mutableState.update { it.copy(status = PlanPurchaseStatus.LAUNCHING, errorCode = null) }
        viewModelScope.launch {
            runCatching { repository.startPurchase(activity, offer) }
                .onFailure { mutableState.update { current -> current.copy(status = PlanPurchaseStatus.UNAVAILABLE, errorCode = "PLAY_UNAVAILABLE") } }
        }
    }

    fun retryVerification() {
        mutableState.update { it.copy(status = PlanPurchaseStatus.VERIFYING, errorCode = null) }
        viewModelScope.launch {
            runCatching { repository.refreshPurchases() }
                .onFailure { mutableState.update { state -> state.copy(status = PlanPurchaseStatus.RETRYABLE, errorCode = "UNAVAILABLE") } }
        }
    }

    fun refreshCatalog() {
        mutableState.update { it.copy(status = PlanPurchaseStatus.LOADING, errorCode = null) }
        viewModelScope.launch {
            val offers = runCatching { repository.loadOffers() }.getOrDefault(emptyList())
            if (offers.isEmpty()) {
                mutableState.update { it.copy(offers = emptyList(), selectedProductId = null, status = PlanPurchaseStatus.UNAVAILABLE) }
                return@launch
            }
            val annual = offers.firstOrNull { it.product.option == CommercialOption.ANNUAL }
            val selected = annual ?: offers.first()
            mutableState.update { state ->
                state.copy(
                    offers = offers.sortedBy { offer -> when (offer.product.option) { CommercialOption.ANNUAL -> 0; CommercialOption.MONTHLY -> 1; CommercialOption.LIFETIME -> 2; CommercialOption.FREE -> 3 } },
                    selectedProductId = state.selectedProductId?.takeIf { id -> offers.any { it.product.storeProductId == id } } ?: selected.product.storeProductId,
                    status = if (state.effectivePremium) PlanPurchaseStatus.VERIFIED else PlanPurchaseStatus.READY,
                )
            }
        }
    }

    private fun applyVerificationResult(result: BillingVerificationResult) {
        mutableState.update { state -> PlanPurchaseUiState.forResult(state, result) }
    }
}
