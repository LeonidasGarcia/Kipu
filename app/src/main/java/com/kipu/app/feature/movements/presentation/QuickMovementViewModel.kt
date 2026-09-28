package com.kipu.app.feature.movements.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.finance.domain.MoneyInputParser
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.usecase.ObserveInstruments
import com.kipu.app.feature.accounts.domain.usecase.ConfirmCreditPurchase
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.categories.domain.usecase.ObserveCategories
import com.kipu.app.feature.categories.data.sync.CategorySyncScheduler
import com.kipu.app.feature.movements.domain.RegisterTransaction
import com.kipu.app.feature.movements.domain.model.MovementSyncStatus
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
import java.time.Instant
import javax.inject.Inject

data class CategoryOption(
    val id: String,
    val name: String,
    val icon: String,
    val categoryType: CategoryType = CategoryType.GENERAL,
    val parentCategoryId: String? = null,
    val parentName: String? = null,
) {
    val displayName: String get() = parentName?.let { "$it > $name" } ?: name
}

data class QuickMovementUiState(
    val type: MovementType = MovementType.EXPENSE,
    val amountText: String = "",
    val currency: String = "PEN",
    val selectedSourceAccountId: String? = null,
    val selectedSourceCardId: String? = null,
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
    val availableCreditCards: List<CreditCard> = emptyList(),
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
    data class TransactionSaved(
        val message: String,
        val movementType: MovementType = MovementType.EXPENSE,
    ) : QuickMovementUiEvent
    data class ShowMessage(val message: String) : QuickMovementUiEvent
}

