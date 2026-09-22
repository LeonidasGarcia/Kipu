package com.kipu.app.navigation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kipu.app.feature.accounts.presentation.instruments.MovementClassificationEditor
import com.kipu.app.feature.accounts.presentation.instruments.MovementClassificationEditorViewModel

const val MOVEMENT_EDIT_ROUTE = "movement/edit/{movementId}"

fun NavController.navigateToMovementEdit(movementId: String) {
    navigate("movement/edit/$movementId")
}

fun NavGraphBuilder.movementDestinations(
    navController: NavController,
) {
    composable(
        route = MOVEMENT_EDIT_ROUTE,
        arguments = listOf(
            navArgument("movementId") {
                type = NavType.StringType
            }
        ),
    ) {
        val viewModel: MovementClassificationEditorViewModel = hiltViewModel()
        MovementClassificationEditor(
            viewModel = viewModel,
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
