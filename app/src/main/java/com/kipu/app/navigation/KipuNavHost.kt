package com.kipu.app.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.kipu.app.feature.plans.presentation.PlanSelectionRoute

const val PLAN_SELECTION_ROUTE = "onboarding/plan-selection"

fun NavGraphBuilder.planSelectionDestination(onConfirmed: () -> Unit) {
    composable(PLAN_SELECTION_ROUTE) { PlanSelectionRoute(onConfirmed) }
}

fun NavGraphBuilder.appDestinations(
    navController: NavController,
    onAuthenticated: (userId: String) -> Unit,
    onSignOut: () -> Unit,
) {
    authDestinations(navController = navController, onAuthenticated = onAuthenticated)
    planSelectionDestination(onConfirmed = { navController.navigate(ACCOUNTS_DASHBOARD_ROUTE) })
    settingsDestinations(navController = navController, onSignOut = onSignOut)
    accountsDestinations(navController = navController)
    movementsDestinations(navController = navController)
}
