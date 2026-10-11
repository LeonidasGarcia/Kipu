package com.kipu.app.feature.movements.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.plans.data.entitlement.offlineEntitlementRefreshTicker
import com.kipu.app.feature.movements.domain.MovementMaintenanceRepository
import com.kipu.app.feature.movements.domain.MovementRepository
import com.kipu.app.feature.movements.domain.QueryMovementHistory
import com.kipu.app.feature.movements.domain.VoidTransaction
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
import java.math.BigInteger
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class MovementHistoryUiState(
    val ownerId: String? = null,
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
    val appliedFilters: AdvancedFiltersState = AdvancedFiltersState(),
    val queryError: Boolean = false,
    val loadMoreError: String? = null,
    val recovery: HistoryAccessRecovery = HistoryAccessRecovery.IDLE,
    val selectedDetail: TransactionItem? = null,
    val detailRevisions: List<com.kipu.app.feature.movements.domain.model.MovementRevisionAudit> = emptyList(),
    val detailLoading: Boolean = false,
    val detailError: Boolean = false,
    val totalFilteredCount: Long = 0,
    val netByCurrency: Map<String, BigInteger> = emptyMap(),
    val mostUsedAccountId: String? = null,
) {
    val hasActiveAdvancedFilters: Boolean
        get() = selectedAccountIds.isNotEmpty() ||
            selectedCategoryIds.isNotEmpty() ||
            minAmountMinor != null ||
            maxAmountMinor != null ||
            fromDate != null ||
            toDate != null ||
            selectedFinancialStates.isNotEmpty() || appliedFilters.cardIds.isNotEmpty() ||
            appliedFilters.merchantIds.isNotEmpty() || appliedFilters.syncStatuses.isNotEmpty()
}

private data class VoidDialogState(
    val selectedItem: TransactionItem? = null,
    val isVoiding: Boolean = false,
    val errorMessage: String? = null,
)

