package com.kipu.app.feature.movements.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.finance.domain.MoneyInputParser
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.usecase.ObserveInstruments
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.usecase.ObserveCategories
import com.kipu.app.feature.categories.data.sync.CategorySyncScheduler
import com.kipu.app.feature.movements.domain.RegisterTransaction
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.RegisterTransactionCommand
import com.kipu.app.feature.movements.domain.model.RegisterTransactionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class CategoryOption(
    val id: String,
    val name: String,
    val icon: String,
)

data class QuickMovementUiState(
    val type: MovementType = MovementType.EXPENSE,
    val amountText: String = "",
    val currency: String = "PEN",
    val selectedSourceAccountId: String? = null,
    val selectedDestinationAccountId: String? = null,
    val selectedCategoryId: String? = null,
    val selectedCategoryName: String? = null,
    val selectedCategoryIcon: String? = null,
    val merchantName: String = "",
    val selectedMerchantId: String? = null,
    val merchantProvisionalText: String? = null,
    val occurredAt: Long = System.currentTimeMillis(),
    val note: String = "",
    val isMoreDetailsExpanded: Boolean = false,
    val isSaving: Boolean = false,
    val availableAccounts: List<Account> = emptyList(),
    val availableCategories: List<CategoryOption> = emptyList(),
    val amountError: String? = null,
    val accountError: String? = null,
    val categoryError: String? = null,
    val destinationAccountError: String? = null,
    val generalError: String? = null,
    val showDuplicateWarning: Boolean = false,
    val pendingCommandForDuplicate: RegisterTransactionCommand? = null,
)

sealed interface QuickMovementUiEvent {
    data object TransactionSaved : QuickMovementUiEvent
    data class ShowMessage(val message: String) : QuickMovementUiEvent
}

