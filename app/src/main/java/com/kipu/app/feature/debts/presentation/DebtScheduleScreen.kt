package com.kipu.app.feature.debts.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.feature.debts.domain.DebtAmountParser
import com.kipu.app.feature.debts.domain.model.CloseDebtAction
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Currency
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtScheduleScreen(
    state: DebtScheduleUiState,
    onInstallmentCountChange: (String) -> Unit,
    onFirstDueDateChange: (String) -> Unit,
    onReminderLeadDaysChange: (Int?) -> Unit,
    onSchedule: () -> Unit,
    onCancelSchedule: () -> Unit,
    onCloseDebt: (CloseDebtAction, String) -> Unit,
    onNavigateBack: () -> Unit,
    onAdjustmentAmountChange: (String) -> Unit = {},
    onForgivenessAmountChange: (String) -> Unit = {},
    onClosureReasonChange: (String) -> Unit = {},
) {
    val debt = state.debt
    val emeraldColors = rememberCalmEmeraldColors()
    var attemptedAction by remember(debt?.debtId) { mutableStateOf<CloseDebtAction?>(null) }
    var selectedTab by remember(debt?.debtId) {
        mutableIntStateOf(if (debt != null && debt.remainingPrincipalMinor == 0L) 1 else 0)
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(onClick = onNavigateBack, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                        }
                        Text(
                            if (selectedTab == 0) "Cuotas y cronograma" else "Ajustes y cierre contable",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = { /* Help dialog */ }) {
                        Icon(
                            if (selectedTab == 0) Icons.Rounded.HelpOutline else Icons.Rounded.Info,
                            contentDescription = "Ayuda",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Tab Selector: Cuotas vs Ajustes
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = emeraldColors.primaryDeep,
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = emeraldColors.primaryDeep,
                                height = 3.dp,
                            )
                        }
                    },
                    divider = { HorizontalDivider(color = emeraldColors.borderSubtle) },
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                "Cuotas y cronograma",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 0) emeraldColors.primaryDeep else emeraldColors.secondaryMuted,
                            )
                        },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                "Ajustes y cierre",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 1) emeraldColors.primaryDeep else emeraldColors.secondaryMuted,
                            )
                        },
                    )
                }
            }
        },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (debt == null) {
                Text(state.errorMessage ?: "Cargando deuda…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                if (selectedTab == 0) {
                    // ==========================================
                    // TAB 0: CUOTAS Y CRONOGRAMA
                    // ==========================================
                    ScheduleProjectionTab(
                        debt = debt,
                        state = state,
                        emeraldColors = emeraldColors,
                        onInstallmentCountChange = onInstallmentCountChange,
                        onFirstDueDateChange = onFirstDueDateChange,
                        onReminderLeadDaysChange = onReminderLeadDaysChange,
                        onSchedule = onSchedule,
                        onCancelSchedule = onCancelSchedule,
                    )
                } else {
                    // ==========================================
                    // TAB 1: AJUSTES Y CIERRE CONTABLE
                    // ==========================================
                    AdjustmentsAndClosureTab(
                        debt = debt,
                        state = state,
                        emeraldColors = emeraldColors,
                        onAdjustmentAmountChange = onAdjustmentAmountChange,
                        onForgivenessAmountChange = onForgivenessAmountChange,
                        onClosureReasonChange = onClosureReasonChange,
                        onRequestAction = { action -> attemptedAction = action },
                    )
                }
            }
        }
    }

    attemptedAction?.let { action ->
        AlertDialog(
            onDismissRequest = { attemptedAction = null },
            title = { Text(closureTitle(action), fontWeight = FontWeight.Bold) },
            text = { Text("Confirma esta acción. El motivo y el saldo actualizado quedarán en el historial de auditoría.") },
            confirmButton = {
                Button(
                    onClick = {
                        val currentAction = action
                        attemptedAction = null
                        onCloseDebt(currentAction, state.closureReason)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = emeraldColors.primaryDeep),
                ) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { attemptedAction = null }) { Text("Volver") } },
        )
    }
}

