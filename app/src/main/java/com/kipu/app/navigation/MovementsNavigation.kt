package com.kipu.app.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.kipu.app.feature.movements.presentation.MovementHistoryRoute

const val MOVEMENTS_HISTORY_ROUTE = "movements/history"

fun NavGraphBuilder.movementsDestinations(
    navController: NavController,
) {
    composable(MOVEMENTS_HISTORY_ROUTE) {
        MovementHistoryRoute(
            onNavigateToSettings = { navController.navigate("settings/account") },
            onNavigateToNotifications = { navController.navigate("settings/notifications") },
        )
    }
}
