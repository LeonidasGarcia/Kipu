package com.kipu.app.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.kipu.app.feature.movements.presentation.MovementEditorRoute
import com.kipu.app.feature.movements.presentation.MovementHistoryRoute

const val MOVEMENTS_HISTORY_ROUTE = "movements/history"
const val MOVEMENTS_HISTORY_PATTERN = "movements/history?accountId={accountId}&categoryId={categoryId}&query={query}"
const val MOVEMENT_EDITOR_ROUTE = "movements/editor/{transactionId}"

fun NavController.navigateToMovementHistory(accountId: String? = null, categoryId: String? = null, query: String? = null) {
    val params = mutableListOf<String>()
    if (!accountId.isNullOrBlank()) params.add("accountId=$accountId")
    if (!categoryId.isNullOrBlank()) params.add("categoryId=$categoryId")
    if (!query.isNullOrBlank()) params.add("query=$query")
    val route = if (params.isEmpty()) MOVEMENTS_HISTORY_ROUTE else "$MOVEMENTS_HISTORY_ROUTE?${params.joinToString("&")}"
    navigate(route)
}

fun NavController.navigateToMovementEditor(transactionId: String) {
    navigate("movements/editor/$transactionId")
}

fun NavGraphBuilder.movementsDestinations(
    navController: NavController,
) {
    composable(
        route = MOVEMENTS_HISTORY_PATTERN,
        arguments = listOf(
            navArgument("accountId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument("categoryId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument("query") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
        deepLinks = listOf(
            navDeepLink { uriPattern = "kipu://movements/history?accountId={accountId}&categoryId={categoryId}&query={query}" }
        )
    ) {
        MovementHistoryRoute(
            onNavigateToSettings = { navController.navigate(PROFILE_SETTINGS_ROUTE) },
            onNavigateToNewAccount = { navController.navigateToAccountForm() },
            onNavigateToEditor = { transactionId ->
                navController.navigateToMovementEditor(transactionId)
            },
        )
    }
    composable(
        route = MOVEMENT_EDITOR_ROUTE,
        arguments = listOf(
            navArgument("transactionId") { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val transactionId = backStackEntry.arguments?.getString("transactionId").orEmpty()
        MovementEditorRoute(
            transactionId = transactionId,
            onDismiss = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
        )
    }
}

