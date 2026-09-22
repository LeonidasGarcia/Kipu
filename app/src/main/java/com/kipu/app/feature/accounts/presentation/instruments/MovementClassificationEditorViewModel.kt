package com.kipu.app.feature.accounts.presentation.instruments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.finance.domain.model.MovementId
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.domain.usecase.CategoryItem
import com.kipu.app.feature.categories.domain.usecase.ObserveCategories
import com.kipu.app.feature.categories.domain.usecase.SearchMerchantCatalog
import com.kipu.app.feature.categories.domain.usecase.UpdateMovementClassification
import com.kipu.app.feature.categories.presentation.components.CatalogStatus
import com.kipu.app.feature.categories.presentation.components.MerchantPickerState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MovementClassificationEditorState(
    val movementId: MovementId? = null,
    val selectedCategoryId: CategoryId? = null,
    val selectedCategoryItem: CategoryItem? = null,
    val availableCategories: List<CategoryItem> = emptyList(),
    val merchantPickerState: MerchantPickerState = MerchantPickerState(),
    val isSaving: Boolean = false,
    val isSavedSuccessfully: Boolean = false,
    val errorMessage: String? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class MovementClassificationEditorViewModel @Inject constructor(
    private val updateMovementClassification: UpdateMovementClassification,
    private val observeCategories: ObserveCategories,
    private val searchMerchantCatalog: SearchMerchantCatalog,
    private val sessionCoordinator: SessionCoordinator,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val movementIdString: String? = savedStateHandle["movementId"]
    private val movementId: MovementId? = movementIdString?.let { runCatching { MovementId(it) }.getOrNull() }

    private val _uiState = MutableStateFlow(
        MovementClassificationEditorState(movementId = movementId)
    )
    val uiState: StateFlow<MovementClassificationEditorState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var currentUserId: UserId? = null

    init {
        val mid = movementId
        if (mid == null) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "ID de movimiento inválido") }
        } else {
            viewModelScope.launch {
                sessionCoordinator.localAccess.collectLatest { access ->
                    if (access is LocalAccess.Available) {
                        val userId = UserId(access.userId)
                        currentUserId = userId
                        observeData(userId, mid)
                    }
                }
            }
        }
    }

    private fun observeData(userId: UserId, mid: MovementId) {
        // Observe categories
        viewModelScope.launch {
            observeCategories(userId).collectLatest { categories ->
                _uiState.update { current ->
                    val selected = categories.findCategoryItem(current.selectedCategoryId)
                    current.copy(
                        availableCategories = categories,
                        selectedCategoryItem = selected,
                        isLoading = false,
                    )
                }
            }
        }

        // Observe movement classification
        viewModelScope.launch {
            updateMovementClassification(mid).collectLatest { classification ->
                if (classification != null) {
                    _uiState.update { current ->
                        val selected = current.availableCategories.findCategoryItem(classification.categoryId)
                        current.copy(
                            selectedCategoryId = classification.categoryId,
                            selectedCategoryItem = selected,
                            merchantPickerState = current.merchantPickerState.copy(
                                selectedMerchant = classification.merchantId?.let {
                                    MerchantCatalogEntry(it, "Comercio asignado", "")
                                },
                                provisionalText = classification.merchantProvisionalText,
                            )
                        )
                    }
                }
            }
        }
    }

    fun selectCategory(categoryId: CategoryId?) {
        _uiState.update { current ->
            val selected = current.availableCategories.findCategoryItem(categoryId)
            current.copy(
                selectedCategoryId = categoryId,
                selectedCategoryItem = selected,
            )
        }
    }

    fun clearCategory() {
        selectCategory(null)
    }

    fun onMerchantQueryChange(query: String) {
        _uiState.update { current ->
            current.copy(
                merchantPickerState = current.merchantPickerState.copy(
                    query = query,
                    isSearching = query.isNotBlank(),
                )
            )
        }

        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { current ->
                current.copy(
                    merchantPickerState = current.merchantPickerState.copy(
                        searchResults = emptyList(),
                        isSearching = false,
                    )
                )
            }
            return
        }

        searchJob = viewModelScope.launch {
            searchMerchantCatalog(query).collectLatest { results ->
                _uiState.update { current ->
                    current.copy(
                        merchantPickerState = current.merchantPickerState.copy(
                            searchResults = results,
                            isSearching = false,
                        )
                    )
                }
            }
        }
    }

    fun selectMerchant(merchant: MerchantCatalogEntry) {
        _uiState.update { current ->
            current.copy(
                merchantPickerState = current.merchantPickerState.copy(
                    selectedMerchant = merchant,
                    provisionalText = null,
                    query = "",
                    searchResults = emptyList(),
                )
            )
        }
    }

    fun setProvisionalText(text: String) {
        val trimmed = text.trim()
        _uiState.update { current ->
            current.copy(
                merchantPickerState = current.merchantPickerState.copy(
                    selectedMerchant = null,
                    provisionalText = trimmed.ifBlank { null },
                    query = "",
                    searchResults = emptyList(),
                )
            )
        }
    }

    fun clearMerchantSelection() {
        _uiState.update { current ->
            current.copy(
                merchantPickerState = current.merchantPickerState.copy(
                    selectedMerchant = null,
                    provisionalText = null,
                    query = "",
                    searchResults = emptyList(),
                )
            )
        }
    }

    fun saveClassification() {
        val mid = movementId ?: return
        val state = _uiState.value

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val result = updateMovementClassification.assignClassification(
                movementId = mid,
                categoryId = state.selectedCategoryId,
                merchantId = state.merchantPickerState.selectedMerchant?.id,
                provisionalText = state.merchantPickerState.provisionalText,
            )

            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isSaving = false, isSavedSuccessfully = true) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isSaving = false, errorMessage = error.message ?: "Error al guardar") }
                }
            )
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

private fun List<CategoryItem>.findCategoryItem(categoryId: CategoryId?): CategoryItem? {
    if (categoryId == null) return null
    for (root in this) {
        if (root.category.id == categoryId) return root
        for (sub in root.subcategories) {
            if (sub.category.id == categoryId) return sub
        }
    }
    return null
}
