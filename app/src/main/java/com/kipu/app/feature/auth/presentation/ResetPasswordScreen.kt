package com.kipu.app.feature.auth.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ResetPasswordScreen(
    uiState: RecoveryUiState,
    onPasswordChanged: (String) -> Unit,
    onSubmitNewPassword: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthSurface(modifier) {
        AuthCard(recovery = true) {
            Text("Restablecer contraseña", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Text("Usa entre 8 y 72 caracteres, al menos una letra y un número.")
            Spacer(Modifier.height(24.dp))
            AuthPasswordField(uiState.newPassword, onPasswordChanged, "Nueva contraseña",
                error = uiState.passwordError, enabled = !uiState.isLoading && !uiState.isPasswordResetSuccess,
                onDone = onSubmitNewPassword)
            AuthErrorMessage(uiState.errorMessage)
            if (uiState.isPasswordResetSuccess) Text("¡Tu contraseña ha sido actualizada con éxito!")
            Spacer(Modifier.height(24.dp))
            AuthPrimaryButton("Guardar contraseña", onSubmitNewPassword,
                enabled = !uiState.isPasswordResetSuccess, loading = uiState.isLoading)
            TextButton(onNavigateToLogin, Modifier.fillMaxWidth()) { Text("Ir a Iniciar Sesión") }
        }
    }
}
