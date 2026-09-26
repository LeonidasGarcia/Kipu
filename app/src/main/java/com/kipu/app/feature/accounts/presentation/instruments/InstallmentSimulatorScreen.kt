package com.kipu.app.feature.accounts.presentation.instruments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.InstallmentScheduleItem
import com.kipu.app.feature.accounts.domain.model.InstallmentSimulation
import com.kipu.app.feature.accounts.domain.model.PurchaseCandidate
import com.kipu.app.feature.accounts.domain.usecase.SimulateInstallments
import com.kipu.app.ui.component.MoneyText
import java.time.format.DateTimeFormatter

@Composable
fun InstallmentSimulatorScreen(
    candidate: PurchaseCandidate,
    card: CreditCard,
    teaBps: Int? = null,
    onConfirmPurchase: (installments: Int) -> Unit,
    onRejectPurchase: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedInstallments by remember { mutableIntStateOf(candidate.suggestedInstallments.coerceIn(1, 36)) }

    val simulation = remember(selectedInstallments, candidate, card, teaBps) {
        SimulateInstallments()(
            candidate = candidate,
            card = card,
            installmentsCount = selectedInstallments,
            acceptedReferenceTeaBps = teaBps,
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Column {
                Text(
                    text = "Confirmar compra a crédito",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${card.issuer} •••• ${card.lastFourDigits} | ${candidate.merchant}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Purchase amount
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Monto principal",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MoneyText(
                        money = candidate.amount,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Installment slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Número de cuotas:",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "$selectedInstallments ${if (selectedInstallments == 1) "cuota (directo)" else "cuotas"}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = selectedInstallments.toFloat(),
                    onValueChange = { selectedInstallments = it.toInt().coerceIn(1, 36) },
                    valueRange = 1f..36f,
                    steps = 34,
                    modifier = Modifier.semantics {
                        contentDescription = "Selector de 1 a 36 cuotas, actual $selectedInstallments"
                    },
                )
            }

            // Simulation summary
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Interés estimado:", style = MaterialTheme.typography.bodyMedium)
                        MoneyText(money = simulation.totalInterest, style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Total financiado:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        MoneyText(
                            money = simulation.totalFinanced,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (simulation.appliedTeaBps != null) {
                        Text(
                            text = "Tasa aplicada: ${(simulation.appliedTeaBps / 100.0)}% TEA",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Disclaimer
            Text(
                text = simulation.disclaimer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )

            HorizontalDivider()

            // Schedule preview
            Text(
                text = "Cronograma proyectado",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(simulation.schedule) { item ->
                    InstallmentRow(item = item)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onRejectPurchase,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Rechazar")
                }
                Button(
                    onClick = { onConfirmPurchase(selectedInstallments) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Confirmar")
                }
            }
        }
    }
}

@Composable
private fun InstallmentRow(item: InstallmentScheduleItem) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "Cuota ${item.installmentNumber}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = item.dueDate.format(dateFormatter),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MoneyText(
            money = item.amount,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
