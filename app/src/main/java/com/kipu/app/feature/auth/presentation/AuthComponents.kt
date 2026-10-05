package com.kipu.app.feature.auth.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.kipu.app.ui.component.KipuCard
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.KipuMotionTokens
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

internal object AuthLayout {
    val MaxWidth = 480.dp
    val ScreenPadding = 20.dp
    val CardPadding = 24.dp
    val CardRadius = 32.dp
    val FieldRadius = 18.dp
    val ButtonHeight = 52.dp
    val Gap = 16.dp
}

/** Insets are consumed by the owning screen, including the keyboard. */
@Composable
internal fun AuthSurface(
    modifier: Modifier = Modifier,
    topAligned: Boolean = false,
    verticalPadding: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = rememberCalmEmeraldColors()
    Box(
        modifier.fillMaxSize().background(colors.background).safeDrawingPadding().imePadding(),
        contentAlignment = if (topAligned) Alignment.TopCenter else Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = AuthLayout.MaxWidth).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AuthLayout.ScreenPadding, vertical = verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@Composable
internal fun AuthCard(
    modifier: Modifier = Modifier,
    recovery: Boolean = false,
    contentPadding: Dp = AuthLayout.CardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = rememberCalmEmeraldColors()
    KipuCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AuthLayout.CardRadius),
        containerColor = if (recovery) colors.pillTrack else colors.surfaceCard,
        contentColor = colors.primaryText,
        borderColor = colors.borderSubtle,
        elevation = if (recovery) 0.dp else 3.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(contentPadding), content = content)
    }
}

@Composable
internal fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    placeholder: String = "",
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Email,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
    transformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = rememberCalmEmeraldColors()
    val focus = LocalFocusManager.current
    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge,
            color = if (error == null) colors.primaryText else MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value, onValueChange = onValueChange, enabled = enabled,
            singleLine = true, isError = error != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { contentDescription = label },
            placeholder = { Text(placeholder) },
            textStyle = MaterialTheme.typography.bodyLarge,
            visualTransformation = transformation,
            leadingIcon = leadingIcon?.let { { Icon(it, null,
                tint = if (error == null) colors.secondaryMuted else MaterialTheme.colorScheme.error) } },
            trailingIcon = trailing,
            shape = RoundedCornerShape(AuthLayout.FieldRadius),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.primaryText, unfocusedTextColor = colors.primaryText,
                focusedBorderColor = colors.primaryDeep, unfocusedBorderColor = colors.borderSubtle,
                focusedContainerColor = colors.surfaceCard, unfocusedContainerColor = colors.surfaceCard,
                disabledContainerColor = colors.surfaceCard,
                cursorColor = colors.primaryDeep,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) },
                onDone = { focus.clearFocus(); if (enabled) onDone() },
            ),
        )
        AuthErrorMessage(error)
    }
}

@Composable
internal fun AuthPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "Contraseña",
    error: String? = null,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {},
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    AuthTextField(value, onValueChange, label, error = error, enabled = enabled,
        keyboardType = KeyboardType.Password, imeAction = imeAction, onDone = onDone,
        transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailing = {
            IconButton(onClick = { visible = !visible }, enabled = enabled) {
                Icon(if (visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    if (visible) "Ocultar $label" else "Mostrar $label",
                    tint = rememberCalmEmeraldColors().secondaryMuted)
            }
        })
}

@Composable
internal fun AuthPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    pill: Boolean = false,
) {
    val colors = rememberCalmEmeraldColors()
    val focus = LocalFocusManager.current
    Button(
        onClick = { focus.clearFocus(); onClick() },
        enabled = enabled && !loading,
        modifier = Modifier.fillMaxWidth().heightIn(min = AuthLayout.ButtonHeight),
        shape = RoundedCornerShape(if (pill) 28.dp else AuthLayout.FieldRadius),
        colors = ButtonDefaults.buttonColors(containerColor = colors.primaryDeep,
            contentColor = colors.onPrimaryDeep,
            disabledContainerColor = colors.primaryDeep.copy(alpha = .25f),
            disabledContentColor = colors.primaryText),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp,
                color = colors.primaryDeep)
            Spacer(Modifier.width(10.dp))
        }
        Text(if (loading) "Procesando…" else text, style = MaterialTheme.typography.titleSmall)
        if (!loading && icon != null) { Spacer(Modifier.width(10.dp)); Icon(icon, null) }
    }
}

@Composable
internal fun AuthErrorMessage(message: String?) {
    val duration = if (rememberReducedMotionEnabled()) 0 else KipuMotionTokens.FeedbackMillis
    AnimatedVisibility(visible = message != null,
        enter = fadeIn(tween(duration)), exit = fadeOut(tween(duration))) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Text(message.orEmpty(), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}
