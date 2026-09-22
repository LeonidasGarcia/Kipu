package com.kipu.app.feature.categories.presentation.categories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.model.CategoryConflict
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.CategoryPresentation
import com.kipu.app.feature.categories.domain.model.ConflictId
import com.kipu.app.feature.categories.domain.usecase.ResolveCategoryConflict
import com.kipu.app.feature.categories.domain.usecase.UpdateCategoryPresentation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoryPresentationUiState(
    val categoryId: CategoryId? = null,
    val name: String = "",
    val icon: String = "category",
    val color: String = "#3F51B5",
    val expectedRevision: Long = 1L,
    val isSystemCategory: Boolean = false,
    val isSaving: Boolean = false,
    val isSavedSuccessfully: Boolean = false,
    val conflicts: List<CategoryConflict> = emptyList(),
    val isResolving: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class CategoryPresentationViewModel @Inject constructor(
    private val updateCategoryPresentation: UpdateCategoryPresentation,
    private val resolveCategoryConflict: ResolveCategoryConflict,
    private val repository: CategoriesRepository,
    private val sessionCoordinator: SessionCoordinator,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val categoryIdString: String? = savedStateHandle["categoryId"]
    private val categoryId: CategoryId? = categoryIdString?.let { runCatching { CategoryId(it) }.getOrNull() }

    private val _uiState = MutableStateFlow(
        CategoryPresentationUiState(categoryId = categoryId)
    )
    val uiState: StateFlow<CategoryPresentationUiState> = _uiState.asStateFlow()

    private var currentUserId: UserId? = null

    init {
        viewModelScope.launch {
            sessionCoordinator.localAccess.collectLatest { access ->
                if (access is LocalAccess.Available) {
                    val userId = UserId(access.userId)
                    currentUserId = userId
                    loadCategoryData(userId)
                    observeConflicts(userId)
                }
            }
        }
    }

    private fun loadCategoryData(userId: UserId) {
        val cid = categoryId ?: run {
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        viewModelScope.launch {
            val category = repository.getCategory(cid)
            val presentation = category?.let { repository.observeCategoryPresentations(userId) }

            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    isSystemCategory = category?.isSystem ?: false,
                    expectedRevision = category?.revision ?: 1L,
                )
            }
        }
    }

    private fun observeConflicts(userId: UserId) {
        viewModelScope.launch {
            resolveCategoryConflict.observeConflicts(userId).collectLatest { conflictList ->
                _uiState.update { it.copy(conflicts = conflictList) }
            }
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun onIconChanged(icon: String) {
        _uiState.update { it.copy(icon = icon) }
    }

    fun onColorChanged(color: String) {
        _uiState.update { it.copy(color = color) }
    }

    fun savePresentation() {
        val userId = currentUserId ?: return
        val cid = categoryId ?: return
        val state = _uiState.value

        val trimmedName = state.name.trim()
        if (trimmedName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "El nombre de la categoría no puede estar vacío") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val presentation = CategoryPresentation(
                categoryId = cid,
                ownerId = userId,
                name = trimmedName,
                icon = state.icon,
                color = state.color,
                revision = state.expectedRevision,
            )

            val result = updateCategoryPresentation(presentation, state.expectedRevision)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            isSavedSuccessfully = true,
                            infoMessage = "Aspecto de categoría guardado. Tus movimientos pasados permanecen intactos.",
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.message ?: "Error al guardar personalización",
                        )
                    }
                }
            )
        }
    }

    fun resolveConflict(conflictId: ConflictId, chosenVersion: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isResolving = true) }
            val result = resolveCategoryConflict.resolve(conflictId, chosenVersion)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isResolving = false,
                            infoMessage = "Conflicto resuelto exitosamente",
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isResolving = false,
                            errorMessage = error.message ?: "Error al resolver conflicto",
                        )
                    }
                }
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }
}
