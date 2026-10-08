package com.kipu.app.feature.debts.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.debts.data.local.DebtDao
import com.kipu.app.feature.debts.domain.DebtCommandHasher
import com.kipu.app.feature.debts.domain.DebtRepository
import com.kipu.app.feature.debts.domain.model.DebtCommandIdentity
import com.kipu.app.feature.debts.domain.model.DebtDeleteResult
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class DebtDetailUiState(
    val debt: DebtSummary? = null,
    val hasFinancialHistory: Boolean = false,
    val activities: List<DebtSettlementActivity> = emptyList(),
    val installments: List<DebtScheduleItem> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

sealed interface DebtDetailUiEvent {
    data object Deleted : DebtDetailUiEvent
}

@HiltViewModel
class DebtDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DebtRepository,
    debtDao: DebtDao,
    private val sessionCoordinator: SessionCoordinator,
) : ViewModel() {
    val debtId: String = savedStateHandle.get<String>("debtId").orEmpty()
    private val _state = MutableStateFlow(DebtDetailUiState())
    val state = _state.asStateFlow()
    private val _events = Channel<DebtDetailUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        val userId = (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId
        if (userId == null || debtId.isBlank()) {
            _state.value = DebtDetailUiState(isLoading = false, errorMessage = "No se encontró esta deuda.")
        } else {
            viewModelScope.launch {
                combine(
                    repository.observeDebt(userId, debtId),
                    debtDao.observeHasFinancialHistory(userId, debtId),
                    debtDao.observeSettlementActivities(userId, debtId),
                    repository.observeInstallments(userId, debtId),
                ) { debt, hasHistory, activities, installments ->
                    DebtDetailUiState(
                        debt = debt,
                        hasFinancialHistory = hasHistory,
                        activities = activities.map { row ->
                            DebtSettlementActivity(
                                eventId = row.eventId,
                                principalMinor = row.principalMinor,
                                interestMinor = row.interestMinor,
                                occurredAt = row.occurredAt,
                                isVoided = row.isVoided,
                                eventType = row.eventType,
                                principalDeltaMinor = row.principalDeltaMinor,
                            )
                        },
                        installments = installments,
                        isLoading = false,
                    )
                }
                    .catch { _state.value = DebtDetailUiState(isLoading = false, errorMessage = "No se pudo cargar el detalle.") }
                    .collect { _state.value = it }
            }
        }
    }

    fun delete() {
        val userId = (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId ?: return
        viewModelScope.launch {
            val operationId = UUID.randomUUID().toString()
            val identity = DebtCommandIdentity(operationId, DebtCommandHasher.sha256("delete:$userId:$debtId"))
            when (val result = repository.deleteDebtIfUnreferenced(userId, debtId, identity)) {
                is DebtDeleteResult.Deleted -> _events.send(DebtDetailUiEvent.Deleted)
                is DebtDeleteResult.Rejected -> _state.value = _state.value.copy(
                    errorMessage = if (result.code == "HISTORY_PRESERVED") {
                        "El saldo pendiente y los movimientos se conservan como parte del historial."
                    } else "No se pudo eliminar la deuda (${result.code}).",
                )
                is DebtDeleteResult.Conflict -> _state.value = _state.value.copy(
                    errorMessage = "La deuda cambió en otro dispositivo. Actualiza antes de eliminarla.",
                )
            }
        }
    }
}
