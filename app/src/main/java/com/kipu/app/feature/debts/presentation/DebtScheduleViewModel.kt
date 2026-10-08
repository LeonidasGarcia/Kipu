package com.kipu.app.feature.debts.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.debts.domain.DebtAmountParser
import com.kipu.app.feature.debts.domain.DebtCommandHasher
import com.kipu.app.feature.debts.domain.DebtRepository
import com.kipu.app.feature.debts.domain.model.CloseDebtAction
import com.kipu.app.feature.debts.domain.model.DebtClosureCommand
import com.kipu.app.feature.debts.domain.model.DebtCommandIdentity
import com.kipu.app.feature.debts.domain.model.DebtCommandResult
import com.kipu.app.feature.debts.domain.model.DebtPlanResult
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.feature.debts.domain.model.SetDebtScheduleCommand
import com.kipu.app.feature.debts.domain.usecase.SetDebtSchedule
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DebtScheduleUiState(
    val debt: DebtSummary? = null,
    val installments: List<DebtScheduleItem> = emptyList(),
    val installmentCount: String = "3",
    val firstDueDate: String = LocalDate.now().plusMonths(1).toString(),
    val reminderLeadDays: Int? = null,
    val adjustmentAmount: String = "",
    val forgivenessAmount: String = "",
    val closureReason: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

sealed interface DebtScheduleUiEvent {
    data object Closed : DebtScheduleUiEvent
}

@HiltViewModel
class DebtScheduleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DebtRepository,
    private val sessionCoordinator: SessionCoordinator,
) : ViewModel() {
    val debtId: String = savedStateHandle.get<String>("debtId").orEmpty()
    private val _state = MutableStateFlow(DebtScheduleUiState())
    val state = _state.asStateFlow()
    private val _events = Channel<DebtScheduleUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        val userId = activeUserId()
        if (userId == null || debtId.isBlank()) {
            _state.value = DebtScheduleUiState(isLoading = false, errorMessage = "No se encontr\u00f3 esta deuda.")
        } else {
            viewModelScope.launch {
                combine(repository.observeDebt(userId, debtId), repository.observeInstallments(userId, debtId)) { debt, installments ->
                    debt to installments
                }.catch {
                    _state.update { it.copy(isLoading = false, errorMessage = "No se pudo cargar el cronograma.") }
                }.collect { (debt, installments) ->
                    _state.update { current ->
                        current.copy(
                            debt = debt,
                            installments = installments,
                            reminderLeadDays = current.reminderLeadDays ?: debt?.reminderLeadDays,
                            isLoading = false,
                            errorMessage = if (debt == null) "No se encontr\u00f3 esta deuda." else current.errorMessage,
                        )
                    }
                }
            }
        }
    }

    fun onInstallmentCountChange(value: String) = _state.update { it.copy(installmentCount = value.filter(Char::isDigit).take(3), errorMessage = null) }
    fun onFirstDueDateChange(value: String) = _state.update { it.copy(firstDueDate = value.take(10), errorMessage = null) }
    fun onReminderLeadDaysChange(value: Int?) = _state.update { it.copy(reminderLeadDays = value, errorMessage = null) }
    fun onAdjustmentAmountChange(value: String) = _state.update { it.copy(adjustmentAmount = value.take(16), errorMessage = null) }
    fun onForgivenessAmountChange(value: String) = _state.update { it.copy(forgivenessAmount = value.take(16), errorMessage = null) }
    fun onClosureReasonChange(value: String) = _state.update { it.copy(closureReason = value.take(500), errorMessage = null) }

    fun saveSchedule() {
        val userId = activeUserId() ?: return showError("La sesi\u00f3n no est\u00e1 disponible.")
        val current = _state.value
        val debt = current.debt ?: return showError("No se encontr\u00f3 esta deuda.")
        val count = current.installmentCount.toIntOrNull()
            ?: return showError("Ingresa una cantidad de cuotas v\u00e1lida.")
        val firstDueDate = runCatching { LocalDate.parse(current.firstDueDate) }.getOrNull()
            ?: return showError("Usa una fecha v\u00e1lida con formato AAAA-MM-DD.")
        val operationId = UUID.randomUUID().toString()
        val firstNumber = (current.installments.maxOfOrNull { it.installmentNumber } ?: 0) + 1
        val plan = SetDebtSchedule().plan(debt, count, firstDueDate, current.reminderLeadDays, firstNumber, operationId)
        if (plan !is DebtPlanResult.Valid) return showError(scheduleError((plan as DebtPlanResult.Invalid).code))
        val hash = DebtCommandHasher.sha256(
            "SET_DEBT_SCHEDULE|1|$userId|${debt.debtId}|${debt.revision}|$count|$firstDueDate|${current.reminderLeadDays}|$operationId",
        )
        val command = SetDebtScheduleCommand(
            identity = DebtCommandIdentity(operationId, hash),
            debtId = debt.debtId,
            expectedRevision = debt.revision,
            installments = plan.value.installments,
            reminderLeadDays = current.reminderLeadDays,
        )
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null, successMessage = null) }
            handleScheduleResult(repository.setDebtSchedule(userId, command))
        }
    }

    fun cancelSchedule() {
        val userId = activeUserId() ?: return showError("La sesi\u00f3n no est\u00e1 disponible.")
        val debt = _state.value.debt ?: return showError("No se encontr\u00f3 esta deuda.")
        val plan = SetDebtSchedule().cancel(debt)
        if (plan !is DebtPlanResult.Valid) return showError(scheduleError((plan as DebtPlanResult.Invalid).code))
        val operationId = UUID.randomUUID().toString()
        val command = SetDebtScheduleCommand(
            identity = DebtCommandIdentity(
                operationId,
                DebtCommandHasher.sha256("CANCEL_DEBT_SCHEDULE|1|$userId|${debt.debtId}|${debt.revision}|$operationId"),
            ),
            debtId = debt.debtId,
            expectedRevision = debt.revision,
            installments = plan.value.installments,
            reminderLeadDays = null,
            cancelSchedule = true,
        )
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null, successMessage = null) }
            handleScheduleResult(repository.setDebtSchedule(userId, command))
        }
    }

    fun closeDebt(action: CloseDebtAction, reason: String) {
        val userId = activeUserId() ?: return showError("La sesi\u00f3n no est\u00e1 disponible.")
        val current = _state.value
        val debt = current.debt ?: return showError("No se encontr\u00f3 esta deuda.")
        val amount = when (action) {
            CloseDebtAction.ADJUST -> parseSignedMinor(current.adjustmentAmount)
            CloseDebtAction.FORGIVE -> DebtAmountParser.toMinorUnits(current.forgivenessAmount)
            else -> null
        }
        if (action == CloseDebtAction.ADJUST && amount == null) return showError("Ingresa un ajuste distinto de cero.")
        if (action == CloseDebtAction.FORGIVE && amount == null) return showError("Ingresa un monto v\u00e1lido para condonar.")
        val operationId = UUID.randomUUID().toString()
        val canonical = "CLOSE_DEBT|1|$userId|${debt.debtId}|${debt.revision}|${action.name}|${amount ?: 0}|${reason.trim()}|$operationId"
        val command = DebtClosureCommand(
            identity = DebtCommandIdentity(operationId, DebtCommandHasher.sha256(canonical)),
            debtId = debt.debtId,
            expectedRevision = debt.revision,
            action = action,
            amountMinor = amount.takeIf { action == CloseDebtAction.FORGIVE },
            principalDeltaMinor = amount.takeIf { action == CloseDebtAction.ADJUST },
            reason = reason.trim().takeIf(String::isNotEmpty),
        )
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null, successMessage = null) }
            when (val result = repository.closeDebt(userId, command)) {
                is DebtCommandResult.Applied, is DebtCommandResult.Duplicate -> _events.send(DebtScheduleUiEvent.Closed)
                is DebtCommandResult.Conflict -> showError("La deuda cambi\u00f3 en otro dispositivo. Actualiza antes de continuar.")
                is DebtCommandResult.Rejected -> showError(closureError(result.code))
                is DebtCommandResult.Retryable -> showError("Se guard\u00f3 localmente; se sincronizar\u00e1 al recuperar la conexi\u00f3n.")
            }
            _state.update { it.copy(isSaving = false) }
        }
    }

    private suspend fun handleScheduleResult(result: DebtCommandResult) {
        when (result) {
            is DebtCommandResult.Applied, is DebtCommandResult.Duplicate ->
                _state.update { it.copy(isSaving = false, successMessage = "Cronograma guardado sin registrar pagos.") }
            is DebtCommandResult.Conflict -> showError("La deuda cambi\u00f3 en otro dispositivo. Actualiza el cronograma.")
            is DebtCommandResult.Rejected -> showError(scheduleError(result.code))
            is DebtCommandResult.Retryable -> _state.update { it.copy(isSaving = false, successMessage = "Guardado en este dispositivo; se sincronizar\u00e1 al recuperar la conexi\u00f3n.") }
        }
    }

    private fun activeUserId(): String? =
        (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId

    private fun showError(message: String) = _state.update { it.copy(isSaving = false, errorMessage = message) }

    private fun scheduleError(code: String): String = when (code) {
        "DEBT_NOT_ACTIVE" -> "Solo puedes planificar cuotas para una deuda activa."
        "SCHEDULE_TOTAL_MISMATCH" -> "Las cuotas deben sumar exactamente el saldo pendiente."
        "INVALID_REMINDER_LEAD_DAYS" -> "El anticipo del recordatorio debe estar entre 0 y 365 d\u00edas."
        else -> "No se pudo guardar el cronograma ($code)."
    }

    private fun closureError(code: String): String = when (code) {
        "BALANCE_REMAINS" -> "El saldo pendiente debe ser cero antes de marcarla liquidada."
        "REASON_REQUIRED" -> "Escribe el motivo para conservar una auditor\u00eda del cierre."
        "ADJUSTMENT_EXCEEDS_BALANCE" -> "El ajuste no puede dejar un saldo negativo."
        "FORGIVENESS_EXCEEDS_BALANCE" -> "La condonaci\u00f3n no puede superar el saldo pendiente."
        else -> "No se pudo cerrar la deuda ($code)."
    }

    private fun parseSignedMinor(input: String): Long? {
        val trimmed = input.trim()
        val negative = trimmed.startsWith('-')
        val magnitude = DebtAmountParser.toMinorUnits(trimmed.removePrefix("-").removePrefix("+")) ?: return null
        return if (negative) -magnitude else magnitude
    }
}
