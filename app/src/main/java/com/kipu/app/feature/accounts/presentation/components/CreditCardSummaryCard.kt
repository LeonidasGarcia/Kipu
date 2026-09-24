package com.kipu.app.feature.accounts.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.CreditCalculations
import com.kipu.app.feature.accounts.domain.model.CreditCardWithSummary
import com.kipu.app.ui.component.MaskedCardReference
import com.kipu.app.ui.component.MoneyText

@Composable
fun CreditCardSummaryCard(
    creditCardWithSummary: CreditCardWithSummary,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val card = creditCardWithSummary.card
    val utilization = creditCardWithSummary.utilizationPercentage
    val nextBilling = CreditCalculations.calculateNextDate(card.billingDay)
    val nextDue = CreditCalculations.calculateNextDate(card.dueDay)

    val progressColor = when {
        utilization >= 80.0 -> MaterialTheme.colorScheme.error
        utilization >= 50.0 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.CreditCard,
                        contentDescription = null,
                        tint = progressColor,
                        modifier = Modifier.size(28.dp),
                    )
                    Column {
                        Text(
                            text = card.alias ?: "${card.issuer} ${card.network}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (card.isPlanLocked) {
                            Text(
                                text = "Bloqueada por el plan Free",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "${card.issuer} ${card.network}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            MaskedCardReference(
                                lastFourDigits = card.lastFourDigits,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Deuda actual",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    MoneyText(
                        money = creditCardWithSummary.debt,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (creditCardWithSummary.debt.minorUnits > 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Utilization progress bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Utilización: %.1f%%".format(utilization),
                    style = MaterialTheme.typography.labelSmall,
                    color = progressColor,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Línea: ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (utilization / 100.0).toFloat().coerceIn(0f, 1f) },
                color = progressColor,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Disponible",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    MoneyText(
                        money = creditCardWithSummary.availableCredit,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Próx. pago: $nextDue",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Corte: $nextBilling",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}
