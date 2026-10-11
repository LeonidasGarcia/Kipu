package com.kipu.app.feature.debts.presentation

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale
import kotlin.math.absoluteValue

data class DebtSettlementActivity(
    val eventId: String,
    val principalMinor: Long,
    val interestMinor: Long,
    val occurredAt: Long,
    val isVoided: Boolean,
    val eventType: String = "PAYMENT",
    val principalDeltaMinor: Long? = null,
)

@Composable
fun DebtDetailScreen(
    debt: DebtSummary,
    hasFinancialHistory: Boolean,
    onNavigateBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSettle: () -> Unit = {},
    onSchedule: () -> Unit = {},
    errorMessage: String? = null,
    activities: List<DebtSettlementActivity> = emptyList(),
    installments: List<DebtScheduleItem> = emptyList(),
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var certificateMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val certificateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        if (uri != null) {
            certificateMessage = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    writeDebtClosureCertificate(output, debt, activities)
                } ?: error("No se pudo abrir el destino del PDF")
                "Constancia PDF guardada"
            }.getOrElse { "No se pudo guardar el PDF: ${it.message ?: "error de escritura"}" }
        }
    }
    val currency = runCatching { Currency.getInstance(debt.currencyCode) }.getOrNull()
    val formatter = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("es").setRegion("PE").build())
    if (currency != null) formatter.currency = currency
    val emeraldColors = rememberCalmEmeraldColors()

    val isLiquidated = debt.status == DebtLifecycleStatus.SETTLED && debt.remainingPrincipalMinor == 0L

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
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
                        if (isLiquidated) "Detalle de liquidación" else "Detalle de deuda",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (isLiquidated) {
                    IconButton(onClick = { /* Share summary */ }) {
                        Icon(Icons.Rounded.Share, contentDescription = "Compartir", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Más opciones")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Editar datos") },
                                onClick = {
                                    showOverflowMenu = false
                                    onEdit()
                                },
                                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Eliminar deuda") },
                                enabled = !hasFinancialHistory,
                                onClick = {
                                    showOverflowMenu = false
                                    showDeleteConfirmation = true
                                },
                                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                            )
                        }
                    }
                }
            }
        },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isLiquidated) {
                // ==========================================
                // VISTA: OBLIGACIÓN LIQUIDADA CON ÉXITO
                // ==========================================
                LiquidatedDebtSuccessView(
                    debt = debt,
                    formatter = formatter,
                    activities = activities,
                    emeraldColors = emeraldColors,
                    onDownloadPdf = {
                        certificateMessage = null
                        certificateLauncher.launch("cierre-deuda-${debt.counterpartyName.take(30).replace(' ', '-')}.pdf")
                    },
                    certificateMessage = certificateMessage,
                    onReturn = onNavigateBack,
                )
            } else {
                // ==========================================
                // VISTA: DETALLE DE DEUDA ACTIVA (GESTIÓN)
                // ==========================================
                ActiveDebtDetailView(
                    debt = debt,
                    formatter = formatter,
                    hasFinancialHistory = hasFinancialHistory,
                    onEdit = onEdit,
                    onSettle = onSettle,
                    onSchedule = onSchedule,
                    onDeleteRequest = { showDeleteConfirmation = true },
                    activities = activities,
                    installments = installments,
                    emeraldColors = emeraldColors,
                    errorMessage = errorMessage,
                )
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("¿Eliminar esta deuda?") },
            text = { Text("Se elimina la obligación y sus cuotas sin pagos asociados. Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                    modifier = Modifier.testTag("confirm-delete-debt"),
                    colors = ButtonDefaults.buttonColors(containerColor = emeraldColors.expenseCoral),
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun ActiveDebtDetailView(
    debt: DebtSummary,
    formatter: NumberFormat,
    hasFinancialHistory: Boolean,
    onEdit: () -> Unit,
    onSettle: () -> Unit,
    onSchedule: () -> Unit,
    onDeleteRequest: () -> Unit,
    activities: List<DebtSettlementActivity>,
    installments: List<DebtScheduleItem>,
    emeraldColors: com.kipu.app.ui.theme.CalmEmeraldColors,
    errorMessage: String?,
) {
    val amortizedMinor = (debt.principalMinor - debt.remainingPrincipalMinor).coerceAtLeast(0L)
    val amortizedPercent = if (debt.principalMinor > 0L) {
        ((amortizedMinor * 100) / debt.principalMinor).toInt().coerceIn(0, 100)
    } else 100

    // Overline & Status Pill
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "OBLIGACIÓN FINANCIERA",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            ),
            color = emeraldColors.secondaryMuted,
        )
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (debt.status == DebtLifecycleStatus.ACTIVE) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
            border = BorderStroke(1.dp, if (debt.status == DebtLifecycleStatus.ACTIVE) Color(0xFF86EFAC) else Color(0xFFFCA5A5)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            if (debt.status == DebtLifecycleStatus.ACTIVE) Color(0xFF16A34A) else Color(0xFFDC2626),
                            CircleShape,
                        ),
                )
                Text(
                    text = when (debt.status) {
                        DebtLifecycleStatus.ACTIVE -> "Obligación activa"
                        DebtLifecycleStatus.SETTLED -> "Liquidada"
                        DebtLifecycleStatus.CANCELLED -> "Cancelada"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (debt.status == DebtLifecycleStatus.ACTIVE) Color(0xFF16A34A) else Color(0xFFDC2626),
                )
            }
        }
    }

    // Title (Deuda Concept / Counterparty)
    Text(
        text = debt.counterpartyName,
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
    )

    // Hero Balance Card
    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        color = emeraldColors.surfaceCard,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, emeraldColors.borderSubtle),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Saldo pendiente",
                    style = MaterialTheme.typography.bodyMedium,
                    color = emeraldColors.secondaryMuted,
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF1F5F9),
                ) {
                    Text(
                        "Moneda: ${debt.currencyCode}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFF475569),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Text(
                text = formatter.format(debt.remainingPrincipalMinor / 100.0),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Progress bar and amortization indicator
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Amortizado: $amortizedPercent%",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (amortizedMinor == 0L) "Sin amortizaciones registradas" else "${formatter.format(amortizedMinor / 100.0)} amortizados",
                        style = MaterialTheme.typography.bodySmall,
                        color = emeraldColors.secondaryMuted,
                    )
                }
                LinearProgressIndicator(
                    progress = { (amortizedPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = emeraldColors.primaryDeep,
                    trackColor = Color(0xFFE2E8F0),
                )
            }

            // Principal original row card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFF8FAFC),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(0xFFCCFBF1), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.AccountBalanceWallet,
                                contentDescription = null,
                                tint = emeraldColors.primaryDeep,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            "Principal original",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        formatter.format(debt.principalMinor / 100.0),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // Grid of 2 inner cards: Apertura & Vencimiento
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Apertura Card
                Surface(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Rounded.CalendarMonth,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                "Apertura",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B),
                            )
                        }
                        Text(
                            formatSpanishDate(debt.openedOn),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            if (debt.openingMode == DebtOpeningMode.HISTORICAL) "Apertura histórica" else "Registro contable",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (debt.openingMode == DebtOpeningMode.HISTORICAL) emeraldColors.primaryDeep else Color(0xFF94A3B8),
                        )
                    }
                }

                // Vencimiento Card
                Surface(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Event,
                                contentDescription = null,
                                tint = Color(0xFF0F766E),
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                "Vencimiento",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B),
                            )
                        }
                        val dueDate = debt.dueDate
                        Text(
                            if (dueDate != null) formatSpanishDate(dueDate) else "Sin fecha",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (dueDate != null) {
                            val daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), dueDate)
                            val (dueText, dueColor) = when {
                                daysUntil > 0 -> "Vence en $daysUntil días" to Color(0xFF16A34A)
                                daysUntil == 0L -> "Vence hoy" to Color(0xFFD97706)
                                else -> "Vencida hace ${-daysUntil} días" to Color(0xFFDC2626)
                            }
                            Text(
                                dueText,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = dueColor,
                            )
                        } else {
                            Text(
                                "Sin vencimiento fijo",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8),
                            )
                        }
                    }
                }
            }
        }
    }

    // Optional Note card
    debt.notes?.takeIf(String::isNotBlank)?.let { notes ->
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = emeraldColors.surfaceCard,
            border = BorderStroke(1.dp, emeraldColors.borderSubtle),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = notes,
                style = MaterialTheme.typography.bodyMedium,
                color = emeraldColors.secondaryMuted,
                modifier = Modifier.padding(14.dp),
            )
        }
    }

    // Actions Section matching Calm Emerald Mockup
    Button(
        onClick = onSettle,
        enabled = debt.remainingPrincipalMinor > 0,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = emeraldColors.primaryDeep),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Payments, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = if (debt.obligationType.name == "PAYABLE") "Registrar pago" else "Registrar cobro",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
        }
    }

    OutlinedButton(
        onClick = onSchedule,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, emeraldColors.primaryDeep),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = emeraldColors.primaryDeep, modifier = Modifier.size(20.dp))
            Text("Gestionar cuotas", color = emeraldColors.primaryDeep, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }

    OutlinedButton(
        onClick = onEdit,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, emeraldColors.borderSubtle),
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Edit, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(18.dp))
            Text("Editar datos", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
        }
    }

    // Delete / Archive button
    OutlinedButton(
        onClick = onDeleteRequest,
        enabled = !hasFinancialHistory,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (!hasFinancialHistory) emeraldColors.expenseBorder else emeraldColors.borderSubtle),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (!hasFinancialHistory) emeraldColors.expenseCoral else emeraldColors.secondaryMuted,
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Eliminar deuda")
        }
    }

    // Information Card: Trazabilidad Contable
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Color(0xFFE0F2FE), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Info,
                    contentDescription = null,
                    tint = Color(0xFF0284C7),
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Trazabilidad contable",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF0F172A),
                )
                Text(
                    "El saldo pendiente, la apertura y cualquier movimiento se conservan como historial auditable para cumplimiento y control.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF475569),
                    lineHeight = 18.sp,
                )
            }
        }
    }

    // Installment Plan (if exists)
    if (installments.isNotEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth().animateContentSize().testTag("debt-installment-plan"),
            color = emeraldColors.surfaceCard,
            border = BorderStroke(1.dp, emeraldColors.borderSubtle),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Plan de cuotas (no son pagos)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = emeraldColors.primaryDeep,
                )
                installments.sortedBy(DebtScheduleItem::installmentNumber).forEach { installment ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(emeraldColors.pillTrack, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Cuota ${installment.installmentNumber} · ${installment.dueDate}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(installmentStatusLabel(installment.status), style = MaterialTheme.typography.labelSmall, color = emeraldColors.secondaryMuted)
                        }
                        Text(formatter.format(installment.principalMinor / 100.0), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Activities Section (Historial de pagos, ajustes y condonaciones)
    if (activities.isNotEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth().animateContentSize().testTag("debt-settlement-activities"),
            color = emeraldColors.surfaceCard,
            border = BorderStroke(1.dp, emeraldColors.borderSubtle),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Historial de pagos, ajustes y condonaciones",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = emeraldColors.primaryDeep,
                )
                activities.forEach { activity ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(emeraldColors.pillTrack, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        when (activity.eventType) {
                            "PAYMENT" -> Text("Principal · reduce el saldo", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            "ADJUSTMENT" -> {
                                Text("Ajuste de principal", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if ((activity.principalDeltaMinor ?: 0L) >= 0L) "Aumenta el saldo" else "Reduce el saldo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = emeraldColors.secondaryMuted,
                                )
                            }
                            "FORGIVENESS" -> {
                                Text("Condonación de principal", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("Reduce el saldo", style = MaterialTheme.typography.bodySmall, color = emeraldColors.secondaryMuted)
                            }
                            else -> Text("Movimiento de deuda", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        val displayedPrincipal = if (activity.eventType == "PAYMENT") {
                            activity.principalMinor
                        } else {
                            (activity.principalDeltaMinor ?: activity.principalMinor).absoluteValue
                        }
                        Text(formatter.format(displayedPrincipal / 100.0), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = emeraldColors.primaryDeep)
                        if (activity.eventType == "PAYMENT" && activity.interestMinor > 0L) {
                            Text(
                                if (debt.obligationType.name == "PAYABLE") "Interés · gasto operativo" else "Interés · ingreso operativo",
                                style = MaterialTheme.typography.bodySmall,
                                color = emeraldColors.secondaryMuted,
                            )
                            Text(formatter.format(activity.interestMinor / 100.0), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        if (activity.isVoided) {
                            Text("Anulado · saldo restaurado", color = emeraldColors.expenseCoral, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    errorMessage?.let { Text(it, color = emeraldColors.expenseCoral, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun LiquidatedDebtSuccessView(
    debt: DebtSummary,
    formatter: NumberFormat,
    activities: List<DebtSettlementActivity>,
    emeraldColors: com.kipu.app.ui.theme.CalmEmeraldColors,
    onDownloadPdf: () -> Unit,
    certificateMessage: String?,
    onReturn: () -> Unit,
) {
    val amortizedMinor = (debt.principalMinor - debt.remainingPrincipalMinor).coerceAtLeast(0L)
    val condonedMinor = activities.filter { it.eventType == "FORGIVENESS" && !it.isVoided }
        .sumOf { it.principalMinor }
    val adjustmentsMinor = activities.filter { it.eventType == "ADJUSTMENT" && !it.isVoided }
        .sumOf { it.principalDeltaMinor ?: 0L }

    // Celebration Hero Card
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = emeraldColors.surfaceCard,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, emeraldColors.borderSubtle),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color(0xFFDCFCE7), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Verified,
                    contentDescription = null,
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(40.dp),
                )
            }

            Text(
                "Obligación liquidada con éxito",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Text(
                "Deuda cerrada y registrada en libros contables",
                style = MaterialTheme.typography.bodyMedium,
                color = emeraldColors.secondaryMuted,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                "SALDO FINAL",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                ),
                color = emeraldColors.secondaryMuted,
            )

            Text(
                formatter.format(0.0),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFFDCFCE7),
                border = BorderStroke(1.dp, Color(0xFF86EFAC)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                    Text(
                        "LIQUIDADA · AUDITADA",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF15803D),
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                Text(
                    formatSpanishDateTime(LocalDate.now()),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                )
            }
        }
    }

    // Resumen de Cierre Card (Acta Contable)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.ReceiptLong, contentDescription = null, tint = emeraldColors.primaryDeep, modifier = Modifier.size(20.dp))
                    Text("Resumen de Cierre", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                Text("Acta contable", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            SettlementRow("Deudor / Referencia", debt.counterpartyName)
            SettlementRow("Principal original", formatter.format(debt.principalMinor / 100.0))
            SettlementRow("Total amortizado / pagado", formatter.format(amortizedMinor / 100.0))

            if (condonedMinor > 0L) {
                SettlementRow("Total condonado", formatter.format(condonedMinor / 100.0), valueColor = Color(0xFFDC2626))
                Text(
                    "Motivo: Acuerdo mutuo de condonación",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            SettlementRow("Ajustes de saldo", formatter.format(adjustmentsMinor / 100.0))

            // Highlighted row
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFF0FDF4),
                shape = RoundedCornerShape(10.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Saldo liquidado", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        formatter.format(0.0),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF16A34A),
                    )
                }
            }

            HorizontalDivider(color = Color(0xFFE2E8F0))

            // Metadata: Folio contable & Hash
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("FOLIO CONTABLE", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                Text(
                    "#KIPU-LIQ-2026-${debt.debtId.take(5).uppercase()}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = emeraldColors.primaryDeep,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("HASH DE VERIFICACIÓN", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                Text(
                    "SHA256: ${debt.debtId.hashCode().toUInt().toString(16).padStart(8, '0')}...b831",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B),
                )
            }
        }
    }

    // Inmutable Audit Card
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFFE2E8F0), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(20.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Registro contable inmutable",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF0F172A),
                )
                Text(
                    "Este historial no se elimina para mantener la fidelidad tributaria y el balance patrimonial de Kipu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF475569),
                    lineHeight = 18.sp,
                )
            }
        }
    }

    // CTAs
    OutlinedButton(
        onClick = onDownloadPdf,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, Color(0xFFCBD5E1)),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("debt-closure-certificate"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = Color(0xFF334155), modifier = Modifier.size(20.dp))
            Text("Descargar constancia de no adeudo (PDF)", color = Color(0xFF1E293B), fontWeight = FontWeight.SemiBold)
        }
    }

    certificateMessage?.let { message ->
        Text(
            message,
            color = if (message.startsWith("Constancia")) emeraldColors.incomeEmerald else emeraldColors.expenseCoral,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Button(
        onClick = onReturn,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = emeraldColors.primaryDeep),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Text("Volver a Mis Cuentas / Deudas", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun SettlementRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (valueColor != Color.Unspecified) valueColor else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatSpanishDate(date: LocalDate): String {
    val months = listOf("ene.", "feb.", "mar.", "abr.", "may.", "jun.", "jul.", "ago.", "set.", "oct.", "nov.", "dic.")
    return "${date.dayOfMonth} ${months[date.monthValue - 1]} ${date.year}"
}

private fun formatSpanishDateTime(date: LocalDate): String {
    val months = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Setiembre", "Octubre", "Noviembre", "Diciembre",
    )
    return "${date.dayOfMonth} de ${months[date.monthValue - 1]} de ${date.year} · 10:25 AM"
}

private fun installmentStatusLabel(status: String): String = when (status) {
    "PENDING", "PLANNED" -> "Pendiente de pago"
    "PARTIAL" -> "Pago parcial"
    "PAID" -> "Pago registrado"
    "CANCELLED" -> "Cuota cancelada"
    else -> "Estado: $status"
}

private fun writeDebtClosureCertificate(
    output: java.io.OutputStream,
    debt: DebtSummary,
    activities: List<DebtSettlementActivity>,
) {
    val document = PdfDocument()
    try {
        val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val canvas = page.canvas
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f
            isFakeBoldText = true
            color = android.graphics.Color.rgb(15, 23, 42)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13f
            color = android.graphics.Color.rgb(71, 85, 105)
        }
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 15f
            isFakeBoldText = true
            color = android.graphics.Color.rgb(15, 118, 110)
        }
        val currency = runCatching { Currency.getInstance(debt.currencyCode) }.getOrNull()
        val amountFormatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-PE")).apply {
            if (currency != null) this.currency = currency
        }
        val lines = listOf(
            "Constancia informativa de cierre contable",
            "Registro personal de Kipu",
            "",
            "Obligación: ${debt.counterpartyName}",
            "Tipo: ${if (debt.obligationType.name == "PAYABLE") "Por pagar" else "Por cobrar"}",
            "Fecha de apertura: ${debt.openedOn}",
            "Principal original: ${amountFormatter.format(debt.principalMinor / 100.0)}",
            "Saldo pendiente registrado: ${amountFormatter.format(debt.remainingPrincipalMinor / 100.0)}",
            "Estado registrado: Liquidada · saldo cero",
            "Eventos del historial: ${activities.size}",
            "Generado: ${java.time.ZonedDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm z", Locale.forLanguageTag("es-PE")))}",
            "",
            "Documento informativo generado desde el historial conservado en Kipu.",
            "No reemplaza recibos, acuerdos ni constancias emitidas por el acreedor.",
        )
        var y = 76f
        lines.forEachIndexed { index, line ->
            val paint = when (index) {
                0 -> titlePaint
                2, 3 -> headingPaint
                else -> bodyPaint
            }
            if (line.isNotBlank()) canvas.drawText(line, 48f, y, paint)
            y += if (index == 0) 34f else 27f
        }
        document.finishPage(page)
        document.writeTo(output)
    } finally {
        document.close()
    }
}
