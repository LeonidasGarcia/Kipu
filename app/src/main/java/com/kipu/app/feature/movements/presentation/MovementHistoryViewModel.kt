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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    val recovery: HistoryAccessRecovery = HistoryAccessRecovery.IDLE,
    val selectedDetail: TransactionItem? = null,
    val detailRevisions: List<com.kipu.app.feature.movements.domain.model.MovementRevisionAudit> = emptyList(),
    val detailLoading: Boolean = false,
    val detailError: Boolean = false,
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

private data class HistoryAccessInput(
    val userId: String?,
    val query: MovementHistoryQuery,
    val requiresAdvancedAccess: Boolean,
    val filters: AdvancedFiltersState = AdvancedFiltersState(),
    val refresh: Int = 0,
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
    private val _advancedFilters = MutableStateFlow(AdvancedFiltersState())
    private val _detail = MutableStateFlow(HistoryDetailState())
    private val _recovery = MutableStateFlow(HistoryAccessRecovery.IDLE)
    private val _readRetry = MutableStateFlow(0)
    private val _accessRefresh = MutableStateFlow(0)
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

    private val ownerTransactions = combine(activeUserId, _readRetry) { owner, _ -> owner }
        .flatMapLatest { owner ->
            if (owner == null) flowOf(OwnerHistory(null)) else
                movementRepository.observeTransactions(owner)
                    .map { OwnerHistory(owner, it.filter { item -> item.transaction.userId == owner }) }
                    .onStart { emit(OwnerHistory(owner, loading = true)) }
                    .catch { emit(OwnerHistory(owner, error = true)) }
        }

    private val historyAccessStatus = combine(
        activeUserId,
        basicFilterState,
        _advancedFilters,
        offlineEntitlementRefreshTicker(),
        _accessRefresh,
    ) { userId, basic, advanced, _, _ ->
        val (queryText, filterType, _) = basic
        HistoryAccessInput(
            userId = userId,
            query = MovementHistoryQuery(
                queryText = queryText.takeIf(String::isNotBlank),
                fromInclusive = advanced.fromDate,
                toExclusive = advanced.toDate,
                types = filterType?.let(::setOf) ?: emptySet(),
            ),
            requiresAdvancedAccess = advanced.hasPremiumOnlyCriteria() || advanced.showPanel,
            filters = advanced,
            refresh = _accessRefresh.value,
        )
    }.flatMapLatest { input ->
        flow {
            val decision = queryMovementHistoryUseCase?.evaluateAccess(
                userId = input.userId,
                query = input.query,
                requiresAdvancedAccess = input.requiresAdvancedAccess,
            ) ?: MovementHistoryAccessDecision.Allowed
            emit(HistoryAccessEvaluation(input, decision))
        }.catch { emit(HistoryAccessEvaluation(input, MovementHistoryAccessDecision.RevalidationRequired(input.query.toBasicFallback()))) }
    }

    val uiState: StateFlow<MovementHistoryUiState> = combine(
        ownerTransactions,
        basicFilterState,
        _voidState,
        _advancedFilters,
        historyAccessStatus,
    ) { read, (query, filterType, showSheet), voidState, adv, evaluated ->
        val owner = (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId
        val ownerMatches = read.ownerId == owner
        val items = if (ownerMatches) read.items else emptyList()
        val evaluationMatches = evaluated.input.userId == owner &&
            evaluated.input.query.queryText == query.takeIf(String::isNotBlank) &&
            evaluated.input.query.types == (filterType?.let { setOf(it) } ?: emptySet<MovementType>()) &&
            evaluated.input.query.fromInclusive == adv.fromDate && evaluated.input.query.toExclusive == adv.toDate &&
            evaluated.input.requiresAdvancedAccess == (adv.hasPremiumOnlyCriteria() || adv.showPanel)
        val snapshotMatches = evaluationMatches && evaluated.input.filters == adv && evaluated.input.refresh == _accessRefresh.value
        val accessDecision = if (snapshotMatches) evaluated.decision else MovementHistoryAccessDecision.NotAuthorized
        val premiumFiltersAllowed = snapshotMatches && accessDecision == MovementHistoryAccessDecision.Allowed
        val filtered = items.filter { item ->
            val matchesType = filterType == null || item.transaction.type == filterType
            val matchesQuery = query.isBlank() ||
                (item.categoryName?.contains(query, ignoreCase = true) == true) ||
                (item.merchantName?.contains(query, ignoreCase = true) == true) ||
                (item.sourceAccountAlias?.contains(query, ignoreCase = true) == true) ||
                (item.transaction.note?.contains(query, ignoreCase = true) == true)

            val matchesAccounts = !premiumFiltersAllowed || adv.accountIds.isEmpty() ||
                (item.transaction.sourceAccountId in adv.accountIds || item.transaction.destinationAccountId in adv.accountIds)

            val matchesCategories = !premiumFiltersAllowed || adv.categoryIds.isEmpty() ||
                (item.transaction.categoryId in adv.categoryIds)

            val matchesMinAmount = !premiumFiltersAllowed || adv.minAmountMinor == null || (item.transaction.currency == adv.currency && item.transaction.amountMinor >= adv.minAmountMinor)
            val matchesMaxAmount = !premiumFiltersAllowed || adv.maxAmountMinor == null || (item.transaction.currency == adv.currency && item.transaction.amountMinor <= adv.maxAmountMinor)

            val matchesDates = (adv.fromDate == null || item.transaction.occurredAt >= adv.fromDate) &&
                (adv.toDate == null || item.transaction.occurredAt < adv.toDate)

            val financialState = when (item.transaction.status) {
                com.kipu.app.feature.movements.domain.model.TransactionStatus.ACTIVE,
                com.kipu.app.feature.movements.domain.model.TransactionStatus.CONFIRMED -> MovementFinancialState.CONFIRMED
                com.kipu.app.feature.movements.domain.model.TransactionStatus.REVISED -> MovementFinancialState.REVISED
                com.kipu.app.feature.movements.domain.model.TransactionStatus.VOIDED -> MovementFinancialState.VOIDED
                com.kipu.app.feature.movements.domain.model.TransactionStatus.FAILED -> MovementFinancialState.LEGACY_FAILED
            }
            val matchesStates = !premiumFiltersAllowed || adv.financialStates.isEmpty() || financialState in adv.financialStates

            val matchesCards = !premiumFiltersAllowed || adv.cardIds.isEmpty() || item.transaction.cardId in adv.cardIds
            val matchesMerchants = !premiumFiltersAllowed || adv.merchantIds.isEmpty() || item.transaction.merchantId in adv.merchantIds
            val matchesSync = !premiumFiltersAllowed || adv.syncStatuses.isEmpty() || item.transaction.syncStatus in adv.syncStatuses
            matchesType && matchesQuery && matchesAccounts && matchesCategories &&
                matchesMinAmount && matchesMaxAmount && matchesDates && matchesStates && matchesCards && matchesMerchants && matchesSync
        }

        val grouped = groupTransactionsByDate(filtered)

        MovementHistoryUiState(
            ownerId = owner,
            isLoading = read.loading || !ownerMatches,
            queryError = ownerMatches && read.error,
            appliedFilters = adv,
            searchQuery = query,
            selectedFilterType = filterType,
            allTransactions = items,
            filteredTransactions = grouped,
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
            hasMorePages = false,
            accessStatus = accessDecision,
            fallbackUsed = (adv.hasPremiumOnlyCriteria() || adv.showPanel) && (
                accessDecision is MovementHistoryAccessDecision.PremiumRequired ||
                    accessDecision is MovementHistoryAccessDecision.RevalidationRequired
                ),
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
            selectedDetail = state.allTransactions.firstOrNull { ownsDetail && it.transaction.id == detail.transactionId },
            detailRevisions = detail.revisions.takeIf { ownsDetail } ?: emptyList(),
            detailLoading = ownsDetail && detail.loading,
            detailError = ownsDetail && detail.error,
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
        result.filters?.let { _advancedFilters.value = it.copy(showPanel = false) }
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

    fun onOpenDetail(item: TransactionItem) {
        val owner = (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId ?: return
        if (item.transaction.userId != owner) return
        detailJob?.cancel()
        _detail.value = HistoryDetailState(owner, item.transaction.id, loading = true)
        detailJob = viewModelScope.launch {
            try {
                val revisions = maintenanceRepository?.getRevisionAudit(owner, item.transaction.id).orEmpty()
                if ((sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId == owner) {
                    _detail.value = HistoryDetailState(owner, item.transaction.id, revisions)
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
        return items.sortedByDescending { it.transaction.occurredAt }.groupBy { item ->
            val date = java.time.Instant.ofEpochMilli(item.transaction.occurredAt).atZone(zone).toLocalDate()
            when (date) {
                today -> "Hoy"
                today.minusDays(1) -> "Ayer"
                else -> format.format(date).lowercase(Locale.forLanguageTag("es-PE"))
            }
        }
    }
}

private fun AdvancedFiltersState.hasPremiumOnlyCriteria(): Boolean =
    accountIds.isNotEmpty() || categoryIds.isNotEmpty() || minAmountMinor != null ||
        maxAmountMinor != null || financialStates.isNotEmpty() ||
        cardIds.isNotEmpty() || merchantIds.isNotEmpty() || syncStatuses.isNotEmpty()


enum class HistoryAccessRecovery { IDLE, VERIFYING, VERIFIED, NO_PURCHASE, PENDING, RETRYABLE, NO_ACCESS }
private data class OwnerHistory(val ownerId: String?, val items: List<TransactionItem> = emptyList(), val loading: Boolean = false, val error: Boolean = false)
private data class HistoryAccessEvaluation(val input: HistoryAccessInput, val decision: MovementHistoryAccessDecision)
private data class HistoryDetailState(
    val ownerId: String? = null, val transactionId: String? = null,
    val revisions: List<com.kipu.app.feature.movements.domain.model.MovementRevisionAudit> = emptyList(),
    val loading: Boolean = false, val error: Boolean = false,
)
