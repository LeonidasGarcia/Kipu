package com.kipu.app.feature.debts.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.debts.data.local.DebtDao
import com.kipu.app.feature.debts.domain.DebtRepository
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import com.kipu.app.feature.debts.domain.model.DebtSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DebtListUiState(
    val debts: List<DebtSummary> = emptyList(),
    val scheduledInstallments: Map<String, DebtScheduleItem> = emptyMap(),
    val selectedType: DebtObligationType? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class DebtListViewModel @Inject constructor(
    repository: DebtRepository,
    debtDao: DebtDao,
    sessionCoordinator: SessionCoordinator,
) : ViewModel() {
    private val _state = MutableStateFlow(DebtListUiState())
    val state = _state.asStateFlow()

    init {
        val userId = (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId
        if (userId == null) {
            _state.value = DebtListUiState(isLoading = false, errorMessage = "Inicia sesión para consultar tus deudas.")
        } else {
            viewModelScope.launch {
                combine(
                    repository.observeDebts(userId),
                    debtDao.observeInstallmentHighlights(userId),
                ) { debts, installments ->
                    val highlights = installments.groupBy { it.debtId }.mapValues { (_, items) ->
                        val selected = items.firstOrNull { it.status == "PENDING" || it.status == "PARTIAL" }
                            ?: items.lastOrNull { it.status == "PAID" }
                        selected?.let { installment ->
                            runCatching {
                                DebtScheduleItem(
                                    id = installment.id,
                                    installmentNumber = installment.installmentNumber,
                                    dueDate = java.time.LocalDate.parse(installment.dueDate),
                                    principalMinor = installment.amountMinor,
                                    status = installment.status,
                                    revision = installment.revision,
                                )
                            }.getOrNull()
                        }
                    }.mapNotNull { (debtId, installment) -> installment?.let { debtId to it } }.toMap()
                    debts to highlights
                }
                    .catch { _state.update { it.copy(isLoading = false, errorMessage = "No se pudieron cargar tus deudas.") } }
                    .collect { (debts, highlights) ->
                        _state.update {
                            it.copy(debts = debts, scheduledInstallments = highlights, isLoading = false, errorMessage = null)
                        }
                    }
            }
        }
    }

    fun selectType(type: DebtObligationType?) = _state.update { it.copy(selectedType = type) }
}
