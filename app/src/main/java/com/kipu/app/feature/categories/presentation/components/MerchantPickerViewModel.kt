package com.kipu.app.feature.categories.presentation.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.domain.usecase.SearchMerchantCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CatalogStatus {
    AVAILABLE,
    STALE,
    UNAVAILABLE
}

data class MerchantPickerState(
    val query: String = "",
    val searchResults: List<MerchantCatalogEntry> = emptyList(),
    val catalogEntries: List<MerchantCatalogEntry> = emptyList(),
    val categoryFilters: List<com.kipu.app.feature.categories.domain.model.MerchantCategoryFilter> = emptyList(),
    val selectedMerchant: MerchantCatalogEntry? = null,
    val provisionalText: String? = null,
    val isSearching: Boolean = false,
    val catalogStatus: CatalogStatus = CatalogStatus.AVAILABLE,
) {
    val hasSelection: Boolean get() = selectedMerchant != null || !provisionalText.isNullOrBlank()
}

@HiltViewModel
class MerchantPickerViewModel @Inject constructor(
    private val searchMerchantCatalog: SearchMerchantCatalog,
    private val repository: CategoriesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MerchantPickerState())
    val uiState: StateFlow<MerchantPickerState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeMerchantCatalog().collectLatest { entries ->
                _uiState.update { it.copy(catalogEntries = entries) }
            }
        }
        viewModelScope.launch {
            repository.observeMerchantCategoryFilters().collectLatest { filters ->
                _uiState.update { it.copy(categoryFilters = filters) }
            }
        }
    }

    fun initialize(
        initialMerchant: MerchantCatalogEntry? = null,
        initialProvisionalText: String? = null,
    ) {
        _uiState.update {
            it.copy(
                selectedMerchant = initialMerchant,
                provisionalText = initialProvisionalText,
            )
        }
    }

    fun onQueryChanged(newQuery: String) {
        _uiState.update { it.copy(query = newQuery, isSearching = newQuery.isNotBlank()) }

        searchJob?.cancel()
        if (newQuery.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            searchMerchantCatalog(newQuery).collectLatest { results ->
                _uiState.update { current ->
                    current.copy(
                        searchResults = results,
                        isSearching = false,
                    )
                }
            }
        }
    }

    fun selectMerchant(merchant: MerchantCatalogEntry) {
        _uiState.update {
            it.copy(
                selectedMerchant = merchant,
                provisionalText = null, // Exclusivity: clear provisional text
                query = "",
                searchResults = emptyList(),
            )
        }
    }

    fun setProvisionalText(text: String) {
        val trimmed = text.trim()
        _uiState.update {
            it.copy(
                selectedMerchant = null, // Exclusivity: clear catalog merchant
                provisionalText = trimmed.ifBlank { null },
                query = "",
                searchResults = emptyList(),
            )
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedMerchant = null,
                provisionalText = null,
                query = "",
                searchResults = emptyList(),
            )
        }
    }
}
