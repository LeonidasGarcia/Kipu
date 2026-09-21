package com.kipu.app.feature.auth.presentation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun SignOutDialog(
    pendingCount: Int,
    onConfirmSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cerrar Sesión") },
        text = {
            if (pendingCount > 0) {
                Text(
                    "Tienes $pendingCount cambio(s) pendiente(s) de sincronizar. " +
                        "Si cierras sesión ahora, estos cambios permanecerán guardados de forma segura en este dispositivo, " +
                        "pero no estarán disponibles para otra cuenta ni se sincronizarán hasta que vuelvas a iniciar sesión."
                )
            } else {
                Text("¿Estás seguro de que deseas cerrar sesión?")
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmSignOut,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (pendingCount > 0) MaterialTheme.colorScheme.error else Color(0xFF0F766E)
                ),
            ) {
                Text(if (pendingCount > 0) "Cerrar sesión de todos modos" else "Cerrar sesión")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}
