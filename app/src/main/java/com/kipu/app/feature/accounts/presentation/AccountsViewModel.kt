package com.kipu.app.feature.accounts.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.OperationId
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.FinancialInstrumentsRepository
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.Card
import com.kipu.app.feature.accounts.domain.model.FinancialDashboardData
import com.kipu.app.feature.accounts.domain.usecase.ArchiveInstrument
import com.kipu.app.feature.accounts.domain.usecase.CreateLiquidAccount
import com.kipu.app.feature.accounts.domain.usecase.ObserveFinancialDashboard
import com.kipu.app.feature.accounts.domain.usecase.ObserveInstruments
import com.kipu.app.feature.accounts.domain.usecase.ReactivateInstrument
import com.kipu.app.feature.accounts.domain.usecase.RecordOpeningAdjustment
import com.kipu.app.feature.accounts.domain.usecase.UpdateInstrumentAppearance
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = true,
    val dashboardData: FinancialDashboardData? = null,
    val activeAlerts: List<com.kipu.app.feature.accounts.domain.usecase.UtilizationAlert> = emptyList(),
    val isMasked: Boolean = false,
    val errorMessage: String? = null,
)

data class InstrumentsUiState(
    val isLoading: Boolean = true,
    val activeAccounts: List<Account> = emptyList(),
    val archivedAccounts: List<Account> = emptyList(),
    val activeCards: List<Card> = emptyList(),
    val archivedCards: List<Card> = emptyList(),
    val activeComputableCount: Int = 0,
    val maxFreeQuota: Int = FinancialInstrumentsRepository.FREE_TIER_MAX_COMPUTABLE_INSTRUMENTS,
    val selectedFreeInstrumentIds: Set<String> = emptySet(),
    val isMasked: Boolean = false,
)

