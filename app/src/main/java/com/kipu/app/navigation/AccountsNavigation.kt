package com.kipu.app.navigation

import android.net.Uri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.feature.accounts.presentation.AccountsViewModel
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.presentation.dashboard.DashboardScreen
import com.kipu.app.feature.accounts.presentation.detail.AccountDetailScreen
import com.kipu.app.feature.notifications.presentation.NotificationBadgeViewModel
import com.kipu.app.navigation.navigateToNotifications
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipu.app.feature.accounts.presentation.instruments.UnifiedInstrumentFormScreen
import com.kipu.app.feature.accounts.presentation.instruments.RateCatalogScreen
import com.kipu.app.feature.settings.presentation.SettingsViewModel
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.presentation.DebtListScreen
import com.kipu.app.feature.debts.presentation.DebtListViewModel

const val ACCOUNTS_DASHBOARD_ROUTE = "accounts/dashboard"
const val ACCOUNT_FORM_ROUTE = "accounts/create"
const val CARD_FORM_ROUTE = "cards/register"
const val RATE_CATALOG_ROUTE = "cards/{cardId}/rates"
const val ACCOUNT_DETAIL_ROUTE = "accounts/instrument/{instrumentId}?isCard={isCard}&startPurchase={startPurchase}"
const val ACCOUNT_DASHBOARD_FEEDBACK_KEY = "account_dashboard_feedback"

private fun NavController.returnToAccountsDashboard(message: String) {
    previousBackStackEntry?.savedStateHandle?.set(ACCOUNT_DASHBOARD_FEEDBACK_KEY, message)
    popBackStack()
}

fun NavController.navigateToAccountForm() {
    navigate(ACCOUNT_FORM_ROUTE)
}

fun NavController.navigateToCardForm() {
    navigate(CARD_FORM_ROUTE)
}

fun NavController.navigateToRateCatalog(cardId: String) {
    navigate("cards/$cardId/rates")
}

fun NavController.navigateToAccountDetail(accountId: String) {
    navigateToInstrumentDetail(accountId, isCard = false, startPurchase = false)
}

fun NavController.navigateToCardDetail(cardId: String, startPurchase: Boolean = false) {
    navigateToInstrumentDetail(cardId, isCard = true, startPurchase = startPurchase)
}

private fun NavController.navigateToInstrumentDetail(instrumentId: String, isCard: Boolean, startPurchase: Boolean) {
    navigate("accounts/instrument/${Uri.encode(instrumentId)}?isCard=$isCard&startPurchase=$startPurchase")
}

fun NavGraphBuilder.accountsDestinations(
    navController: NavController,
    movementsSelected: State<Boolean>,
    onSelectMoney: () -> Unit,
    debtsSelected: State<Boolean> = androidx.compose.runtime.mutableStateOf(false),
    onBackFromDebtTab: () -> Unit = onSelectMoney,
) {
    composable(ACCOUNTS_DASHBOARD_ROUTE) { backStackEntry ->
        val selectedTabIndex = remember(movementsSelected, debtsSelected) {
            derivedStateOf {
                when {
                    debtsSelected.value -> 2
                    movementsSelected.value -> 1
                    else -> 0
                }
            }
        }
        BackHandler(enabled = movementsSelected.value || debtsSelected.value) {
            if (debtsSelected.value) onBackFromDebtTab() else onSelectMoney()
        }
        RetainedRootTabs(selectedTabIndex = selectedTabIndex) {
            val moneySelected = selectedTabIndex.value == 0
            val movementTabSelected = selectedTabIndex.value == 1
            val debtTabSelected = selectedTabIndex.value == 2
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (moneySelected) Modifier else Modifier.clearAndSetSemantics { })
                    .rootTabInputShield(active = moneySelected),
            ) {
                AccountsRootContent(navController, backStackEntry, movementsSelected)
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (movementTabSelected) Modifier else Modifier.clearAndSetSemantics { })
                    .rootTabInputShield(active = movementTabSelected),
            ) {
                MovementHistoryContent(navController, backStackEntry, movementsSelected)
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (debtTabSelected) Modifier else Modifier.clearAndSetSemantics { })
                    .rootTabInputShield(active = debtTabSelected),
            ) {
                DebtRootContent(navController, backStackEntry)
            }
        }
    }

    composable(
        route = ACCOUNT_DETAIL_ROUTE,
        arguments = listOf(
            navArgument("instrumentId") { type = NavType.StringType },
            navArgument("isCard") { type = NavType.BoolType; defaultValue = false },
            navArgument("startPurchase") { type = NavType.BoolType; defaultValue = false },
        ),
    ) { backStackEntry ->
        val dashboardBackStackEntry = navController.previousBackStackEntry
            ?.takeIf { it.destination.route == ACCOUNTS_DASHBOARD_ROUTE }
        val viewModel: AccountsViewModel = dashboardBackStackEntry
            ?.let { hiltViewModel(it) }
            ?: hiltViewModel(backStackEntry)
        val instrumentId = backStackEntry.arguments?.getString("instrumentId").orEmpty()
        val isCard = backStackEntry.arguments?.getBoolean("isCard") ?: false
        val startPurchaseOnOpen = backStackEntry.arguments?.getBoolean("startPurchase") ?: false
        AccountDetailScreen(
            instrumentId = instrumentId,
            isCard = isCard,
            viewModel = viewModel,
            onNavigateBack = { navController.popBackStack() },
            onReturnToDashboard = { message ->
                navController.returnToAccountsDashboard(message)
            },
            onNavigateToRateCatalog = { cardId -> navController.navigateToRateCatalog(cardId) },
            startPurchaseOnOpen = startPurchaseOnOpen,
        )
    }

    composable(ACCOUNT_FORM_ROUTE) {
        val viewModel: AccountsViewModel = hiltViewModel()
        UnifiedInstrumentFormScreen(
            viewModel = viewModel,
            onNavigateBack = { navController.popBackStack() },
            onSaveSuccess = { message -> navController.returnToAccountsDashboard(message) },
            onNavigateToCardDetail = { card -> navController.navigateToCardDetail(card.id.value) },
            onNavigateToRecordConsumption = { card -> navController.navigateToCardDetail(card.id.value, startPurchase = true) },
            onNavigateToPlans = { navController.navigate(PLAN_PURCHASE_ROUTE) },
        )
    }

    composable(CARD_FORM_ROUTE) {
        val viewModel: AccountsViewModel = hiltViewModel()
        UnifiedInstrumentFormScreen(
            viewModel = viewModel,
            onNavigateBack = { navController.popBackStack() },
            onSaveSuccess = { message -> navController.returnToAccountsDashboard(message) },
            onNavigateToCardDetail = { card -> navController.navigateToCardDetail(card.id.value) },
            onNavigateToRecordConsumption = { card -> navController.navigateToCardDetail(card.id.value, startPurchase = true) },
            initialCreditCard = true,
            onNavigateToPlans = { navController.navigate(PLAN_PURCHASE_ROUTE) },
        )
    }

    composable(
        route = RATE_CATALOG_ROUTE,
        arguments = listOf(
            navArgument("cardId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        ),
    ) { backStackEntry ->
        val cardIdStr = backStackEntry.arguments?.getString("cardId")
        val cardId = cardIdStr?.let {
            runCatching { CardId(it) }.getOrNull()
        }
        val viewModel: AccountsViewModel = hiltViewModel()
        val products = viewModel.creditProductCatalog.collectAsStateWithLifecycle().value
        val catalogError = viewModel.creditCatalogError.collectAsStateWithLifecycle().value
        val catalogLoading = viewModel.creditCatalogLoading.collectAsStateWithLifecycle().value
        val instruments = viewModel.instrumentsUiState.collectAsStateWithLifecycle().value
        val creditCard = (instruments.activeCards + instruments.archivedCards)
            .filterIsInstance<CreditCard>()
            .firstOrNull { it.id == cardId }
        RateCatalogScreen(
            creditCard = creditCard,
            products = products,
            catalogError = catalogError,
            catalogLoading = catalogLoading,
            cardLoading = instruments.isLoading,
            events = viewModel.events,
            onLoadCatalog = viewModel::loadCreditProductCatalog,
            onUpdatePersonalTea = viewModel::updatePersonalTea,
            onNavigateBack = { navController.popBackStack() },
        )
    }
}

private fun Modifier.rootTabInputShield(active: Boolean): Modifier = pointerInput(active) {
    if (!active) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
    }
}

