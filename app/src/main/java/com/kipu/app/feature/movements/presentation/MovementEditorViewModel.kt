package com.kipu.app.feature.movements.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.finance.domain.MoneyInputParser
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.usecase.ObserveInstruments
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.usecase.ObserveCategories
import com.kipu.app.feature.movements.data.local.MovementConflictProposalEntity
import com.kipu.app.feature.movements.data.local.MovementRevisionSnapshotCodec
import com.kipu.app.feature.movements.domain.MovementMaintenanceRepository
import com.kipu.app.feature.movements.domain.MovementRepository
import com.kipu.app.feature.movements.domain.ReviseTransaction
import com.kipu.app.feature.movements.domain.model.MovementFinancialState
import com.kipu.app.feature.movements.domain.model.MovementMutationResult
import com.kipu.app.feature.movements.domain.model.MovementRevisionCommand
import com.kipu.app.feature.movements.domain.model.MovementRevisionPayload
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.ui.component.formatMinorUnits
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class MovementEditorUiState(
    val isLoading: Boolean = true,
    val transactionId: String = "",
    val expectedRevision: Long = 1L,
    val movementType: MovementType = MovementType.EXPENSE,
    val currency: String = "PEN",
    val isSpecialized: Boolean = false,
    val specializedMessage: String? = null,

    // Initial / Baseline values for diff & summary
    val initialAmountMinor: Long = 0L,
    val initialSourceAccountId: String? = null,
    val initialDestinationAccountId: String? = null,
    val initialCategoryId: String? = null,
    val initialCategoryName: String? = null,
    val initialMerchantName: String? = null,
    val initialMerchantId: String? = null,
    val initialOccurredAt: Long = 0L,
    val initialNote: String? = null,

    // Form inputs
    val amountText: String = "",
    val selectedSourceAccountId: String? = null,
    val selectedDestinationAccountId: String? = null,
    val selectedCategoryId: String? = null,
    val selectedCategoryName: String? = null,
    val merchantName: String = "",
    val selectedMerchantId: String? = null,
    val selectedMerchantDisplayName: String? = null,
    val occurredAt: Long = 0L,
    val note: String = "",

    // Selectable options
    val availableAccounts: List<Account> = emptyList(),
    val availableCategories: List<CategoryOption> = emptyList(),

    // Field errors
    val amountError: String? = null,
    val sourceAccountError: String? = null,
    val destinationAccountError: String? = null,
    val categoryError: String? = null,
    val generalError: String? = null,

    // Operation flags
    val isSaving: Boolean = false,
    val isSuccess: Boolean = false,
    val showConfirmDiscardDialog: Boolean = false,

    // Conflict proposal state (T072)
    val hasConflict: Boolean = false,
    val conflictProposal: MovementConflictProposalEntity? = null,
    val conflictProposedAmountMinor: Long? = null,
    val conflictProposedOccurredAt: Long? = null,
    val conflictProposedSourceAccountId: String? = null,
    val isOfficialVoided: Boolean = false,
) {
    val hasUnsavedChanges: Boolean
        get() {
            if (isSpecialized) return false
            val parsedAmount = MoneyInputParser.parseMinorUnits(amountText)
            val amountChanged = parsedAmount != null && parsedAmount != initialAmountMinor
            val sourceChanged = selectedSourceAccountId != initialSourceAccountId
            val destChanged = selectedDestinationAccountId != initialDestinationAccountId
            val catChanged = selectedCategoryId != initialCategoryId
            val merchantChanged = selectedMerchantId != initialMerchantId ||
                merchantName.trim() != (initialMerchantName ?: "").trim()
            val dateChanged = occurredAt != initialOccurredAt
            val noteChanged = note.trim() != (initialNote ?: "").trim()
            return amountChanged || sourceChanged || destChanged || catChanged || merchantChanged || dateChanged || noteChanged
        }

    val isOnlyNoteChanged: Boolean
        get() {
            if (!hasUnsavedChanges) return false
            val parsedAmount = MoneyInputParser.parseMinorUnits(amountText)
            val noteChanged = note.trim() != (initialNote ?: "").trim()
            val amountSame = parsedAmount == initialAmountMinor
            val sourceSame = selectedSourceAccountId == initialSourceAccountId
            val destSame = selectedDestinationAccountId == initialDestinationAccountId
            val catSame = selectedCategoryId == initialCategoryId
            val merchantSame = selectedMerchantId == initialMerchantId &&
                merchantName.trim() == (initialMerchantName ?: "").trim()
            val dateSame = occurredAt == initialOccurredAt
            return noteChanged && amountSame && sourceSame && destSame && catSame && merchantSame && dateSame
        }
}