data class MovementVoidBalanceState(
    val sourceMinorUnits: Long? = null,
    val destinationMinorUnits: Long? = null,
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
    val cardIds: Set<String> = emptySet(),
    val merchantIds: Set<String> = emptySet(),
    val syncStatuses: Set<com.kipu.app.feature.movements.domain.model.MovementSyncStatus> = emptySet(),
    val currency: String? = null,
) {
    fun hasCriteria(): Boolean =
        accountIds.isNotEmpty() || categoryIds.isNotEmpty() || minAmountMinor != null ||
            maxAmountMinor != null || fromDate != null || toDate != null || financialStates.isNotEmpty() ||
            cardIds.isNotEmpty() || merchantIds.isNotEmpty() || syncStatuses.isNotEmpty()
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
    private val billingRepository: com.kipu.app.feature.plans.purchase.BillingPurchaseRepository? = null,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedFilterType = MutableStateFlow<MovementType?>(null)
    private val _showRegisterSheet = MutableStateFlow(false)
    private val _voidState = MutableStateFlow(VoidDialogState())
    val voidBalanceState: StateFlow<MovementVoidBalanceState> = _voidState
        .flatMapLatest { dialog ->
            val tx = dialog.selectedItem?.transaction
            if (tx == null) {
                flowOf(MovementVoidBalanceState())
            } else {
                val sourceBalance = tx.sourceAccountId?.let { accountId ->
                    movementRepository.observeBalance(tx.userId, accountId)
                } ?: flowOf(null)
                val destinationBalance = tx.destinationAccountId?.let { accountId ->
                    movementRepository.observeBalance(tx.userId, accountId)
                } ?: flowOf(null)
                combine(sourceBalance, destinationBalance) { source, destination ->
                    MovementVoidBalanceState(source, destination)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MovementVoidBalanceState())
    private val _advancedFilters = MutableStateFlow(AdvancedFiltersState())
    private val _detail = MutableStateFlow(HistoryDetailState())
    private val _recovery = MutableStateFlow(HistoryAccessRecovery.IDLE)
    private val _readRetry = MutableStateFlow(0)
    private val _accessRefresh = MutableStateFlow(0)
    private val _datasetInvalidation = MutableStateFlow(0)
    private val _entitlementTick = MutableStateFlow(0)
    private val _history = MutableStateFlow(HistoryLoadState())
    private val _panelAccess = MutableStateFlow(PanelAccess())
    private var detailJob: kotlinx.coroutines.Job? = null
    private var recoveryJob: kotlinx.coroutines.Job? = null

    init {
        var previousOwner = (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId
        viewModelScope.launch {
            sessionCoordinator.localAccess.collect { access ->
                val owner = (access as? LocalAccess.Available)?.userId
                if (owner != previousOwner) {
                    detailJob?.cancel(); recoveryJob?.cancel()
                    _detail.value = HistoryDetailState()
                    _recovery.value = HistoryAccessRecovery.IDLE
                    _voidState.value = VoidDialogState()
                    _advancedFilters.value = AdvancedFiltersState()
                    _searchQuery.value = ""
                    _selectedFilterType.value = null
                    _showRegisterSheet.value = false
                    previousOwner = owner
                }
            }
        }
    }

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

    private val basicFilterState = combine(
        _searchQuery,
        _selectedFilterType,
        _showRegisterSheet,
    ) { query, filterType, showSheet ->
        Triple(query, filterType, showSheet)
    }

    private val historyRequests = combine(
        activeUserId,
        basicFilterState,
        _advancedFilters,
        _accessRefresh,
        _readRetry,
    ) { userId, basic, advanced, accessRefresh, retry ->
        val (queryText, filterType, _) = basic
        HistoryRequest(
            ownerId = userId,
            query = MovementHistoryQuery(
                queryText = queryText.takeIf(String::isNotBlank),
                fromInclusive = advanced.fromDate,
                toExclusive = advanced.toDate,
                types = filterType?.let(::setOf) ?: emptySet(),
                advancedCriteria = advanced.toHistoryCriteria(),
            ),
            accessRefresh = accessRefresh,
            retry = retry,
            invalidation = 0,
            entitlementTick = 0,
        )
    }.combine(combine(_datasetInvalidation, _entitlementTick) { invalidation, entitlementTick ->
        invalidation to entitlementTick
    }) { request, (invalidation, entitlementTick) ->
        request.copy(invalidation = invalidation, entitlementTick = entitlementTick)
    }.distinctUntilChanged { previous, next -> previous.key == next.key }

    init {
        viewModelScope.launch {
            historyRequests.collect { request -> loadFirstPage(request) }
        }
        viewModelScope.launch {
            historyRequests.combine(_advancedFilters) { request, filters -> request to filters.showPanel }
                .collect { (request, showPanel) ->
                    val decision = try {
                        queryMovementHistoryUseCase?.evaluateAccess(
                            request.ownerId,
                            request.query,
                            request.query.requiresAdvancedAccess || showPanel,
                        ) ?: MovementHistoryAccessDecision.Allowed
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        MovementHistoryAccessDecision.RevalidationRequired(request.query.toBasicFallback())
                    }
                    if (isCurrent(request)) _panelAccess.value = PanelAccess(request.key, showPanel, decision)
                }
        }
        queryMovementHistoryUseCase?.let { useCase ->
            viewModelScope.launch {
                useCase.observeHistoryInvalidations().collect {
                    _datasetInvalidation.update { it + 1 }
                    refreshSelectedDetail()
                }
            }
        }
        viewModelScope.launch {
            offlineEntitlementRefreshTicker().collect {
                _entitlementTick.update { it + 1 }
            }
        }
    }

    val uiState: StateFlow<MovementHistoryUiState> = combine(
        activeUserId,
        basicFilterState,
        _voidState,
        _advancedFilters,
        combine(_history, _panelAccess) { history, panel -> history to panel },
    ) { owner, (query, filterType, showSheet), voidState, adv, (history, panel) ->
        val matchesOwner = history.ownerId == owner
        val items = history.items.takeIf { matchesOwner }.orEmpty()
        val accessDecision = if (adv.showPanel && panel.key == history.key) {
            panel.decision
        } else history.accessDecision.takeIf { matchesOwner } ?: MovementHistoryAccessDecision.NotAuthorized

        MovementHistoryUiState(
            ownerId = owner,
            isLoading = owner != null && (!matchesOwner || history.loadingFirst),
            queryError = matchesOwner && history.firstPageError,
            loadMoreError = history.loadMoreError.takeIf { matchesOwner },
            appliedFilters = adv,
            searchQuery = query,
            selectedFilterType = filterType,
            allTransactions = items,
            filteredTransactions = groupTransactionsByDate(items),
            totalFilteredCount = history.summary.totalCount.takeIf { matchesOwner } ?: 0L,
            netByCurrency = history.summary.netByCurrency.takeIf { matchesOwner }.orEmpty(),
            mostUsedAccountId = history.summary.mostUsedAccountId.takeIf { matchesOwner },
            showRegisterSheet = showSheet && (sessionCoordinator.localAccess.value is LocalAccess.Available),
            selectedTransactionForVoid = voidState.selectedItem?.takeIf { it.transaction.userId == owner },
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
            hasMorePages = matchesOwner && history.hasMore,
            isLoadingNextPage = matchesOwner && history.loadingMore,
            currentCursor = history.cursor.takeIf { matchesOwner },
            accessStatus = accessDecision,
            fallbackUsed = matchesOwner && history.fallbackUsed,
            accessMessage = when (accessDecision) {
                is MovementHistoryAccessDecision.PremiumRequired -> "PREMIUM_REQUIRED"
                is MovementHistoryAccessDecision.RevalidationRequired -> "REVALIDATION_REQUIRED"
                else -> null
            },
        )
    }.combine(combine(_detail, _recovery) { detail, recovery -> detail to recovery }) { state, (detail, recovery) ->
        val currentOwner = (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId
        val ownsDetail = detail.ownerId == currentOwner
        state.copy(
            recovery = recovery,
            selectedDetail = detail.item?.takeIf { ownsDetail },
            detailRevisions = detail.revisions.takeIf { ownsDetail } ?: emptyList(),
            detailLoading = ownsDetail && detail.loading,
            detailError = ownsDetail && detail.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
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
        _advancedFilters.update { it.copy(minAmountMinor = min, maxAmountMinor = max, currency = "PEN".takeIf { min != null || max != null }) }
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

    fun onApplyFilterDraft(draft: MovementFilterDraft): FilterDraftValidation {
        val result = draft.validate()
        result.filters?.let {
            _advancedFilters.value = it.copy(showPanel = false)
            _selectedFilterType.value = draft.movementType
        }
        return result
    }

    fun onRemoveFilter(key: String) {
        val id = key.substringAfter(':', "")
        _advancedFilters.update { current -> when (key.substringBefore(':')) {
            "account" -> current.copy(accountIds = current.accountIds - id)
            "category" -> current.copy(categoryIds = current.categoryIds - id)
            "card" -> current.copy(cardIds = current.cardIds - id)
            "merchant" -> current.copy(merchantIds = current.merchantIds - id)
            "amount" -> current.copy(minAmountMinor = null, maxAmountMinor = null, currency = null)
            "dates" -> current.copy(fromDate = null, toDate = null)
            "state" -> current.copy(financialStates = current.financialStates.filterNot { it.name == id }.toSet())
            "sync" -> current.copy(syncStatuses = current.syncStatuses.filterNot { it.name == id }.toSet())
            else -> current
        } }
    }

    fun onRetryHistory() { _readRetry.update { it + 1 } }

    fun onLoadMore() {
        val history = _history.value
        val owner = (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId ?: return
        val request = currentHistoryRequest(owner) ?: return
        if (history.ownerId != owner || history.key != request.key || !history.hasMore ||
            history.loadingFirst || history.loadingMore || history.cursor == null
        ) return

        _history.update { it.copy(loadingMore = true, loadMoreError = null) }
        viewModelScope.launch {
            try {
                val page = queryMovementHistoryUseCase?.invoke(owner, request.query.copy(cursor = history.cursor))
                    ?: return@launch
                if (!isCurrent(request) || _history.value.key != request.key) return@launch
                if (page.fallbackUsed != history.fallbackUsed) {
                    loadFirstPage(request)
                    return@launch
                }
                _history.update { current ->
                    if (current.key != request.key) current else current.copy(
                        items = (current.items + page.items)
                            .distinctBy { it.transaction.id }
                            .sortedWith(compareByDescending<TransactionItem> { it.transaction.occurredAt }
                                .thenByDescending { it.transaction.id }),
                        cursor = page.nextCursor,
                        hasMore = page.hasMore,
                        loadingMore = false,
                        accessDecision = page.accessDecision,
                    )
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (isCurrent(request)) _history.update {
                    if (it.key == request.key) it.copy(loadingMore = false, loadMoreError = error.message ?: "No se pudo cargar más movimientos") else it
                }
            }
        }
    }

    fun onOpenDetail(item: TransactionItem) {
        val owner = (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId ?: return
        if (item.transaction.userId != owner) return
        detailJob?.cancel()
        _detail.value = HistoryDetailState(owner, item.transaction.id, item = item, loading = true)
        detailJob = viewModelScope.launch {
            try {
                val refreshedItem = queryMovementHistoryUseCase?.getHistoryItemById(owner, item.transaction.id) ?: item
                val revisions = maintenanceRepository?.getRevisionAudit(owner, item.transaction.id).orEmpty()
                if ((sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId == owner) {
                    _detail.value = HistoryDetailState(owner, item.transaction.id, item = refreshedItem, revisions = revisions)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { _detail.update { it.copy(loading = false, error = true) } }
        }
    }

    fun onCloseDetail() { detailJob?.cancel(); _detail.value = HistoryDetailState() }

    fun onDismissRecovery() { if (recoveryJob?.isActive != true) _recovery.value = HistoryAccessRecovery.IDLE }

    fun onRevalidateAccess() {
        if (recoveryJob?.isActive == true) return
        val owner = (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId ?: return
        _recovery.value = HistoryAccessRecovery.VERIFYING
        recoveryJob = viewModelScope.launch {
            val result = try {
                kotlinx.coroutines.withTimeout(35_000L) { billingRepository?.restoreAndVerifyAccess() }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) { null }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            if ((sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId != owner) return@launch
            _recovery.value = when (result) {
                is com.kipu.app.feature.plans.domain.model.BillingVerificationResult.Verified ->
                    if (result.effectivePremium) HistoryAccessRecovery.VERIFIED else HistoryAccessRecovery.NO_ACCESS
                is com.kipu.app.feature.plans.domain.model.BillingVerificationResult.PaymentPending -> HistoryAccessRecovery.PENDING
                is com.kipu.app.feature.plans.domain.model.BillingVerificationResult.Retryable ->
                    if (result.code == "NO_RECOVERABLE_PURCHASE") HistoryAccessRecovery.NO_PURCHASE else HistoryAccessRecovery.RETRYABLE
                else -> HistoryAccessRecovery.RETRYABLE
            }
            _accessRefresh.update { it + 1 }
        }
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
        if (!_voidState.value.isVoiding) _voidState.update { it.copy(selectedItem = null, errorMessage = null) }
    }

    fun onConfirmVoid(reason: String?, onCompleted: (Boolean, String?) -> Unit = { _, _ -> }) {
        if (_voidState.value.isVoiding) return
        val targetItem = _voidState.value.selectedItem ?: return
        val userId = (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId
        if (userId == null || targetItem.transaction.userId != userId) {
            _voidState.update { it.copy(errorMessage = "Sesión no disponible") }
            onCompleted(false, "Sesión no disponible")
            return
        }

        _voidState.update { it.copy(isVoiding = true, errorMessage = null) }
        viewModelScope.launch {
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
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (e: Exception) {
                val errorMsg = e.message ?: "Error al anular movimiento"
                _voidState.update { it.copy(isVoiding = false, errorMessage = errorMsg) }
                onCompleted(false, errorMsg)
            }
        }
    }

    private fun groupTransactionsByDate(items: List<TransactionItem>): Map<String, List<TransactionItem>> {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        val format = java.time.format.DateTimeFormatter.ofPattern("d 'de' MMMM yyyy", Locale.forLanguageTag("es-PE"))
        return items.sortedWith(compareByDescending<TransactionItem> { it.transaction.occurredAt }
            .thenByDescending { it.transaction.id }).groupBy { item ->
            val date = java.time.Instant.ofEpochMilli(item.transaction.occurredAt).atZone(zone).toLocalDate()
            when (date) {
                today -> "Hoy"
                today.minusDays(1) -> "Ayer"
                else -> format.format(date).lowercase(Locale.forLanguageTag("es-PE"))
            }
        }
    }

    private fun currentHistoryRequest(owner: String): HistoryRequest? {
        val advanced = _advancedFilters.value
        return HistoryRequest(
            ownerId = owner,
            query = MovementHistoryQuery(
                queryText = _searchQuery.value.takeIf(String::isNotBlank),
                fromInclusive = advanced.fromDate,
                toExclusive = advanced.toDate,
                types = _selectedFilterType.value?.let(::setOf) ?: emptySet(),
                advancedCriteria = advanced.toHistoryCriteria(),
            ),
            accessRefresh = _accessRefresh.value,
            retry = _readRetry.value,
            invalidation = _datasetInvalidation.value,
            entitlementTick = _entitlementTick.value,
        )
    }

    private fun loadFirstPage(request: HistoryRequest) {
        val owner = request.ownerId
        if (owner.isNullOrBlank()) {
            _history.value = HistoryLoadState(ownerId = null, key = request.key)
            return
        }
        _history.value = HistoryLoadState(ownerId = owner, key = request.key, loadingFirst = true)
        viewModelScope.launch {
            try {
                val useCase = queryMovementHistoryUseCase ?: run {
                    // The use case is always injected in production. This keeps legacy lightweight fixtures inert.
                    _history.update { if (it.key == request.key) it.copy(loadingFirst = false) else it }
                    return@launch
                }
                val page = useCase(owner, request.query)
                val summary = useCase.getHistorySummary(owner, request.query)
                if (!isCurrent(request)) return@launch
                _history.value = HistoryLoadState(
                    ownerId = owner,
                    key = request.key,
                    items = page.items.distinctBy { it.transaction.id }.sortedWith(
                        compareByDescending<TransactionItem> { it.transaction.occurredAt }.thenByDescending { it.transaction.id },
                    ),
                    cursor = page.nextCursor,
                    hasMore = page.hasMore,
                    accessDecision = page.accessDecision,
                    fallbackUsed = page.fallbackUsed,
                    summary = summary ?: com.kipu.app.feature.movements.domain.model.MovementHistorySummary(0L, emptyMap(), null),
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (isCurrent(request)) _history.value = HistoryLoadState(
                    ownerId = owner,
                    key = request.key,
                    firstPageError = true,
                )
            }
        }
    }

    private fun isCurrent(request: HistoryRequest): Boolean =
        request.ownerId == (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId &&
            currentHistoryRequest(request.ownerId ?: return false)?.key == request.key

    private fun refreshSelectedDetail() {
        val detail = _detail.value
        val owner = (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId
        val transactionId = detail.transactionId ?: return
        if (detail.ownerId != owner || owner == null) return
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            val refreshed = try { queryMovementHistoryUseCase?.getHistoryItemById(owner, transactionId) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { return@launch }
            if ((sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId != owner ||
                _detail.value.transactionId != transactionId
            ) return@launch
            if (refreshed == null) {
                _detail.value = HistoryDetailState()
            } else {
                val revisions = try {
                    maintenanceRepository?.getRevisionAudit(owner, transactionId).orEmpty()
                } catch (_: Exception) {
                    _detail.value.revisions
                }
                _detail.value = _detail.value.copy(item = refreshed, revisions = revisions)
            }
        }
    }
}

private fun AdvancedFiltersState.hasPremiumOnlyCriteria(): Boolean =
    accountIds.isNotEmpty() || categoryIds.isNotEmpty() || minAmountMinor != null ||
        maxAmountMinor != null || financialStates.isNotEmpty() ||
        cardIds.isNotEmpty() || merchantIds.isNotEmpty() || syncStatuses.isNotEmpty()

private fun AdvancedFiltersState.toHistoryCriteria() =
    com.kipu.app.feature.movements.domain.model.AdvancedHistoryCriteria(
        accountIds = accountIds,
        cardIds = cardIds,
        categoryIds = categoryIds,
        merchantIds = merchantIds,
        minAmountMinor = minAmountMinor,
        maxAmountMinor = maxAmountMinor,
        currency = currency,
        financialStates = financialStates,
        syncStatuses = syncStatuses,
    ).takeIf { it.hasCriteria() }


enum class HistoryAccessRecovery { IDLE, VERIFYING, VERIFIED, NO_PURCHASE, PENDING, RETRYABLE, NO_ACCESS }
private data class HistoryRequest(
    val ownerId: String?,
    val query: MovementHistoryQuery,
    val accessRefresh: Int,
    val retry: Int,
    val invalidation: Int,
    val entitlementTick: Int,
) {
    // Access refreshes and dataset invalidations intentionally produce a new generation, not a new page cursor.
    val key = HistoryKey(ownerId, query, accessRefresh, retry, invalidation, entitlementTick)
}

private data class HistoryKey(
    val ownerId: String?,
    val query: MovementHistoryQuery,
    val accessRefresh: Int,
    val retry: Int,
    val invalidation: Int,
    val entitlementTick: Int,
)

private data class HistoryLoadState(
    val ownerId: String? = null,
    val key: HistoryKey? = null,
    val items: List<TransactionItem> = emptyList(),
    val cursor: MovementHistoryCursor? = null,
    val hasMore: Boolean = false,
    val loadingFirst: Boolean = false,
    val loadingMore: Boolean = false,
    val firstPageError: Boolean = false,
    val loadMoreError: String? = null,
    val accessDecision: MovementHistoryAccessDecision = MovementHistoryAccessDecision.NotAuthorized,
    val fallbackUsed: Boolean = false,
    val summary: com.kipu.app.feature.movements.domain.model.MovementHistorySummary =
        com.kipu.app.feature.movements.domain.model.MovementHistorySummary(0L, emptyMap(), null),
)
private data class PanelAccess(
    val key: HistoryKey? = null,
    val panelShown: Boolean = false,
    val decision: MovementHistoryAccessDecision = MovementHistoryAccessDecision.NotAuthorized,
)
private data class HistoryDetailState(
    val ownerId: String? = null, val transactionId: String? = null,
    val item: TransactionItem? = null,
    val revisions: List<com.kipu.app.feature.movements.domain.model.MovementRevisionAudit> = emptyList(),
    val loading: Boolean = false, val error: Boolean = false,
)