@HiltViewModel
class QuickMovementViewModel @Inject constructor(
    private val registerTransactionUseCase: RegisterTransaction,
    private val observeInstruments: ObserveInstruments,
    private val observeCategories: ObserveCategories,
    private val categorySyncScheduler: CategorySyncScheduler,
    private val sessionCoordinator: SessionCoordinator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickMovementUiState())
    val uiState: StateFlow<QuickMovementUiState> = _uiState.asStateFlow()

    private val _events = Channel<QuickMovementUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        getUserId()?.let { ownerId ->
            categorySyncScheduler.scheduleSync(ownerId)
            viewModelScope.launch {
                observeCategories(UserId(ownerId)).collect { items ->
                    val options = items.filter { it.category.isActive }
                        .flatMap { root -> listOf(root) + root.subcategories }
                        .filter { it.category.isActive }
                        .map { CategoryOption(it.category.id.value, it.displayName, it.icon) }
                    _uiState.update { current ->
                        val selection = options.find { it.id == current.selectedCategoryId }
                        current.copy(
                            availableCategories = options,
                            selectedCategoryId = selection?.id,
                            selectedCategoryName = selection?.name,
                            selectedCategoryIcon = selection?.icon,
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            observeInstruments.observeAccounts(activeOnly = true).collect { accounts ->
                _uiState.update { current ->
                    val selectedAccount = accounts.find { it.id.value == current.selectedSourceAccountId }
                        ?: accounts.firstOrNull()
                    current.copy(
                        availableAccounts = accounts,
                        selectedSourceAccountId = selectedAccount?.id?.value,
                        currency = selectedAccount?.currency?.name ?: current.currency,
                        selectedDestinationAccountId = current.selectedDestinationAccountId
                            ?.takeIf { destination -> accounts.any { it.id.value == destination } },
                    )
                }
            }
        }
    }

    fun onTypeSelected(type: MovementType) {
        _uiState.update { current ->
            current.copy(
                type = type,
                selectedCategoryId = if (type == MovementType.EXPENSE) current.selectedCategoryId else null,
                selectedCategoryName = if (type == MovementType.EXPENSE) current.selectedCategoryName else null,
                selectedCategoryIcon = if (type == MovementType.EXPENSE) current.selectedCategoryIcon else null,
                merchantName = if (type == MovementType.EXPENSE) current.merchantName else "",
                selectedMerchantId = if (type == MovementType.EXPENSE) current.selectedMerchantId else null,
                merchantProvisionalText = if (type == MovementType.EXPENSE) current.merchantProvisionalText else null,
                amountError = null,
                accountError = null,
                categoryError = null,
                destinationAccountError = null,
            )
        }
    }

    fun onAmountChanged(amount: String) {
        val sanitized = amount.filter { it.isDigit() || it == '.' || it == ',' }
            .replace(',', '.')
        _uiState.update { it.copy(amountText = sanitized, amountError = null) }
    }

    fun onSourceAccountSelected(accountId: String) {
        _uiState.update { current ->
            val account = current.availableAccounts.find { it.id.value == accountId } ?: return@update current
            current.copy(
                selectedSourceAccountId = accountId,
                currency = account.currency.name,
                selectedDestinationAccountId = current.selectedDestinationAccountId
                    ?.takeIf { destination ->
                        current.availableAccounts.any { it.id.value == destination && it.currency == account.currency }
                    },
                accountError = null,
                destinationAccountError = null,
            )
        }
    }

    fun onDestinationAccountSelected(accountId: String) {
        _uiState.update { it.copy(selectedDestinationAccountId = accountId, destinationAccountError = null) }
    }

    fun onCategorySelected(category: CategoryOption) {
        _uiState.update {
            it.copy(
                selectedCategoryId = category.id,
                selectedCategoryName = category.name,
                selectedCategoryIcon = category.icon,
                categoryError = null,
            )
        }
    }

    fun onMerchantSelected(merchant: MerchantCatalogEntry) {
        _uiState.update {
            it.copy(merchantName = merchant.name, selectedMerchantId = merchant.id.value, merchantProvisionalText = null)
        }
    }

    fun onMerchantProvisionalText(text: String) {
        val name = text.trim()
        _uiState.update {
            it.copy(merchantName = name, selectedMerchantId = null, merchantProvisionalText = name.takeIf(String::isNotBlank))
        }
    }

    fun onMerchantCleared() {
        _uiState.update { it.copy(merchantName = "", selectedMerchantId = null, merchantProvisionalText = null) }
    }

    fun onOccurredAtChanged(occurredAt: Long) {
        _uiState.update { it.copy(occurredAt = occurredAt) }
    }

    fun onNoteChanged(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun onToggleMoreDetails() {
        _uiState.update { it.copy(isMoreDetailsExpanded = !it.isMoreDetailsExpanded) }
    }

    fun onSave() {
        val state = _uiState.value
        val amountMinor = parseAmountMinor(state.amountText)
        if (amountMinor == null || amountMinor <= 0) {
            _uiState.update { it.copy(amountError = "Ingresa un monto válido mayor a 0") }
            return
        }

        if (state.selectedSourceAccountId == null) {
            _uiState.update { it.copy(accountError = "Selecciona una cuenta") }
            return
        }

        if (state.type == MovementType.EXPENSE && state.selectedCategoryId == null) {
            _uiState.update { it.copy(categoryError = "Selecciona una categoría") }
            return
        }

        if (state.type == MovementType.TRANSFER) {
            if (state.selectedDestinationAccountId == null) {
                _uiState.update { it.copy(destinationAccountError = "Selecciona la cuenta de destino") }
                return
            }
            if (state.selectedSourceAccountId == state.selectedDestinationAccountId) {
                _uiState.update { it.copy(destinationAccountError = "La cuenta de origen y destino deben ser distintas") }
                return
            }
            if (state.availableAccounts.find { it.id.value == state.selectedDestinationAccountId }?.currency?.name != state.currency) {
                _uiState.update { it.copy(destinationAccountError = "Las cuentas deben usar la misma moneda") }
                return
            }
        }

        val userId = getUserId() ?: run {
            _uiState.update { it.copy(generalError = "Inicia sesión para registrar un movimiento") }
            return
        }
        val command = RegisterTransactionCommand(
            idempotencyKey = UUID.randomUUID().toString(),
            userId = userId,
            type = state.type,
            amountMinor = amountMinor,
            currency = state.currency,
            sourceAccountId = state.selectedSourceAccountId,
            destinationAccountId = if (state.type == MovementType.TRANSFER) state.selectedDestinationAccountId else null,
            categoryId = state.selectedCategoryId,
            merchantId = state.selectedMerchantId,
            merchantProvisionalText = state.merchantProvisionalText,
            occurredAt = state.occurredAt,
            note = state.note.takeIf { it.isNotBlank() },
            ignoreSimilarityWarning = false,
        )

        executeRegister(command)
    }

    fun onConfirmDuplicate() {
        val pending = _uiState.value.pendingCommandForDuplicate ?: return
        // Generate new idempotencyKey to treat as separate legitimate purchase
        val newCommand = pending.copy(
            idempotencyKey = UUID.randomUUID().toString(),
            ignoreSimilarityWarning = true,
        )
        _uiState.update { it.copy(showDuplicateWarning = false, pendingCommandForDuplicate = null) }
        executeRegister(newCommand)
    }

    fun onDismissDuplicate() {
        _uiState.update { it.copy(showDuplicateWarning = false, pendingCommandForDuplicate = null) }
    }

    private fun executeRegister(command: RegisterTransactionCommand) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, generalError = null) }
            when (val result = registerTransactionUseCase(command)) {
                is RegisterTransactionResult.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.send(QuickMovementUiEvent.TransactionSaved)
                    resetForm()
                }
                is RegisterTransactionResult.SimilarTransactionWarning -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            showDuplicateWarning = true,
                            pendingCommandForDuplicate = command,
                        )
                    }
                }
                is RegisterTransactionResult.Conflict -> {
                    _uiState.update { it.copy(isSaving = false, generalError = result.message) }
                }
                is RegisterTransactionResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            generalError = result.message,
                            amountError = if (result.field == "amount") result.message else it.amountError,
                            categoryError = if (result.field == "category") result.message else it.categoryError,
                        )
                    }
                }
                is RegisterTransactionResult.Failure -> {
                    _uiState.update { it.copy(isSaving = false, generalError = result.message) }
                }
            }
        }
    }

    fun resetForm() {
        _uiState.update { current ->
            QuickMovementUiState(
                type = MovementType.EXPENSE,
                availableAccounts = current.availableAccounts,
                selectedSourceAccountId = current.availableAccounts.firstOrNull()?.id?.value,
                currency = current.availableAccounts.firstOrNull()?.currency?.name ?: current.currency,
                availableCategories = current.availableCategories,
            )
        }
    }

    private fun parseAmountMinor(amountText: String): Long? {
        return MoneyInputParser.parseMinorUnits(amountText)
    }

    private fun getUserId(): String? {
        return sessionCoordinator.currentOwner?.verifiedUserId
            ?: (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId
    }
}
