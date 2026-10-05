package com.kipu.app.feature.auth.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.ui.theme.KipuTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real Compose rendering in the isolated lab variant; never invokes a remote Auth command. */
class AuthVisualFlowTest {
    @get:Rule val rule = createComposeRule()

    private fun capture(name: String) {
        rule.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.filesDir, "auth-evidence/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun loginAndRegistrationRenderAndValidateConfirmation() {
        val state = mutableStateOf(AuthUiState())
        var submitted = 0
        rule.setContent {
            KipuTheme(darkTheme = false) {
                LoginScreen(state.value, { state.value = state.value.copy(email = it) },
                    { state.value = state.value.copy(password = it) }, {}, { submitted++ }, {})
            }
        }
        capture("login")
        rule.onNodeWithText("Registrarse").performClick()
        rule.onNodeWithContentDescription("Correo electrónico").performTextInput("demo@example.com")
        rule.onNodeWithContentDescription("Contraseña", useUnmergedTree = true).performTextInput("Password123")
        rule.onNodeWithContentDescription("Confirmar contraseña", useUnmergedTree = true).performTextInput("Different123")
        rule.onNodeWithText("Crear cuenta").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(0, submitted) }
        capture("register-error")
        rule.onNodeWithContentDescription("Confirmar contraseña", useUnmergedTree = true).performTextReplacement("Password123")
        rule.onNodeWithText("Crear cuenta").performScrollTo()
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNodeWithText("Kipu").performScrollTo()
        capture("register")
        rule.onNodeWithText("Crear cuenta").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, submitted) }
    }

    @Test fun registrationFitsA16WindowWithoutScrollingWithKeyboardClosed() {
        rule.setContent { KipuTheme(darkTheme = false) {
            Box(Modifier.width(384.dp).height(832.dp).testTag("a16-auth-window")) {
                LoginScreen(AuthUiState(email = "demo@example.com", password = "Password123"),
                    {}, {}, {}, {}, {}, initialRegisterMode = true)
            }
        } }
        rule.onNodeWithContentDescription("Confirmar contraseña", useUnmergedTree = true)
            .performTextInput("Password123")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.waitForIdle()
        rule.onNodeWithTag("auth-form-logo").assertIsDisplayed()
        rule.onNodeWithText("Correo electrónico").assertIsDisplayed()
        rule.onNodeWithText("Las contraseñas coinciden").assertIsDisplayed()
        rule.onNodeWithText("Crear cuenta").assertIsDisplayed()
        rule.onNodeWithText("Al registrarte aceptas los ", substring = true).assertIsDisplayed()
        val footer = rule.onNodeWithText("Al registrarte aceptas los ", substring = true).getUnclippedBoundsInRoot()
        val viewport = rule.onNodeWithTag("a16-auth-window").getUnclippedBoundsInRoot()
        assertTrue("The full legal footer fits within the A16 window", footer.bottom <= viewport.bottom)
        capture("register-a16-window")
    }

    @Test fun passwordRequirementsClearlyDistinguishPendingAndCompleted() {
        val state = mutableStateOf(AuthUiState(password = "abcdef"))
        rule.setContent { KipuTheme(darkTheme = false) {
            LoginScreen(state.value, {}, { state.value = state.value.copy(password = it) },
                {}, {}, {}, initialRegisterMode = true)
        } }
        val completed = SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Cumplido")
        val pending = SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Pendiente")
        rule.onNodeWithText("Entre 8 y 72 caracteres", substring = true).assert(pending)
        rule.onNodeWithText("Al menos una letra", substring = true).assert(completed)
        rule.onNodeWithText("Al menos un número", substring = true).assert(pending)
        capture("register-requirements-pending")
        rule.onNodeWithText("Crear cuenta").performScrollTo().performClick()
        val invalid = SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "No cumplido")
        rule.onNodeWithText("Entre 8 y 72 caracteres", substring = true).assert(invalid)
        rule.onNodeWithText("Al menos un número", substring = true).assert(invalid)
        rule.onNodeWithText("Al menos una letra", substring = true).assert(completed)
        capture("register-requirements-submitted")
        rule.runOnIdle { state.value = state.value.copy(password = "Password123") }
        rule.onAllNodes(completed).assertCountEquals(3)
        capture("register-requirements-completed")
    }

    @Test fun modeChangeKeepsEmailAndMovesCardThroughIntermediateFrames() {
        val state = mutableStateOf(AuthUiState(email = "demo@example.com", password = "Password123"))
        rule.setContent { KipuTheme(darkTheme = false) {
            LoginScreen(state.value, { state.value = state.value.copy(email = it) },
                { state.value = state.value.copy(password = it) }, {}, {}, {})
        } }
        val before = rule.onNodeWithTag("auth-form-logo").fetchSemanticsNode().boundsInRoot.top
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Registrarse").performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(100)
        val during = rule.onNodeWithTag("auth-form-logo").fetchSemanticsNode().boundsInRoot.top
        rule.mainClock.advanceTimeBy(400)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        val after = rule.onNodeWithTag("auth-form-logo").fetchSemanticsNode().boundsInRoot.top
        assertTrue("The growing form moves up", after < before)
        assertTrue("The card must pass through an intermediate position", during < before && during > after)
        rule.runOnIdle { assertEquals("demo@example.com", state.value.email); assertEquals("", state.value.password) }
        rule.onNodeWithText("Confirmar contraseña").assertExists()
        rule.onNodeWithText("Iniciar Sesión").performClick()
        rule.onNodeWithText("Ingresar").assertExists()
        rule.onNodeWithText("Confirmar contraseña").assertDoesNotExist()
    }

    @Test fun registerEntryCanSwitchToLoginAndUseTheLoginAction() {
        var login = 0
        var registration = 0
        var navigated = 0
        rule.setContent { KipuTheme(darkTheme = false) {
            RegisterScreen(AuthUiState(), {}, {}, { registration++ }, { navigated++ }, {}, {},
                onLoginClick = { login++ })
        } }
        rule.onNodeWithText("Iniciar Sesión").performClick()
        rule.onNodeWithText("Ingresar").performClick()
        rule.runOnIdle { assertEquals(1, login); assertEquals(0, registration); assertEquals(0, navigated) }
    }

    @Test fun recoveryRendersFormErrorAcceptedAndLoading() {
        val state = mutableStateOf(RecoveryUiState())
        rule.setContent { KipuTheme(darkTheme = false) { RecoveryScreen(state.value, {}, {}, {}) } }
        capture("recovery")
        rule.runOnIdle { state.value = RecoveryUiState(email = "correo-invalido", emailError = "Formato de correo no válido.") }
        capture("recovery-error")
        rule.runOnIdle { state.value = RecoveryUiState(email = "demo@example.com", isRequestAccepted = true, resendSeconds = 45) }
        capture("recovery-accepted")
        rule.onNodeWithText("¡Revisa tu bandeja de entrada!").assertExists()
        rule.onNodeWithText("Reenviar enlace en 00:45").assertIsNotEnabled()
        rule.runOnIdle { state.value = RecoveryUiState(isLoading = true) }
        capture("recovery-loading")
    }

    @Test fun pagerSwipesReturnsAndFinishesExactlyThreePages() {
        var completed = 0
        val pages = mutableListOf<Int>()
        rule.setContent { KipuTheme(darkTheme = false) { OnboardingScreen(onPageChanged = { pages += it }, onComplete = { completed++ }) } }
        capture("onboarding-1")
        rule.onNodeWithText("Siguiente").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Página 2 de 3").assertExists()
        capture("onboarding-2")
        rule.onNodeWithContentDescription("Página anterior").performClick()
        rule.onNodeWithContentDescription("Página 1 de 3").assertExists()
        rule.onNodeWithTag("auth-intro-pager").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Página 2 de 3").assertExists()
        rule.onNodeWithText("Siguiente").performClick()
        rule.onNodeWithContentDescription("Página 3 de 3").assertExists()
        rule.onNodeWithText("Tus finanzas son tuyas").assertExists()
        capture("onboarding-3")
        rule.onNodeWithText("Empezar").performClick()
        rule.runOnIdle { assertEquals(1, completed); assertEquals(2, pages.last()) }
    }

    @Test fun thirdPageExistingAccountActionCompletesIntroduction() {
        var completed = 0
        rule.setContent { KipuTheme(darkTheme = false) {
            OnboardingScreen(initialPage = 2, onComplete = { completed++ })
        } }
        rule.onNodeWithContentDescription("Página 3 de 3").assertExists()
        rule.onNodeWithText("Ya tengo una cuenta").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(1, completed) }
    }

    @Test fun thirdPageEnlargedTextKeepsBothActionsReachableWhileSaving() {
        val saving = mutableStateOf(false)
        rule.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                androidx.compose.ui.unit.Density(density.density, 2f)) {
                KipuTheme(darkTheme = false) {
                    OnboardingScreen(initialPage = 2, onComplete = {}, saving = saving.value)
                }
            }
        }
        rule.onNodeWithText("Empezar").assertIsDisplayed()
        rule.onNodeWithText("Ya tengo una cuenta").assertIsDisplayed()
        rule.onNodeWithText("Tus finanzas son tuyas").performScrollTo().assertIsDisplayed()
        capture("onboarding-3-font-200")
        rule.runOnIdle { saving.value = true }
        rule.onNodeWithText("Ya tengo una cuenta").assertIsNotEnabled()
        rule.onNodeWithText("Omitir").assertIsNotEnabled()
    }

    @Test fun compactLayoutKeepsRecoveryActionReachable() {
        rule.setContent { KipuTheme(darkTheme = false) {
            Box(Modifier.width(320.dp).height(480.dp)) { RecoveryScreen(RecoveryUiState(), {}, {}, {}) }
        } }
        rule.onNodeWithText("Enviar enlace de recuperación").performScrollTo().assertIsDisplayed()
        capture("recovery-compact")
    }

    @Test fun enlargedTextKeepsLoginAndRegistrationActionsReachable() {
        rule.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                androidx.compose.ui.unit.Density(density.density, 2f)) {
                KipuTheme(darkTheme = false) {
                    LoginScreen(AuthUiState(), {}, {}, {}, {}, {})
                }
            }
        }
        rule.onNodeWithText("Ingresar").performScrollTo().assertIsDisplayed()
        capture("login-font-200")
        rule.onNodeWithText("Registrarse").performScrollTo().performClick()
        rule.onNodeWithText("Crear cuenta").performScrollTo().assertIsDisplayed()
        capture("register-font-200")
    }

    @Test fun darkLoginUsesThemeTokensAndPasswordToggleIsAccessible() {
        rule.setContent { KipuTheme(darkTheme = true) {
            LoginScreen(AuthUiState(password = "Password123"), {}, {}, {}, {}, {})
        } }
        rule.onNodeWithContentDescription("Mostrar Contraseña").performClick()
        rule.onNodeWithContentDescription("Ocultar Contraseña").assertExists()
        capture("login-dark")
    }
}
