package com.kipu.app.feature.settings.presentation

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BalanceMaskToggle(
    hideBalances: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier.size(48.dp),
    ) {
        Icon(
            imageVector = if (hideBalances) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            contentDescription = if (hideBalances) "Mostrar montos y saldos" else "Ocultar montos y saldos",
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

fun formatMaskedAmount(amount: String, hideBalances: Boolean): String {
    return if (hideBalances) "••••••" else amount
}