sealed interface MovementEditorUiEvent {
    data class TransactionRevised(val message: String) : MovementEditorUiEvent
    data class ShowMessage(val message: String) : MovementEditorUiEvent
    data object CloseEditor : MovementEditorUiEvent
}

@HiltViewModel
class MovementEditorViewModel @Inject constructor(
    private val sessionCoordinator: SessionCoordinator,
    private val reviseTransactionUseCase: ReviseTransaction,
    private val maintenanceRepository: MovementMaintenanceRepository,
    private val movementRepository: MovementRepository,
    private val observeInstruments: ObserveInstruments,
    private val observeCategories: ObserveCategories,
    private val categoriesRepository: CategoriesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MovementEditorUiState())
    val uiState: StateFlow<MovementEditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<MovementEditorUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        val ownerId = getUserId()
        if (ownerId != null) {
            viewModelScope.launch {
                observeInstruments.observeAccounts(activeOnly = true).collect { accounts ->
                    _uiState.update { it.copy(availableAccounts = accounts) }
                }
            }
            viewModelScope.launch {
                observeCategories(UserId(ownerId)).collect { items ->
                    val options = items.filter { it.category.isActive }
                        .flatMap { root -> listOf(root to null) + root.subcategories.map { it to root } }
                        .filter { (item, _) -> item.category.isActive }
                        .map { (item, parent) ->
                            CategoryOption(
                                id = item.category.id.value,
                                name = item.displayName,
                                icon = item.icon,
                                categoryType = item.category.categoryType,
                                parentCategoryId = parent?.category?.id?.value,
                                parentName = parent?.displayName,
                                color = item.color,
                            )
                        }
                    _uiState.update { current ->
                        val matchingCat = options.firstOrNull { it.id == current.selectedCategoryId }
                        current.copy(
                            availableCategories = options,
                            selectedCategoryName = matchingCat?.displayName ?: current.selectedCategoryName,
                        )
                    }
                }
            }
        }
    }

    fun loadTransaction(txId: String, merchantDisplayName: String? = null) {
        val userId = getUserId() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, transactionId = txId, generalError = null) }

            val headDeferred = async { maintenanceRepository.getRevisionHead(userId, txId) }
            val transactionDeferred = async { movementRepository.getTransactionById(userId, txId) }
            val head = headDeferred.await()
            val tx = transactionDeferred.await()

            if (head == null || tx == null) {
                _uiState.update { it.copy(isLoading = false, generalError = "No se encontró el movimiento") }
                return@launch
            }

            val isSpecialized = !head.payload.operationKind.isNullOrBlank() ||
                tx.cardId != null ||
                tx.installmentCount != null ||
                tx.operationKind.equals("CARD_PAYMENT", ignoreCase = true) ||
                tx.operationKind.equals("CARD_PURCHASE", ignoreCase = true) ||
                tx.legacyKind in setOf("OPENING", "ADJUSTMENT", "REVERSAL", "CARD_PAYMENT_CASH")

            val specializedMsg = if (isSpecialized) {
                "Este movimiento tiene cuotas o una deuda asociada. Revísalo desde su gestión específica"
            } else null

            val formattedAmount = formatMinorUnits(head.payload.amountMinor)

            _uiState.update { current ->
                val matchingCat = current.availableCategories.firstOrNull { it.id == head.payload.categoryId }
                current.copy(
                    isLoading = false,
                    transactionId = txId,
                    expectedRevision = head.commandBaseRevision,
                    movementType = head.payload.type,
                    currency = head.payload.currency,
                    isSpecialized = isSpecialized,
                    specializedMessage = specializedMsg,
                    initialAmountMinor = head.payload.amountMinor,
                    initialSourceAccountId = head.payload.sourceAccountId,
                    initialDestinationAccountId = head.payload.destinationAccountId,
                    initialCategoryId = head.payload.categoryId,
                    initialCategoryName = matchingCat?.displayName,
                    initialMerchantName = head.payload.merchantProvisionalText,
                    initialMerchantId = head.payload.merchantId,
                    initialOccurredAt = head.payload.occurredAt,
                    initialNote = head.payload.note,
                    amountText = formattedAmount,
                    selectedSourceAccountId = head.payload.sourceAccountId,
                    selectedDestinationAccountId = head.payload.destinationAccountId,
                    selectedCategoryId = head.payload.categoryId,
                    selectedCategoryName = matchingCat?.displayName,
                    merchantName = head.payload.merchantProvisionalText ?: "",
                    selectedMerchantId = head.payload.merchantId,
                    selectedMerchantDisplayName = merchantDisplayName,
                    occurredAt = head.payload.occurredAt,
                    note = head.payload.note ?: "",
                    hasConflict = false,
                    conflictProposal = null,
                    conflictProposedAmountMinor = null,
                    conflictProposedOccurredAt = null,
                    conflictProposedSourceAccountId = null,
                    isOfficialVoided = head.financialState == MovementFinancialState.VOIDED,
                )
            }

            // Conflict history is secondary. Show the editable movement as soon as its
            // authoritative head and transaction are ready, then enrich the editor in place.
            viewModelScope.launch {
                val activeProposal = try {
                    maintenanceRepository.getConflictProposals(userId, txId)
                        .firstOrNull { it.resolution == "PENDING" }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                if (activeProposal != null) {
                    val decodedSnapshot = runCatching {
                        MovementRevisionSnapshotCodec.decodeSnapshot(activeProposal.proposedSnapshot)
                    }.getOrNull()
                    _uiState.update { current ->
                        if (current.transactionId != txId) {
                            current
                        } else {
                            current.copy(
                                hasConflict = true,
                                conflictProposal = activeProposal,
                                conflictProposedAmountMinor = decodedSnapshot?.amountMinor,
                                conflictProposedOccurredAt = decodedSnapshot?.occurredAt,
                                conflictProposedSourceAccountId = decodedSnapshot?.sourceAccountId,
                            )
                        }
                    }
                }
            }

            // Keep catalog lookup off the editor's critical loading path. Room can emit its
            // merchant snapshot after the primary movement data is already ready.
            head.payload.merchantId?.let { merchantId ->
                viewModelScope.launch {
                    try {
                        val displayName = categoriesRepository.getMerchantById(MerchantId(merchantId))?.name
                        if (displayName != null) {
                            _uiState.update { current ->
                                if (
                                    current.transactionId == txId &&
                                    current.selectedMerchantId == merchantId &&
                                    current.selectedMerchantDisplayName == null
                                ) {
                                    current.copy(selectedMerchantDisplayName = displayName)
                                } else {
                                    current
                                }
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // The movement remains editable using its stored merchant ID.
                    }
                }
            }
        }
    }

    fun onAmountChanged(newText: String) {
        _uiState.update { it.copy(amountText = newText, amountError = null) }
    }

    fun onSourceAccountSelected(accountId: String) {
        _uiState.update { it.copy(selectedSourceAccountId = accountId, sourceAccountError = null) }
    }

    fun onDestinationAccountSelected(accountId: String) {
        _uiState.update { it.copy(selectedDestinationAccountId = accountId, destinationAccountError = null) }
    }

    fun onCategorySelected(category: CategoryOption) {
        _uiState.update {
            it.copy(
                selectedCategoryId = category.id,
                selectedCategoryName = category.displayName,
                categoryError = null,
            )
        }
    }

    fun onMerchantChanged(merchant: String) {
        _uiState.update { current ->
            if (merchant == current.merchantName) current
            else current.copy(
                merchantName = merchant,
                selectedMerchantId = null,
                selectedMerchantDisplayName = null,
            )
        }
    }

    fun onDateChanged(epochMilli: Long) {
        _uiState.update { it.copy(occurredAt = epochMilli) }
    }

    fun onNoteChanged(newNote: String) {
        _uiState.update { it.copy(note = newNote) }
    }

    fun onAttemptClose() {
        if (_uiState.value.hasUnsavedChanges) {
            _uiState.update { it.copy(showConfirmDiscardDialog = true) }
        } else {
            _events.trySend(MovementEditorUiEvent.CloseEditor)
        }
    }

    fun onConfirmDiscard() {
        _uiState.update { it.copy(showConfirmDiscardDialog = false) }
        _events.trySend(MovementEditorUiEvent.CloseEditor)
    }

    fun onDismissDiscardDialog() {
        _uiState.update { it.copy(showConfirmDiscardDialog = false) }
    }

    fun onSave() {
        val currentState = _uiState.value
        if (currentState.isSpecialized || currentState.isSaving || currentState.hasConflict) return

        val userId = getUserId() ?: return
        val parsedAmount = MoneyInputParser.parseMinorUnits(currentState.amountText)

        var hasError = false
        if (parsedAmount == null || parsedAmount <= 0L) {
            _uiState.update { it.copy(amountError = "El monto debe ser mayor a 0") }
            hasError = true
        }

        if (currentState.selectedSourceAccountId.isNullOrBlank()) {
            _uiState.update { it.copy(sourceAccountError = "Selecciona una cuenta de origen") }
            hasError = true
        }

        if (currentState.movementType == MovementType.TRANSFER) {
            if (currentState.selectedDestinationAccountId.isNullOrBlank() ||
                currentState.selectedDestinationAccountId == currentState.selectedSourceAccountId
            ) {
                _uiState.update { it.copy(destinationAccountError = "La cuenta destino debe ser diferente al origen") }
                hasError = true
            }
        }

        if (currentState.movementType == MovementType.EXPENSE && currentState.selectedCategoryId.isNullOrBlank()) {
            _uiState.update { it.copy(categoryError = "Selecciona una categoría para el gasto") }
            hasError = true
        }

        if (hasError) return

        _uiState.update { it.copy(isSaving = true, generalError = null) }

        viewModelScope.launch {
            val revisedPayload = MovementRevisionPayload(
                type = currentState.movementType,
                operationKind = null,
                amountMinor = parsedAmount!!,
                currency = currentState.currency,
                sourceAccountId = currentState.selectedSourceAccountId,
                destinationAccountId = if (currentState.movementType == MovementType.TRANSFER) currentState.selectedDestinationAccountId else null,
                categoryId = if (currentState.movementType != MovementType.TRANSFER) currentState.selectedCategoryId else null,
                merchantId = currentState.selectedMerchantId,
                merchantProvisionalText = currentState.merchantName.trim()
                    .takeIf { currentState.selectedMerchantId == null && it.isNotBlank() },
                occurredAt = currentState.occurredAt,
                note = currentState.note.trim().takeIf { it.isNotBlank() },
            )

            val command = MovementRevisionCommand.Revise(
                idempotencyKey = UUID.randomUUID().toString(),
                transactionId = currentState.transactionId,
                expectedRevision = currentState.expectedRevision,
                payload = revisedPayload,
            )

            when (val result = reviseTransactionUseCase(userId, command)) {
                is MovementMutationResult.Success -> {
                    _uiState.update { it.copy(isSaving = false, isSuccess = true) }
                    _events.send(MovementEditorUiEvent.TransactionRevised("Movimiento corregido (Pendiente de sincronizar)"))
                    _events.send(MovementEditorUiEvent.CloseEditor)
                }
                is MovementMutationResult.Conflict -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            hasConflict = true,
                            generalError = "Conflicto de sincronización: el movimiento cambió en otro dispositivo",
                        )
                    }
                    loadTransaction(currentState.transactionId)
                }
                is MovementMutationResult.Rejected -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            generalError = "No se pudo guardar la corrección (${result.code})",
                        )
                    }
                }
            }
        }
    }

    fun onDiscardConflict(proposalId: String) {
        val userId = getUserId() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            maintenanceRepository.discardProposal(userId, proposalId)
            loadTransaction(_uiState.value.transactionId)
        }
    }

    fun onRedoConflict(proposalId: String) {
        val userId = getUserId() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val res = maintenanceRepository.redoProposal(userId, proposalId)) {
                is MovementMutationResult.Success -> {
                    _events.send(MovementEditorUiEvent.TransactionRevised("Propuesta reaplicada con éxito"))
                    _events.send(MovementEditorUiEvent.CloseEditor)
                }
                else -> {
                    loadTransaction(_uiState.value.transactionId)
                }
            }
        }
    }

    private fun getUserId(): String? {
        return sessionCoordinator.currentOwner?.verifiedUserId
            ?: (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId
    }
}
