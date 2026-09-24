package com.kipu.app.navigation

import android.net.Uri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.compose.runtime.collectAsState
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.feature.accounts.presentation.AccountsViewModel
import com.kipu.app.feature.accounts.presentation.dashboard.DashboardScreen
import com.kipu.app.feature.accounts.presentation.detail.AccountDetailScreen
import com.kipu.app.feature.accounts.presentation.instruments.AccountFormScreen
import com.kipu.app.feature.accounts.presentation.instruments.CardFormScreen
import com.kipu.app.feature.accounts.presentation.instruments.RateCatalogScreen

const val ACCOUNTS_DASHBOARD_ROUTE = "accounts/dashboard"
const val ACCOUNT_FORM_ROUTE = "accounts/create"
const val CARD_FORM_ROUTE = "cards/register"
const val RATE_CATALOG_ROUTE = "cards/{cardId}/rates"
const val ACCOUNT_DETAIL_ROUTE = "accounts/instrument/{instrumentId}?isCard={isCard}"
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
    navigateToInstrumentDetail(accountId, isCard = false)
}

fun NavController.navigateToCardDetail(cardId: String) {
    navigateToInstrumentDetail(cardId, isCard = true)
}

private fun NavController.navigateToInstrumentDetail(instrumentId: String, isCard: Boolean) {
    navigate("accounts/instrument/${Uri.encode(instrumentId)}?isCard=$isCard")
}

fun NavGraphBuilder.accountsDestinations(
    navController: NavController,
) {
    composable(ACCOUNTS_DASHBOARD_ROUTE) { backStackEntry ->
        val viewModel: AccountsViewModel = hiltViewModel()
        val feedback = backStackEntry.savedStateHandle
            .getStateFlow<String?>(ACCOUNT_DASHBOARD_FEEDBACK_KEY, null)
            .collectAsState()
        DashboardScreen(
            viewModel = viewModel,
            onNavigateToNewAccount = { navController.navigateToAccountForm() },
            onNavigateToNewCard = { navController.navigateToCardForm() },
            onNavigateToMovements = { navController.navigate(MOVEMENTS_HISTORY_ROUTE) },
            onAccountClick = { accountId -> navController.navigateToAccountDetail(accountId) },
            onCardClick = { cardId -> navController.navigateToCardDetail(cardId) },
            onNavigateToSettings = { navController.navigate(PROFILE_SETTINGS_ROUTE) },
            feedbackMessage = feedback.value,
            onFeedbackConsumed = { backStackEntry.savedStateHandle[ACCOUNT_DASHBOARD_FEEDBACK_KEY] = null },
        )
    }

    composable(
        route = ACCOUNT_DETAIL_ROUTE,
        arguments = listOf(
            navArgument("instrumentId") { type = NavType.StringType },
            navArgument("isCard") { type = NavType.BoolType; defaultValue = false },
        ),
    ) { backStackEntry ->
        val viewModel: AccountsViewModel = hiltViewModel()
        val instrumentId = backStackEntry.arguments?.getString("instrumentId").orEmpty()
        val isCard = backStackEntry.arguments?.getBoolean("isCard") ?: false
        AccountDetailScreen(
            instrumentId = instrumentId,
            isCard = isCard,
            viewModel = viewModel,
            onNavigateBack = { navController.popBackStack() },
            onReturnToDashboard = { message ->
                navController.returnToAccountsDashboard(message)
            },
        )
    }

    composable(ACCOUNT_FORM_ROUTE) {
        val viewModel: AccountsViewModel = hiltViewModel()
        AccountFormScreen(
            viewModel = viewModel,
            onNavigateBack = { navController.popBackStack() },
            onSaveSuccess = { message -> navController.returnToAccountsDashboard(message) },
        )
    }

    composable(CARD_FORM_ROUTE) {
        val viewModel: AccountsViewModel = hiltViewModel()
        CardFormScreen(
            viewModel = viewModel,
            onNavigateBack = { navController.popBackStack() },
            onSaveSuccess = { message -> navController.returnToAccountsDashboard(message) },
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
        RateCatalogScreen(
            cardId = cardId,
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
