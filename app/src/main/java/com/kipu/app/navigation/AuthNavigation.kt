package com.kipu.app.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.kipu.app.feature.auth.presentation.AuthNavigationEvent
import com.kipu.app.feature.auth.presentation.AuthViewModel
import com.kipu.app.feature.auth.presentation.LoginScreen
import com.kipu.app.feature.auth.presentation.RegisterScreen

const val AUTH_LOGIN_ROUTE = "auth/login"
const val AUTH_REGISTER_ROUTE = "auth/register"
const val AUTH_RECOVERY_ROUTE = "auth/recovery"

fun NavGraphBuilder.authDestinations(
    navController: NavController,
    onAuthenticated: (userId: String) -> Unit,
) {
    composable(AUTH_LOGIN_ROUTE) {
        val viewModel: AuthViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            viewModel.navigationEvents.collect { event ->
                when (event) {
                    is AuthNavigationEvent.NavigateToHome -> onAuthenticated(event.userId)
                    is AuthNavigationEvent.NavigateToRegister -> navController.navigate(AUTH_REGISTER_ROUTE)
                    is AuthNavigationEvent.NavigateToRecovery -> navController.navigate(AUTH_RECOVERY_ROUTE)
                    else -> Unit
                }
            }
        }

        LoginScreen(
            uiState = uiState,
            onEmailChanged = viewModel::onEmailChanged,
            onPasswordChanged = viewModel::onPasswordChanged,
            onLoginClick = viewModel::login,
            onRegisterClick = viewModel::register,
            onNavigateToRecovery = { navController.navigate(AUTH_RECOVERY_ROUTE) },
            onDismissExistingAccountDialog = viewModel::dismissExistingAccountDialog,
            onDismissConfirmationDialog = viewModel::dismissConfirmationDialog,
        )
    }

    composable(AUTH_REGISTER_ROUTE) {
        val viewModel: AuthViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            viewModel.navigationEvents.collect { event ->
                when (event) {
                    is AuthNavigationEvent.NavigateToHome -> onAuthenticated(event.userId)
                    is AuthNavigationEvent.NavigateToLogin -> navController.navigate(AUTH_LOGIN_ROUTE)
                    else -> Unit
                }
            }
        }

        RegisterScreen(
            uiState = uiState,
            onEmailChanged = viewModel::onEmailChanged,
            onPasswordChanged = viewModel::onPasswordChanged,
            onRegisterClick = viewModel::register,
            onNavigateToLogin = { navController.navigate(AUTH_LOGIN_ROUTE) },
            onDismissExistingAccountDialog = viewModel::dismissExistingAccountDialog,
            onDismissConfirmationDialog = viewModel::dismissConfirmationDialog,
        )
    }

    composable(AUTH_RECOVERY_ROUTE) {
        val viewModel: com.kipu.app.feature.auth.presentation.RecoveryViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        com.kipu.app.feature.auth.presentation.RecoveryScreen(
            uiState = uiState,
            onEmailChanged = viewModel::onEmailChanged,
            onSubmitRecovery = viewModel::submitRecoveryRequest,
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