// =========================================================================
// TAB 0: CUOTAS Y CRONOGRAMA IMPLEMENTATION
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleProjectionTab(
    debt: DebtSummary,
    state: DebtScheduleUiState,
    emeraldColors: com.kipu.app.ui.theme.CalmEmeraldColors,
    onInstallmentCountChange: (String) -> Unit,
    onFirstDueDateChange: (String) -> Unit,
    onReminderLeadDaysChange: (Int?) -> Unit,
    onSchedule: () -> Unit,
    onCancelSchedule: () -> Unit,
) {
    val formatter = currencyFormatter(debt.currencyCode)
    val count = state.installmentCount.toIntOrNull()?.coerceIn(1, 48) ?: 3
    val approxPerQuotaMinor = if (count > 0) debt.remainingPrincipalMinor / count else 0L

    var showDatePicker by remember { mutableStateOf(false) }

    // Header Card
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = emeraldColors.surfaceCard,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, emeraldColors.borderSubtle),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFFCCFBF1), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null, tint = emeraldColors.primaryDeep, modifier = Modifier.size(24.dp))
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(debt.counterpartyName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFDCFCE7),
                        ) {
                            Text(
                                "Activa",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFF16A34A),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text("Saldo pendiente a programar", style = MaterialTheme.typography.bodySmall, color = emeraldColors.secondaryMuted)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Total", style = MaterialTheme.typography.labelSmall, color = emeraldColors.secondaryMuted)
                Text(
                    formatter.format(debt.remainingPrincipalMinor / 100.0),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }

    // Aviso contable importante banner
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFEFF6FF),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFDBEAFE)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color(0xFFDBEAFE), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Aviso contable importante", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF1E3A8A))
                Text(
                    "Las cuotas planificadas organizan tu calendario de vencimientos, pero NO registran pagos automáticamente. Los pagos reales deben registrarse por separado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF1E40AF),
                    lineHeight = 18.sp,
                )
            }
        }
    }

    // Section: Configurar cronograma de pagos
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Configurar cronograma de pagos", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = Color(0xFFCCFBF1),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = emeraldColors.primaryDeep, modifier = Modifier.size(14.dp))
                Text("Kipu Auto", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = emeraldColors.primaryDeep)
            }
        }
    }

    // Steppers Grid: Cantidad de cuotas & Avisar días antes
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Cantidad de cuotas
        Surface(
            modifier = Modifier.weight(1f),
            color = emeraldColors.surfaceCard,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, emeraldColors.borderSubtle),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Cantidad de cuotas", style = MaterialTheme.typography.labelSmall, color = emeraldColors.secondaryMuted)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF1F5F9),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(
                            onClick = { if (count > 1) onInstallmentCountChange((count - 1).toString()) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Rounded.Remove, contentDescription = "Menos", modifier = Modifier.size(18.dp))
                        }
                        Text(
                            count.toString(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        IconButton(
                            onClick = { if (count < 48) onInstallmentCountChange((count + 1).toString()) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Más", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.ReceiptLong, contentDescription = null, tint = emeraldColors.primaryDeep, modifier = Modifier.size(14.dp))
                    Text(
                        "Aprox. ${formatter.format(approxPerQuotaMinor / 100.0)} / cuota",
                        style = MaterialTheme.typography.labelSmall,
                        color = emeraldColors.primaryDeep,
                    )
                }
            }
        }

        // Avisar días antes
        val leadDays = state.reminderLeadDays ?: 3
        Surface(
            modifier = Modifier.weight(1f),
            color = emeraldColors.surfaceCard,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, emeraldColors.borderSubtle),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Avisar días antes", style = MaterialTheme.typography.labelSmall, color = emeraldColors.secondaryMuted)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF1F5F9),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(
                            onClick = { onReminderLeadDaysChange(if (leadDays > 0) leadDays - 1 else 0) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Rounded.Remove, contentDescription = "Menos", modifier = Modifier.size(18.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Rounded.Notifications, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(16.dp))
                            Text(
                                "$leadDays días",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                        IconButton(
                            onClick = { onReminderLeadDaysChange(leadDays + 1) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Más", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Text("Dejar en 0 para desactivar recordatorios", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
            }
        }
    }

    // Primer vencimiento card
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDatePicker = true },
        color = emeraldColors.surfaceCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, emeraldColors.borderSubtle),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Primer vencimiento", style = MaterialTheme.typography.labelSmall, color = emeraldColors.secondaryMuted)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = emeraldColors.primaryDeep, modifier = Modifier.size(18.dp))
                        Text(
                            formatReadableSpanishDate(state.firstDueDate),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                    Icon(Icons.Rounded.EditCalendar, contentDescription = "Editar fecha", tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Periodicidad", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                Text("Mensual (cada 30 días)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = Color(0xFF475569))
            }
        }
    }

    // Section: Cronograma proyectado
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cronograma proyectado", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFFE2E8F0),
            ) {
                Text(
                    "$count cuotas",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF334155),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(modifier = Modifier.size(6.dp).background(Color(0xFFD97706), CircleShape))
            Text("Sin amortizar", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD97706))
        }
    }

    // List of projected or saved cuotas
    val projectedItems = remember(debt.remainingPrincipalMinor, count, state.firstDueDate) {
        calculateProjections(debt.remainingPrincipalMinor, count, state.firstDueDate)
    }

    projectedItems.forEachIndexed { index, item ->
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = emeraldColors.surfaceCard,
            border = BorderStroke(1.dp, if (index == 0) emeraldColors.primaryDeep.copy(alpha = 0.5f) else emeraldColors.borderSubtle),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(if (index == 0) Color(0xFFCCFBF1) else Color(0xFFF1F5F9), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            (index + 1).toString(),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (index == 0) emeraldColors.primaryDeep else Color(0xFF475569),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Cuota ${index + 1}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (index == 0) Color(0xFFFEF3C7) else Color(0xFFF1F5F9),
                            ) {
                                Text(
                                    if (index == 0) "Pendiente" else "Futura",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (index == 0) Color(0xFFB45309) else Color(0xFF475569),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Rounded.Schedule, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                            Text(item.formattedDate, style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatter.format(item.amountMinor / 100.0),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(item.caption, style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                }
            }
        }
    }

    // Total projection sum card
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                Text("Suma total proyectada:", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
            }
            Text(
                "${formatter.format(debt.remainingPrincipalMinor / 100.0)} exactos",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF16A34A),
            )
        }
    }

    // Primary CTA: Guardar cronograma
    Button(
        onClick = onSchedule,
        enabled = !state.isSaving,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = emeraldColors.primaryDeep),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(if (state.isSaving) "Guardando…" else "Guardar cronograma", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }

    Text(
        "Podrás modificar las fechas en cualquier momento",
        style = MaterialTheme.typography.labelSmall,
        color = Color(0xFF94A3B8),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )

    if (state.installments.any { it.status == "PENDING" || it.status == "PARTIAL" }) {
        OutlinedButton(
            onClick = onCancelSchedule,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("debt-schedule-cancel"),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
        ) { Text("Cancelar cuotas y avisos") }
    }

    AnimatedVisibility(visible = state.successMessage != null) {
        state.successMessage?.let {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = emeraldColors.pillTrack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    it,
                    color = emeraldColors.primaryDeep,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = runCatching {
                LocalDate.parse(state.firstDueDate).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrNull(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            val localDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                            onFirstDueDateChange(localDate.toString())
                        }
                        showDatePicker = false
                    },
                ) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// =========================================================================
// TAB 1: AJUSTES Y CIERRE CONTABLE IMPLEMENTATION
// =========================================================================

@Composable
private fun AdjustmentsAndClosureTab(
    debt: DebtSummary,
    state: DebtScheduleUiState,
    emeraldColors: com.kipu.app.ui.theme.CalmEmeraldColors,
    onAdjustmentAmountChange: (String) -> Unit,
    onForgivenessAmountChange: (String) -> Unit,
    onClosureReasonChange: (String) -> Unit,
    onRequestAction: (CloseDebtAction) -> Unit,
) {
    val formatter = currencyFormatter(debt.currencyCode)
    val isZeroBalance = debt.remainingPrincipalMinor == 0L
    var isReduce by remember { mutableStateOf(true) }
    var isSection1Expanded by remember { mutableStateOf(!isZeroBalance) }

    // Validation state
    val parsedForgivenessMinor = remember(state.forgivenessAmount) {
        DebtAmountParser.toMinorUnits(state.forgivenessAmount)
    }
    val isForgivenessExceeded = parsedForgivenessMinor != null && parsedForgivenessMinor > debt.remainingPrincipalMinor
    var attemptedForgivenessSubmit by remember { mutableStateOf(false) }
    val isReasonMissing = attemptedForgivenessSubmit && state.closureReason.isBlank()
    val hasValidationError = isForgivenessExceeded || isReasonMissing || (state.errorMessage != null)

    // Top Header Card
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = emeraldColors.surfaceCard,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, emeraldColors.borderSubtle),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF1F5F9),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(if (isZeroBalance) Color(0xFF16A34A) else Color(0xFFD97706), CircleShape),
                        )
                        Text(debt.counterpartyName, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                    }
                }

                if (isZeroBalance) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFDCFCE7),
                        border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(14.dp))
                            Text("Saldada", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF15803D))
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(if (hasValidationError) Color(0xFFFEE2E2) else Color(0xFFCCFBF1), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.AccountBalanceWallet,
                            contentDescription = null,
                            tint = if (hasValidationError) Color(0xFFDC2626) else emeraldColors.primaryDeep,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Text(
                if (isZeroBalance) "Saldo pendiente liquidación" else "Saldo pendiente exigible",
                style = MaterialTheme.typography.bodySmall,
                color = emeraldColors.secondaryMuted,
            )

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    formatter.format(debt.remainingPrincipalMinor / 100.0),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 34.sp,
                    ),
                    color = if (hasValidationError && !isZeroBalance) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurface,
                )
                if (isZeroBalance) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFDCFCE7),
                    ) {
                        Text(
                            "100% amortizado",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF16A34A),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                } else if (hasValidationError) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFEE2E2),
                    ) {
                        Text(
                            "Activa",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFDC2626),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            if (isZeroBalance) {
                Text("Saldo liquidado · Listo para cierre contable", style = MaterialTheme.typography.bodySmall, color = Color(0xFF16A34A))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF0FDF4),
                    border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(20.dp))
                        Text(
                            "Saldo completamente amortizado. La obligación cumple los requisitos contables para cerrarse definitivamente sin saldos pendientes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF15803D),
                            lineHeight = 18.sp,
                        )
                    }
                }
            } else {
                Text(
                    "Obligación contable ${debt.counterpartyName}. Liquidación sujeta a balance cero.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                )
            }
        }
    }

    // Validation Error Banner (if error message exists or client validation fails)
    AnimatedVisibility(visible = hasValidationError) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFFEF2F2),
            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(22.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Error de validación contable", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF991B1B))
                    Text(
                        state.errorMessage ?: "Revisa los campos señalados antes de procesar el ajuste contable. Hay montos excedentes y campos obligatorios vacíos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFDC2626),
                    )
                }
            }
        }
    }

    // SECTION 1: Ajuste manual de saldo
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = emeraldColors.surfaceCard,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, emeraldColors.borderSubtle),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (!isZeroBalance) isSection1Expanded = !isSection1Expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("1. Ajuste manual de saldo", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text(
                            if (isZeroBalance) "Variaciones de redondeo o notas" else "Incrementar o debitar saldo por diferencia",
                            style = MaterialTheme.typography.labelSmall,
                            color = emeraldColors.secondaryMuted,
                        )
                    }
                }
                if (isZeroBalance) {
                    Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFF1F5F9)) {
                        Text("Sin ajustes", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                } else {
                    Icon(
                        if (isSection1Expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                    )
                }
            }

            AnimatedVisibility(visible = isSection1Expanded && !isZeroBalance) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Monto del ajuste (+/-)", style = MaterialTheme.typography.labelSmall, color = emeraldColors.secondaryMuted)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF1F5F9),
                        ) {
                            Row(modifier = Modifier.padding(3.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isReduce) emeraldColors.primaryDeep else Color.Transparent)
                                        .clickable { isReduce = true }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        "- Reducir",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (isReduce) Color.White else Color(0xFF475569),
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (!isReduce) emeraldColors.primaryDeep else Color.Transparent)
                                        .clickable { isReduce = false }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        "+ Aumentar",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (!isReduce) Color.White else Color(0xFF475569),
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = state.adjustmentAmount.removePrefix("-").removePrefix("+"),
                            onValueChange = { onAdjustmentAmountChange(if (isReduce) "-$it" else it) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Ej: 10.00") },
                            prefix = { Text(if (debt.currencyCode == "PEN") "S/ " else "${debt.currencyCode} ") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                        )
                    }

                    Text(
                        "El signo negativo reduce el saldo exigible al deudor.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                    )

                    OutlinedTextField(
                        value = state.closureReason,
                        onValueChange = onClosureReasonChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Motivo del ajuste *") },
                        placeholder = { Text("Ej: Corrección de cálculo inicial o redondeo") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                    )

                    OutlinedButton(
                        onClick = { onRequestAction(CloseDebtAction.ADJUST) },
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, emeraldColors.primaryDeep),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = emeraldColors.primaryDeep, modifier = Modifier.size(18.dp))
                            Text("Aplicar ajuste de saldo", color = emeraldColors.primaryDeep, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    // SECTION 2: Condonación de deuda
    val isSection2Error = (isForgivenessExceeded || isReasonMissing) && !isZeroBalance
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = emeraldColors.surfaceCard,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            if (isSection2Error) 1.5.dp else 1.dp,
            if (isSection2Error) Color(0xFFEF4444) else emeraldColors.borderSubtle,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(if (isSection2Error) Color(0xFFFEE2E2) else Color(0xFFDCFCE7), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.VolunteerActivism,
                            contentDescription = null,
                            tint = if (isSection2Error) Color(0xFFDC2626) else Color(0xFF16A34A),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column {
                        Text("2. Condonación de deuda", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text(
                            if (isZeroBalance) "Completada con éxito" else "Extinción parcial o total sin ingreso monetario",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isZeroBalance) Color(0xFF16A34A) else emeraldColors.secondaryMuted,
                        )
                    }
                }
                if (isZeroBalance) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(20.dp))
                }
            }

            if (isZeroBalance) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${formatter.format(0.0)} exonerados",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF1E293B),
                            )
                            Text(
                                formatReadableSpanishDate(debt.openedOn.toString()),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B),
                            )
                        }
                        HorizontalDivider(color = Color(0xFFE2E8F0))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Rounded.Description, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                            Text(
                                "Motivo: \"${debt.notes?.takeIf(String::isNotBlank) ?: "Acuerdo mutuo de exoneración comercial"}\"",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF475569),
                            )
                        }
                    }
                }
            } else {
                // Shortcut row: Atajo: Aplicar saldo total S/ XX.XX
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Atajo:", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFF1F5F9),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.clickable {
                            onForgivenessAmountChange(
                                String.format(Locale.US, "%.2f", debt.remainingPrincipalMinor / 100.0),
                            )
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                "Aplicar saldo total",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF334155),
                            )
                            Text(
                                formatter.format(debt.remainingPrincipalMinor / 100.0),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = emeraldColors.primaryDeep,
                            )
                        }
                    }
                }

                // Field: Monto a condonar
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Monto a condonar (${currencyLabel(debt.currencyCode)}) *",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isForgivenessExceeded) Color(0xFFDC2626) else emeraldColors.secondaryMuted,
                        )
                        Text("Máx: ${formatter.format(debt.remainingPrincipalMinor / 100.0)}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                    }
                    OutlinedTextField(
                        value = state.forgivenessAmount,
                        onValueChange = {
                            attemptedForgivenessSubmit = false
                            onForgivenessAmountChange(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        prefix = { Text(if (debt.currencyCode == "PEN") "S/ " else "${debt.currencyCode} ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        isError = isForgivenessExceeded,
                        trailingIcon = if (isForgivenessExceeded) {
                            { Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Color(0xFFDC2626)) }
                        } else null,
                    )
                    if (isForgivenessExceeded) {
                        Text(
                            "✕ El monto excede el saldo pendiente máximo exigible de ${formatter.format(debt.remainingPrincipalMinor / 100.0)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFDC2626),
                        )
                    }
                }

                // Field: Motivo de condonación
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Motivo de condonación * (Requerido para auditoría)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isReasonMissing) Color(0xFFDC2626) else emeraldColors.secondaryMuted,
                    )
                    OutlinedTextField(
                        value = state.closureReason,
                        onValueChange = {
                            attemptedForgivenessSubmit = false
                            onClosureReasonChange(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Escribe el justificativo contable obligatorio...") },
                        shape = RoundedCornerShape(12.dp),
                        minLines = 2,
                        maxLines = 4,
                        isError = isReasonMissing,
                        trailingIcon = if (isReasonMissing) {
                            { Icon(Icons.Rounded.PriorityHigh, contentDescription = null, tint = Color(0xFFDC2626)) }
                        } else null,
                    )
                    if (isReasonMissing) {
                        Text(
                            "⚠ El motivo es obligatorio para fines de auditoría e historial contable",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFDC2626),
                        )
                    }
                }

                Button(
                    onClick = {
                        if (isForgivenessExceeded || state.closureReason.isBlank() || parsedForgivenessMinor == null) {
                            attemptedForgivenessSubmit = true
                        } else {
                            onRequestAction(CloseDebtAction.FORGIVE)
                        }
                    },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = emeraldColors.primaryDeep),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.VolunteerActivism, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Registrar condonación", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // SECTION 3: Cierre y Liquidación
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = emeraldColors.surfaceCard,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(if (isZeroBalance) 1.5.dp else 1.dp, if (isZeroBalance) Color(0xFF10B981) else emeraldColors.borderSubtle),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(if (isZeroBalance) Color(0xFFCCFBF1) else Color(0xFFF1F5F9), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (isZeroBalance) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = if (isZeroBalance) emeraldColors.primaryDeep else Color(0xFF64748B),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column {
                        Text("3. Cierre y Liquidación", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text(
                            if (isZeroBalance) "Paso final disponible" else "Estado final de la obligación en libros",
                            style = MaterialTheme.typography.labelSmall,
                            color = emeraldColors.secondaryMuted,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isZeroBalance) Color(0xFFCCFBF1) else Color(0xFFF1F5F9),
                ) {
                    Text(
                        if (isZeroBalance) "Habilitado" else "Bloqueado",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isZeroBalance) emeraldColors.primaryDeep else Color(0xFF94A3B8),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            if (isZeroBalance) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF0FDF4),
                    border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(18.dp))
                        Text("Obligación elegible para liquidación definitiva", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = Color(0xFF15803D))
                    }
                }

                Text(
                    "Al liquidar, se archivará la deuda del panel de cobro activo y se guardará la constancia en el historial contable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                    lineHeight = 18.sp,
                )

                Button(
                    onClick = { onRequestAction(CloseDebtAction.SETTLE) },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = emeraldColors.primaryDeep),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text("Liquidar obligación (Cierre definitivo)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                OutlinedButton(
                    onClick = { onRequestAction(CloseDebtAction.CANCEL) },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Archivar obligación sin liquidar")
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Cierre de obligación no disponible", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                            Text(
                                "La obligación aún tiene ${formatter.format(debt.remainingPrincipalMinor / 100.0)} pendientes. Amortiza, ajusta o condona el saldo restante para habilitar la liquidación definitiva.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B),
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }

                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(disabledContainerColor = Color(0xFFE2E8F0), disabledContentColor = Color(0xFF94A3B8)),
                ) {
                    Text("Liquidar obligación (Cierre definitivo)")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE2E8F0))
                    Text("O ALTERNATIVAMENTE", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE2E8F0))
                }

                OutlinedButton(
                    onClick = { onRequestAction(CloseDebtAction.CANCEL) },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Archivar obligación sin liquidar")
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                    Text(
                        "Mantiene el saldo exigible de ${formatter.format(debt.remainingPrincipalMinor / 100.0)} pero se oculta del panel de cobro activo.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                    )
                }
            }
        }
    }
}

