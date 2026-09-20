package com.kipu.app.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.kipu.app.feature.plans.presentation.PlanSelectionRoute

const val PLAN_SELECTION_ROUTE = "onboarding/plan-selection"
fun NavGraphBuilder.planSelectionDestination(onConfirmed: () -> Unit) { composable(PLAN_SELECTION_ROUTE) { PlanSelectionRoute(onConfirmed) } }
