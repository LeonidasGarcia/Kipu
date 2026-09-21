package com.kipu.app.feature.auth.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthScreensTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loginScreenDisplaysAllRequiredFieldsAndButtons() {
        composeTestRule.setContent {
            LoginScreen(
                uiState = AuthUiState(),
                onEmailChanged = {},
                onPasswordChanged = {},
                onLoginClick = {},
                onNavigateToRegister = {},
                onNavigateToRecovery = {},
            )
        }

        composeTestRule.onNodeWithText("Iniciar Sesión").assertIsDisplayed()
        composeTestRule.onNodeWithText("Correo electrónico").assertIsDisplayed()
        composeTestRule.onNodeWithText("Contraseña").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ingresar").assertIsDisplayed()
        composeTestRule.onNodeWithText("¿Olvidaste tu contraseña?").assertIsDisplayed()
        composeTestRule.onNodeWithText("¿No tienes cuenta? Regístrate").assertIsDisplayed()
    }

    @Test
    fun registerScreenDisplaysFieldsAndExistingAccountDialog() {
        composeTestRule.setContent {
            RegisterScreen(
                uiState = AuthUiState(showExistingAccountDialog = true),
                onEmailChanged = {},
                onPasswordChanged = {},
                onRegisterClick = {},
                onNavigateToLogin = {},
                onDismissExistingAccountDialog = {},
                onDismissConfirmationDialog = {},
            )
        }

        composeTestRule.onNodeWithText("Crear Cuenta").assertIsDisplayed()
        composeTestRule.onNodeWithText("Registrarme").assertIsDisplayed()
        // FR-051 dialog
        composeTestRule.onNodeWithText("Cuenta existente").assertIsDisplayed()
        composeTestRule.onNodeWithText("El correo ingresado ya está asociado a una cuenta Kipu. ¿Deseas iniciar sesión?").assertIsDisplayed()
    }
}
