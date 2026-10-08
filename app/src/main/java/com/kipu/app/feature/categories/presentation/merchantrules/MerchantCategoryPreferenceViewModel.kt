package com.kipu.app.feature.categories.presentation.merchantrules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.CategoryRules
import com.kipu.app.feature.categories.domain.model.Category
import com.kipu.app.feature.categories.domain.model.CategoryPresentation
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.MerchantCategoryPreference
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.domain.usecase.DeleteMerchantCategoryPreference
import com.kipu.app.feature.categories.domain.usecase.SetMerchantCategoryPreference
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MerchantCategoryChoice(
    val category: Category,
    val displayName: String,
)

data class MerchantCategoryPreferenceUiState(
    val ownerId: UserId? = null,
    val merchants: List<MerchantCatalogEntry> = emptyList(),
    val catalog: List<MerchantCatalogEntry> = emptyList(),
    val merchantQuery: String = "",
    val selectedMerchant: MerchantCatalogEntry? = null,
    val eligibleCategories: List<MerchantCategoryChoice> = emptyList(),
    val selectedCategoryId: com.kipu.app.feature.categories.domain.model.CategoryId? = null,
    val allPreferences: List<MerchantCategoryPreference> = emptyList(),
    val existingPreference: MerchantCategoryPreference? = null,
    val needsNewChoice: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class MerchantCategoryPreferenceViewModel @Inject constructor(
    private val repository: CategoriesRepository,
    private val sessionCoordinator: SessionCoordinator,
    private val setPreference: SetMerchantCategoryPreference,
    private val deletePreference: DeleteMerchantCategoryPreference,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MerchantCategoryPreferenceUiState())
    val uiState = _uiState.asStateFlow()
    private var merchantSearchJob: Job? = null
    private var ownerObservationJob: Job? = null

    init {
        viewModelScope.launch {
            sessionCoordinator.localAccess.collectLatest { access ->
                ownerObservationJob?.cancel()
                val owner = (access as? LocalAccess.Available)?.userId?.let(::UserId)
                _uiState.value = MerchantCategoryPreferenceUiState(ownerId = owner)
                if (owner != null) {
                    ownerObservationJob = launch {
                        combine(
                            repository.observeMerchantCatalog(),
                            repository.observeCategories(owner),
                            repository.observeCategoryPresentations(owner),
                            repository.observeMerchantCategoryPreferences(owner),
                        ) { catalog, categories, presentations, preferences ->
                            PreferenceSnapshot(catalog, categories, presentations, preferences)
                        }.collect { snapshot -> applySnapshot(owner, snapshot) }
                    }
                }
            }
        }
    }

    fun onMerchantQueryChange(value: String) {
        merchantSearchJob?.cancel()
        _uiState.update { it.copy(merchantQuery = value, merchants = emptyList(), selectedMerchant = null) }
        if (value.isBlank()) return
        merchantSearchJob = viewModelScope.launch {
            repository.searchMerchants(value).collectLatest { results ->
                _uiState.update { it.copy(merchants = results.filter(MerchantCatalogEntry::isActive)) }
            }
        }
    }

    fun onSelectMerchant(merchant: MerchantCatalogEntry) {
        merchantSearchJob?.cancel()
        val state = _uiState.value
        val preference = state.allPreferences.firstOrNull {
            it.ownerId == state.ownerId && it.merchantId == merchant.id && it.deletedAt == null
        }
        val categoryAvailable = preference?.categoryId?.let { id -> state.eligibleCategories.any { it.category.id == id } } == true
        _uiState.update {
            it.copy(
                selectedMerchant = merchant,
                merchantQuery = merchant.name,
                merchants = emptyList(),
                selectedCategoryId = preference?.categoryId?.takeIf { categoryAvailable },
                needsNewChoice = preference != null && !categoryAvailable,
                message = if (preference != null && !categoryAvailable) {
                    "La categoría guardada ya no está disponible. Elige otra."
                } else null,
            )
        }
    }

    fun onSelectCategory(categoryId: com.kipu.app.feature.categories.domain.model.CategoryId) {
        if (_uiState.value.eligibleCategories.none { it.category.id == categoryId }) return
        _uiState.update { it.copy(selectedCategoryId = categoryId, needsNewChoice = false, message = null) }
    }

    fun onSave() {
        val state = _uiState.value
        val owner = state.ownerId ?: return
        val merchant = state.selectedMerchant ?: return
        val categoryId = state.selectedCategoryId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            val result = setPreference(owner, merchant.id, categoryId)
            _uiState.update {
                it.copy(
                    isSaving = false,
                    message = if (result.isSuccess) "Preferencia guardada para operaciones futuras." else
                        result.exceptionOrNull()?.message ?: "No se pudo guardar la preferencia.",
                    needsNewChoice = false,
                )
            }
        }
    }

    fun onClear() {
        val state = _uiState.value
        val owner = state.ownerId ?: return
        val merchant = state.selectedMerchant ?: return
        viewModelScope.launch {
            val result = deletePreference(owner, merchant.id)
            _uiState.update {
                it.copy(
                    selectedCategoryId = null,
                    existingPreference = null,
                    message = if (result.isSuccess) "Preferencia eliminada." else
                        result.exceptionOrNull()?.message ?: "No se pudo eliminar la preferencia.",
                )
            }
        }
    }

    private fun applySnapshot(owner: UserId, snapshot: PreferenceSnapshot) {
        val presentations = snapshot.presentations.associateBy(CategoryPresentation::categoryId)
        val byId = snapshot.categories.associateBy(Category::id)
        val eligible = snapshot.categories.filter { category ->
            val parent = category.parentId?.let(byId::get)
            CategoryRules.isEligibleForAssignment(category, parent) &&
                !category.isPlanLocked && parent?.isPlanLocked != true &&
                (category.ownerId == null || category.ownerId == owner)
        }.map { category ->
            MerchantCategoryChoice(
                category = category,
                displayName = presentations[category.id]?.name ?: "Categoría personal",
            )
        }.sortedBy(MerchantCategoryChoice::displayName)
        val state = _uiState.value
        val merchant = state.selectedMerchant
        val preference = merchant?.let { selected ->
            snapshot.preferences.firstOrNull { it.ownerId == owner && it.merchantId == selected.id && it.deletedAt == null }
        }
        val preferenceIsEligible = preference?.categoryId?.let { id -> eligible.any { it.category.id == id } } == true
        _uiState.update {
            it.copy(
                ownerId = owner,
                catalog = snapshot.catalog,
                eligibleCategories = eligible,
                allPreferences = snapshot.preferences,
                existingPreference = preference,
                selectedCategoryId = when {
                    preferenceIsEligible -> preference?.categoryId
                    it.selectedCategoryId != null && eligible.any { item -> item.category.id == it.selectedCategoryId } -> it.selectedCategoryId
                    else -> null
                },
                needsNewChoice = preference != null && !preferenceIsEligible,
                message = if (preference != null && !preferenceIsEligible) {
                    "La categoría guardada ya no está disponible. Elige otra."
                } else it.message,
            )
        }
    }

    private data class PreferenceSnapshot(
        val catalog: List<MerchantCatalogEntry>,
        val categories: List<Category>,
        val presentations: List<CategoryPresentation>,
        val preferences: List<MerchantCategoryPreference>,
    )
}
