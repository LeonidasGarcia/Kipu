package com.kipu.app.feature.plans.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.feature.plans.domain.*
import com.kipu.app.feature.plans.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
class PlanSelectionViewModel @Inject constructor(private val repository: PlanPreferencesRepository, private val confirmPlanSelection: ConfirmPlanSelection) : ViewModel() {
    constructor(repository: PlanPreferencesRepository, @Suppress("UNUSED_PARAMETER") state: SavedStateHandle) : this(repository, ConfirmPlanSelection(repository, OperationIdProvider(UUID::randomUUID)))
    private val mutableState = MutableStateFlow(PlanSelectionUiState())
    val uiState: StateFlow<PlanSelectionUiState> = mutableState.asStateFlow()
    private val eventChannel = Channel<PlanSelectionEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    init { viewModelScope.launch { val value = runCatching { repository.getTrialEligibility() }.getOrDefault(TrialEligibilitySnapshot.UNKNOWN); mutableState.update { it.copy(eligibility = value, isLoadingEligibility = false) } } }
    fun onOptionSelected(option: CommercialOption) = mutableState.update { it.copy(selectedOption = option, error = null, errorMessage = null) }
    fun selectOption(option: CommercialOption) = onOptionSelected(option)
    fun onAbandoned() = Unit
    fun confirmSelection() = confirm(mutableState.value.selectedOption)
    fun continueWithFree() = confirm(CommercialOption.FREE)
    private fun confirm(option: CommercialOption) {
        if (mutableState.value.isConfirming) return
        val confirmation = mutableState.value
        mutableState.update { it.copy(isConfirming = true, error = null, errorMessage = null) }
        viewModelScope.launch {
            runCatching { confirmPlanSelection(option, confirmation.eligibility) }
                .onSuccess { eventChannel.send(PlanSelectionEvent.Confirmed) }
                .onFailure { mutableState.update { state -> state.copy(error = PlanSelectionError.CONFIRMATION_FAILED, errorMessage = "No se pudo confirmar el plan") } }
            mutableState.update { it.copy(isConfirming = false) }
        }
    }
}
