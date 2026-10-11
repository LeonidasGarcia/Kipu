package com.kipu.app.feature.accounts.presentation.instruments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.InstallmentScheduleItem
import com.kipu.app.feature.accounts.domain.model.InstallmentSimulation
import com.kipu.app.feature.accounts.domain.model.PurchaseCandidate
import com.kipu.app.feature.accounts.domain.usecase.SimulateInstallments
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.time.format.DateTimeFormatter

@Composable
fun InstallmentSimulatorScreen(
    candidate: PurchaseCandidate,
    card: CreditCard,
    teaBps: Int? = null,
    availableCredit: Money? = null,
    onConfirmPurchase: (installments: Int) -> Unit,
    onRejectPurchase: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedInstallments by remember {
        mutableIntStateOf(candidate.suggestedInstallments.takeIf { it in setOf(1, 3, 6, 12) } ?: 3)
    }
    var merchantConfirmsZeroInterest by remember(candidate.id) { mutableStateOf(false) }
    val emeraldColors = rememberCalmEmeraldColors()

    val simulation = remember(selectedInstallments, candidate, card, teaBps, merchantConfirmsZeroInterest) {
        SimulateInstallments()(
            candidate = candidate,
            card = card,
            installmentsCount = selectedInstallments,
            acceptedReferenceTeaBps = teaBps,
            zeroInterestPromotion = selectedInstallments == 3 && merchantConfirmsZeroInterest,
        )
    }
    val hasFinancialEstimate = simulation.appliedTeaBps != null ||
        (selectedInstallments == 3 && merchantConfirmsZeroInterest)
    val availableMinor = availableCredit?.minorUnits?.coerceAtLeast(0L)
    val overLimitMinor = availableMinor?.let { available ->
        (candidate.amount.minorUnits - available).takeIf { it > 0L }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        border = BorderStroke(1.dp, emeraldColors.borderSubtle),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 760.dp)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header with merchant badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(emeraldColors.pillTrack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ShoppingBag,
                        contentDescription = null,
                        tint = emeraldColors.primaryDeep,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Simular compra a crédito",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${card.issuer} •••• ${card.lastFourDigits} · ${candidate.merchant}",
                        style = MaterialTheme.typography.bodySmall,
                        color = emeraldColors.secondaryMuted,
                    )
                }
            }

            // Purchase amount hero card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = emeraldColors.surfaceCard,
                border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Monto principal a financiar",
                        style = MaterialTheme.typography.labelMedium,
                        color = emeraldColors.secondaryMuted,
                    )
                    MoneyText(
                        money = candidate.amount,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = emeraldColors.primaryDeep,
                    )
                }
            }

            // Installment options selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Número de cuotas:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (selectedInstallments == 1) "1 pago (Directo)" else "$selectedInstallments cuotas",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = emeraldColors.primaryDeep,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Opciones de pago: 1, 3, 6 o 12 cuotas. Seleccionado: $selectedInstallments"
                        },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(1, 3, 6, 12).forEach { count ->
                        val isSelected = selectedInstallments == count
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedInstallments = count
                                if (count != 3) merchantConfirmsZeroInterest = false
                            },
                            label = {
                                Text(
                                    text = if (count == 1) "1 pago" else "$count",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = emeraldColors.primaryDeep,
                                selectedLabelColor = Color.White,
                                containerColor = emeraldColors.pillTrack,
                                labelColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) emeraldColors.primaryDeep else emeraldColors.borderSubtle,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (selectedInstallments == 3) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = emeraldColors.incomeBg,
                    border = BorderStroke(1.dp, emeraldColors.incomeBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = merchantConfirmsZeroInterest,
                            onCheckedChange = { merchantConfirmsZeroInterest = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = emeraldColors.primaryDeep,
                            ),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Promoción de 3 cuotas sin intereses confirmada por el comercio",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = emeraldColors.incomeEmerald,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // P10 Overutilization Warning
            AnimatedVisibility(visible = overLimitMinor != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = emeraldColors.warningBg,
                    border = BorderStroke(1.dp, emeraldColors.warningBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Alerta: el importe supera el crédito disponible; la compra se puede confirmar con sobreutilización"
                        },
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = emeraldColors.warningAmber,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = "Compra sobre la línea disponible",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = emeraldColors.warningText,
                            )
                        }
                        overLimitMinor?.let { excess ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Sobreutilización estimada",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = emeraldColors.warningText,
                                )
                                MoneyText(
                                    money = Money(excess, card.currency),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = emeraldColors.warningText,
                                )
                            }
                        }
                        Text(
                            text = "Regla P10: Puedes confirmar la compra completa. Kipu mostrará el disponible desde S/ 0.00 y registrará la sobreutilización.",
                            style = MaterialTheme.typography.bodySmall,
                            color = emeraldColors.warningText.copy(alpha = 0.9f),
                        )
                    }
                }
            }

            // Simulation impact card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = emeraldColors.surfaceCard,
                border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Interés financiero estimado:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = emeraldColors.secondaryMuted,
                        )
                        if (hasFinancialEstimate) {
                            MoneyText(
                                money = simulation.totalInterest,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        } else {
                            Text("No disponible", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Total a pagar financiado:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (hasFinancialEstimate) {
                            MoneyText(
                                money = simulation.totalFinanced,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = emeraldColors.primaryDeep,
                            )
                        } else {
                            Text("No disponible", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (simulation.appliedTeaBps != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Rounded.Percent, contentDescription = null, tint = emeraldColors.secondaryMuted, modifier = Modifier.size(14.dp))
                            Text(
                                text = "Tasa contractual aplicada: ${(simulation.appliedTeaBps / 100.0)}% TEA",
                                style = MaterialTheme.typography.labelSmall,
                                color = emeraldColors.secondaryMuted,
                            )
                        }
                    }
                }
            }

            // Disclaimer notice
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = emeraldColors.secondaryMuted,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = simulation.disclaimer,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = emeraldColors.secondaryMuted,
                )
            }

            HorizontalDivider(color = emeraldColors.borderSubtle)

            // Schedule preview section
            Text(
                text = if (hasFinancialEstimate) "Cronograma de pagos estimado" else "Distribución referencial del principal",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = emeraldColors.primaryDeep,
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                simulation.schedule.forEach { item ->
                    InstallmentRow(item = item)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onRejectPurchase,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                ) {
                    Text("Rechazar", fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick = { onConfirmPurchase(selectedInstallments) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = emeraldColors.primaryDeep),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                ) {
                    Text("Confirmar compra", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun InstallmentRow(item: InstallmentScheduleItem) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    val emeraldColors = rememberCalmEmeraldColors()

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = emeraldColors.surfaceCard,
        border = BorderStroke(1.dp, emeraldColors.borderSubtle),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Cuota ${item.installmentNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = item.dueDate.format(dateFormatter),
                    style = MaterialTheme.typography.labelSmall,
                    color = emeraldColors.secondaryMuted,
                )
            }
            MoneyText(
                money = item.amount,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = emeraldColors.primaryDeep,
            )
        }
    }
}
