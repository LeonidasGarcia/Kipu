package com.kipu.app.feature.debts.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.debts.domain.DebtCommandHasher
import com.kipu.app.feature.debts.domain.DebtRepository
import com.kipu.app.feature.debts.domain.model.DebtCommandIdentity
import com.kipu.app.feature.debts.domain.model.DebtDescriptionPatch
import com.kipu.app.feature.debts.domain.model.DebtDetailsEditResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DebtEditUiEvent {
    data object Saved : DebtEditUiEvent
}

@HiltViewModel
class DebtEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DebtRepository,
    private val sessionCoordinator: SessionCoordinator,
) : ViewModel() {
    val debtId = savedStateHandle.get<String>("debtId").orEmpty()
    private val _state = MutableStateFlow(DebtEditUiState(isLoading = true))
    val state = _state.asStateFlow()
    private val _events = Channel<DebtEditUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        val userId = activeUserId()
        if (userId == null || debtId.isBlank()) {
            _state.value = DebtEditUiState(isLoading = false, errorMessage = "No se encontró esta deuda.")
        } else {
            viewModelScope.launch {
                repository.observeDebt(userId, debtId)
                    .catch { _state.update { it.copy(isLoading = false, errorMessage = "No se pudo cargar la deuda.") } }
                    .collect { debt ->
                        _state.update { current ->
                            if (debt == null) current.copy(isLoading = false, errorMessage = "No se encontró esta deuda.")
                            else current.copy(
                                counterpartyName = debt.counterpartyName,
                                principalAmount = formatAmount(debt.principalMinor, debt.currencyCode),
                                currencyCode = debt.currencyCode,
                                dueDate = debt.dueDate?.toString().orEmpty(),
                                notes = debt.notes.orEmpty(),
                                revision = debt.revision,
                                isLoading = false,
                                errorMessage = null,
                            )
                        }
                    }
            }
        }
    }

    fun onCounterpartyNameChange(value: String) = _state.update { it.copy(counterpartyName = value, errorMessage = null) }
    fun onDueDateChange(value: String) = _state.update { it.copy(dueDate = value, errorMessage = null) }
    fun onNotesChange(value: String) = _state.update { it.copy(notes = value, errorMessage = null) }

    fun save() {
        val current = _state.value
        val userId = activeUserId() ?: return
        if (current.revision <= 0L || current.counterpartyName.isBlank()) return
        val dueDate = current.dueDate.takeIf(String::isNotBlank)?.let { raw ->
            runCatching { LocalDate.parse(raw) }.getOrElse {
                _state.update { it.copy(errorMessage = "Usa una fecha válida con formato AAAA-MM-DD.") }
                return
            }
        }
        _state.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val operationId = UUID.randomUUID().toString()
            val canonical = "edit:$userId:$debtId:${current.revision}:${current.counterpartyName.trim()}:${dueDate ?: ""}:${current.notes.trim()}"
            val result = runCatching {
                repository.editDebtDetails(
                    userId = userId,
                    debtId = debtId,
                    patch = DebtDescriptionPatch(
                        counterpartyName = current.counterpartyName,
                        dueDate = dueDate,
                        notes = current.notes,
                        expectedRevision = current.revision,
                    ),
                    identity = DebtCommandIdentity(operationId, DebtCommandHasher.sha256(canonical)),
                )
            }.getOrElse {
                _state.update { it.copy(isSaving = false, errorMessage = "No se pudieron guardar los cambios.") }
                return@launch
            }
            when (result) {
                is DebtDetailsEditResult.Applied -> _events.send(DebtEditUiEvent.Saved)
                is DebtDetailsEditResult.Conflict -> _state.update {
                    it.copy(isSaving = false, errorMessage = "La deuda cambió en otro dispositivo. Vuelve a abrir la edición.")
                }
                is DebtDetailsEditResult.Rejected -> _state.update {
                    it.copy(isSaving = false, errorMessage = "No se guardaron los cambios (${result.code}).")
                }
            }
        }
    }

    private fun activeUserId(): String? =
        (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId

    private fun formatAmount(minor: Long, currency: String): String {
        val major = BigDecimal.valueOf(minor, 2).setScale(2, RoundingMode.UNNECESSARY).toPlainString()
        val symbol = if (currency == "PEN") "S/" else "$"
        return "$symbol $major"
    }
}
