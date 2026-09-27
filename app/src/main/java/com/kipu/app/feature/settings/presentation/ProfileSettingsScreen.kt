package com.kipu.app.feature.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    onNavigateToCategories: (() -> Unit)? = null,
    onNavigateToMovements: (() -> Unit)? = null,
    onNavigateToAccounts: (() -> Unit)? = null,
    onNavigateToPurchase: (() -> Unit)? = null,
    onSignOut: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var currencyDropdownExpanded by remember { mutableStateOf(false) }

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
                title = { Text("Ajustes", fontWeight = FontWeight.SemiBold, fontSize = 18.sp) },
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SyncStateBanner(state.syncState)

                // 1. Divisa principal
                SettingsCard(
                    icon = Icons.Default.Payments,
                    title = "Divisa principal",
                    subtitle = "Moneda base para balances y consolidado",
                ) {
                    ExposedDropdownMenuBox(
                        expanded = currencyDropdownExpanded,
                        onExpandedChange = { currencyDropdownExpanded = !currencyDropdownExpanded },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                    ) {
                                        Text(
                                            text = state.currencyCode,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (state.currencyCode == "PEN") "Soles peruanos (S/)" else "Dólares americanos ($)",
                                        fontWeight = FontWeight.Medium,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyDropdownExpanded)
                            }
                        }

                        ExposedDropdownMenu(
                            expanded = currencyDropdownExpanded,
                            onDismissRequest = { currencyDropdownExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("PEN - Soles peruanos (S/)") },
                                onClick = {
                                    viewModel.onCurrencyCodeChanged("PEN")
                                    viewModel.savePreferences()
                                    currencyDropdownExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("USD - Dólares americanos ($)") },
                                onClick = {
                                    viewModel.onCurrencyCodeChanged("USD")
                                    viewModel.savePreferences()
                                    currencyDropdownExpanded = false
                                },
                            )
                        }
                    }
                }

                // 2. Inicio del ciclo contable
                SettingsCard(
                    icon = Icons.Default.CalendarToday,
                    title = "Inicio del ciclo contable",
                    subtitle = "Reinicio mensual de presupuestos y balances",
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Día de corte",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            ) {
                                IconButton(
                                    onClick = {
                                        viewModel.stepMonthStart(-1)
                                    },
                                    enabled = state.monthStart > 1,
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Text("−", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                }

                                Text(
                                    text = "Día ${state.monthStart}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )

                                IconButton(
                                    onClick = {
                                        viewModel.stepMonthStart(1)
                                    },
                                    enabled = state.monthStart < 28,
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 3. Tema de la aplicación
                SettingsCard(
                    icon = Icons.Default.Palette,
                    title = "Tema de la aplicación",
                    subtitle = "Preferencia visual de interfaz",
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = state.themeMode == ThemeMode.LIGHT,
                            onClick = {
                                viewModel.onThemeModeChanged(ThemeMode.LIGHT)
                                viewModel.savePreferences()
                            },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Claro", fontSize = 12.sp)
                                }
                            },
                            modifier = Modifier.weight(1f).height(40.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                            ),
                        )

                        FilterChip(
                            selected = state.themeMode == ThemeMode.DARK,
                            onClick = {
                                viewModel.onThemeModeChanged(ThemeMode.DARK)
                                viewModel.savePreferences()
                            },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Oscuro", fontSize = 12.sp)
                                }
                            },
                            modifier = Modifier.weight(1f).height(40.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                            ),
                        )

                        FilterChip(
                            selected = state.themeMode == ThemeMode.SYSTEM,
                            onClick = {
                                viewModel.onThemeModeChanged(ThemeMode.SYSTEM)
                                viewModel.savePreferences()
                            },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Smartphone, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sistema", fontSize = 12.sp)
                                }
                            },
                            modifier = Modifier.weight(1f).height(40.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }

                // 4. Ocultar montos sensibles
                SettingsCard(
                    icon = Icons.Default.VisibilityOff,
                    title = "Ocultar montos sensibles",
                    subtitle = "Modo MoneyText en vista general",
                    trailing = {
                        Switch(
                            checked = state.hideBalances,
                            onCheckedChange = {
                                viewModel.toggleHideBalances()
                                viewModel.savePreferences()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                )

                // 5. Alertas de vencimiento
                SettingsCard(
                    icon = Icons.Default.Notifications,
                    title = "Alertas de vencimiento",
                    subtitle = "Días de anticipación para recordatorios",
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Avisar previo al corte",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = "3 días",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Acceso a Historial de Movimientos
                onNavigateToMovements?.let {
                    SettingsActionCard(
                        icon = Icons.Default.Payments,
                        title = "Historial de Movimientos (Ledger)",
                        subtitle = "Consultar ledger y registrar operaciones",
                        onClick = it,
                    )
                }

                // Acceso a Cuentas y Tarjetas
                onNavigateToAccounts?.let {
                    SettingsActionCard(
                        icon = Icons.Default.AccountBalance,
                        title = "Mi Dinero Real / Cuentas",
                        subtitle = "Ver cuentas bancarias y tarjetas registradas",
                        onClick = it,
                    )
                }

                onNavigateToPurchase?.let {
                    SettingsActionCard(
                        icon = Icons.Default.Payments,
                        title = "Ver ofertas Kipu Premium",
                        subtitle = "Consulta precios localizados y condiciones en Google Play",
                        onClick = it,
                    )
                }

                // 6. Gestionar Categorías
                SettingsActionCard(
                    icon = Icons.Default.Category,
                    title = "Gestionar Categorías",
                    subtitle = "Ingresos, gastos y presupuestos asignados",
                    onClick = { onNavigateToCategories?.invoke() },
                )

                // 7. Configurar Captura / Permisos
                SettingsActionCard(
                    icon = Icons.Default.Chat,
                    title = "Configurar Captura",
                    subtitle = "Lectura de notificaciones y SMS bancarios",
                    onClick = { onNavigateToPermissions?.invoke() },
                )

                onNavigateToBiometrics?.let {
                    SettingsActionCard(
                        icon = Icons.Default.Fingerprint,
                        title = "Bloqueo local",
                        subtitle = "Configurar huella, rostro o credencial del dispositivo",
                        onClick = it,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 8. Botón Cerrar sesión
                if (onSignOut != null) {
                    Button(
                        onClick = viewModel::requestSignOut,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFDE8E8),
                            contentColor = Color(0xFFE53935),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cerrar sesión", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
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
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                trailing?.invoke()
            }

            content?.invoke()
        }
    }
}

@Composable
private fun SettingsActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
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
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