@HiltViewModel
class QuickMovementViewModel @Inject constructor(
    private val registerTransactionUseCase: RegisterTransaction,
    private val confirmCreditPurchaseUseCase: ConfirmCreditPurchase,
    private val observeInstruments: ObserveInstruments,
    private val observeCategories: ObserveCategories,
    private val categorySyncScheduler: CategorySyncScheduler,
    private val sessionCoordinator: SessionCoordinator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickMovementUiState())
    val uiState: StateFlow<QuickMovementUiState> = _uiState.asStateFlow()

    private var allCategoryOptions: List<CategoryOption> = emptyList()

    private val _events = Channel<QuickMovementUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        getUserId()?.let { ownerId ->
            categorySyncScheduler.scheduleSync(ownerId)
            viewModelScope.launch {
                observeCategories(UserId(ownerId)).collect { items ->
                    allCategoryOptions = items.filter { it.category.isActive }
                        .flatMap { root -> listOf(root to null) + root.subcategories.map { it to root } }
                        .filter { (item, _) -> item.category.isActive && !item.category.isPlanLocked }
                        .map { (item, parent) ->
                            CategoryOption(
                                id = item.category.id.value,
                                name = item.displayName,
                                icon = item.icon,
                                categoryType = item.category.categoryType,
                                parentCategoryId = parent?.category?.id?.value,
                                parentName = parent?.displayName,
                            )
                        }
                    _uiState.update { current ->
                        val options = categoriesFor(current.type)
                        val selection = options.find { it.id == current.selectedCategoryId }
                        current.copy(
                            availableCategories = options,
                            selectedCategoryId = selection?.id,
                selectedCategoryName = selection?.displayName,
                            selectedCategoryIcon = selection?.icon,
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            combine(
                observeInstruments.observeAccounts(activeOnly = true),
                observeInstruments.observeCards(activeOnly = true),
            ) { accounts, cards ->
                accounts to cards.filterIsInstance<CreditCard>().filterNot { it.isArchived || it.isPlanLocked }
            }.collect { (accounts, creditCards) ->
                _uiState.update { current ->
                    val selectedCard = creditCards.find { it.id.value == current.selectedSourceCardId }
                    val selectedAccount = if (selectedCard == null) {
                        accounts.find { it.id.value == current.selectedSourceAccountId } ?: accounts.firstOrNull()
                    } else null
                    current.copy(
                        availableAccounts = accounts,
                        availableCreditCards = creditCards,
                        selectedSourceAccountId = selectedAccount?.id?.value,
                        selectedSourceCardId = selectedCard?.id?.value,
                        currency = (selectedCard?.currency ?: selectedAccount?.currency)?.name ?: current.currency,
                        selectedDestinationAccountId = current.selectedDestinationAccountId
                            ?.takeIf { destination -> accounts.any { it.id.value == destination } },
                    )
                }
            }
        }
    }

    fun onTypeSelected(type: MovementType) {
        _uiState.update { current ->
            val options = categoriesFor(type)
            val selection = if (current.type == type) {
                options.find { it.id == current.selectedCategoryId }
            } else {
                null
            }
            current.copy(
                type = type,
                availableCategories = options,
                selectedCategoryId = selection?.id,
                selectedCategoryName = selection?.displayName,
                selectedCategoryIcon = selection?.icon,
                merchantName = if (type == MovementType.EXPENSE) current.merchantName else "",
                selectedMerchantId = if (type == MovementType.EXPENSE) current.selectedMerchantId else null,
                merchantProvisionalText = if (type == MovementType.EXPENSE) current.merchantProvisionalText else null,
                amountError = null,
                accountError = null,
                categoryError = null,
                destinationAccountError = null,
                selectedSourceCardId = if (type == MovementType.EXPENSE) current.selectedSourceCardId else null,
                selectedSourceAccountId = if (type == MovementType.EXPENSE && current.selectedSourceCardId != null) {
                    null
                } else {
                    current.selectedSourceAccountId ?: current.availableAccounts.firstOrNull()?.id?.value
                },
                currency = if (type == MovementType.EXPENSE && current.selectedSourceCardId != null) {
                    current.availableCreditCards.find { it.id.value == current.selectedSourceCardId }?.currency?.name ?: current.currency
                } else if (type != MovementType.EXPENSE) {
                    current.availableAccounts.find { it.id.value == current.selectedSourceAccountId }?.currency?.name
                        ?: current.availableAccounts.firstOrNull()?.currency?.name
                        ?: current.currency
                } else current.currency,
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
                selectedSourceCardId = null,
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

    fun onSourceCardSelected(cardId: String) {
        _uiState.update { current ->
            if (current.type != MovementType.EXPENSE) return@update current
            val card = current.availableCreditCards.find { it.id.value == cardId } ?: return@update current
            current.copy(
                selectedSourceAccountId = null,
                selectedSourceCardId = card.id.value,
                currency = card.currency.name,
                selectedDestinationAccountId = null,
                accountError = null,
                destinationAccountError = null,
            )
        }
    }

    fun onDestinationAccountSelected(accountId: String) {
        _uiState.update { it.copy(selectedDestinationAccountId = accountId, destinationAccountError = null) }
    }

    fun onCategorySelected(category: CategoryOption) {
        if (category !in _uiState.value.availableCategories || _uiState.value.type == MovementType.TRANSFER) return
        _uiState.update {
            it.copy(
                selectedCategoryId = category.id,
                selectedCategoryName = category.displayName,
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
        if (state.isSaving) return
        val amountMinor = parseAmountMinor(state.amountText)
        if (amountMinor == null || amountMinor <= 0) {
            _uiState.update { it.copy(amountError = "Ingresa un monto válido mayor a 0") }
            return
        }

        if (state.selectedSourceAccountId == null && state.selectedSourceCardId == null) {
            _uiState.update { it.copy(accountError = "Selecciona una cuenta o tarjeta de crédito") }
            return
        }
        if (state.selectedSourceCardId != null && state.type != MovementType.EXPENSE) {
            _uiState.update { it.copy(accountError = "Las tarjetas de crédito solo pueden registrar gastos") }
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
        _uiState.update { it.copy(isSaving = true, generalError = null) }
        if (state.selectedSourceCardId != null) {
            executeCreditPurchase(state, amountMinor)
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
            categoryId = state.selectedCategoryId.takeUnless { state.type == MovementType.TRANSFER },
            merchantId = state.selectedMerchantId,
            merchantProvisionalText = state.merchantProvisionalText,
            occurredAt = state.occurredAt,
            note = state.note.takeIf { it.isNotBlank() },
            ignoreSimilarityWarning = false,
        )

        executeRegister(command)
    }

    private fun executeCreditPurchase(state: QuickMovementUiState, amountMinor: Long) {
        val cardId = state.selectedSourceCardId?.let(::CardId) ?: return
        val merchant = state.merchantProvisionalText
            ?: state.merchantName.takeIf(String::isNotBlank)
            ?: "Compra con tarjeta"
        val currency = runCatching { Currency.valueOf(state.currency) }.getOrDefault(Currency.PEN)
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, generalError = null) }
            confirmCreditPurchaseUseCase(
                cardId = cardId,
                amount = Money(amountMinor, currency),
                merchant = merchant,
                effectiveAt = Instant.ofEpochMilli(state.occurredAt),
                installments = 1,
                categoryId = requireNotNull(state.selectedCategoryId),
                merchantId = state.selectedMerchantId,
                note = state.note.takeIf(String::isNotBlank),
            ).fold(
                onSuccess = {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.send(QuickMovementUiEvent.TransactionSaved("Compra con tarjeta registrada y pendiente de sincronización"))
                    resetForm()
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isSaving = false, generalError = error.message ?: "No se pudo registrar la compra") }
                },
            )
        }
    }

    fun onConfirmDuplicate() {
        val pending = _uiState.value.pendingCommandForDuplicate ?: return
        if (_uiState.value.isSaving) return
        // Generate new idempotencyKey to treat as separate legitimate purchase
        val newCommand = pending.copy(
            idempotencyKey = UUID.randomUUID().toString(),
            ignoreSimilarityWarning = true,
        )
        _uiState.update { it.copy(showDuplicateWarning = false, pendingCommandForDuplicate = null, isSaving = true) }
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
                    val message = when (result.transaction.syncStatus) {
                        MovementSyncStatus.SYNCED, MovementSyncStatus.MIGRATED_LOCAL ->
                            "Movimiento guardado y sincronizado"
                        MovementSyncStatus.CONFLICT, MovementSyncStatus.FAILED_PERMANENT ->
                            "Guardado en este dispositivo · Revisión de sincronización pendiente"
                        MovementSyncStatus.PENDING, MovementSyncStatus.IN_FLIGHT ->
                            "Guardado en este dispositivo · Pendiente de sincronización"
                    }
                    _events.send(QuickMovementUiEvent.TransactionSaved(message, command.type))
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
                availableCreditCards = current.availableCreditCards,
                selectedSourceAccountId = current.availableAccounts.firstOrNull()?.id?.value,
                currency = current.availableAccounts.firstOrNull()?.currency?.name ?: current.currency,
                availableCategories = categoriesFor(MovementType.EXPENSE),
            )
        }
    }

    private fun parseAmountMinor(amountText: String): Long? {
        return MoneyInputParser.parseMinorUnits(amountText)
    }

    private fun categoriesFor(type: MovementType): List<CategoryOption> {
        val expectedType = when (type) {
            MovementType.EXPENSE -> CategoryType.EXPENSE
            MovementType.INCOME -> CategoryType.INCOME
            MovementType.TRANSFER -> return emptyList()
        }
        return allCategoryOptions.filter {
            it.categoryType == expectedType || it.categoryType == CategoryType.GENERAL
        }
    }

    private fun getUserId(): String? {
        return sessionCoordinator.currentOwner?.verifiedUserId
            ?: (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId
    }
}
