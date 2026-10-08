package com.kipu.app.feature.debts.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.data.local.AccountDao
import com.kipu.app.feature.categories.data.local.CategoryDao
import com.kipu.app.feature.debts.domain.DebtAmountParser
import com.kipu.app.feature.debts.domain.DebtCommandHasher
import com.kipu.app.feature.debts.domain.DebtRepository
import com.kipu.app.feature.debts.domain.model.DebtCommandIdentity
import com.kipu.app.feature.debts.domain.model.DebtCommandResult
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.feature.debts.domain.model.SettleDebtCommand
import dagger.hilt.android.lifecycle.HiltViewModel
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

data class DebtCategoryOption(
    val id: String,
    val name: String,
    val categoryType: String = "EXPENSE",
)

data class DebtSettlementUiState(
    val debt: DebtSummary? = null,
    val principalAmount: String = "",
    val interestAmount: String = "0",
    val selectedAccountId: String? = null,
    val selectedInterestCategoryId: String? = null,
    val availableAccounts: List<DebtAccountOption> = emptyList(),
    val availableInterestCategories: List<DebtCategoryOption> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val conflictMessage: String? = null,
    val errorMessage: String? = null,
)

sealed interface DebtSettlementUiEvent {
    data object Settled : DebtSettlementUiEvent
}

