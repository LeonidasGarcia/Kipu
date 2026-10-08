package com.kipu.app.feature.categories.presentation.merchantrules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.model.MerchantAliasRule
import com.kipu.app.feature.categories.domain.model.MerchantAliasRuleId
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.SourceMerchantText
import com.kipu.app.feature.categories.domain.usecase.DeleteMerchantAliasRule
import com.kipu.app.feature.categories.domain.usecase.SaveMerchantAliasRule
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MerchantAliasRulesUiState(
    val ownerId: UserId? = null,
    val isPremiumVerified: Boolean = false,
    val sourceText: String = "",
    val merchantQuery: String = "",
    val merchants: List<MerchantCatalogEntry> = emptyList(),
    val catalog: List<MerchantCatalogEntry> = emptyList(),
    val candidateMerchant: MerchantCatalogEntry? = null,
    val selectedMerchant: MerchantCatalogEntry? = null,
    val rules: List<MerchantAliasRule> = emptyList(),
    val editingRule: MerchantAliasRule? = null,
    val isSaving: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class MerchantAliasRulesViewModel @Inject constructor(
    private val repository: CategoriesRepository,
    private val sessionCoordinator: SessionCoordinator,
    private val saveMerchantAliasRule: SaveMerchantAliasRule,
    private val deleteMerchantAliasRule: DeleteMerchantAliasRule,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MerchantAliasRulesUiState())
    val uiState = _uiState.asStateFlow()
    private var merchantSearchJob: Job? = null
    private var ownerObservationJob: Job? = null

    init {
        viewModelScope.launch {
            sessionCoordinator.localAccess.collectLatest { access ->
                ownerObservationJob?.cancel()
                val owner = (access as? LocalAccess.Available)?.userId?.let(::UserId)
                _uiState.value = MerchantAliasRulesUiState(ownerId = owner)
                if (owner != null) {
                    ownerObservationJob = launch {
                        combine(
                            repository.observeMerchantAliasRules(owner),
                            repository.observePremiumVerified(owner),
                            repository.observeMerchantCatalog(),
                        ) { rules, premium, catalog -> Triple(rules, premium, catalog) }
                            .collect { (rules, premium, catalog) ->
                                _uiState.update { current ->
                                    current.copy(
                                        rules = rules,
                                        isPremiumVerified = premium,
                                        catalog = catalog,
                                        selectedMerchant = current.selectedMerchant?.let { selected ->
                                            catalog.firstOrNull { it.id == selected.id && it.isActive }
                                        },
                                        candidateMerchant = current.candidateMerchant?.let { candidate ->
                                            catalog.firstOrNull { it.id == candidate.id && it.isActive }
                                        },
                                    )
                                }
                            }
                    }
                }
            }
        }
    }

    fun onSourceTextChange(value: String) {
        _uiState.update { it.copy(sourceText = value, message = null) }
    }

    fun onMerchantQueryChange(value: String) {
        merchantSearchJob?.cancel()
        _uiState.update {
            it.copy(merchantQuery = value, merchants = emptyList(), candidateMerchant = null, selectedMerchant = null)
        }
        if (value.isBlank()) return
        merchantSearchJob = viewModelScope.launch {
            repository.searchMerchants(value).collectLatest { matches ->
                _uiState.update { it.copy(merchants = matches.filter(MerchantCatalogEntry::isActive) ) }
            }
        }
    }

    fun onSelectMerchant(merchant: MerchantCatalogEntry) {
        _uiState.update { it.copy(candidateMerchant = merchant, selectedMerchant = null, message = null) }
    }

    fun onConfirmMerchant() {
        val candidate = _uiState.value.candidateMerchant ?: return
        _uiState.update { it.copy(selectedMerchant = candidate, candidateMerchant = null, message = null) }
    }

    fun onEdit(ruleId: MerchantAliasRuleId) {
        val state = _uiState.value
        val rule = state.rules.firstOrNull { it.id == ruleId && it.ownerId == state.ownerId && it.deletedAt == null } ?: return
        val merchant = state.catalog.firstOrNull { it.id == rule.merchantId && it.isActive } ?: return
        merchantSearchJob?.cancel()
        _uiState.update {
            it.copy(
                sourceText = rule.normalizedPattern,
                merchantQuery = merchant.name,
                merchants = emptyList(),
                candidateMerchant = null,
                selectedMerchant = merchant,
                editingRule = rule,
                message = null,
            )
        }
    }

    fun onCancelEdit() {
        _uiState.update {
            it.copy(
                sourceText = "", merchantQuery = "", merchants = emptyList(), candidateMerchant = null,
                selectedMerchant = null, editingRule = null, message = null,
            )
        }
    }

    fun onSave() {
        val state = _uiState.value
        val owner = state.ownerId ?: return
        val merchant = state.selectedMerchant ?: return
        if (state.sourceText.isBlank() || (!state.isPremiumVerified && state.editingRule == null)) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            val result = saveMerchantAliasRule(
                ownerId = owner,
                sourceText = SourceMerchantText(state.sourceText),
                merchant = merchant,
                confirmedMerchantId = merchant.id,
                existingRule = state.editingRule,
            )
            _uiState.update {
                it.copy(
                    isSaving = false,
                    message = if (result.isSuccess) "Alias guardado para operaciones futuras." else
                        result.exceptionOrNull()?.message ?: "No se pudo guardar el alias.",
                    sourceText = if (result.isSuccess) "" else it.sourceText,
                    editingRule = if (result.isSuccess) null else it.editingRule,
                    selectedMerchant = if (result.isSuccess) null else it.selectedMerchant,
                    merchantQuery = if (result.isSuccess) "" else it.merchantQuery,
                )
            }
        }
    }

    fun onDelete(ruleId: MerchantAliasRuleId) {
        val owner = _uiState.value.ownerId ?: return
        viewModelScope.launch {
            val result = deleteMerchantAliasRule(owner, ruleId)
            _uiState.update {
                it.copy(message = if (result.isSuccess) "Alias eliminado." else
                    result.exceptionOrNull()?.message ?: "No se pudo eliminar el alias.")
            }
        }
    }
}
