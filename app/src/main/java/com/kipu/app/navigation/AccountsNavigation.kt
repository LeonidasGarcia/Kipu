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
import androidx.navigation.NavBackStackEntry
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.feature.accounts.presentation.AccountsViewModel
import com.kipu.app.feature.accounts.presentation.dashboard.DashboardScreen
import com.kipu.app.feature.accounts.presentation.detail.AccountDetailScreen
import com.kipu.app.feature.notifications.presentation.NotificationBadgeViewModel
import com.kipu.app.navigation.navigateToNotifications
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipu.app.feature.accounts.presentation.instruments.UnifiedInstrumentFormScreen
import com.kipu.app.feature.accounts.presentation.instruments.RateCatalogScreen
import com.kipu.app.feature.settings.presentation.SettingsViewModel

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
) {
    composable(ACCOUNTS_DASHBOARD_ROUTE) { backStackEntry ->
        BackHandler(enabled = movementsSelected.value) { onSelectMoney() }
        RetainedRootTabs(movementsSelected = movementsSelected) {
            Box { AccountsRootContent(navController, backStackEntry, movementsSelected) }
            Box { MovementHistoryContent(navController, backStackEntry, movementsSelected) }
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
        val viewModel: AccountsViewModel = hiltViewModel()
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
        RateCatalogScreen(
            cardId = cardId,
            products = products,
            catalogError = catalogError,
            events = viewModel.events,
            onLoadCatalog = viewModel::loadCreditProductCatalog,
            onUpdatePersonalTea = viewModel::updatePersonalTea,
            onNavigateBack = { navController.popBackStack() },
        )
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
