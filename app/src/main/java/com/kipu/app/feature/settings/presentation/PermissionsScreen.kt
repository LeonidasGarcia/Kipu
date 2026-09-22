package com.kipu.app.feature.settings.presentation

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kipu.app.feature.settings.domain.model.ConsentState
import com.kipu.app.feature.settings.domain.model.DeviceAuthorization
import com.kipu.app.feature.settings.domain.model.PermissionSource

private val KipuTeal = Color(0xFF0F766E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    viewModel: PermissionsViewModel,
    onNavigateBack: () -> Unit,
    onOpenSettingsIntent: (PermissionSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refresh()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Permisos y Automatización",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = KipuTeal)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Guarantee Banner (FR-017)
                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = "El registro manual siempre permanece 100% operativo sin importar el estado de estos permisos opcionales.",
                            fontSize = 13.sp,
                            color = Color(0xFF1B5E20),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Text(
                    text = "Fuentes Disponibles",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = KipuTeal,
                )

                // List of permission cards
                state.items.forEach { item ->
                    PermissionCard(
                        item = item,
                        onShowRationale = { viewModel.showRationale(item.source) },
                        onGrantConsent = { viewModel.grantConsent(item.source) },
                        onRevokeConsent = { viewModel.revokeConsent(item.source) },
                        onOpenSettings = { onOpenSettingsIntent(item.source) },
                    )
                }
            }
        }
    }

    // Contextual Rationale Dialog
    state.showRationaleFor?.let { source ->
        val item = state.items.find { it.source == source }
        if (item != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissRationale() },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = KipuTeal,
                    )
                },
                title = { Text(item.title) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.rationale)
                        if (item.requiresPremium) {
                            Text(
                                "Nota: Requiere suscripción activa al Plan Premium.",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.grantConsent(source)
                            if (source == PermissionSource.OWN_NOTIFICATIONS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = KipuTeal),
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text("Aceptar y Continuar")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.dismissRationale() },
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text("Cerrar")
                    }
                },
            )
        }
    }
}

@Composable
private fun PermissionCard(
    item: PermissionItemUiState,
    onShowRationale: () -> Unit,
    onGrantConsent: () -> Unit,
    onRevokeConsent: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val isGranted = item.isProcessingAuthorized

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "${item.title}. Estado: ${if (isGranted) "Activo" else "Inactivo"}"
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    Text(
                        text = item.description,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Processing status badge with icon + text
                Surface(
                    color = if (isGranted) Color(0xFFE6F4EA) else Color(0xFFF1F3F4),
                    shape = MaterialTheme.shapes.extraSmall,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Close,
                            contentDescription = null,
                            tint = if (isGranted) Color(0xFF137333) else Color(0xFF5F6368),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = if (isGranted) "Activo" else "Inactivo",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isGranted) Color(0xFF137333) else Color(0xFF5F6368),
                        )
                    }
                }
            }

            // Diagnostic details
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val devText = if (item.deviceAuth == DeviceAuthorization.GRANTED) "Concedido en sistema" else "Desactivado en sistema"
                val consentText = if (item.consentState == ConsentState.GRANTED) "Consentimiento otorgado" else "Sin consentimiento de cuenta"

                Text("• Dispositivo: $devText", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("• Cuenta: $consentText", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.requiresPremium) {
                    Text("• Nivel: Requiere Plan Premium", fontSize = 12.sp, color = KipuTeal, fontWeight = FontWeight.Medium)
                }
            }

            // Action buttons (48dp touch targets)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onShowRationale,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Text("Explicación", fontSize = 13.sp)
                }

                if (item.consentState == ConsentState.GRANTED) {
                    Button(
                        onClick = onRevokeConsent,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD93025)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Text("Revocar", fontSize = 13.sp)
                    }
                } else {
                    Button(
                        onClick = onGrantConsent,
                        colors = ButtonDefaults.buttonColors(containerColor = KipuTeal),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Text("Habilitar", fontSize = 13.sp)
                    }
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Abrir ajustes del sistema",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
