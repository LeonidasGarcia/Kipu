package com.kipu.app.feature.auth.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun LoginScreen(
    uiState: AuthUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onNavigateToRecovery: () -> Unit,
    onDismissExistingAccountDialog: () -> Unit = {},
    onDismissConfirmationDialog: () -> Unit = {},
    initialRegisterMode: Boolean = false,
    modifier: Modifier = Modifier,
    onNavigateToRegister: (() -> Unit)? = null,
) {
    AuthFormScreen(uiState, onEmailChanged, onPasswordChanged, onLoginClick,
        onRegisterClick, onNavigateToRecovery, onDismissExistingAccountDialog,
        onDismissConfirmationDialog, initialRegisterMode, modifier,
        onNavigateToRegister = onNavigateToRegister)
}
