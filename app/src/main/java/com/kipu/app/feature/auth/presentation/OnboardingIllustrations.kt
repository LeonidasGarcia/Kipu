package com.kipu.app.feature.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import com.kipu.app.ui.component.KipuCard
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

/** Illustrative sample data is restricted to onboarding and never enters the ledger. */
@Composable
internal fun OnboardingMoneyIllustration() {
    val colors = rememberCalmEmeraldColors()
    Column(Modifier.fillMaxWidth().background(Brush.radialGradient(
        listOf(colors.incomeBg, colors.background))).padding(14.dp)
        .clearAndSetSemantics { contentDescription = "Ilustración: cuentas, tarjeta y un ingreso en un solo lugar. Datos de ejemplo." },
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IllustrationCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Total disponible", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("● En tiempo real", color = colors.primaryDeep, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(12.dp))
            Text("S/ 8,420.50", style = MaterialTheme.typography.headlineMedium)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IllustrationCard(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IllustrationIcon(Icons.Outlined.AccountBalance)
                    Spacer(Modifier.width(8.dp))
                    Text("Ahorros", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(22.dp))
                Text("Sueldo BCP", style = MaterialTheme.typography.bodyMedium)
                Text("S/ 3,850.00", style = MaterialTheme.typography.titleSmall)
            }
            Surface(Modifier.weight(1f), shape = RoundedCornerShape(18.dp), color = colors.primaryDeep,
                contentColor = colors.onPrimaryDeep) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("VISA", style = MaterialTheme.typography.titleSmall)
                        Icon(Icons.Outlined.Contactless, null, Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(38.dp))
                    Text("Oro BCP", style = MaterialTheme.typography.bodyMedium)
                    Text("•••• 4821", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        Surface(shape = RoundedCornerShape(16.dp), color = colors.pillTrack) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                IllustrationIcon(Icons.Outlined.Payments)
                Spacer(Modifier.width(10.dp))
                Text("Sueldo de Quincena", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text("+S/ 1,925.00", style = MaterialTheme.typography.labelLarge, color = colors.primaryDeep)
            }
        }
    }
}

@Composable
internal fun OnboardingLedgerIllustration() {
    val colors = rememberCalmEmeraldColors()
    KipuCard(Modifier.fillMaxWidth().clearAndSetSemantics {
        contentDescription = "Ilustración: cuentas, billetera y efectivo; movimiento sugerido de supermercado; ledger organizado. Datos de ejemplo."
    }, shape = RoundedCornerShape(28.dp), containerColor = colors.pillTrack, borderColor = colors.borderSubtle) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SourcePill("BCP / BBVA", "Cuentas", Icons.Outlined.AccountBalance, Modifier.weight(1f))
                SourcePill("Billetera", "Digital", Icons.Outlined.PhoneAndroid, Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceCard) {
                    Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Payments, null, Modifier.size(20.dp))
                        Text("Sol", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            LedgerArrow()
            IllustrationCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IllustrationIcon(Icons.Outlined.NotificationsActive)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Supermercado", style = MaterialTheme.typography.bodyMedium)
                        Text("● Detección sugerida", style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("- S/ 142.50", style = MaterialTheme.typography.labelLarge, color = colors.primaryDeep)
                        Text("Hoy, 10:45 am", style = MaterialTheme.typography.labelSmall, color = colors.secondaryMuted)
                    }
                }
            }
            LedgerArrow()
            Surface(shape = RoundedCornerShape(20.dp), color = colors.primaryDeep, contentColor = colors.onPrimaryDeep) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AccountBalanceWallet, null, Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Kipu Ledger Sereno", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.CheckCircle, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Organizado", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** Privacy illustration from the corrected third reference; all elements are Compose. */
@Composable
internal fun OnboardingPrivacyIllustration() {
    val density = LocalDensity.current
    // Labels belong to a decorative diagram with one accessible description. Keep its
    // proportions, while all explanatory text and interactive controls retain user scaling.
    CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
        PrivacyIllustrationContent()
    }
}

