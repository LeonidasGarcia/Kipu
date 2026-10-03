package com.kipu.app.feature.movements.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.movements.domain.MovementMaintenanceRepository
import com.kipu.app.feature.movements.domain.MovementRepository
import com.kipu.app.feature.movements.domain.QueryMovementHistory
import com.kipu.app.feature.movements.domain.VoidTransaction
import com.kipu.app.feature.movements.domain.model.AdvancedHistoryCriteria
import com.kipu.app.feature.movements.domain.model.MovementFinancialState
import com.kipu.app.feature.movements.domain.model.MovementHistoryAccessDecision
import com.kipu.app.feature.movements.domain.model.MovementHistoryCursor
import com.kipu.app.feature.movements.domain.model.MovementHistoryQuery
import com.kipu.app.feature.movements.domain.model.MovementMutationResult
import com.kipu.app.feature.movements.domain.model.MovementRevisionCommand
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.TransactionItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class MovementHistoryUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val selectedFilterType: MovementType? = null, // null means "Todos"
    val allTransactions: List<TransactionItem> = emptyList(),
    val filteredTransactions: Map<String, List<TransactionItem>> = emptyMap(),
    val showRegisterSheet: Boolean = false,
    val selectedTransactionForVoid: TransactionItem? = null,
    val isVoiding: Boolean = false,
    val voidErrorMessage: String? = null,

    // Advanced Filters (HU-22 / US6)
    val showAdvancedFilterPanel: Boolean = false,
    val selectedAccountIds: Set<String> = emptySet(),
    val selectedCategoryIds: Set<String> = emptySet(),
    val minAmountMinor: Long? = null,
    val maxAmountMinor: Long? = null,
    val fromDate: Long? = null,
    val toDate: Long? = null,
    val selectedFinancialStates: Set<MovementFinancialState> = emptySet(),

    // Keyset pagination & access
    val hasMorePages: Boolean = false,
    val isLoadingNextPage: Boolean = false,
    val currentCursor: MovementHistoryCursor? = null,
    val accessStatus: MovementHistoryAccessDecision = MovementHistoryAccessDecision.Allowed,
    val fallbackUsed: Boolean = false,
    val accessMessage: String? = null,
) {
    val hasActiveAdvancedFilters: Boolean
        get() = selectedAccountIds.isNotEmpty() ||
            selectedCategoryIds.isNotEmpty() ||
            minAmountMinor != null ||
            maxAmountMinor != null ||
            fromDate != null ||
            toDate != null ||
            selectedFinancialStates.isNotEmpty()
}

private data class VoidDialogState(
    val selectedItem: TransactionItem? = null,
    val isVoiding: Boolean = false,
    val errorMessage: String? = null,
)

