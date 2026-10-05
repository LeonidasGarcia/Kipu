package com.kipu.app.feature.auth.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.KipuMotionTokens
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

@Composable
fun RecoveryScreen(
    uiState: RecoveryUiState,
    onEmailChanged: (String) -> Unit,
    onSubmitRecovery: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToRegister: () -> Unit = {},
) {
    val colors = rememberCalmEmeraldColors()
    val context = LocalContext.current
    var mailError by remember { mutableStateOf<String?>(null) }
    val duration = if (rememberReducedMotionEnabled()) 0 else KipuMotionTokens.SubtreeEnterMillis
    BoxWithConstraints(modifier.fillMaxSize()) {
    val headerGap = (maxHeight - 830.dp).coerceIn(24.dp, 130.dp)
    AuthSurface(topAligned = true) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onNavigateBack, Modifier.width(80.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = colors.primaryDeep) }
            Text("Kipu", style = MaterialTheme.typography.headlineMedium,
                color = colors.primaryDeep, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            TextButton(onNavigateBack, Modifier.width(80.dp)) { Text("Omitir", color = colors.primaryText) }
        }
        Spacer(Modifier.height(headerGap))
        AnimatedContent(uiState.isRequestAccepted,
            transitionSpec = { fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) },
            label = "recoveryStep") { accepted ->
            AuthCard(recovery = true) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(60.dp).background(colors.borderSubtle, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(if (accepted) Icons.Outlined.MarkEmailRead else Icons.Outlined.LockReset,
                            null, Modifier.size(28.dp), tint = colors.primaryDeep)
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(color = colors.background, shape = RoundedCornerShape(24.dp)) {
                        Text(if (accepted) "Paso 2 de 2" else "Paso 1 de 2", color = colors.primaryDeep,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(if (accepted) "¡Revisa tu bandeja de entrada!" else "Recuperar contraseña",
                    style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                if (accepted) {
                    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                        Text("Si el correo está asociado a una cuenta Kipu, recibirás un enlace seguro para restablecer tu contraseña en:",
                            style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(6.dp))
                        Text(uiState.submittedEmail.ifEmpty { uiState.email }, style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(24.dp))
                    RecoveryHint("El enlace es temporal y de un solo uso. Revisa también tu carpeta de spam.", Icons.Outlined.Schedule)
                    Spacer(Modifier.height(24.dp))
                    AuthPrimaryButton("Abrir aplicación de correo", {
                        try {
                            context.startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL))
                            mailError = null
                        } catch (_: ActivityNotFoundException) {
                            mailError = "No hay una aplicación de correo disponible. Abre tu correo desde el navegador."
                        }
                    }, icon = Icons.Outlined.Email, pill = true)
                    AuthErrorMessage(mailError)
                    Spacer(Modifier.height(16.dp))
                    Text("¿No recibiste el correo?", style = MaterialTheme.typography.bodyLarge)
                    TextButton(onSubmitRecovery,
                        enabled = !uiState.isLoading && uiState.resendSeconds == 0) {
                        Icon(Icons.Outlined.HourglassEmpty, null)
                        Spacer(Modifier.width(8.dp))
                        Text(when {
                            uiState.isLoading -> "Procesando…"
                            uiState.resendSeconds > 0 -> "Reenviar enlace en 00:%02d".format(uiState.resendSeconds)
                            else -> "Reenviar enlace"
                        })
                    }
                    AuthErrorMessage(uiState.errorMessage)
                } else {
                    Text("Ingresa el correo electrónico asociado a tu cuenta de Kipu y te enviaremos un enlace seguro para restablecerla.",
                        style = MaterialTheme.typography.bodyLarge)
                    val error = uiState.emailError ?: uiState.errorMessage
                    if (error != null) {
                        Spacer(Modifier.height(20.dp))
                        Surface(color = colors.expenseBg, shape = RoundedCornerShape(18.dp)) {
                            Box(Modifier.padding(16.dp)) { AuthErrorMessage(error) }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    AuthTextField(uiState.email, onEmailChanged, "Correo electrónico",
                        error = uiState.emailError, placeholder = "ejemplo@correo.com",
                        leadingIcon = Icons.Outlined.Email, enabled = !uiState.isLoading,
                        imeAction = ImeAction.Done, onDone = onSubmitRecovery)
                    if (uiState.emailError != null) TextButton(onNavigateToRegister) { Text("¿No tienes cuenta? Crear una cuenta") }
                    Spacer(Modifier.height(20.dp))
                    RecoveryHint("Revisa también tu carpeta de spam o correo no deseado.", Icons.Outlined.Info)
                    Spacer(Modifier.height(24.dp))
                    AuthPrimaryButton(if (uiState.resendSeconds > 0) "Intenta en ${uiState.resendSeconds} segundos" else "Enviar enlace de recuperación", onSubmitRecovery,
                        enabled = uiState.resendSeconds == 0,
                        loading = uiState.isLoading, icon = Icons.AutoMirrored.Outlined.ArrowForward, pill = true)
                }
                Spacer(Modifier.height(24.dp))
                HorizontalDivider(color = colors.borderSubtle)
                TextButton(onNavigateBack, Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Volver a Iniciar sesión", color = colors.primaryDeep)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Shield, null, Modifier.size(18.dp), tint = colors.secondaryMuted)
            Spacer(Modifier.width(8.dp))
            Text("Protegido con cifrado Kipu", style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
        }
    }
    }
}

@Composable
private fun RecoveryHint(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val colors = rememberCalmEmeraldColors()
    Surface(color = colors.borderSubtle.copy(alpha = .5f), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = colors.primaryDeep)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
