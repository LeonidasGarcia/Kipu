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
import com.kipu.app.feature.auth.presentation.OnboardingScreen
import com.kipu.app.feature.auth.presentation.OnboardingViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

const val AUTH_LOGIN_ROUTE = "auth/login"
const val AUTH_REGISTER_ROUTE = "auth/register"
const val AUTH_RECOVERY_ROUTE = "auth/recovery"
const val AUTH_RESET_PASSWORD_ROUTE = "auth/reset-password"
const val AUTH_START_ROUTE = "auth/start"
const val AUTH_INTRO_ROUTE = "auth/introduction"

fun NavController.navigateToAuthLogin() {
    navigate(AUTH_LOGIN_ROUTE) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}

fun NavGraphBuilder.authDestinations(
    navController: NavController,
    onAuthenticated: (userId: String) -> Unit,
) {
    composable(AUTH_START_ROUTE) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
    composable(AUTH_INTRO_ROUTE) {
        val viewModel: OnboardingViewModel = hiltViewModel()
        val checkpoint by viewModel.checkpoint.collectAsStateWithLifecycle(initialValue = null)
        val saving by viewModel.saving.collectAsStateWithLifecycle()
        val error by viewModel.error.collectAsStateWithLifecycle()
        checkpoint?.let { initial ->
            OnboardingScreen(initialPage = initial.page, onPageChanged = viewModel::savePage,
                saving = saving, error = error, onComplete = {
                    viewModel.complete {
                        navController.navigate(AUTH_LOGIN_ROUTE) {
                            popUpTo(AUTH_INTRO_ROUTE) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                })
        }
    }
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
            onNavigateToLogin = {
                navController.navigate(AUTH_LOGIN_ROUTE) {
                    popUpTo(AUTH_LOGIN_ROUTE) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onDismissExistingAccountDialog = viewModel::dismissExistingAccountDialog,
            onDismissConfirmationDialog = viewModel::dismissConfirmationDialog,
            onLoginClick = viewModel::login,
            onNavigateToRecovery = { navController.navigate(AUTH_RECOVERY_ROUTE) },
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
            onNavigateToRegister = { navController.navigate(AUTH_REGISTER_ROUTE) { launchSingleTop = true } },
        )
    }

    composable(AUTH_RESET_PASSWORD_ROUTE) {
        val viewModel: com.kipu.app.feature.auth.presentation.RecoveryViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) { viewModel.showInvalidRecoveryLinkIfNeeded() }

        com.kipu.app.feature.auth.presentation.ResetPasswordScreen(
            uiState = uiState,
            onPasswordChanged = viewModel::onNewPasswordChanged,
            onSubmitNewPassword = viewModel::submitNewPassword,
            onNavigateToLogin = {
                navController.navigateToAuthLogin()
            },
        )
    }
}
