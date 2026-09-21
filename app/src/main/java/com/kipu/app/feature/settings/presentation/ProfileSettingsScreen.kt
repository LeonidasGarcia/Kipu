package com.kipu.app.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipu.app.feature.auth.presentation.SignOutDialog
import com.kipu.app.feature.settings.domain.model.SyncState
import com.kipu.app.feature.settings.domain.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPermissions: (() -> Unit)? = null,
    onNavigateToBiometrics: (() -> Unit)? = null,
    onSignOut: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(state.infoMessage) {
        state.infoMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                SyncStateBanner(state.syncState)

                SettingsSection("MI SUSCRIPCIÓN") {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("Estado actual", style = MaterialTheme.typography.labelMedium)
                            Text("Pendiente de conectar", fontWeight = FontWeight.SemiBold)
                            InfoLine("Vigencia", "Sin datos")
                            InfoLine("Facturación", "Sin datos")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {}, enabled = false) { Text("Restaurar compras") }
                                OutlinedButton(onClick = {}, enabled = false) { Text("Ver cupos") }
                            }
                            Text("Funciones de suscripción próximamente", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                SettingsSection("PREFERENCIAS FINANCIERAS") {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Divisa principal", fontWeight = FontWeight.SemiBold)
                            Text("Moneda base para consolidación de saldos", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CurrencyOption("Soles (PEN)", state.currencyCode == "PEN", Modifier.weight(1f)) {
                                    viewModel.onCurrencyCodeChanged("PEN")
                                }
                                CurrencyOption("Dólares (USD)", state.currencyCode == "USD", Modifier.weight(1f)) {
                                    viewModel.onCurrencyCodeChanged("USD")
                                }
                            }
                            Spacer(Modifier.height(18.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Inicio del ciclo mensual", fontWeight = FontWeight.Medium)
                                    Text("Día de corte para presupuestos", style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(
                                    onClick = { viewModel.onMonthStartChanged(state.monthStart - 1) },
                                    enabled = state.monthStart > 1,
                                ) { Text("−", fontSize = 22.sp) }
                                Text("Día ${state.monthStart}", fontWeight = FontWeight.SemiBold)
                                IconButton(
                                    onClick = { viewModel.onMonthStartChanged(state.monthStart + 1) },
                                    enabled = state.monthStart < 28,
                                ) { Text("+", fontSize = 22.sp) }
                            }
                            Spacer(Modifier.height(14.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Modo de privacidad", fontWeight = FontWeight.Medium)
                                    Text("Enmascarar cifras numéricas (••••)", style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(
                                    checked = state.hideBalances,
                                    onCheckedChange = { viewModel.toggleHideBalances() },
                                )
                            }
                            PendingSetting("Categorías", "Administrar categorías de ingresos y gastos")
                        }
                    }
                }

                SettingsSection("TEMA DE LA APLICACIÓN") {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            ThemeOption("Sistema", state.themeMode == ThemeMode.SYSTEM) { viewModel.onThemeModeChanged(ThemeMode.SYSTEM) }
                            ThemeOption("Claro", state.themeMode == ThemeMode.LIGHT) { viewModel.onThemeModeChanged(ThemeMode.LIGHT) }
                            ThemeOption("Oscuro", state.themeMode == ThemeMode.DARK) { viewModel.onThemeModeChanged(ThemeMode.DARK) }
                        }
                    }
                }

                Button(
                    onClick = viewModel::savePreferences,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !state.isSaving && state.monthStartError == null,
                ) {
                    if (state.isSaving) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    else Text("Guardar cambios")
                }

                SettingsSection("NOTIFICACIONES Y ALERTAS") {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Anticipación de vencimientos", fontWeight = FontWeight.Medium)
                                    Text("Aviso previo a la fecha límite", style = MaterialTheme.typography.bodySmall)
                                }
                                Text("3 días (ejemplo)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("Configuración próximamente", style = MaterialTheme.typography.labelSmall)
                            onNavigateToPermissions?.let {
                                ActiveSetting("Permisos y automatización", "Alertas y fuentes de datos opcionales", it)
                            }
                        }
                    }
                }

                SettingsSection("SEGURIDAD Y COPIAS DE SEGURIDAD") {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            onNavigateToBiometrics?.let {
                                ActiveSetting("Bloqueo local y biometría", "Protege el acceso y tus balances", it)
                            }
                            PendingSetting("Exportar datos contables", "Archivos CSV y JSON")
                            PendingSetting("Restaurar respaldo local", "Copia de seguridad local")
                            PendingSetting("Eliminar cuenta y registros", "Borrado de datos locales y remotos", destructive = true)
                        }
                    }
                }

                if (onSignOut != null) {
                    OutlinedButton(
                        onClick = viewModel::requestSignOut,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("Cerrar sesión") }
                }
            }
        }
    }

    if (state.showSignOutDialog) {
        SignOutDialog(
            pendingCount = state.pendingChangesCount,
            onConfirmSignOut = { viewModel.confirmSignOut { onSignOut?.invoke() } },
            onDismiss = viewModel::dismissSignOutDialog,
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CurrencyOption(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick, modifier = modifier) { Text(label, maxLines = 1) }
    else OutlinedButton(onClick = onClick, modifier = modifier) { Text(label, maxLines = 1) }
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, fontSize = 13.sp)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ActiveSetting(title: String, subtitle: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
private fun PendingSetting(title: String, subtitle: String, destructive: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.Medium,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Text("Próximamente", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SyncStateBanner(syncState: SyncState) {
    val message = when (syncState) {
        SyncState.SYNCED -> "Preferencias sincronizadas"
        SyncState.PENDING -> "Cambios pendientes de sincronizar"
        SyncState.CONFLICT -> "Conflicto de sincronización"
        SyncState.WAITING_FOR_AUTH -> "Esperando inicio de sesión para sincronizar"
        SyncState.ERROR -> "Error al sincronizar"
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(message, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}
