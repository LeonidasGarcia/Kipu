package com.kipu.app.feature.accounts.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.core.finance.domain.CreditCalculations
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.CreditCardWithSummary
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.component.MaskedCardReference
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.KipuMotionTokens
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CreditCardSummaryCard(
    creditCardWithSummary: CreditCardWithSummary,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val card = creditCardWithSummary.card
    val utilization = creditCardWithSummary.utilizationPercentage
    val nextBilling = remember(card.billingDay) { CreditCalculations.calculateNextDate(card.billingDay) }
    val nextDue = remember(card.dueDay) { CreditCalculations.calculateNextDate(card.dueDay) }
    val emeraldColors = rememberCalmEmeraldColors()
    val reducedMotion = rememberReducedMotionEnabled()

    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-PE"))
    }
    val formattedDue = remember(nextDue) { nextDue.format(dateFormatter) }
    val formattedBilling = remember(nextBilling) { nextBilling.format(dateFormatter) }

    val isMasked = LocalBalanceMasked.current
    val progressColorTarget = if (isMasked) {
        emeraldColors.secondaryMuted
    } else when {
        utilization >= 80.0 -> emeraldColors.expenseCoral
        utilization >= 50.0 -> emeraldColors.warningAmber
        else -> emeraldColors.incomeEmerald
    }
    val progressColor = animateColorAsState(
        targetValue = progressColorTarget,
        animationSpec = if (reducedMotion) snap() else tween(KipuMotionTokens.MediumMillis, easing = FastOutSlowInEasing),
        label = "cardSummaryUtilizationColor",
    ).value
    val progressRatioTarget = remember(isMasked, utilization) {
        if (isMasked) 0f else (utilization / 100.0).toFloat().coerceIn(0f, 1f)
    }
    val animatedProgress = animateFloatAsState(
        targetValue = progressRatioTarget,
        animationSpec = if (reducedMotion) snap() else tween(KipuMotionTokens.MediumMillis, easing = FastOutSlowInEasing),
        label = "cardSummaryUtilizationProgress",
    ).value
    val progressRatio = if (isMasked) 0f else animatedProgress

    val (statusLabel, statusBg, statusBorder, statusText) = when {
        card.isArchived -> Quadruple(
            "Archivada",
            emeraldColors.pillTrack,
            emeraldColors.borderSubtle,
            emeraldColors.secondaryMuted,
        )
        card.isPlanLocked -> Quadruple(
            "Bloqueada por plan",
            emeraldColors.warningBg,
            emeraldColors.warningBorder,
            emeraldColors.warningText,
        )
        isMasked -> Quadruple(
            "Uso oculto",
            emeraldColors.pillTrack,
            emeraldColors.borderSubtle,
            emeraldColors.secondaryMuted,
        )
        utilization > 100.0 -> Quadruple(
            "Sobreutilizada",
            emeraldColors.expenseBg,
            emeraldColors.expenseBorder,
            emeraldColors.expenseCoral,
        )
        utilization >= 80.0 -> Quadruple(
            "Uso alto",
            emeraldColors.warningBg,
            emeraldColors.warningBorder,
            emeraldColors.warningText,
        )
        utilization >= 50.0 -> Quadruple(
            "Uso moderado",
            emeraldColors.warningBg,
            emeraldColors.warningBorder,
            emeraldColors.warningText,
        )
        else -> Quadruple(
            "En uso normal",
            emeraldColors.incomeBg,
            emeraldColors.incomeBorder,
            emeraldColors.incomeEmerald,
        )
    }
    val animatedStatusBg = animateColorAsState(
        targetValue = statusBg,
        animationSpec = if (reducedMotion) snap() else tween(KipuMotionTokens.MediumMillis, easing = FastOutSlowInEasing),
        label = "cardSummaryStatusBackground",
    ).value
    val animatedStatusBorder = animateColorAsState(
        targetValue = statusBorder,
        animationSpec = if (reducedMotion) snap() else tween(KipuMotionTokens.MediumMillis, easing = FastOutSlowInEasing),
        label = "cardSummaryStatusBorder",
    ).value
    val animatedStatusText = animateColorAsState(
        targetValue = statusText,
        animationSpec = if (reducedMotion) snap() else tween(KipuMotionTokens.MediumMillis, easing = FastOutSlowInEasing),
        label = "cardSummaryStatusText",
    ).value

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = emeraldColors.surfaceCard),
        border = BorderStroke(1.dp, emeraldColors.borderSubtle),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale
            val isStacked = maxWidth < 340.dp || fontScale >= 1.25f

            Column(modifier = Modifier.padding(16.dp)) {
                // Top section: Icon + Name + Masked Card + Status
                if (isStacked) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (emeraldColors.isDark) Color(0xFF1E293B) else Color(0xFF0F172A),
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.CreditCard,
                                        contentDescription = null,
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = card.alias ?: "${card.issuer} ${card.network}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                                    fontWeight = FontWeight.SemiBold,
                                    color = emeraldColors.primaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = "${card.issuer} · ${card.network}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = emeraldColors.secondaryMuted,
                                    )
                                    MaskedCardReference(
                                        lastFourDigits = card.lastFourDigits,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    )
                                }
                            }
                        }

                        // Status Badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = animatedStatusBg,
                            border = BorderStroke(1.dp, animatedStatusBorder),
                        ) {
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                color = animatedStatusText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (emeraldColors.isDark) Color(0xFF1E293B) else Color(0xFF0F172A),
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.CreditCard,
                                        contentDescription = null,
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = card.alias ?: "${card.issuer} ${card.network}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                                    fontWeight = FontWeight.SemiBold,
                                    color = emeraldColors.primaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = "${card.issuer} · ${card.network} ·",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = emeraldColors.secondaryMuted,
                                    )
                                    MaskedCardReference(
                                        lastFourDigits = card.lastFourDigits,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    )
                                }
                            }
                        }

                        // Status Badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = animatedStatusBg,
                            border = BorderStroke(1.dp, animatedStatusBorder),
                        ) {
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                color = animatedStatusText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Middle section: Deuda actual vs Disponible en línea
                if (isStacked) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column {
                            Text(
                                text = "Deuda actual",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = emeraldColors.secondaryMuted,
                            )
                            MoneyText(
                                money = creditCardWithSummary.debt,
                                isMasked = isMasked,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                ),
                                color = if (!isMasked && creditCardWithSummary.debt.minorUnits > 0L) emeraldColors.expenseCoral else emeraldColors.primaryText,
                            )
                        }

                        Column {
                            Text(
                                text = "Disponible en línea",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = emeraldColors.secondaryMuted,
                            )
                            MoneyText(
                                money = creditCardWithSummary.availableCredit,
                                isMasked = isMasked,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                ),
                                color = emeraldColors.primaryText,
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = "Deuda actual",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = emeraldColors.secondaryMuted,
                            )
                            MoneyText(
                                money = creditCardWithSummary.debt,
                                isMasked = isMasked,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                ),
                                color = if (!isMasked && creditCardWithSummary.debt.minorUnits > 0L) emeraldColors.expenseCoral else emeraldColors.primaryText,
                            )
                        }

                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = "Disponible en línea",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = emeraldColors.secondaryMuted,
                            )
                            MoneyText(
                                money = creditCardWithSummary.availableCredit,
                                isMasked = isMasked,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                ),
                                color = emeraldColors.primaryText,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Utilization Progress Bar
                val displayedUtilization = remember(isMasked, utilization) { when {
                    isMasked -> "••%"
                    utilization > 100.0 -> "%.1f%%".format(Locale.US, utilization)
                    else -> "%.0f%%".format(Locale.US, utilization)
                } }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        Text(
                            text = "Utilización de línea (Límite: ",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = emeraldColors.secondaryMuted,
                        )
                        MoneyText(
                            money = Money(card.creditLimitMinorUnits, card.currency),
                            isMasked = isMasked,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                            color = emeraldColors.secondaryMuted,
                        )
                        Text(
                            text = ")",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = emeraldColors.secondaryMuted,
                        )
                    }
                    Text(
                        text = displayedUtilization,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                        color = progressColor,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progressRatio },
                    color = progressColor,
                    trackColor = if (emeraldColors.isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom row: Próximo pago and Pagar tarjeta button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarToday,
                            contentDescription = null,
                            tint = emeraldColors.incomeEmerald,
                            modifier = Modifier.size(15.dp),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Próximo pago: ",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = emeraldColors.secondaryMuted,
                            )
                            Text(
                                text = formattedDue,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = emeraldColors.primaryText,
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = emeraldColors.incomeBg,
                        border = BorderStroke(1.dp, emeraldColors.incomeBorder),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onClick),
                    ) {
                        Text(
                            text = "Pagar tarjeta",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                            ),
                            color = emeraldColors.incomeEmerald,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