// =========================================================================
// HELPERS
// =========================================================================

private data class ProjectedQuota(
    val installmentNumber: Int,
    val amountMinor: Long,
    val formattedDate: String,
    val caption: String,
)

private fun calculateProjections(totalMinor: Long, count: Int, firstDueDateStr: String): List<ProjectedQuota> {
    if (count <= 0 || totalMinor <= 0L) return emptyList()
    val base = totalMinor / count
    val remainder = totalMinor % count
    val startDate = runCatching { LocalDate.parse(firstDueDateStr) }.getOrDefault(LocalDate.now().plusMonths(1))

    return (1..count).map { num ->
        val amount = if (num == 1) base + remainder else base
        val date = startDate.plusMonths((num - 1).toLong())
        val caption = when {
            num == 1 && remainder > 0L -> "Ajuste de céntimos"
            num == count -> "Cuota final"
            else -> "Cuota base"
        }
        ProjectedQuota(
            installmentNumber = num,
            amountMinor = amount,
            formattedDate = formatShortSpanishDate(date),
            caption = caption,
        )
    }
}

private fun formatShortSpanishDate(date: LocalDate): String {
    val months = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Set", "Oct", "Nov", "Dic")
    return "${date.dayOfMonth} ${months[date.monthValue - 1]} ${date.year}"
}

private fun formatReadableSpanishDate(dateStr: String): String {
    val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return dateStr
    val months = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "setiembre", "octubre", "noviembre", "diciembre",
    )
    return "${date.dayOfMonth} de ${months[date.monthValue - 1]} de ${date.year}"
}

private fun currencyFormatter(currencyCode: String): NumberFormat =
    NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("es").setRegion("PE").build()).apply {
        runCatching { Currency.getInstance(currencyCode) }.getOrNull()?.let { currency = it }
    }

private fun currencyLabel(currencyCode: String): String = if (currencyCode == "PEN") "S/" else currencyCode

private fun closureTitle(action: CloseDebtAction): String = when (action) {
    CloseDebtAction.SETTLE -> "Marcar como liquidada"
    CloseDebtAction.CANCEL -> "Cancelar o archivar obligación"
    CloseDebtAction.ADJUST -> "Aplicar ajuste de saldo"
    CloseDebtAction.FORGIVE -> "Registrar condonación"
}
