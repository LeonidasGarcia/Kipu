package com.kipu.app.feature.movements.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.movements.domain.MovementRepository
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.TransactionItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class MovementHistoryUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val selectedFilterType: MovementType? = null, // null means "Todos"
    val allTransactions: List<TransactionItem> = emptyList(),
    val filteredTransactions: Map<String, List<TransactionItem>> = emptyMap(),
    val showRegisterSheet: Boolean = false,
)

@HiltViewModel
class MovementHistoryViewModel @Inject constructor(
    private val movementRepository: MovementRepository,
    private val sessionCoordinator: SessionCoordinator,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedFilterType = MutableStateFlow<MovementType?>(null)
    private val _showRegisterSheet = MutableStateFlow(false)

    private val userId: String
        get() = sessionCoordinator.currentOwner?.verifiedUserId
            ?: (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId
            ?: "local_user"

    val uiState: StateFlow<MovementHistoryUiState> = combine(
        movementRepository.observeTransactions(userId),
        _searchQuery,
        _selectedFilterType,
        _showRegisterSheet,
    ) { items, query, filterType, showSheet ->
        val filtered = items.filter { item ->
            val matchesType = filterType == null || item.transaction.type == filterType
            val matchesQuery = query.isBlank() ||
                (item.categoryName?.contains(query, ignoreCase = true) == true) ||
                (item.merchantName?.contains(query, ignoreCase = true) == true) ||
                (item.sourceAccountAlias?.contains(query, ignoreCase = true) == true) ||
                (item.transaction.note?.contains(query, ignoreCase = true) == true)

            matchesType && matchesQuery
        }

        val grouped = groupTransactionsByDate(filtered)

        MovementHistoryUiState(
            isLoading = false,
            searchQuery = query,
            selectedFilterType = filterType,
            allTransactions = items,
            filteredTransactions = grouped,
            showRegisterSheet = showSheet,
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

    fun onOpenRegisterSheet() {
        _showRegisterSheet.value = true
    }

    fun onCloseRegisterSheet() {
        _showRegisterSheet.value = false
    }

    private fun groupTransactionsByDate(items: List<TransactionItem>): Map<String, List<TransactionItem>> {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val yesterdayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(System.currentTimeMillis() - 86_400_000L))
        val headerFormat = SimpleDateFormat("d 'de' MMMM", Locale("es", "PE"))

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