@HiltViewModel
class DebtSettlementViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DebtRepository,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val sessionCoordinator: SessionCoordinator,
) : ViewModel() {
    val debtId: String = savedStateHandle.get<String>("debtId").orEmpty()
    private val _state = MutableStateFlow(DebtSettlementUiState())
    val state = _state.asStateFlow()
    private val _events = Channel<DebtSettlementUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        val userId = activeUserId()
        if (userId == null || debtId.isBlank()) {
            _state.value = DebtSettlementUiState(isLoading = false, errorMessage = "No se encontró esta deuda.")
        } else {
            viewModelScope.launch {
                combine(
                    repository.observeDebt(userId, debtId),
                    accountDao.observeActive(userId),
                    categoryDao.observeCategoriesForUser(userId),
                    categoryDao.observePresentationsForUser(userId),
                ) { debt, accountEntities, categories, presentations ->
                    val type = debt?.obligationType
                    val validTypes = if (type == DebtObligationType.PAYABLE) setOf("GENERAL", "EXPENSE")
                        else setOf("GENERAL", "INCOME")
                    val presentationById = presentations.associateBy { it.categoryId }
                    val accounts = accountEntities
                        .filterNot { it.type in EXCLUDED_ACCOUNT_TYPES }
                        .filter { debt == null || it.currency == debt.currencyCode }
                        .map { DebtAccountOption(it.id, it.alias, it.currency) }
                    val interestCategories = categories
                        .filter { it.isActive && it.categoryType in validTypes }
                        .filter { it.userId == null || it.userId == userId }
                        .map { category ->
                            DebtCategoryOption(
                                id = category.id,
                                name = presentationById[category.id]?.name ?: "Interés",
                                categoryType = category.categoryType,
                            )
                        }
                    Triple(debt, accounts, interestCategories)
                }.catch {
                    _state.update { it.copy(isLoading = false, errorMessage = "No se pudo cargar la liquidación.") }
                }.collect { (debt, accounts, categories) ->
                    _state.update { current ->
                        current.copy(
                            debt = debt,
                            availableAccounts = accounts,
                            availableInterestCategories = categories,
                            selectedAccountId = current.selectedAccountId?.takeIf { id -> accounts.any { it.id == id } }
                                ?: accounts.firstOrNull()?.id,
                            selectedInterestCategoryId = current.selectedInterestCategoryId
                                ?.takeIf { id -> categories.any { it.id == id } }
                                ?: categories.firstOrNull()?.id,
                            isLoading = false,
                        )
                    }
                }
            }
        }
    }

    fun onPrincipalAmountChange(value: String) = _state.update {
        it.copy(principalAmount = value, errorMessage = null, conflictMessage = null)
    }

    fun onInterestAmountChange(value: String) = _state.update {
        it.copy(interestAmount = value, errorMessage = null, conflictMessage = null)
    }

    fun onAccountSelected(accountId: String) = _state.update { current ->
        if (current.availableAccounts.any { it.id == accountId }) {
            current.copy(selectedAccountId = accountId, errorMessage = null)
        } else current
    }

    fun onInterestCategorySelected(categoryId: String) = _state.update { current ->
        if (current.availableInterestCategories.any { it.id == categoryId }) {
            current.copy(selectedInterestCategoryId = categoryId, errorMessage = null)
        } else current
    }

    fun save() {
        val current = _state.value
        val debt = current.debt ?: return
        val userId = activeUserId() ?: run {
            _state.update { it.copy(errorMessage = "Inicia sesión para registrar el pago o cobro.") }
            return
        }
        if (current.isSaving || current.conflictMessage != null) return
        val principal = DebtAmountParser.toMinorUnits(current.principalAmount) ?: run {
            _state.update { it.copy(errorMessage = "Ingresa un principal mayor que cero.") }
            return
        }
        if (principal > debt.remainingPrincipalMinor) {
            _state.update { it.copy(errorMessage = PRINCIPAL_EXCEEDS_REMAINING) }
            return
        }
        val interest = DebtAmountParser.toNonNegativeMinorUnits(current.interestAmount) ?: run {
            _state.update { it.copy(errorMessage = "Revisa el interés; usa hasta dos decimales y no un monto negativo.") }
            return
        }
        val accountId = current.selectedAccountId?.takeIf { id ->
            current.availableAccounts.any { it.id == id && it.currencyCode == debt.currencyCode }
        } ?: run {
            _state.update { it.copy(errorMessage = "Elige una cuenta activa en ${debt.currencyCode}.") }
            return
        }
        val categoryId = current.selectedInterestCategoryId
        if (interest > 0L && debt.obligationType == DebtObligationType.PAYABLE && categoryId == null) {
            _state.update { it.copy(errorMessage = "Elige la categoría del interés para registrar el gasto.") }
            return
        }

        val operationId = UUID.randomUUID().toString()
        val occurredAt = System.currentTimeMillis()
        val canonical = listOf(
            "SETTLE_DEBT_V1", userId, debt.debtId, debt.revision.toString(), accountId,
            principal.toString(), interest.toString(), categoryId.orEmpty(), "", occurredAt.toString(),
        ).joinToString("|")
        val command = SettleDebtCommand(
            identity = DebtCommandIdentity(operationId, DebtCommandHasher.sha256(canonical)),
            debtId = debt.debtId,
            expectedRevision = debt.revision,
            accountId = accountId,
            principalMinor = principal,
            interestMinor = interest,
            interestCategoryId = categoryId.takeIf { interest > 0L },
            occurredAt = occurredAt,
        )
        _state.update { it.copy(isSaving = true, errorMessage = null, conflictMessage = null) }
        viewModelScope.launch {
            when (val result = runCatching { repository.settleDebt(userId, command) }.getOrElse {
                _state.update { it.copy(isSaving = false, errorMessage = "Guardamos tus datos. Revisa la conexión e inténtalo otra vez.") }
                return@launch
            }) {
                is DebtCommandResult.Applied, is DebtCommandResult.Duplicate -> {
                    _events.send(DebtSettlementUiEvent.Settled)
                }
                is DebtCommandResult.Conflict -> _state.update {
                    it.copy(
                        isSaving = false,
                        conflictMessage = "La deuda cambió en otro dispositivo. Actualizamos el saldo pendiente.",
                    )
                }
                is DebtCommandResult.Rejected -> _state.update {
                    it.copy(isSaving = false, errorMessage = result.message())
                }
                is DebtCommandResult.Retryable -> _state.update {
                    it.copy(isSaving = false, errorMessage = "Guardamos el movimiento y volveremos a sincronizarlo.")
                }
            }
        }
    }

    private fun DebtCommandResult.Rejected.message(): String = when (code) {
        "PRINCIPAL_EXCEEDS_REMAINING" -> PRINCIPAL_EXCEEDS_REMAINING
        "DEBT_REVISION_CONFLICT" -> "La deuda cambió en otro dispositivo. Actualiza el saldo antes de continuar."
        "INTEREST_CATEGORY_REQUIRED", "INVALID_INTEREST_CATEGORY" -> "Elige una categoría válida para el interés."
        "ACCOUNT_NOT_FOUND", "ACCOUNT_NOT_ELIGIBLE", "ACCOUNT_CURRENCY_MISMATCH" -> "Elige una cuenta activa en la moneda de la deuda."
        "INSTALLMENT_NOT_FOUND", "INSTALLMENT_PRINCIPAL_EXCEEDED" -> "El principal supera el saldo de la cuota seleccionada."
        else -> "No se pudo registrar la liquidación ($code)."
    }

    private fun activeUserId(): String? =
        (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId

    private companion object {
        val EXCLUDED_ACCOUNT_TYPES = setOf("GOALS_VIRTUAL", "CREDIT_LIABILITY")
        const val PRINCIPAL_EXCEEDS_REMAINING = "El principal supera el saldo pendiente."
    }
}