data class AdvancedFiltersState(
    val showPanel: Boolean = false,
    val accountIds: Set<String> = emptySet(),
    val categoryIds: Set<String> = emptySet(),
    val minAmountMinor: Long? = null,
    val maxAmountMinor: Long? = null,
    val fromDate: Long? = null,
    val toDate: Long? = null,
    val financialStates: Set<MovementFinancialState> = emptySet(),
) {
    fun hasCriteria(): Boolean =
        accountIds.isNotEmpty() || categoryIds.isNotEmpty() || minAmountMinor != null ||
            maxAmountMinor != null || fromDate != null || toDate != null || financialStates.isNotEmpty()
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class MovementHistoryViewModel @Inject constructor(
    private val movementRepository: MovementRepository,
    private val sessionCoordinator: SessionCoordinator,
    private val voidTransactionUseCase: VoidTransaction? = null,
    private val maintenanceRepository: MovementMaintenanceRepository? = null,
    private val queryMovementHistoryUseCase: QueryMovementHistory? = null,
    savedStateHandle: SavedStateHandle? = null,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedFilterType = MutableStateFlow<MovementType?>(null)
    private val _showRegisterSheet = MutableStateFlow(false)
    private val _voidState = MutableStateFlow(VoidDialogState())
    private val _advancedFilters = MutableStateFlow(AdvancedFiltersState())

    init {
        // Deep link query parameters; owner is always derived from local session, never from deep link
        val initialQuery = savedStateHandle?.get<String>("query")
        val initialAccountId = savedStateHandle?.get<String>("accountId")
        val initialCategoryId = savedStateHandle?.get<String>("categoryId")

        if (!initialQuery.isNullOrBlank()) {
            _searchQuery.value = initialQuery
        }
        if (!initialAccountId.isNullOrBlank() || !initialCategoryId.isNullOrBlank()) {
            _advancedFilters.update { current ->
                current.copy(
                    accountIds = if (!initialAccountId.isNullOrBlank()) setOf(initialAccountId) else current.accountIds,
                    categoryIds = if (!initialCategoryId.isNullOrBlank()) setOf(initialCategoryId) else current.categoryIds,
                )
            }
        }
    }

    private val activeUserId = sessionCoordinator.localAccess
        .map { access -> (access as? LocalAccess.Available)?.userId }
        .distinctUntilChanged()

    private val ownerTransactions = activeUserId
        .flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList<TransactionItem>())
            } else {
                movementRepository.observeTransactions(userId)
            }
        }

    private val basicFilterState = combine(
        _searchQuery,
        _selectedFilterType,
        _showRegisterSheet,
    ) { query, filterType, showSheet ->
        Triple(query, filterType, showSheet)
    }

    val uiState: StateFlow<MovementHistoryUiState> = combine(
        ownerTransactions,
        basicFilterState,
        _voidState,
        _advancedFilters,
    ) { items, (query, filterType, showSheet), voidState, adv ->
        val filtered = items.filter { item ->
            val matchesType = filterType == null || item.transaction.type == filterType
            val matchesQuery = query.isBlank() ||
                (item.categoryName?.contains(query, ignoreCase = true) == true) ||
                (item.merchantName?.contains(query, ignoreCase = true) == true) ||
                (item.sourceAccountAlias?.contains(query, ignoreCase = true) == true) ||
                (item.transaction.note?.contains(query, ignoreCase = true) == true)

            val matchesAccounts = adv.accountIds.isEmpty() ||
                (item.transaction.sourceAccountId in adv.accountIds || item.transaction.destinationAccountId in adv.accountIds)

            val matchesCategories = adv.categoryIds.isEmpty() ||
                (item.transaction.categoryId in adv.categoryIds)

            val matchesMinAmount = adv.minAmountMinor == null || item.transaction.amountMinor >= adv.minAmountMinor
            val matchesMaxAmount = adv.maxAmountMinor == null || item.transaction.amountMinor <= adv.maxAmountMinor

            val matchesDates = (adv.fromDate == null || item.transaction.occurredAt >= adv.fromDate) &&
                (adv.toDate == null || item.transaction.occurredAt < adv.toDate)

            val matchesStates = adv.financialStates.isEmpty() ||
                (item.transaction.status.name in adv.financialStates.map { it.name })

            matchesType && matchesQuery && matchesAccounts && matchesCategories &&
                matchesMinAmount && matchesMaxAmount && matchesDates && matchesStates
        }

        val grouped = groupTransactionsByDate(filtered)

        MovementHistoryUiState(
            isLoading = false,
            searchQuery = query,
            selectedFilterType = filterType,
            allTransactions = items,
            filteredTransactions = grouped,
            showRegisterSheet = showSheet && (sessionCoordinator.localAccess.value is LocalAccess.Available),
            selectedTransactionForVoid = voidState.selectedItem,
            isVoiding = voidState.isVoiding,
            voidErrorMessage = voidState.errorMessage,
            showAdvancedFilterPanel = adv.showPanel,
            selectedAccountIds = adv.accountIds,
            selectedCategoryIds = adv.categoryIds,
            minAmountMinor = adv.minAmountMinor,
            maxAmountMinor = adv.maxAmountMinor,
            fromDate = adv.fromDate,
            toDate = adv.toDate,
            selectedFinancialStates = adv.financialStates,
            hasMorePages = false,
            accessStatus = MovementHistoryAccessDecision.Allowed,
            fallbackUsed = false,
            accessMessage = null,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MovementHistoryUiState(),
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onFilterTypeSelected(type: MovementType?) {
        _selectedFilterType.value = type
    }

    fun onDateRangeSelected(from: Long?, to: Long?) {
        _advancedFilters.update { it.copy(fromDate = from, toDate = to) }
    }

    fun onAccountFilterToggled(accountId: String) {
        _advancedFilters.update { current ->
            val updated = if (accountId in current.accountIds) current.accountIds - accountId else current.accountIds + accountId
            current.copy(accountIds = updated)
        }
    }

    fun onCategoryFilterToggled(categoryId: String) {
        _advancedFilters.update { current ->
            val updated = if (categoryId in current.categoryIds) current.categoryIds - categoryId else current.categoryIds + categoryId
            current.copy(categoryIds = updated)
        }
    }

    fun onAmountRangeChanged(min: Long?, max: Long?) {
        _advancedFilters.update { it.copy(minAmountMinor = min, maxAmountMinor = max) }
    }

    fun onFinancialStateToggled(state: MovementFinancialState) {
        _advancedFilters.update { current ->
            val updated = if (state in current.financialStates) current.financialStates - state else current.financialStates + state
            current.copy(financialStates = updated)
        }
    }

    fun onToggleAdvancedFilterPanel(show: Boolean) {
        _advancedFilters.update { it.copy(showPanel = show) }
    }

    fun onClearFilters() {
        _searchQuery.value = ""
        _selectedFilterType.value = null
        _advancedFilters.value = AdvancedFiltersState()
    }

    fun onResetAdvancedFilters() {
        _advancedFilters.value = AdvancedFiltersState(showPanel = _advancedFilters.value.showPanel)
    }

    fun showAllTransactionsAfterTransfer() {
        _selectedFilterType.value = null
        _searchQuery.value = ""
    }

    fun onOpenRegisterSheet() {
        _showRegisterSheet.value = true
    }

    fun onCloseRegisterSheet() {
        _showRegisterSheet.value = false
    }

    fun onSelectTransactionForVoid(item: TransactionItem) {
        _voidState.update { it.copy(selectedItem = item, errorMessage = null) }
    }

    fun onDismissVoidDialog() {
        _voidState.update { it.copy(selectedItem = null, errorMessage = null) }
    }

    fun onConfirmVoid(reason: String?, onCompleted: (Boolean, String?) -> Unit = { _, _ -> }) {
        val targetItem = _voidState.value.selectedItem ?: return
        val userId = (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId
        if (userId == null) {
            _voidState.update { it.copy(errorMessage = "Sesión no disponible") }
            onCompleted(false, "Sesión no disponible")
            return
        }

        viewModelScope.launch {
            _voidState.update { it.copy(isVoiding = true, errorMessage = null) }
            try {
                val head = maintenanceRepository?.getRevisionHead(userId, targetItem.transaction.id)
                if (head == null) {
                    _voidState.update { it.copy(isVoiding = false, errorMessage = "No se pudo obtener la revisión del movimiento") }
                    onCompleted(false, "No se pudo obtener la revisión del movimiento")
                    return@launch
                }

                if (head.financialState == MovementFinancialState.VOIDED) {
                    _voidState.update { it.copy(isVoiding = false, selectedItem = null) }
                    onCompleted(true, "El movimiento ya se encuentra anulado")
                    return@launch
                }

                val command = MovementRevisionCommand.Void(
                    idempotencyKey = UUID.randomUUID().toString(),
                    transactionId = targetItem.transaction.id,
                    expectedRevision = head.commandBaseRevision,
                    reason = reason,
                )

                val result = voidTransactionUseCase?.invoke(userId, command)
                when (result) {
                    is MovementMutationResult.Success -> {
                        _voidState.update { it.copy(isVoiding = false, selectedItem = null) }
                        onCompleted(true, null)
                    }
                    is MovementMutationResult.Conflict -> {
                        val msg = "Conflicto de revisión: el movimiento cambió en otro dispositivo"
                        _voidState.update { it.copy(isVoiding = false, errorMessage = msg) }
                        onCompleted(false, msg)
                    }
                    is MovementMutationResult.Rejected -> {
                        val message = when (result.code) {
                            "OPERATION_SPECIALIZED" -> "No se puede anular un movimiento especializado desde el historial genérico"
                            "ALREADY_VOIDED_FOR_EDIT" -> "El movimiento ya se encuentra anulado"
                            else -> "No se pudo anular el movimiento (${result.code})"
                        }
                        _voidState.update { it.copy(isVoiding = false, errorMessage = message) }
                        onCompleted(false, message)
                    }
                    null -> {
                        val msg = "Caso de uso no configurado"
                        _voidState.update { it.copy(isVoiding = false, errorMessage = msg) }
                        onCompleted(false, msg)
                    }
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Error al anular movimiento"
                _voidState.update { it.copy(isVoiding = false, errorMessage = errorMsg) }
                onCompleted(false, errorMsg)
            }
        }
    }

    private fun groupTransactionsByDate(items: List<TransactionItem>): Map<String, List<TransactionItem>> {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val yesterdayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(System.currentTimeMillis() - 86_400_000L))
        val headerFormat = SimpleDateFormat("d 'de' MMMM", Locale.forLanguageTag("es-PE"))

        return items.groupBy { item ->
            val itemDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(item.transaction.occurredAt))
            when (itemDateStr) {
                todayStr -> "Hoy"
                yesterdayStr -> "Ayer"
                else -> headerFormat.format(Date(item.transaction.occurredAt))
            }
        }
    }
}
