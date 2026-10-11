package com.kipu.app.feature.debts.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.data.local.AccountDao
import com.kipu.app.feature.debts.domain.DebtAmountParser
import com.kipu.app.feature.debts.domain.DebtCommandHasher
import com.kipu.app.feature.debts.domain.DebtRepository
import com.kipu.app.feature.debts.domain.model.DebtCommandIdentity
import com.kipu.app.feature.debts.domain.model.DebtCommandResult
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.feature.debts.domain.model.OpenDebtCommand
import com.kipu.app.feature.plans.data.entitlement.EffectiveEntitlementEvaluator
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheDao
import com.kipu.app.feature.plans.domain.model.OfflineEntitlementLeaseDecision
import dagger.hilt.android.lifecycle.HiltViewModel
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

sealed interface DebtOpeningUiEvent {
    data class Saved(val debtId: String) : DebtOpeningUiEvent
}

@HiltViewModel
class DebtOpeningViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DebtRepository,
    private val accountDao: AccountDao,
    private val sessionCoordinator: SessionCoordinator,
    private val featureAccessCacheDao: FeatureAccessCacheDao,
    private val entitlementEvaluator: EffectiveEntitlementEvaluator,
) : ViewModel() {
    val obligationType: DebtObligationType = runCatching {
        DebtObligationType.valueOf(savedStateHandle.get<String>("obligationType") ?: "PAYABLE")
    }.getOrDefault(DebtObligationType.PAYABLE)

    private val _state = MutableStateFlow(DebtFormUiState())
    val state = _state.asStateFlow()
    private val _events = Channel<DebtOpeningUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        activeUserId()?.let(::observeAccounts)
    }

    fun onCounterpartyNameChange(value: String) = _state.update { it.copy(counterpartyName = value, errorMessage = null) }
    fun onPrincipalAmountChange(value: String) = _state.update { it.copy(principalAmount = value, errorMessage = null) }
    fun onOpeningModeChange(value: DebtOpeningMode) = _state.update { it.copy(openingMode = value, errorMessage = null) }
    fun onDueDateChange(value: String) = _state.update { current ->
        current.copy(
            dueDateInput = value,
            dueDate = value.takeIf(String::isNotBlank)?.let { raw ->
                runCatching { LocalDate.parse(raw) }.getOrNull()
            },
            errorMessage = null,
        )
    }
    fun onNotesChange(value: String) = _state.update { it.copy(notes = value, errorMessage = null) }

    fun onCurrencyChange(currencyCode: String) {
        if (currencyCode !in SUPPORTED_CURRENCIES) return
        _state.update { current ->
            current.copy(
                currencyCode = currencyCode,
                selectedAccountId = current.availableAccounts.firstOrNull { it.currencyCode == currencyCode }?.id,
                errorMessage = null,
            )
        }
    }

    fun onAccountSelected(accountId: String) {
        _state.update { current ->
            val option = current.availableAccounts.firstOrNull { it.id == accountId }
            if (option == null || option.currencyCode != current.currencyCode) current
            else current.copy(selectedAccountId = accountId, errorMessage = null)
        }
    }

    fun save() {
        val current = _state.value
        if (!current.isValidForSave()) return
        val ownerId = activeUserId() ?: run {
            _state.update { it.copy(errorMessage = "Inicia sesión para guardar esta deuda.") }
            return
        }
        val principalMinor = DebtAmountParser.toMinorUnits(current.principalAmount) ?: run {
            _state.update { it.copy(errorMessage = "Ingresa un monto mayor que cero con hasta dos decimales.") }
            return
        }
        _state.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val operationId = UUID.randomUUID().toString()
            val debtId = UUID.randomUUID().toString()
            val canonical = listOf(
                obligationType.name,
                current.counterpartyName.trim().replace(WHITESPACE, " "),
                principalMinor.toString(),
                current.currencyCode,
                current.openedOn.toString(),
                current.openingMode.name,
                current.selectedAccountId.orEmpty(),
                current.dueDate?.toString().orEmpty(),
                current.reminderLeadDays?.toString().orEmpty(),
                current.notes.trim(),
            ).joinToString("|")
            val command = OpenDebtCommand(
                identity = DebtCommandIdentity(operationId, DebtCommandHasher.sha256(canonical)),
                debtId = debtId,
                obligationType = obligationType,
                counterpartyName = current.counterpartyName,
                principalMinor = principalMinor,
                currencyCode = current.currencyCode,
                openedOn = current.openedOn,
                openingMode = current.openingMode,
                accountId = current.selectedAccountId,
                dueDate = current.dueDate,
                reminderLeadDays = current.reminderLeadDays,
                notes = current.notes,
            )
            val result = runCatching {
                repository.openDebt(ownerId, command, hasVerifiedPremium(ownerId))
            }.getOrElse {
                _state.update { it.copy(isSaving = false, errorMessage = "No se pudo guardar. Conservamos los datos para que reintentes.") }
                return@launch
            }
            when (result) {
                is DebtCommandResult.Applied -> {
                    _events.send(DebtOpeningUiEvent.Saved(result.debtId))
                    resetAfterSave()
                }
                is DebtCommandResult.Duplicate -> {
                    _events.send(DebtOpeningUiEvent.Saved(result.debtId))
                    resetAfterSave()
                }
                is DebtCommandResult.Rejected -> _state.update { it.copy(isSaving = false, errorMessage = result.message()) }
                is DebtCommandResult.Conflict -> _state.update { it.copy(isSaving = false, errorMessage = "La deuda cambió en otro dispositivo. Actualiza la lista antes de continuar.") }
                is DebtCommandResult.Retryable -> _state.update { it.copy(isSaving = false, errorMessage = "Guardamos el movimiento en este dispositivo y volveremos a sincronizarlo.") }
            }
        }
    }

    private fun observeAccounts(userId: String) {
        viewModelScope.launch {
            accountDao.observeActive(userId)
                .catch { _state.update { it.copy(errorMessage = "No se pudieron cargar las cuentas.") } }
                .collect { entities ->
                    val accounts = entities
                        .filterNot { it.type in EXCLUDED_ACCOUNT_TYPES }
                        .map { DebtAccountOption(it.id, it.alias, it.currency) }
                    _state.update { current ->
                        val existing = accounts.firstOrNull { it.id == current.selectedAccountId && it.currencyCode == current.currencyCode }
                        current.copy(
                            availableAccounts = accounts,
                            selectedAccountId = existing?.id ?: accounts.firstOrNull { it.currencyCode == current.currencyCode }?.id,
                        )
                    }
                }
        }
    }

    private fun resetAfterSave() {
        _state.update { current ->
            DebtFormUiState(
                currencyCode = current.currencyCode,
                availableAccounts = current.availableAccounts,
                selectedAccountId = current.selectedAccountId,
                openedOn = LocalDate.now(),
            )
        }
    }

    private suspend fun hasVerifiedPremium(userId: String): Boolean {
        val uuid = runCatching { UUID.fromString(userId) }.getOrNull() ?: return false
        val cache = featureAccessCacheDao.get(uuid) ?: return false
        if (!cache.effectiveTier.equals("PREMIUM", ignoreCase = true)) return false
        return entitlementEvaluator.evaluate(userId, cache) is OfflineEntitlementLeaseDecision.Allowed
    }

    private fun activeUserId(): String? =
        (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId

    private fun DebtCommandResult.Rejected.message(): String = when (code) {
        "FREE_DEBT_QUOTA_EXCEEDED" -> "El plan Free permite hasta dos obligaciones activas."
        "ACCOUNT_REQUIRED", "ACCOUNT_NOT_ELIGIBLE", "ACCOUNT_NOT_FOUND" -> "Elige una cuenta activa de la misma moneda."
        "INVALID_AMOUNT_OR_CURRENCY" -> "Revisa el monto y la moneda seleccionada."
        "COUNTERPARTY_REQUIRED" -> "Indica el nombre de la otra persona."
        else -> "No se pudo registrar la deuda ($code)."
    }

    private companion object {
        val SUPPORTED_CURRENCIES = setOf("PEN", "USD")
        val EXCLUDED_ACCOUNT_TYPES = setOf("GOALS_VIRTUAL", "CREDIT_LIABILITY")
        val WHITESPACE = Regex("\\s+")
    }
}
