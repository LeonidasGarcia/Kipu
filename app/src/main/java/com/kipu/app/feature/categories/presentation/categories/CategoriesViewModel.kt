package com.kipu.app.feature.categories.presentation.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.CategoryPresentation
import com.kipu.app.feature.categories.domain.usecase.CategoryItem
import com.kipu.app.feature.categories.domain.usecase.CreateCategory
import com.kipu.app.feature.categories.domain.usecase.ObserveCategories
import com.kipu.app.feature.categories.domain.usecase.ObserveSelectedFreeCategoryRoots
import com.kipu.app.feature.categories.domain.usecase.SaveSelectedFreeCategoryRoots
import com.kipu.app.feature.categories.domain.usecase.SetCategoryActive
import com.kipu.app.feature.categories.domain.usecase.UpdateCategoryPresentation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CategoryTab {
    TODAS,
    PERSONALIZADAS,
}

data class CategoriesUiState(
    val isLoading: Boolean = true,
    val categories: List<CategoryItem> = emptyList(),
    val selectedTab: CategoryTab = CategoryTab.TODAS,
    val activeCustomRootsCount: Int = 0,
    val maxCustomRoots: Int = 5,
    val isCreateDialogOpen: Boolean = false,
    val editingCategoryId: CategoryId? = null,
    val editingRevision: Long = 1L,
    val createParentId: CategoryId? = null,
    val createCategoryName: String = "",
    val createCategoryIcon: String = "restaurant",
    val createCategoryColor: String = "#0F766E",
    val showQuotaExceededDialog: Boolean = false,
    val categoryToDelete: CategoryItem? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val selectedFreeCategoryRootIds: Set<CategoryId> = emptySet(),
    val quotaSelectionDraft: Set<CategoryId> = emptySet(),
    val isQuotaSelectionOpen: Boolean = false,
) {
    val isFreeLimitReached: Boolean get() = activeCustomRootsCount >= maxCustomRoots
    val isEditing: Boolean get() = editingCategoryId != null
}

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val observeCategories: ObserveCategories,
    private val createCategory: CreateCategory,
    private val setCategoryActive: SetCategoryActive,
    private val updateCategoryPresentation: UpdateCategoryPresentation,
    private val observeSelectedFreeCategoryRoots: ObserveSelectedFreeCategoryRoots,
    private val saveSelectedFreeCategoryRoots: SaveSelectedFreeCategoryRoots,
    private val sessionCoordinator: SessionCoordinator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    private var currentUserId: UserId? = null

    init {
        viewModelScope.launch {
            sessionCoordinator.localAccess.collectLatest { access ->
                when (access) {
                    is LocalAccess.Available -> {
                        val userId = UserId(access.userId)
                        currentUserId = userId
                        observeUserCategories(userId)
                    }
                    else -> {
                        currentUserId = null
                        _uiState.update { it.copy(isLoading = false, categories = emptyList()) }
                    }
                }
            }
        }
    }

    private fun observeUserCategories(userId: UserId) {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                observeCategories(userId),
                observeSelectedFreeCategoryRoots(userId),
            ) { items, selected -> items to selected }.collectLatest { (items, selected) ->
                val activeCustomRoots = items.count { it.category.isRoot && it.category.isCustom && it.category.isActive }
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        categories = items,
                        activeCustomRootsCount = activeCustomRoots,
                        selectedFreeCategoryRootIds = selected,
                    )
                }
            }
        }
    }

    fun openQuotaSelection() {
        val state = _uiState.value
        val activeRoots = state.categories.filter { it.category.isRoot && it.category.isCustom && it.category.isActive }
            .map { it.category.id }.toSet()
        _uiState.update {
            it.copy(
                isQuotaSelectionOpen = true,
                quotaSelectionDraft = it.selectedFreeCategoryRootIds.intersect(activeRoots),
                errorMessage = null,
            )
        }
    }

    fun dismissQuotaSelection() {
        _uiState.update { it.copy(isQuotaSelectionOpen = false, quotaSelectionDraft = emptySet()) }
    }

    fun toggleQuotaSelection(categoryId: CategoryId) {
        _uiState.update { state ->
            if (categoryId in state.quotaSelectionDraft) {
                state.copy(quotaSelectionDraft = state.quotaSelectionDraft - categoryId)
            } else if (state.quotaSelectionDraft.size < state.maxCustomRoots) {
                state.copy(quotaSelectionDraft = state.quotaSelectionDraft + categoryId, errorMessage = null)
            } else {
                state.copy(errorMessage = "Puedes elegir hasta ${state.maxCustomRoots} categorías raíz")
            }
        }
    }

    fun saveQuotaSelection() {
        val userId = currentUserId ?: return
        val selected = _uiState.value.quotaSelectionDraft
        if (selected.size > _uiState.value.maxCustomRoots) return
        viewModelScope.launch {
            saveSelectedFreeCategoryRoots(userId, selected).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isQuotaSelectionOpen = false,
                            selectedFreeCategoryRootIds = selected,
                            quotaSelectionDraft = emptySet(),
                            successMessage = "Selección del plan guardada en este dispositivo",
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(errorMessage = error.message ?: "No se pudo guardar la selección") }
                },
            )
        }
    }

    fun onTabSelected(tab: CategoryTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun openCreateDialog(parentId: CategoryId? = null) {
        val state = _uiState.value
        // If user is Free and already has 5 active custom roots and tries to create another root
        if (parentId == null && state.isFreeLimitReached) {
            _uiState.update { it.copy(showQuotaExceededDialog = true) }
            return
        }
        _uiState.update {
            it.copy(
                isCreateDialogOpen = true,
                editingCategoryId = null,
                createParentId = parentId,
                createCategoryName = "",
                createCategoryIcon = if (parentId == null) "restaurant" else "shopping_cart",
                createCategoryColor = "#0F766E",
                errorMessage = null,
            )
        }
    }

    fun openEditDialog(item: CategoryItem) {
        _uiState.update {
            it.copy(
                isCreateDialogOpen = true,
                editingCategoryId = item.category.id,
                editingRevision = item.presentation?.revision ?: 1L,
                createParentId = item.category.parentId,
                createCategoryName = item.displayName,
                createCategoryIcon = item.icon,
                createCategoryColor = item.color,
                errorMessage = null,
            )
        }
    }

    fun closeCreateDialog() {
        _uiState.update {
            it.copy(
                isCreateDialogOpen = false,
                editingCategoryId = null,
                createParentId = null,
                createCategoryName = "",
                errorMessage = null,
            )
        }
    }

    fun dismissQuotaDialog() {
        _uiState.update { it.copy(showQuotaExceededDialog = false) }
    }

    fun requestDeleteCategory(item: CategoryItem) {
        _uiState.update { it.copy(categoryToDelete = item) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(categoryToDelete = null) }
    }

    fun confirmDeleteCategory() {
        val item = _uiState.value.categoryToDelete ?: return
        viewModelScope.launch {
            // Inactivating a category acts as deleting/disabling it per domain rules
            val result = setCategoryActive(item.category.id, false)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            categoryToDelete = null,
                            successMessage = "Categoría inactivada con éxito",
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            categoryToDelete = null,
                            errorMessage = error.message ?: "Error al inactivar la categoría",
                        )
                    }
                }
            )
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(createCategoryName = name) }
    }

    fun onIconChanged(icon: String) {
        _uiState.update { it.copy(createCategoryIcon = icon) }
    }

    fun onColorChanged(color: String) {
        _uiState.update { it.copy(createCategoryColor = color) }
    }

    fun onParentIdChanged(parentId: CategoryId?) {
        _uiState.update { it.copy(createParentId = parentId) }
    }

    fun submitCreateCategory() {
        val userId = currentUserId ?: run {
            _uiState.update { it.copy(errorMessage = "No active user session") }
            return
        }

        val state = _uiState.value
        val name = state.createCategoryName.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "El nombre de la categoría no puede estar vacío") }
            return
        }

        viewModelScope.launch {
            if (state.isEditing) {
                val editingId = state.editingCategoryId!!
                val result = updateCategoryPresentation(
                    presentation = CategoryPresentation(
                        categoryId = editingId,
                        ownerId = userId,
                        name = name,
                        icon = state.createCategoryIcon,
                        color = state.createCategoryColor,
                        revision = state.editingRevision,
                    ),
                    expectedRevision = state.editingRevision,
                )
                result.fold(
                    onSuccess = {
                        closeCreateDialog()
                        _uiState.update { it.copy(successMessage = "Categoría actualizada con éxito") }
                    },
                    onFailure = { error ->
                        _uiState.update { it.copy(errorMessage = error.message ?: "Error al actualizar la categoría") }
                    }
                )
            } else {
                if (state.createParentId == null && state.isFreeLimitReached) {
                    _uiState.update { it.copy(showQuotaExceededDialog = true) }
                    return@launch
                }
                val result = createCategory(
                    ownerId = userId,
                    name = name,
                    icon = state.createCategoryIcon,
                    color = state.createCategoryColor,
                    parentId = state.createParentId,
                )

                result.fold(
                    onSuccess = {
                        closeCreateDialog()
                        _uiState.update { it.copy(successMessage = "Categoría creada con éxito") }
                    },
                    onFailure = { error ->
                        _uiState.update { it.copy(errorMessage = error.message ?: "Error al crear la categoría") }
                    }
                )
            }
        }
    }

    fun toggleCategoryActive(categoryId: CategoryId, currentActive: Boolean) {
        viewModelScope.launch {
            val targetState = !currentActive
            val result = setCategoryActive(categoryId, targetState)
            result.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Error al actualizar estado") }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }
}