@Composable
private fun AccountsRootContent(navController: NavController, backStackEntry: NavBackStackEntry, movementsSelected: State<Boolean>) {
        val viewModel: AccountsViewModel = hiltViewModel()
        val settingsViewModel: SettingsViewModel = hiltViewModel()
        val notificationBadgeViewModel: NotificationBadgeViewModel = hiltViewModel()
        val unreadNotificationCount by notificationBadgeViewModel.unreadCount.collectAsStateWithLifecycle()
        val feedback by backStackEntry.savedStateHandle
            .getStateFlow<String?>(ACCOUNT_DASHBOARD_FEEDBACK_KEY, null)
            .collectAsStateWithLifecycle()
        val openRegisterMovement by backStackEntry.savedStateHandle
            .getStateFlow("open_register_movement", false)
            .collectAsStateWithLifecycle()
        DashboardScreen(
            viewModel = viewModel,
            onToggleMasked = { settingsViewModel.toggleHideBalances() },
            onNavigateToNewAccount = { navController.navigateToAccountForm() },
            onNavigateToNewCard = { navController.navigateToCardForm() },
            onAccountClick = { accountId -> navController.navigateToAccountDetail(accountId) },
            onCardClick = { cardId -> navController.navigateToCardDetail(cardId) },
            onNavigateToSettings = { navController.navigate(PROFILE_SETTINGS_ROUTE) },
            onNavigateToNotifications = navController::navigateToNotifications,
            onNavigateToPlans = { navController.navigate(PLAN_PURCHASE_ROUTE) },
            unreadNotificationCount = unreadNotificationCount,
            feedbackMessage = feedback,
            onFeedbackConsumed = { backStackEntry.savedStateHandle[ACCOUNT_DASHBOARD_FEEDBACK_KEY] = null },
            openRegisterMovement = openRegisterMovement && !movementsSelected.value,
            onConsumeRegisterMovement = { backStackEntry.savedStateHandle["open_register_movement"] = false },
        )
}

@Composable
private fun DebtRootContent(navController: NavController, backStackEntry: NavBackStackEntry) {
    val debtListViewModel: DebtListViewModel = hiltViewModel(backStackEntry)
    val debtListState by debtListViewModel.state.collectAsStateWithLifecycle()
    DebtListScreen(
        debts = debtListState.debts,
        selectedType = debtListState.selectedType,
        onTypeSelected = debtListViewModel::selectType,
        onDebtSelected = navController::navigateToDebtDetail,
        onAddPayable = { navController.navigateToDebtOpening(DebtObligationType.PAYABLE) },
        onAddReceivable = { navController.navigateToDebtOpening(DebtObligationType.RECEIVABLE) },
        scheduledInstallments = debtListState.scheduledInstallments,
        errorMessage = debtListState.errorMessage,
    )
}

