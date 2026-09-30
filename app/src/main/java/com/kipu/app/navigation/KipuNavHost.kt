package com.kipu.app.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.kipu.app.feature.plans.presentation.PlanSelectionRoute
import com.kipu.app.feature.plans.presentation.PlanPurchaseRoute

const val PLAN_SELECTION_ROUTE = "onboarding/plan-selection"
const val PLAN_PURCHASE_ROUTE = "billing/purchase"

fun NavGraphBuilder.planSelectionDestination(navController: NavController, onConfirmed: () -> Unit) {
    composable(PLAN_SELECTION_ROUTE) { PlanSelectionRoute(onConfirmed) }
    composable(PLAN_PURCHASE_ROUTE) { PlanPurchaseRoute(onNavigateBack = { navController.popBackStack() }) }
}

fun NavGraphBuilder.appDestinations(
    navController: NavController,
    onAuthenticated: (userId: String) -> Unit,
    onSignOut: () -> Unit,
) {
    authDestinations(navController = navController, onAuthenticated = onAuthenticated)
    planSelectionDestination(navController = navController, onConfirmed = { navController.navigate(ACCOUNTS_DASHBOARD_ROUTE) })
    settingsDestinations(navController = navController, onSignOut = onSignOut)
    accountsDestinations(navController = navController)
    movementDestinations(navController = navController)
    movementsDestinations(navController = navController)
    notificationDestinations(navController = navController)
}