@Composable
private fun PrivacyIllustrationContent() {
    val colors = rememberCalmEmeraldColors()
    Box(Modifier.widthIn(max = 320.dp).fillMaxWidth().height(220.dp)
        .background(Brush.radialGradient(listOf(colors.incomeBg, colors.background)))
        .clearAndSetSemantics {
            contentDescription = "Ilustración de privacidad: saldo oculto, local y seguro, sin acceso a credenciales ni APIs bancarias."
        }, contentAlignment = Alignment.Center) {
        Surface(Modifier.width(144.dp).height(176.dp).align(Alignment.TopCenter).offset(y = 14.dp),
            shape = RoundedCornerShape(28.dp), color = colors.surfaceCard,
            border = BorderStroke(1.5.dp, colors.primaryDeep)) {
            Column(Modifier.padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = CircleShape, color = colors.borderSubtle,
                    border = BorderStroke(1.dp, colors.secondaryMuted.copy(alpha = .18f))) {
                    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Lock, null, Modifier.size(28.dp), tint = colors.primaryDeep)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Surface(shape = RoundedCornerShape(16.dp), color = colors.borderSubtle) {
                    Text("● Local & Seguro", style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                }
            }
        }
        PrivacyBadge(Modifier.align(Alignment.TopStart).offset(x = 10.dp, y = 8.dp)
            .graphicsLayer { rotationZ = -3f }, Icons.Outlined.AccountBalanceWallet,
            "Saldo Kipu", "••••••")
        PrivacyBadge(Modifier.align(Alignment.BottomEnd).offset(x = (-8).dp, y = (-20).dp)
            .graphicsLayer { rotationZ = 3f }, Icons.Outlined.VisibilityOff,
            "Credenciales", "0% Acceso")
        Surface(Modifier.align(Alignment.BottomStart).offset(x = 40.dp),
            shape = RoundedCornerShape(16.dp), color = colors.pillTrack,
            border = BorderStroke(1.dp, colors.borderSubtle), shadowElevation = 1.dp) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Shield, null, Modifier.size(12.dp), tint = colors.primaryDeep)
                Spacer(Modifier.width(4.dp))
                Text("Sin APIs bancarias", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PrivacyBadge(modifier: Modifier, icon: ImageVector, label: String, value: String) {
    val colors = rememberCalmEmeraldColors()
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = colors.surfaceCard,
        border = BorderStroke(1.dp, colors.borderSubtle), shadowElevation = 2.dp) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).background(colors.incomeBg, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(18.dp), tint = colors.primaryDeep)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Text(value, style = MaterialTheme.typography.labelMedium,
                    color = if (label == "Credenciales") colors.primaryDeep else colors.primaryText)
            }
        }
    }
}

@Composable
private fun LedgerArrow() {
    val colors = rememberCalmEmeraldColors()
    Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        repeat(3) { Box(Modifier.padding(bottom = 3.dp).size(2.dp, 6.dp).background(colors.secondaryMuted.copy(alpha = .45f))) }
        Icon(Icons.Outlined.ArrowDownward, null, Modifier.size(22.dp), tint = colors.primaryDeep)
    }
}

@Composable
private fun SourcePill(title: String, subtitle: String, icon: ImageVector, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = rememberCalmEmeraldColors().surfaceCard) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelSmall)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = rememberCalmEmeraldColors().secondaryMuted)
            }
        }
    }
}

@Composable
private fun IllustrationIcon(icon: ImageVector) {
    val colors = rememberCalmEmeraldColors()
    Box(Modifier.size(30.dp).background(colors.borderSubtle, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(20.dp), tint = colors.primaryDeep)
    }
}

@Composable
private fun IllustrationCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = rememberCalmEmeraldColors()
    KipuCard(modifier, shape = RoundedCornerShape(18.dp), containerColor = colors.surfaceCard,
        contentColor = colors.primaryText, borderColor = colors.borderSubtle) {
        Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}
