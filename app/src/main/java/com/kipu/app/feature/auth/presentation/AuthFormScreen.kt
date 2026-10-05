package com.kipu.app.feature.auth.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.R
import com.kipu.app.feature.auth.domain.PasswordValidator
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.KipuMotionTokens
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

@Composable
internal fun AuthFormScreen(
    uiState: AuthUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onNavigateToRecovery: () -> Unit,
    onDismissExistingAccountDialog: () -> Unit,
    onDismissConfirmationDialog: () -> Unit,
    initialRegisterMode: Boolean,
    modifier: Modifier = Modifier,
    onNavigateToRegister: (() -> Unit)? = null,
    onNavigateToLogin: (() -> Unit)? = null,
) {
    var register by rememberSaveable { mutableStateOf(initialRegisterMode) }
    // Passwords deliberately never enter rememberSaveable or SavedStateHandle.
    var confirmation by remember { mutableStateOf("") }
    var confirmationError by remember { mutableStateOf<String?>(null) }
    var registrationAttempted by remember { mutableStateOf(false) }
    var legalDocument by rememberSaveable { mutableStateOf<String?>(null) }
    val colors = rememberCalmEmeraldColors()
    val duration = if (rememberReducedMotionEnabled()) 0 else KipuMotionTokens.FastMillis
    val canAct = !uiState.isLoading && uiState.cooldownSeconds == 0
    val focus = LocalFocusManager.current

    LaunchedEffect(uiState.password) {
        if (uiState.password.isEmpty()) confirmation = ""
    }
    DisposableEffect(Unit) { onDispose { onPasswordChanged("") } }

    fun changeMode(toRegister: Boolean) {
        if (register == toRegister || !canAct) return
        focus.clearFocus()
        confirmation = ""
        confirmationError = null
        registrationAttempted = false
        onPasswordChanged("")
        if (toRegister && onNavigateToRegister != null) onNavigateToRegister()
        else if (!toRegister && onNavigateToLogin != null) onNavigateToLogin()
        else register = toRegister
    }
    BackHandler(register && !initialRegisterMode && onNavigateToRegister == null) { changeMode(false) }
    fun submit() {
        if (!canAct) return
        if (register) {
            registrationAttempted = true
            val validation = PasswordValidator.validatePasswordConfirmation(uiState.password, confirmation)
            if (validation is PasswordValidator.ValidationResult.Invalid) {
                confirmationError = validation.reason
                return
            }
            confirmation = ""
            onRegisterClick()
        } else onLoginClick()
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
    // Adapt decoration and spacing to the window, never shrink accessible fields or text.
    val compact = maxHeight < 940.dp
    val logoSize = if (compact) 56.dp else 80.dp
    val sectionGap = if (compact) 16.dp else 24.dp
    val smallGap = if (compact) 8.dp else 12.dp
    val fieldGap = if (compact) 12.dp else AuthLayout.Gap
    AuthSurface(verticalPadding = if (compact) 12.dp else 24.dp) {
        AuthCard(contentPadding = if (compact) 20.dp else AuthLayout.CardPadding) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.kipu_logo), null, Modifier.size(logoSize).testTag("auth-form-logo"))
                Spacer(Modifier.height(smallGap))
                Text("Kipu", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(if (compact) 12.dp else sectionGap))
                Row(Modifier.fillMaxWidth().selectableGroup()
                    .clip(RoundedCornerShape(28.dp)).background(colors.pillTrack).padding(4.dp)) {
                    listOf(false to "Iniciar Sesión", true to "Registrarse").forEach { (mode, label) ->
                        Box(Modifier.weight(1f).heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (register == mode) colors.surfaceCard else colors.pillTrack)
                            .selectable(register == mode, enabled = canAct, role = Role.Tab,
                                onClick = { changeMode(mode) }), contentAlignment = Alignment.Center) {
                            Text(label, style = MaterialTheme.typography.labelLarge,
                                color = colors.primaryText, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                }
                Spacer(Modifier.height(sectionGap))
                AuthTextField(uiState.email, onEmailChanged, "Correo electrónico",
                    error = uiState.emailError, placeholder = "ejemplo@correo.com", enabled = canAct,
                    trailing = { Icon(Icons.Outlined.Email, null, tint = colors.secondaryMuted) })
                Spacer(Modifier.height(fieldGap))
                AuthPasswordField(uiState.password, onPasswordChanged, error = uiState.passwordError,
                    enabled = canAct, imeAction = if (register) ImeAction.Next else ImeAction.Done,
                    onDone = ::submit)
                AnimatedVisibility(register,
                    enter = expandVertically(tween(duration), expandFrom = Alignment.Top) + fadeIn(tween(duration)),
                    exit = shrinkVertically(tween(duration), shrinkTowards = Alignment.Top) + fadeOut(tween(duration))) {
                    Column(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(8.dp))
                        PasswordRequirement("Entre 8 y 72 caracteres", uiState.password.length in 8..72, compact, registrationAttempted)
                        PasswordRequirement("Al menos una letra", PasswordValidator.hasLetter(uiState.password), compact, registrationAttempted)
                        PasswordRequirement("Al menos un número", PasswordValidator.hasNumber(uiState.password), compact, registrationAttempted)
                        Spacer(Modifier.height(fieldGap))
                        AuthPasswordField(confirmation, { confirmation = it; confirmationError = null },
                            "Confirmar contraseña", error = confirmationError, enabled = canAct, onDone = ::submit)
                        if (confirmation.isNotEmpty() && uiState.password.isNotEmpty()) {
                            val match = PasswordValidator.passwordsMatch(uiState.password, confirmation)
                            Text(if (match) "Las contraseñas coinciden" else "Las contraseñas no coinciden",
                                color = if (match) colors.incomeEmerald else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
                AuthErrorMessage(uiState.generalError)
                AuthErrorMessage(if (uiState.cooldownSeconds > 0)
                    stringResource(R.string.auth_cooldown_message, uiState.cooldownSeconds) else null)
                Spacer(Modifier.height(sectionGap))
                AuthPrimaryButton(if (register) "Crear cuenta" else "Ingresar", ::submit,
                    enabled = canAct, loading = uiState.isLoading)
                Spacer(Modifier.height(smallGap))
                AnimatedVisibility(!register,
                    enter = expandVertically(tween(duration)) + fadeIn(tween(duration)),
                    exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration))) {
                    TextButton(onNavigateToRecovery, enabled = canAct) {
                        Text("¿Olvidaste tu contraseña?", color = colors.primaryDeep)
                    }
                }
                TextButton({ changeMode(!register) }, enabled = canAct) {
                    Text(if (register) "¿Ya tienes una cuenta? Iniciar sesión" else "¿No tienes cuenta? Regístrate",
                        color = colors.primaryDeep, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(smallGap))
                HorizontalDivider(color = colors.borderSubtle)
                Spacer(Modifier.height(smallGap))
                Text(buildAnnotatedString {
                    append(if (register) "Al registrarte aceptas los " else "Al ingresar aceptas los ")
                    val styles = TextLinkStyles(SpanStyle(color = colors.primaryDeep,
                        textDecoration = TextDecoration.Underline))
                    withLink(LinkAnnotation.Clickable("terms", styles) { legalDocument = "Términos y Condiciones" }) {
                        append("Términos y Condiciones")
                    }
                    append(" y ")
                    withLink(LinkAnnotation.Clickable("privacy", styles) { legalDocument = "Políticas de privacidad" }) {
                        append("Políticas de privacidad")
                    }
                    append(" de Kipu.")
                }, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        }
    }
    }
    if (uiState.showExistingAccountDialog) AlertDialog(
        onDismissRequest = onDismissExistingAccountDialog,
        title = { Text(stringResource(R.string.auth_existing_account_dialog_title)) },
        text = { Text(stringResource(R.string.auth_existing_account_dialog_message)) },
        confirmButton = { TextButton({ onDismissExistingAccountDialog(); changeMode(false) }) { Text("Iniciar Sesión") } },
        dismissButton = { TextButton(onDismissExistingAccountDialog) { Text("Cancelar") } })
    if (uiState.confirmationRequiredEmail != null) AlertDialog(
        onDismissRequest = onDismissConfirmationDialog,
        title = { Text("Verifica tu cuenta") },
        text = { Text(stringResource(R.string.auth_confirmation_required_message)) },
        confirmButton = { TextButton({ onDismissConfirmationDialog(); changeMode(false) }) { Text("Entendido") } })
    // No approved legal URLs/documents exist in the repository: do not invent their contents.
    if (legalDocument != null) AlertDialog(onDismissRequest = { legalDocument = null },
        title = { Text(legalDocument.orEmpty()) },
        text = { Text("Este documento aún no está disponible en la aplicación.") },
        confirmButton = { TextButton({ legalDocument = null }) { Text("Cerrar") } })
}

@Composable
private fun PasswordRequirement(text: String, satisfied: Boolean, compact: Boolean, showErrors: Boolean) {
    val colors = rememberCalmEmeraldColors()
    // Brighter checks match the reference; the text keeps sufficient contrast on a light card.
    val successText = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color(0xFF15803D) else colors.incomeEmerald
    val statusColor = when {
        satisfied -> successText
        showErrors -> MaterialTheme.colorScheme.error
        else -> colors.secondaryMuted
    }
    Row(Modifier.fillMaxWidth().padding(vertical = if (compact) 2.dp else 3.dp).semantics(mergeDescendants = true) {
        stateDescription = if (satisfied) "Cumplido" else if (showErrors) "No cumplido" else "Pendiente"
    }, verticalAlignment = Alignment.CenterVertically) {
        Icon(when {
            satisfied -> Icons.Filled.CheckCircle
            showErrors -> Icons.Outlined.ErrorOutline
            else -> Icons.Outlined.RadioButtonUnchecked
        }, null, Modifier.size(18.dp), tint = if (satisfied && MaterialTheme.colorScheme.surface.luminance() > 0.5f)
            Color(0xFF16A34A) else statusColor)
        Spacer(Modifier.width(8.dp))
        Text(text, style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium, color = statusColor, modifier = Modifier.weight(1f))
    }
}
