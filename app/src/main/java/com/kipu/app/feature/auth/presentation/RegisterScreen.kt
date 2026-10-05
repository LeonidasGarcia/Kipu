package com.kipu.app.feature.auth.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun RegisterScreen(
    uiState: AuthUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onRegisterClick: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onDismissExistingAccountDialog: () -> Unit,
    onDismissConfirmationDialog: () -> Unit,
    modifier: Modifier = Modifier,
    onLoginClick: (() -> Unit)? = null,
    onNavigateToRecovery: () -> Unit = {},
) {
    AuthFormScreen(uiState, onEmailChanged, onPasswordChanged, onLoginClick ?: {},
        onRegisterClick, onNavigateToRecovery, onDismissExistingAccountDialog, onDismissConfirmationDialog,
        initialRegisterMode = true, modifier = modifier,
        onNavigateToLogin = if (onLoginClick == null) onNavigateToLogin else null)
}
