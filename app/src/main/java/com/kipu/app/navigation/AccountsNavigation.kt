package com.kipu.app.navigation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.feature.accounts.presentation.AccountsViewModel
import com.kipu.app.feature.accounts.presentation.dashboard.DashboardScreen
import com.kipu.app.feature.accounts.presentation.instruments.AccountFormScreen
import com.kipu.app.feature.accounts.presentation.instruments.CardFormScreen
import com.kipu.app.feature.accounts.presentation.instruments.RateCatalogScreen

const val ACCOUNTS_DASHBOARD_ROUTE = "accounts/dashboard"
const val ACCOUNT_FORM_ROUTE = "accounts/create"
const val CARD_FORM_ROUTE = "cards/register"
const val RATE_CATALOG_ROUTE = "cards/{cardId}/rates"

fun NavController.navigateToAccountForm() {
    navigate(ACCOUNT_FORM_ROUTE)
}

fun NavController.navigateToCardForm() {
    navigate(CARD_FORM_ROUTE)
}

fun NavController.navigateToRateCatalog(cardId: String) {
    navigate("cards/$cardId/rates")
}

fun NavGraphBuilder.accountsDestinations(
    navController: NavController,
) {
    composable(ACCOUNTS_DASHBOARD_ROUTE) {
        val viewModel: AccountsViewModel = hiltViewModel()
        DashboardScreen(
            viewModel = viewModel,
            onNavigateToNewAccount = { navController.navigateToAccountForm() },
            onNavigateToNewCard = { navController.navigateToCardForm() },
            onNavigateToMovements = { navController.navigate(MOVEMENTS_HISTORY_ROUTE) },
            onNavigateToSettings = { navController.navigate(PROFILE_SETTINGS_ROUTE) },
        )
    }

    composable(ACCOUNT_FORM_ROUTE) {
        val viewModel: AccountsViewModel = hiltViewModel()
        AccountFormScreen(
            viewModel = viewModel,
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable(CARD_FORM_ROUTE) {
        val viewModel: AccountsViewModel = hiltViewModel()
        CardFormScreen(
            viewModel = viewModel,
            onNavigateBack = { navController.popBackStack() },
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