sealed interface AccountUiEvent {
    data class ShowMessage(val message: String) : AccountUiEvent
    data class Error(val message: String) : AccountUiEvent
}

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val observeFinancialDashboard: ObserveFinancialDashboard,
    private val observeInstruments: ObserveInstruments,
    private val createLiquidAccountUseCase: CreateLiquidAccount,
    private val recordOpeningAdjustmentUseCase: RecordOpeningAdjustment,
    private val updateInstrumentAppearanceUseCase: UpdateInstrumentAppearance,
    private val archiveInstrumentUseCase: ArchiveInstrument,
    private val reactivateInstrumentUseCase: ReactivateInstrument,
    private val registerDebitCardUseCase: com.kipu.app.feature.accounts.domain.usecase.RegisterDebitCard,
    private val registerCreditCardUseCase: com.kipu.app.feature.accounts.domain.usecase.RegisterCreditCard,
    private val checkCardDuplicateUseCase: com.kipu.app.feature.accounts.domain.usecase.CheckCardDuplicate,
    private val checkUtilizationThresholds: com.kipu.app.feature.accounts.domain.usecase.CheckUtilizationThresholds,
    private val payCreditCardUseCase: com.kipu.app.feature.accounts.domain.usecase.PayCreditCard,
    private val financialInstrumentsRepository: FinancialInstrumentsRepository,
) : ViewModel() {

    private val _isMasked = MutableStateFlow(false)
    val isMasked: StateFlow<Boolean> = _isMasked.asStateFlow()

    private val _eventChannel = Channel<AccountUiEvent>(Channel.BUFFERED)
    val events = _eventChannel.receiveAsFlow()

    val dashboardUiState: StateFlow<DashboardUiState> = combine(
        observeFinancialDashboard(),
        _isMasked,
    ) { dashboardData, masked ->
        val alerts = dashboardData.creditCards.flatMap { summary ->
            checkUtilizationThresholds.getActiveAlerts(summary.card, summary.debt.minorUnits)
        }
        DashboardUiState(
            isLoading = false,
            dashboardData = dashboardData,
            activeAlerts = alerts,
            isMasked = masked,
            errorMessage = null,
        )
    }.catch { e ->
        emit(DashboardUiState(isLoading = false, errorMessage = e.message ?: "Error al cargar el dashboard"))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(isLoading = true),
    )

    val instrumentsUiState: StateFlow<InstrumentsUiState> = combine(
        observeInstruments.observeAccounts(activeOnly = false),
        observeInstruments.observeCards(activeOnly = false),
        observeInstruments.observeActiveComputableCount(),
        financialInstrumentsRepository.observeSelectedFreeInstrumentIds(),
        _isMasked,
    ) { accounts, cards, computableCount, selectedIds, masked ->
        val activeAccounts = accounts.filter { !it.isArchived }
        val archivedAccounts = accounts.filter { it.isArchived }
        val activeCards = cards.filter { !it.isArchived }
        val archivedCards = cards.filter { it.isArchived }

        InstrumentsUiState(
            isLoading = false,
            activeAccounts = activeAccounts,
            archivedAccounts = archivedAccounts,
            activeCards = activeCards,
            archivedCards = archivedCards,
            activeComputableCount = computableCount,
            selectedFreeInstrumentIds = selectedIds,
            isMasked = masked,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InstrumentsUiState(isLoading = true),
    )

    fun toggleMasked() {
        _isMasked.value = !_isMasked.value
    }

    fun observeAccountBalance(accountId: AccountId): Flow<Money> =
        financialInstrumentsRepository.observeAccountBalance(accountId)

    fun saveFreeInstrumentSelection(ids: Set<String>) {
        viewModelScope.launch {
            financialInstrumentsRepository.saveSelectedFreeInstrumentIds(ids).fold(
                onSuccess = {
                    _eventChannel.send(
                        AccountUiEvent.ShowMessage(
                            "Selección guardada en este dispositivo · Pendiente de sincronización",
                        ),
                    )
                },
                onFailure = { error -> _eventChannel.send(AccountUiEvent.Error(error.message ?: "No se pudo guardar la selección")) },
            )
        }
    }

    fun createAccount(
        alias: String,
        type: AccountType,
        currency: Currency,
        preset: AccountPreset?,
        initialBalanceMinorUnits: Long,
        colorToken: String?,
        iconToken: String?,
        onSuccess: (Account) -> Unit = {},
    ) {
        viewModelScope.launch {
            val accountId = AccountId.generate()
            val dummyUserId = UserId.generate() // Replaced by repo from sessionCoordinator
            val account = Account(
                id = accountId,
                userId = dummyUserId,
                alias = alias,
                type = type,
                currency = currency,
                preset = preset,
                colorToken = colorToken ?: preset?.defaultColorToken,
                iconToken = iconToken ?: preset?.defaultIconToken,
                initialBalance = Money(initialBalanceMinorUnits, currency),
                openedAt = Instant.now(),
            )

            createLiquidAccountUseCase(account).fold(
                onSuccess = { created ->
                    _eventChannel.send(
                        AccountUiEvent.ShowMessage("Cuenta creada en este dispositivo · Pendiente de sincronización"),
                    )
                    onSuccess(created)
                },
                onFailure = { error ->
                    _eventChannel.send(AccountUiEvent.Error(error.message ?: "Error al crear la cuenta"))
                }
            )
        }
    }

    fun recordOpeningAdjustment(
        accountId: AccountId,
        correctedAmount: Money,
        correctedDate: Instant = Instant.now(),
        onSuccess: () -> Unit = {},
    ) {
        viewModelScope.launch {
            recordOpeningAdjustmentUseCase(accountId, correctedAmount, correctedDate).fold(
                onSuccess = {
                    _eventChannel.send(
                        AccountUiEvent.ShowMessage("Saldo inicial corregido · Pendiente de sincronización"),
                    )
                    onSuccess()
                },
                onFailure = { error ->
                    _eventChannel.send(AccountUiEvent.Error(error.message ?: "Error al ajustar saldo inicial"))
                }
            )
        }
    }

    fun updateAppearance(
        accountId: AccountId,
        alias: String,
        preset: AccountPreset?,
        colorToken: String?,
        iconToken: String?,
        onSuccess: () -> Unit = {},
    ) {
        viewModelScope.launch {
            updateInstrumentAppearanceUseCase(accountId, alias, preset, colorToken, iconToken).fold(
                onSuccess = {
                    _eventChannel.send(
                        AccountUiEvent.ShowMessage("Presentación actualizada · Pendiente de sincronización"),
                    )
                    onSuccess()
                },
                onFailure = { error ->
                    _eventChannel.send(AccountUiEvent.Error(error.message ?: "Error al actualizar apariencia"))
                }
            )
        }
    }

    fun archiveInstrument(
        instrumentId: String,
        isCard: Boolean,
        onSuccess: () -> Unit = {},
    ) {
        viewModelScope.launch {
            archiveInstrumentUseCase(instrumentId, isCard).fold(
                onSuccess = {
                    _eventChannel.send(
                        AccountUiEvent.ShowMessage("Instrumento archivado · Pendiente de sincronización"),
                    )
                    onSuccess()
                },
                onFailure = { error ->
                    _eventChannel.send(AccountUiEvent.Error(error.message ?: "Error al archivar instrumento"))
                }
            )
        }
    }

    fun reactivateInstrument(
        instrumentId: String,
        isCard: Boolean,
        isComputable: Boolean,
        onSuccess: () -> Unit = {},
    ) {
        viewModelScope.launch {
            reactivateInstrumentUseCase(instrumentId, isCard, isComputable).fold(
                onSuccess = {
                    _eventChannel.send(
                        AccountUiEvent.ShowMessage("Instrumento reactivado · Pendiente de sincronización"),
                    )
                    onSuccess()
                },
                onFailure = { error ->
                    _eventChannel.send(AccountUiEvent.Error(error.message ?: "Error al reactivar instrumento"))
                }
            )
        }
    }

    suspend fun checkDuplicateCard(
        issuer: String,
        network: com.kipu.app.feature.accounts.domain.model.CardNetwork,
        lastFourDigits: String,
    ): Boolean {
        return checkCardDuplicateUseCase(issuer, network, lastFourDigits)
    }

    fun registerDebitCard(
        alias: String?,
        issuer: String,
        network: com.kipu.app.feature.accounts.domain.model.CardNetwork,
        lastFourDigits: String,
        linkedAccount: Account,
        preset: com.kipu.app.feature.accounts.domain.model.CardPreset?,
        colorToken: String?,
        iconToken: String?,
        onSuccess: (com.kipu.app.feature.accounts.domain.model.DebitCard) -> Unit = {},
    ) {
        viewModelScope.launch {
            val card = com.kipu.app.feature.accounts.domain.model.DebitCard(
                id = com.kipu.app.core.finance.domain.model.CardId.generate(),
                userId = com.kipu.app.core.finance.domain.model.UserId.generate(),
                alias = alias?.takeIf { it.isNotBlank() },
                issuer = issuer.trim(),
                network = network,
                lastFourDigits = lastFourDigits.trim(),
                currency = linkedAccount.currency,
                linkedAccountId = linkedAccount.id,
                preset = preset,
                colorToken = colorToken ?: preset?.defaultColorToken,
                iconToken = iconToken ?: preset?.defaultIconToken,
            )

            registerDebitCardUseCase(card).fold(
                onSuccess = { created ->
                    _eventChannel.send(
                        AccountUiEvent.ShowMessage("Tarjeta de débito vinculada · Pendiente de sincronización"),
                    )
                    onSuccess(created)
                },
                onFailure = { error ->
                    _eventChannel.send(AccountUiEvent.Error(error.message ?: "Error al registrar la tarjeta"))
                }
            )
        }
    }

    fun registerCreditCard(
        alias: String?,
        issuer: String,
        network: com.kipu.app.feature.accounts.domain.model.CardNetwork,
        lastFourDigits: String,
        currency: Currency,
        creditLimitMinorUnits: Long,
        billingDay: Int,
        dueDay: Int,
        personalTeaBps: Int? = null,
        preset: com.kipu.app.feature.accounts.domain.model.CardPreset?,
        colorToken: String?,
        iconToken: String?,
        onSuccess: (com.kipu.app.feature.accounts.domain.model.CreditCard) -> Unit = {},
    ) {
        viewModelScope.launch {
            val card = com.kipu.app.feature.accounts.domain.model.CreditCard(
                id = com.kipu.app.core.finance.domain.model.CardId.generate(),
                userId = com.kipu.app.core.finance.domain.model.UserId.generate(),
                alias = alias?.takeIf { it.isNotBlank() },
                issuer = issuer.trim(),
                network = network,
                lastFourDigits = lastFourDigits.trim(),
                currency = currency,
                creditLimitMinorUnits = creditLimitMinorUnits,
                billingDay = billingDay,
                dueDay = dueDay,
                personalTeaBps = personalTeaBps,
                preset = preset,
                colorToken = colorToken ?: preset?.defaultColorToken,
                iconToken = iconToken ?: preset?.defaultIconToken,
            )

            registerCreditCardUseCase(card).fold(
                onSuccess = { created ->
                    _eventChannel.send(
                        AccountUiEvent.ShowMessage("Tarjeta de crédito registrada · Pendiente de sincronización"),
                    )
                    onSuccess(created)
                },
                onFailure = { error ->
                    _eventChannel.send(AccountUiEvent.Error(error.message ?: "Error al registrar la tarjeta"))
                }
            )
        }
    }

    fun payCreditCard(
        cardId: CardId,
        sourceAccountId: AccountId,
        paymentAmount: Money,
        onSuccess: () -> Unit = {},
    ) {
        viewModelScope.launch {
            payCreditCardUseCase(cardId, sourceAccountId, paymentAmount).fold(
                onSuccess = {
                    _eventChannel.send(
                        AccountUiEvent.ShowMessage("Pago guardado en este dispositivo · Pendiente de sincronización"),
                    )
                    onSuccess()
                },
                onFailure = { error ->
                    _eventChannel.send(AccountUiEvent.Error(error.message ?: "Error al registrar el pago"))
                }
            )
        }
    }
}
