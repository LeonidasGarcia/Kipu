package com.kipu.app.feature.auth.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.R
import com.kipu.app.feature.auth.domain.PasswordValidator
import com.kipu.app.ui.theme.KipuBackground
import com.kipu.app.ui.theme.KipuError
import com.kipu.app.ui.theme.KipuIncome
import com.kipu.app.ui.theme.KipuOnPrimary
import com.kipu.app.ui.theme.KipuOnSurface
import com.kipu.app.ui.theme.KipuOnSurfaceVariant
import com.kipu.app.ui.theme.KipuOutline
import com.kipu.app.ui.theme.KipuOutlineVariant
import com.kipu.app.ui.theme.KipuPrimaryContainer
import com.kipu.app.ui.theme.KipuSecondaryContainer
import com.kipu.app.ui.theme.KipuSurfaceContainerLow
import com.kipu.app.ui.theme.KipuSurfaceContainerLowest
import com.kipu.app.ui.theme.KipuMotionTokens

/**
 * Pantalla 1: Registro, Autenticación y Recuperación unificada
 * Cumple con HU-01, HU-02, heurísticas de Nielsen y diseño Andean Modernist.
 */
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
) {
    var isRegisterMode by remember { mutableStateOf(initialRegisterMode) }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPassword by remember { mutableStateOf("") }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var localRegistrationError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.password) {
        if (uiState.password.isEmpty()) {
            confirmPassword = ""
        }
    }

    // Evaluaciones de Heurísticas de Nielsen para la contraseña
    val hasMinLength = PasswordValidator.hasMinLength(uiState.password)
    val hasNumber = PasswordValidator.hasNumber(uiState.password)
    val hasSpecialChar = PasswordValidator.hasSpecialChar(uiState.password)
    val passwordsMatch = confirmPassword.isNotEmpty() && uiState.password.isNotEmpty() && uiState.password == confirmPassword

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KipuBackground)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = KipuSurfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Logo Badge con nudo Quipu
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(KipuSecondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(32.dp)) {
                        val strokeColor = KipuPrimaryContainer
                        val centerX = size.width / 2f

                        drawLine(
                            color = strokeColor,
                            start = Offset(centerX, 4f),
                            end = Offset(centerX, size.height - 4f),
                            strokeWidth = 3.5.dp.toPx(),
                        )

                        drawCircle(
                            color = strokeColor,
                            radius = 4.5.dp.toPx(),
                            center = Offset(centerX, size.height * 0.38f),
                        )
                        drawCircle(
                            color = strokeColor,
                            radius = 3.5.dp.toPx(),
                            center = Offset(centerX, size.height * 0.68f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Título
                Text(
                    text = "Kipu",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = KipuOnSurface,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Segmented Control (Iniciar Sesión / Registrarse)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(KipuSurfaceContainerLow)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(20.dp))
                            .then(
                                if (!isRegisterMode) {
                                    Modifier
                                        .shadow(2.dp, RoundedCornerShape(20.dp))
                                        .background(KipuSurfaceContainerLowest)
                                } else Modifier
                            )
                            .clickable {
                                isRegisterMode = false
                                localRegistrationError = null
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Iniciar Sesión",
                            fontSize = 14.sp,
                            fontWeight = if (!isRegisterMode) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (!isRegisterMode) KipuOnSurface else KipuOutline,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(20.dp))
                            .then(
                                if (isRegisterMode) {
                                    Modifier
                                        .shadow(2.dp, RoundedCornerShape(20.dp))
                                        .background(KipuSurfaceContainerLowest)
                                } else Modifier
                            )
                            .clickable {
                                isRegisterMode = true
                                localRegistrationError = null
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Registrarse",
                            fontSize = 14.sp,
                            fontWeight = if (isRegisterMode) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isRegisterMode) KipuPrimaryContainer else KipuOutline,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Campo: Correo electrónico
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Correo electrónico",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = KipuOnSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange = {
                            localRegistrationError = null
                            onEmailChanged(it)
                        },
                        placeholder = { Text("ejemplo@correo.com", color = Color(0xFF94A3B8), fontSize = 14.sp) },
                        textStyle = TextStyle(
                            color = KipuOnSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Email,
                                contentDescription = null,
                                tint = KipuOutline,
                            )
                        },
                        isError = uiState.emailError != null,
                        supportingText = uiState.emailError?.let { { Text(it) } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = KipuOnSurface,
                            unfocusedTextColor = KipuOnSurface,
                            cursorColor = KipuPrimaryContainer,
                            focusedBorderColor = KipuPrimaryContainer,
                            unfocusedBorderColor = KipuOutlineVariant,
                            focusedContainerColor = KipuSurfaceContainerLowest,
                            unfocusedContainerColor = KipuSurfaceContainerLowest,
                            focusedPlaceholderColor = Color(0xFF94A3B8),
                            unfocusedPlaceholderColor = Color(0xFF94A3B8),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Campo: Contraseña
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Contraseña",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = KipuOnSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange = {
                            localRegistrationError = null
                            onPasswordChanged(it)
                        },
                        placeholder = { Text("••••••••", color = Color(0xFF94A3B8), fontSize = 14.sp) },
                        textStyle = TextStyle(
                            color = KipuOnSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                                    tint = KipuOutline,
                                )
                            }
                        },
                        isError = uiState.passwordError != null,
                        supportingText = uiState.passwordError?.let { { Text(it) } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = KipuOnSurface,
                            unfocusedTextColor = KipuOnSurface,
                            cursorColor = KipuPrimaryContainer,
                            focusedBorderColor = KipuPrimaryContainer,
                            unfocusedBorderColor = KipuOutlineVariant,
                            focusedContainerColor = KipuSurfaceContainerLowest,
                            unfocusedContainerColor = KipuSurfaceContainerLowest,
                            focusedPlaceholderColor = Color(0xFF94A3B8),
                            unfocusedPlaceholderColor = Color(0xFF94A3B8),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Heurística de Nielsen: Indicadores dinámicos de requisitos de contraseña (solo en modo Registro)
                AnimatedVisibility(
                    visible = isRegisterMode,
                    enter = fadeIn(tween(KipuMotionTokens.FastMillis)) + expandVertically(tween(KipuMotionTokens.FastMillis)),
                    exit = fadeOut(tween(KipuMotionTokens.FastMillis)) + shrinkVertically(tween(KipuMotionTokens.FastMillis)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, start = 4.dp, end = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        PasswordRequirementItem(
                            text = "Mínimo 8 caracteres",
                            satisfied = hasMinLength,
                        )
                        PasswordRequirementItem(
                            text = "Al menos un número (0-9)",
                            satisfied = hasNumber,
                        )
                        PasswordRequirementItem(
                            text = "Al menos un carácter especial (!@#$...)",
                            satisfied = hasSpecialChar,
                        )
                    }
                }

                // Campo: Confirmar Contraseña (solo en modo Registro)
                AnimatedVisibility(
                    visible = isRegisterMode,
                    enter = fadeIn(tween(KipuMotionTokens.FastMillis)) + expandVertically(tween(KipuMotionTokens.FastMillis)),
                    exit = fadeOut(tween(KipuMotionTokens.FastMillis)) + shrinkVertically(tween(KipuMotionTokens.FastMillis)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                    ) {
                        Text(
                            text = "Confirmar Contraseña",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = KipuOnSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = {
                                confirmPassword = it
                                localRegistrationError = null
                            },
                            placeholder = { Text("••••••••", color = Color(0xFF94A3B8), fontSize = 14.sp) },
                            textStyle = TextStyle(
                                color = KipuOnSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                    Icon(
                                        imageVector = if (confirmPasswordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                        contentDescription = if (confirmPasswordVisible) "Ocultar confirmación" else "Mostrar confirmación",
                                        tint = KipuOutline,
                                    )
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = KipuOnSurface,
                                unfocusedTextColor = KipuOnSurface,
                                cursorColor = KipuPrimaryContainer,
                                focusedBorderColor = if (confirmPassword.isNotEmpty() && uiState.password.isNotEmpty() && !passwordsMatch) KipuError else KipuPrimaryContainer,
                                unfocusedBorderColor = if (confirmPassword.isNotEmpty() && uiState.password.isNotEmpty() && !passwordsMatch) KipuError else KipuOutlineVariant,
                                focusedContainerColor = KipuSurfaceContainerLowest,
                                unfocusedContainerColor = KipuSurfaceContainerLowest,
                                focusedPlaceholderColor = Color(0xFF94A3B8),
                                unfocusedPlaceholderColor = Color(0xFF94A3B8),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Indicador dinámico de coincidencia de contraseñas (Heurística #1 y #5)
                        if (confirmPassword.isNotEmpty() && uiState.password.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 4.dp),
                            ) {
                                Icon(
                                    imageVector = if (passwordsMatch) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (passwordsMatch) KipuIncome else KipuError,
                                    modifier = Modifier.size(15.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (passwordsMatch) "Las contraseñas coinciden" else "Las contraseñas no coinciden",
                                    fontSize = 12.sp,
                                    color = if (passwordsMatch) KipuIncome else KipuError,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }

                // Error local de validación
                if (localRegistrationError != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = localRegistrationError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }

                if (uiState.generalError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.generalError,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }

                if (uiState.cooldownSeconds > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.auth_cooldown_message, uiState.cooldownSeconds),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Botón Principal de Acción
                Button(
                    onClick = {
                        localRegistrationError = null
                        if (isRegisterMode) {
                            // Validaciones completas antes de registrar
                            if (!hasMinLength) {
                                localRegistrationError = "La contraseña debe tener al menos 8 caracteres."
                                return@Button
                            }
                            if (!hasNumber) {
                                localRegistrationError = "La contraseña debe incluir al menos un número."
                                return@Button
                            }
                            if (!hasSpecialChar) {
                                localRegistrationError = "La contraseña debe incluir al menos un carácter especial."
                                return@Button
                            }
                            if (confirmPassword.isEmpty()) {
                                localRegistrationError = "Por favor confirma tu contraseña."
                                return@Button
                            }
                            if (!passwordsMatch) {
                                localRegistrationError = "Las contraseñas no coinciden."
                                return@Button
                            }
                            onRegisterClick()
                        } else {
                            onLoginClick()
                        }
                    },
                    enabled = !uiState.isLoading && uiState.cooldownSeconds == 0,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KipuPrimaryContainer,
                        disabledContainerColor = Color(0xFF397F79),
                        disabledContentColor = Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    } else {
                        Text(
                            text = if (isRegisterMode) "Registrarse" else "Ingresar",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = KipuOnPrimary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Enlace "¿Olvidaste tu contraseña?" (solo en modo Iniciar Sesión)
                AnimatedVisibility(
                    visible = !isRegisterMode,
                    enter = fadeIn(tween(KipuMotionTokens.FastMillis)) + expandVertically(tween(KipuMotionTokens.FastMillis)),
                    exit = fadeOut(tween(KipuMotionTokens.FastMillis)) + shrinkVertically(tween(KipuMotionTokens.FastMillis)),
                ) {
                    TextButton(
                        onClick = onNavigateToRecovery,
                        modifier = Modifier.height(36.dp),
                    ) {
                        Text(
                            text = "¿Olvidaste tu contraseña?",
                            color = KipuPrimaryContainer,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                // Enlace para alternar entre Iniciar Sesión y Registrarse
                TextButton(
                    onClick = {
                        isRegisterMode = !isRegisterMode
                        localRegistrationError = null
                    },
                    modifier = Modifier.height(36.dp),
                ) {
                    Text(
                        text = if (isRegisterMode) {
                            "¿Ya tienes una cuenta? Iniciar Sesión"
                        } else {
                            "¿No tienes cuenta? Regístrate"
                        },
                        color = KipuPrimaryContainer,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }

    // Diálogo de cuenta existente (FR-051)
    if (uiState.showExistingAccountDialog) {
        AlertDialog(
            onDismissRequest = onDismissExistingAccountDialog,
            title = { Text("Cuenta ya registrada") },
            text = { Text("Ya existe una cuenta con este correo electrónico. Por favor, inicia sesión.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDismissExistingAccountDialog()
                        isRegisterMode = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KipuPrimaryContainer),
                ) {
                    Text("Iniciar Sesión")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissExistingAccountDialog) {
                    Text("Cancelar")
                }
            },
        )
    }

    // Diálogo de confirmación de correo enviado
    if (uiState.confirmationRequiredEmail != null) {
        AlertDialog(
            onDismissRequest = onDismissConfirmationDialog,
            title = { Text("Confirma tu correo") },
            text = { Text("Hemos enviado un enlace de confirmación a ${uiState.confirmationRequiredEmail}. Por favor revisa tu bandeja de entrada.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDismissConfirmationDialog()
                        isRegisterMode = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KipuPrimaryContainer),
                ) {
                    Text("Entendido")
                }
            },
        )
    }
}

/**
 * Item visual de criterio de contraseña (Heurística de Nielsen: Visibilidad y Prevención de errores).
 */
@Composable
private fun PasswordRequirementItem(
    text: String,
    satisfied: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Icon(
            imageVector = if (satisfied) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (satisfied) KipuIncome else Color(0xFF94A3B8),
            modifier = Modifier.size(15.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = if (satisfied) KipuIncome else Color(0xFF64748B),
            fontWeight = if (satisfied) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
