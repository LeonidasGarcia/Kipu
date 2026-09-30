package com.kipu.app.feature.accounts.presentation.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipu.app.core.finance.domain.CreditCalculations
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.AccountWithBalance
import com.kipu.app.feature.accounts.domain.model.Card as DomainCard
import com.kipu.app.feature.accounts.domain.model.CreditCardWithSummary
import com.kipu.app.feature.accounts.presentation.AccountsViewModel
import com.kipu.app.feature.notifications.presentation.UnreadNotificationBadge
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.component.MaskedCardReference
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.component.symbol
import com.kipu.app.ui.theme.rememberKipuColors
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Design tokens strictly aligned with docs/design/KIPU_CUENTAS_Y_TARJETAS_IMPLEMENTATION_PLAN.md
 */
private object KipuColors {
    val Primary: Color @Composable get() = rememberKipuColors().primary
    val PrimaryText: Color @Composable get() = rememberKipuColors().primaryText
    val PrimaryDark: Color @Composable get() = if (rememberKipuColors().isDark) Color(0xFF074842) else Color(0xFF094E49)
    val PrimaryContainer: Color @Composable get() = rememberKipuColors().primaryContainer
    val Income: Color @Composable get() = rememberKipuColors().positive
    val Expense: Color @Composable get() = rememberKipuColors().debt
    val Warning: Color @Composable get() = rememberKipuColors().warning
    val WarningContainer: Color @Composable get() = rememberKipuColors().warningContainer
    val WarningBadge: Color @Composable get() = if (rememberKipuColors().isDark) Color(0xFF713F12) else Color(0xFFFDE68A)
    val WarningText: Color @Composable get() = rememberKipuColors().onWarningContainer
    val Surface: Color @Composable get() = rememberKipuColors().surface
    val SurfaceVariant: Color @Composable get() = rememberKipuColors().surfaceVariant
    val Background: Color @Composable get() = rememberKipuColors().background
    val Border: Color @Composable get() = rememberKipuColors().border
    val Ink: Color @Composable get() = rememberKipuColors().inkPrimary
    val TextMuted: Color @Composable get() = rememberKipuColors().inkSecondary
    val TextMutedOnVariant: Color @Composable get() = if (rememberKipuColors().isDark) rememberKipuColors().inkSecondary else Color(0xFF475569)
}

/**
 * DashboardScreen implementing the 4 R6 states from Kipu V4.2:
 * 1. Empty State: Sin instrumentos registrados
 * 2. Active Dashboard: Tarjetas en lista compacta con Hero gradiente #0F766E
 * 3. Free Plan Limit: Banner ámbar de límite alcanzado 4/4
 * 4. Privacy Mode: Crossfade instantáneo a saldos ofuscados (S/ •••••••• y S/ ••••••)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: AccountsViewModel,
    onNavigateToNewAccount: () -> Unit,
    onNavigateToNewCard: () -> Unit,
    onNavigateToMovements: () -> Unit = {},
    onAccountClick: (String) -> Unit = {},
    onCardClick: (String) -> Unit = onAccountClick,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    unreadNotificationCount: Int = 0,
    feedbackMessage: String? = null,
    onFeedbackConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
    // Optional callbacks with defaults for bottom bar and plans
    onNavigateToHome: () -> Unit = {},
    onNavigateToAnalysis: () -> Unit = {},
    onNavigateToPlans: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
) {
    val state by viewModel.dashboardUiState.collectAsStateWithLifecycle()
    val instruments by viewModel.instrumentsUiState.collectAsStateWithLifecycle()
    val creditNotifications by viewModel.creditNotifications.collectAsStateWithLifecycle()
    var showQuotaSelection by remember { mutableStateOf(false) }
    var isArchivedExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val isMasked = state.isMasked

    LaunchedEffect(feedbackMessage) {
        feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            onFeedbackConsumed()
        }
    }
    LaunchedEffect(viewModel) { viewModel.refreshCreditUtilizationNotifications() }

    val data = state.dashboardData
    val isEmptyState = !state.isLoading && state.errorMessage == null &&
        data?.liquidAccounts.isNullOrEmpty() &&
        data?.creditCards.isNullOrEmpty() &&
        instruments.activeAccounts.isEmpty() &&
        instruments.activeCards.isEmpty()

    CompositionLocalProvider(LocalBalanceMasked provides isMasked) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Mi Dinero Real",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = KipuColors.Ink,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver",
                                tint = KipuColors.Ink,
                            )
                        }
                    },
                    actions = {
                        // Privacy toggle in TopBar
                        IconButton(
                            onClick = { viewModel.toggleMasked() },
                            modifier = Modifier.semantics {
                                contentDescription = if (isMasked) "Mostrar saldos" else "Ocultar saldos"
                            },
                        ) {
                            Icon(
                                imageVector = if (isMasked) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = if (isMasked) KipuColors.PrimaryText else KipuColors.TextMuted,
                            )
                        }

                        UnreadNotificationBadge(
                            unreadCount = unreadNotificationCount,
                            onClick = onNavigateToNotifications,
                        )

                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Configuración y filtros",
                                tint = KipuColors.TextMuted,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = KipuColors.Background,
                    ),
                )
            },
            bottomBar = {
                KipuBottomBar(
                    onNavigateToHome = onNavigateToHome,
                    onNavigateToMovements = onNavigateToMovements,
                    onNavigateToAnalysis = onNavigateToAnalysis,
                )
            },
            floatingActionButton = {
                if (!isEmptyState) {
                    val computableCount = data?.activeComputableCount ?: instruments.activeComputableCount
                    val maxQuota = data?.maxFreeQuota ?: instruments.maxFreeQuota
                    val isQuotaReached = computableCount >= maxQuota

                    Box {
                        FloatingActionButton(
                            onClick = onNavigateToNewAccount,
                            containerColor = KipuColors.Primary,
                            contentColor = Color.White,
                            shape = CircleShape,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Añadir instrumento",
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        if (isQuotaReached) {
                            Surface(
                                shape = CircleShape,
                                color = KipuColors.Warning,
                                modifier = Modifier
                                    .size(18.dp)
                                    .align(Alignment.TopEnd),
                                border = BorderStroke(1.5.dp, Color.White),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Cupo alcanzado",
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            },
            containerColor = KipuColors.Background,
            modifier = modifier,
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = KipuColors.PrimaryText,
                    )
                } else if (state.errorMessage != null) {
                    Text(
                        text = state.errorMessage ?: "Error desconocido",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                    )
                } else if (isEmptyState) {
                    EmptyDashboardContent(
                        quotaCount = data?.activeComputableCount ?: 0,
                        maxQuota = data?.maxFreeQuota ?: 4,
                        onNavigateToNewAccount = onNavigateToNewAccount,
                        onNavigateToNewCard = onNavigateToNewCard,
                        onNavigateToPlans = onNavigateToPlans,
                    )
                } else {
                    ActiveDashboardContent(
                        data = data,
                        instruments = instruments,
                        creditNotifications = creditNotifications,
                        isMasked = isMasked,
                        isArchivedExpanded = isArchivedExpanded,
                        onToggleArchived = { isArchivedExpanded = !isArchivedExpanded },
                        onToggleMasked = { viewModel.toggleMasked() },
                        onAccountClick = onAccountClick,
                        onCardClick = onCardClick,
                        onManageQuota = { showQuotaSelection = true },
                        onNavigateToPlans = onNavigateToPlans,
                    )
                }
            }
        }
    }

    if (showQuotaSelection) {
        val accounts = instruments.activeAccounts.filter { it.isComputableForQuota }
        val cards = instruments.activeCards.filter { it.isComputableForQuota }
        InstrumentQuotaSelectionDialog(
            accounts = accounts,
            cards = cards,
            selectedIds = instruments.selectedFreeInstrumentIds,
            maxQuota = instruments.maxFreeQuota,
            onDismiss = { showQuotaSelection = false },
            onSave = { ids ->
                viewModel.saveFreeInstrumentSelection(ids)
                showQuotaSelection = false
            },
        )
    }
}

/**
 * State 1: Empty State (Sin instrumentos registrados)
 */
@Composable
private fun EmptyDashboardContent(
    quotaCount: Int,
    maxQuota: Int,
    onNavigateToNewAccount: () -> Unit,
    onNavigateToNewCard: () -> Unit,
    onNavigateToPlans: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            // Quota bar: Plan Free 0 de 4 cuentas
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = KipuColors.Surface,
                border = BorderStroke(1.dp, KipuColors.Border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = KipuColors.Income,
                            modifier = Modifier.size(8.dp),
                        ) {}
                        Text(
                            text = "Plan Free • $quotaCount de $maxQuota cuentas",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = KipuColors.Ink,
                        )
                    }
                    Text(
                        text = "Ver planes >",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = KipuColors.PrimaryText,
                        modifier = Modifier.clickable(onClick = onNavigateToPlans),
                    )
                }
            }
        }

        item {
            // Total cero card: S/ 0.00 PEN
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = KipuColors.Surface),
                border = BorderStroke(1.dp, KipuColors.Border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "TOTAL DISPONIBLE REAL",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = KipuColors.TextMuted,
                            letterSpacing = 0.5.sp,
                        )
                        Text(
                            text = "PEN / S/",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = KipuColors.TextMuted,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "S/ 0.00 PEN",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum",
                        ),
                        color = KipuColors.Ink,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = KipuColors.TextMuted,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Registra tu primera cuenta para conocer tu liquidez real neta.",
                            style = MaterialTheme.typography.bodySmall,
                            color = KipuColors.TextMuted,
                        )
                    }
                }
            }
        }

        item {
            // Central Welcome Card: Aún no tienes cuentas
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = KipuColors.Surface),
                border = BorderStroke(1.dp, KipuColors.Border),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = KipuColors.PrimaryContainer,
                        modifier = Modifier.size(64.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = KipuColors.PrimaryText,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Aún no tienes cuentas",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = KipuColors.Ink,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Centraliza tus cuentas bancarias, tarjetas de crédito y efectivo en un solo lugar seguro para el control total de tus finanzas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KipuColors.TextMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onNavigateToNewAccount,
                        colors = ButtonDefaults.buttonColors(containerColor = KipuColors.Primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Text(
                            text = "+ Agregar mi primera cuenta",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }

        item {
            // Section: OPCIONES RECOMENDADAS
            Text(
                text = "OPCIONES RECOMENDADAS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = KipuColors.TextMuted,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = KipuColors.Surface),
                border = BorderStroke(1.dp, KipuColors.Border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    RecommendedOptionItem(
                        icon = Icons.Default.AccountBalance,
                        iconContainerColor = Color(0xFFE0F2FE),
                        iconTint = Color(0xFF0284C7),
                        title = "Cuenta de sueldo o ahorros",
                        subtitle = "BCP, BBVA, Interbank, Scotiabank",
                        onClick = onNavigateToNewAccount,
                    )
                    Surface(
                        color = KipuColors.Border,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .padding(start = 68.dp),
                    ) {}
                    RecommendedOptionItem(
                        icon = Icons.Default.CreditCard,
                        iconContainerColor = Color(0xFFEDE9FE),
                        iconTint = Color(0xFF7C3AED),
                        title = "Tarjeta de crédito",
                        subtitle = "Monitorea fechas de corte y líneas",
                        onClick = onNavigateToNewCard,
                    )
                    Surface(
                        color = KipuColors.Border,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .padding(start = 68.dp),
                    ) {}
                    RecommendedOptionItem(
                        icon = Icons.Default.Payments,
                        iconContainerColor = Color(0xFFDCFCE7),
                        iconTint = KipuColors.Income,
                        title = "Efectivo o Billetera digital",
                        subtitle = "Control diario de billetes, Yape o Plin",
                        onClick = onNavigateToNewAccount,
                    )
                }
            }
        }

        item {
            // Local Privacy Security Note
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = KipuColors.PrimaryContainer,
                border = BorderStroke(1.dp, if (rememberKipuColors().isDark) KipuColors.PrimaryText else Color(0xFFBBF7D0)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = KipuColors.PrimaryText,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Privacidad Kipu: Cifrado local seguro en tu dispositivo. No requerimos claves bancarias ni contraseñas transaccionales.",
                        style = MaterialTheme.typography.bodySmall,
                        color = KipuColors.PrimaryText,
                        lineHeight = 18.sp,
                    )
                }
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun RecommendedOptionItem(
    icon: ImageVector,
    iconContainerColor: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (effectiveIconContainer, effectiveIconTint) = dashboardPastelColors(iconContainerColor, iconTint)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = effectiveIconContainer,
            modifier = Modifier.size(42.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                tint = effectiveIconTint,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = KipuColors.Ink,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = KipuColors.TextMuted,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.NavigateNext,
            contentDescription = null,
            tint = KipuColors.TextMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun dashboardPastelColors(container: Color, foreground: Color): Pair<Color, Color> {
    return when (container) {
        Color(0xFFE0F2FE) -> if (rememberKipuColors().isDark) Color(0xFF0C4A6E) to Color(0xFF7DD3FC)
            else container to Color(0xFF075985)
        Color(0xFFEDE9FE) -> if (rememberKipuColors().isDark) Color(0xFF4C1D95) to Color(0xFFC4B5FD)
            else container to Color(0xFF5B21B6)
        Color(0xFFDCFCE7) -> if (rememberKipuColors().isDark) Color(0xFF14532D) to Color(0xFF86EFAC)
            else container to Color(0xFF166534)
        Color(0xFFDBEAFE) -> if (rememberKipuColors().isDark) Color(0xFF1E3A8A) to Color(0xFF93C5FD)
            else container to Color(0xFF1E40AF)
        Color(0xFFFEE2E2) -> if (rememberKipuColors().isDark) Color(0xFF7F1D1D) to Color(0xFFFCA5A5)
            else container to Color(0xFFB91C1C)
        Color(0xFFFEF3C7) -> if (rememberKipuColors().isDark) Color(0xFF78350F) to Color(0xFFFCD34D)
            else container to Color(0xFF92400E)
        Color(0xFFF3E8FF) -> if (rememberKipuColors().isDark) Color(0xFF581C87) to Color(0xFFD8B4FE)
            else container to Color(0xFF6B21A8)
        Color(0xFFCFFAFE), Color(0xFFCCFBF1) -> if (rememberKipuColors().isDark) Color(0xFF134E4A) to Color(0xFF5EEAD4)
            else container to Color(0xFF0E7490)
        Color(0xFFF1F5F9) -> if (rememberKipuColors().isDark) Color(0xFF334155) to Color(0xFFCBD5E1)
            else container to Color(0xFF334155)
        else -> if (rememberKipuColors().isDark) rememberKipuColors().surfaceVariant to rememberKipuColors().inkPrimary
            else container to foreground
    }
}

/**
 * States 2, 3, 4: Active Dashboard with Hero Gradient, Compact Cards, Amber Limit Banner, and Privacy Crossfades
 */
@Composable
private fun ActiveDashboardContent(
    data: com.kipu.app.feature.accounts.domain.model.FinancialDashboardData?,
    instruments: com.kipu.app.feature.accounts.presentation.InstrumentsUiState,
    creditNotifications: List<com.kipu.app.feature.accounts.domain.model.CreditUtilizationNotification>,
    isMasked: Boolean,
    isArchivedExpanded: Boolean,
    onToggleArchived: () -> Unit,
    onToggleMasked: () -> Unit,
    onAccountClick: (String) -> Unit,
    onCardClick: (String) -> Unit,
    onManageQuota: () -> Unit,
    onNavigateToPlans: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val computableCount = data?.activeComputableCount ?: instruments.activeComputableCount
    val maxQuota = data?.maxFreeQuota ?: instruments.maxFreeQuota
    val isQuotaReached = computableCount >= maxQuota

    val liquidAccounts = data?.liquidAccounts.orEmpty()
    val activeCreditCards = data?.creditCards.orEmpty().filter { !it.card.isArchived }

    val bankAccountsTotal = liquidAccounts
        .filter { it.account.type != AccountType.CASH }
        .sumOf { it.balance.minorUnits }
    val cashAccountsTotal = liquidAccounts
        .filter { it.account.type == AccountType.CASH }
        .sumOf { it.balance.minorUnits }

    val archivedAccounts = instruments.archivedAccounts
    val archivedCreditCardsWithDebt = data?.creditCards.orEmpty().filter {
        it.card.isArchived && it.debt.minorUnits > 0L
    }
    val archivedCardsWithoutDebt = instruments.archivedCards.filter { card ->
        archivedCreditCardsWithDebt.none { it.card.id == card.id }
    }
    val totalArchivedCount = archivedAccounts.size + archivedCreditCardsWithDebt.size + archivedCardsWithoutDebt.size

    val chevronRotation by animateFloatAsState(
        targetValue = if (isArchivedExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "archivedChevronRotation",
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // State 3: Amber Alert Banner when Free Plan limit is reached
        item {
            AnimatedVisibility(
                visible = isQuotaReached,
                enter = expandVertically(tween(300)) + fadeIn(tween(250)),
                exit = shrinkVertically(tween(250)) + fadeOut(tween(200)),
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KipuColors.WarningContainer),
                    border = BorderStroke(1.dp, KipuColors.Warning),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = KipuColors.Warning,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = "Límite alcanzado",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = KipuColors.WarningText,
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = KipuColors.WarningBadge,
                            ) {
                                Text(
                                    text = "$computableCount / $maxQuota Cuentas",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = KipuColors.WarningText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Has ocupado las $maxQuota cuentas disponibles de tu plan Free. Para añadir más entidades o billeteras digitales, pasa a Kipu Pro.",
                            style = MaterialTheme.typography.bodySmall,
                            color = KipuColors.WarningText,
                            lineHeight = 18.sp,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onNavigateToPlans,
                            colors = ButtonDefaults.buttonColors(containerColor = KipuColors.Primary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "Pasar a Pro (Cuentas Ilimitadas)",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                        if (computableCount > maxQuota) {
                            Spacer(modifier = Modifier.height(6.dp))
                            TextButton(
                                onClick = onManageQuota,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            ) {
                                Text(
                                    text = "Elegir instrumentos disponibles",
                                    color = KipuColors.PrimaryText,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quota bar when NOT reached
        if (!isQuotaReached) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = KipuColors.Surface,
                    border = BorderStroke(1.dp, KipuColors.Border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = KipuColors.Income,
                                modifier = Modifier.size(8.dp),
                            ) {}
                            Text(
                                text = "Plan Free • $computableCount de $maxQuota cuentas vinculadas",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = KipuColors.Ink,
                            )
                        }
                        Text(
                            text = "Ver planes >",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = KipuColors.PrimaryText,
                            modifier = Modifier.clickable(onClick = onNavigateToPlans),
                        )
                    }
                }
            }
        }

        // State 2 & 4: Hero Gradient Card (#0F766E) with Crossfade privacy transitions
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(KipuColors.Primary, KipuColors.PrimaryDark),
                        ),
                    )
                    .padding(20.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Header row: Title + Privacy badge + Eye Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "TOTAL DISPONIBLE",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                letterSpacing = 0.5.sp,
                            )
                            if (isMasked) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.White.copy(alpha = 0.2f),
                                ) {
                                    Text(
                                        text = "🔒 Modo Privado",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            } else {
                                Text(text = "•", color = Color.White.copy(alpha = 0.6f))
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (isQuotaReached) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.White.copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        text = "● En tiempo real",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            IconButton(
                                onClick = onToggleMasked,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = if (isMasked) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isMasked) "Mostrar saldos" else "Ocultar saldos",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    // Main balance with Crossfade
                    Crossfade(
                        targetState = isMasked,
                        animationSpec = tween(durationMillis = 200),
                        label = "heroBalanceCrossfade",
                    ) { masked ->
                        if (masked) {
                            Text(
                                text = "S/ ••••••••",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum",
                                ),
                                color = Color.White,
                            )
                        } else {
                            val totalPenUnits = data?.totalPen?.minorUnits ?: 0L
                            Text(
                                text = "S/ ${formatMinorUnits(totalPenUnits)}",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum",
                                ),
                                color = Color.White,
                            )
                        }
                    }

                    // Subtitle with Crossfade
                    Crossfade(
                        targetState = isMasked,
                        animationSpec = tween(durationMillis = 200),
                        label = "heroSubtitleCrossfade",
                    ) { masked ->
                        Text(
                            text = if (masked) {
                                "🔒 Activos líquidos ocultos · Toca el ojo para revelar"
                            } else {
                                "Activos líquidos (Ahorros y efectivo · Excluye líneas de crédito)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Subtotals breakdown: Cuentas bancarias vs Efectivo físico
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = "En cuentas bancarias",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                            Crossfade(
                                targetState = isMasked,
                                animationSpec = tween(durationMillis = 200),
                                label = "bankSubtotalCrossfade",
                            ) { masked ->
                                Text(
                                    text = if (masked) "S/ ••••••" else "S/ ${formatMinorUnits(bankAccountsTotal)}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFeatureSettings = "tnum",
                                    ),
                                    color = Color.White,
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "En efectivo físico",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                            Crossfade(
                                targetState = isMasked,
                                animationSpec = tween(durationMillis = 200),
                                label = "cashSubtotalCrossfade",
                            ) { masked ->
                                Text(
                                    text = if (masked) "S/ ••••••" else "S/ ${formatMinorUnits(cashAccountsTotal)}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFeatureSettings = "tnum",
                                    ),
                                    color = Color.White,
                                )
                            }
                        }
                    }

                    // Liquid sources and reconciliation indicator
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = "${liquidAccounts.size} fuentes líquidas",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF4ADE80),
                                    modifier = Modifier.size(6.dp),
                                ) {}
                                Text(
                                    text = "100% conciliado",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Credit utilization notifications
        if (creditNotifications.isNotEmpty()) {
            items(creditNotifications, key = { it.id }) { notification ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCardClick(notification.cardId) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            notification.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(notification.body, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Aviso de utilización · ${notification.createdAt}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }

        // Section: Cuentas y efectivo
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Cuentas y efectivo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = KipuColors.Ink,
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isQuotaReached && rememberKipuColors().isDark) Color(0xFF7F1D1D)
                            else if (isQuotaReached) Color(0xFFFEE2E2) else KipuColors.SurfaceVariant,
                    ) {
                        Text(
                            text = if (isQuotaReached) "${liquidAccounts.size} activas (Max)" else "${liquidAccounts.size} activas",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isQuotaReached) KipuColors.Expense else KipuColors.TextMutedOnVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                Text(
                    text = "Gestionar",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                            color = KipuColors.PrimaryText,
                    modifier = Modifier.clickable(onClick = onManageQuota),
                )
            }
        }

        // Compact Account Cards
        items(liquidAccounts, key = { it.account.id.value }) { item ->
            CompactAccountItemCard(
                accountWithBalance = item,
                isMasked = isMasked,
                onClick = { onAccountClick(item.account.id.value) },
            )
        }

        // Section: Tarjetas de crédito
        if (activeCreditCards.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Tarjetas de crédito",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = KipuColors.Ink,
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = KipuColors.SurfaceVariant,
                            ) {
                                Text(
                                    text = "${activeCreditCards.size} ${if (activeCreditCards.size == 1) "activa" else "activas"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = KipuColors.TextMutedOnVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Text(
                        text = "Líneas de crédito (no suman a tu disponible)",
                        style = MaterialTheme.typography.bodySmall,
                        color = KipuColors.TextMuted,
                    )
                }
            }

            // Compact Credit Card Cards
            items(activeCreditCards, key = { it.card.id.value }) { creditItem ->
                CompactCreditCardItemCard(
                    creditCardWithSummary = creditItem,
                    isMasked = isMasked,
                    onClick = { onCardClick(creditItem.card.id.value) },
                )
            }
        }

        // Accordion: Instrumentos y cuentas archivadas
        if (totalArchivedCount > 0) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KipuColors.Surface),
                    border = BorderStroke(1.dp, KipuColors.Border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onToggleArchived),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Archive,
                                    contentDescription = null,
                                    tint = KipuColors.TextMuted,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = "Instrumentos y cuentas archivadas",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = KipuColors.Ink,
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = KipuColors.SurfaceVariant,
                                ) {
                                    Text(
                                        text = "$totalArchivedCount",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = KipuColors.TextMutedOnVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ExpandMore,
                                    contentDescription = if (isArchivedExpanded) "Colapsar" else "Expandir",
                                    tint = KipuColors.TextMuted,
                                    modifier = Modifier.rotate(chevronRotation),
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = isArchivedExpanded,
                            enter = expandVertically(tween(250)) + fadeIn(tween(200)),
                            exit = shrinkVertically(tween(200)) + fadeOut(tween(150)),
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                archivedAccounts.forEach { account ->
                                    ArchivedInstrumentCard(
                                        title = account.alias,
                                        subtitle = "${account.type.toDashboardLabel()} • ${account.currency.name}",
                                        onClick = { onAccountClick(account.id.value) },
                                    )
                                }
                                archivedCreditCardsWithDebt.forEach { item ->
                                    ArchivedInstrumentCard(
                                        title = item.card.alias ?: "${item.card.issuer} ${item.card.network}",
                                        subtitle = "${item.card.issuer} •••• ${item.card.lastFourDigits} · Deuda: ${item.card.currency.name} ${formatMinorUnits(item.debt.minorUnits)}",
                                        badge = "Archivada · Deuda pendiente",
                                        onClick = { onCardClick(item.card.id.value) },
                                    )
                                }
                                archivedCardsWithoutDebt.forEach { card ->
                                    ArchivedInstrumentCard(
                                        title = card.alias ?: "${card.issuer} ${card.network}",
                                        subtitle = "${card.issuer} • ${card.network} •••• ${card.lastFourDigits}",
                                        badge = "Archivada",
                                        onClick = { onCardClick(card.id.value) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}

/**
 * Compact Account Card for State 2 & 4
 */
@Composable
private fun CompactAccountItemCard(
    accountWithBalance: AccountWithBalance,
    isMasked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val account = accountWithBalance.account
    val isCash = account.type == AccountType.CASH

    val (badgeBg, badgeTint, badgeLabel) = resolveAccountBadgeColors(account)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = KipuColors.Surface),
        border = BorderStroke(1.dp, KipuColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Squircle icon container
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = badgeBg,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (badgeLabel != null) {
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeTint,
                        )
                    } else {
                        val iconVector = when (account.type) {
                            AccountType.CASH -> Icons.Default.Payments
                            AccountType.DIGITAL_WALLET -> Icons.Default.Wallet
                            else -> Icons.Default.AccountBalance
                        }
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            tint = badgeTint,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            // Account details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = account.alias,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = KipuColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (account.preset == AccountPreset.BCP && account.alias.contains("Sueldo", ignoreCase = true)) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = rememberKipuColors().positiveContainer,
                        ) {
                            Text(
                                text = "Principal",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = KipuColors.PrimaryText,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                }

                val issuerLabel = account.preset?.defaultName ?: account.type.toDashboardLabel()
                val typeLabel = account.type.toDashboardLabel()
                Text(
                    text = "$issuerLabel · $typeLabel · ${account.currency.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = KipuColors.TextMuted,
                    maxLines = 1,
                )
                if (account.isPlanLocked) {
                    Text(
                        text = "Bloqueado por el plan Free",
                        style = MaterialTheme.typography.labelSmall,
                        color = KipuColors.Expense,
                    )
                }
            }

            // Balance and label
            Column(horizontalAlignment = Alignment.End) {
                Crossfade(
                    targetState = isMasked,
                    animationSpec = tween(durationMillis = 200),
                    label = "accountItemCrossfade",
                ) { masked ->
                    if (masked) {
                        Text(
                            text = "S/ ••••••••",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum",
                            ),
                            color = KipuColors.Ink,
                        )
                    } else {
                        MoneyText(
                            money = accountWithBalance.balance,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = KipuColors.Ink,
                            isMasked = false,
                        )
                    }
                }
                Text(
                    text = if (isCash) "En mano" else "Disponible",
                    style = MaterialTheme.typography.labelSmall,
                    color = KipuColors.TextMuted,
                )
            }
        }
    }
}

/**
 * Compact Credit Card Card for State 2 & 4
 */
@Composable
private fun CompactCreditCardItemCard(
    creditCardWithSummary: CreditCardWithSummary,
    isMasked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val card = creditCardWithSummary.card
    val nextBillingDate = try {
        CreditCalculations.calculateNextDate(card.billingDay)
    } catch (_: Exception) {
        null
    }
    val daysUntil = nextBillingDate?.let {
        ChronoUnit.DAYS.between(LocalDate.now(), it).toInt()
    }
    val cycleSubtitle = when {
        daysUntil != null && daysUntil > 0 -> "Corte: Día ${card.billingDay} · Cierra en $daysUntil días"
        daysUntil != null && daysUntil == 0 -> "Corte: Día ${card.billingDay} · Cierra hoy"
        else -> "Corte: Día ${card.billingDay} · Pago: Día ${card.dueDay}"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = KipuColors.Surface),
        border = BorderStroke(1.dp, KipuColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Squircle dark navy container with credit card icon
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = rememberKipuColors().cardVisualBackground,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CreditCard,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            // Credit card details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.alias ?: "${card.issuer} ${card.network}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = KipuColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Crédito •••• ${card.lastFourDigits}",
                    style = MaterialTheme.typography.bodySmall,
                    color = KipuColors.TextMuted,
                )
                Text(
                    text = cycleSubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                )
                if (card.isPlanLocked) {
                    Text(
                        text = "Bloqueada por el plan Free",
                        style = MaterialTheme.typography.labelSmall,
                        color = KipuColors.Expense,
                    )
                }
            }

            // Debt amount in coral/red with chevron
            Column(horizontalAlignment = Alignment.End) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Crossfade(
                        targetState = isMasked,
                        animationSpec = tween(durationMillis = 200),
                        label = "creditDebtCrossfade",
                    ) { masked ->
                        if (masked) {
                            Text(
                                text = "S/ ••••••••",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum",
                                ),
                                color = KipuColors.Expense,
                            )
                        } else {
                            MoneyText(
                                money = creditCardWithSummary.debt,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (creditCardWithSummary.debt.minorUnits > 0L) KipuColors.Expense else KipuColors.Ink,
                                isMasked = false,
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = null,
                        tint = KipuColors.Expense,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = "Deuda actual",
                    style = MaterialTheme.typography.labelSmall,
                    color = KipuColors.TextMuted,
                )
            }
        }
    }
}

/**
 * Bottom Navigation Bar: Inicio, Movimientos, Mis Cuentas (activa), Análisis
 */
@Composable
private fun KipuBottomBar(
    onNavigateToHome: () -> Unit,
    onNavigateToMovements: () -> Unit,
    onNavigateToAnalysis: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = KipuColors.Surface,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, KipuColors.Border),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KipuBottomNavItem(
                icon = Icons.Default.Home,
                label = "Inicio",
                isSelected = false,
                onClick = onNavigateToHome,
            )
            KipuBottomNavItem(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                label = "Movimientos",
                isSelected = false,
                onClick = onNavigateToMovements,
            )
            KipuBottomNavItem(
                icon = Icons.Default.AccountBalanceWallet,
                label = "Mis Cuentas",
                isSelected = true,
                onClick = { /* Pantalla actual */ },
            )
            KipuBottomNavItem(
                icon = Icons.AutoMirrored.Filled.ShowChart,
                label = "Análisis",
                isSelected = false,
                onClick = onNavigateToAnalysis,
            )
        }
    }
}

@Composable
private fun KipuBottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) KipuColors.PrimaryText else KipuColors.TextMuted,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) KipuColors.PrimaryText else KipuColors.TextMuted,
        )
    }
}

@Composable
private fun ArchivedInstrumentCard(
    title: String,
    subtitle: String,
    badge: String = "Archivado",
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = KipuColors.SurfaceVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = KipuColors.Ink,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = KipuColors.TextMutedOnVariant,
                    )
                }
                val badgeColors = if (badge.contains("Deuda pendiente")) {
                    if (rememberKipuColors().isDark) Color(0xFF7F1D1D) to KipuColors.Expense
                    else Color(0xFFFEE2E2) to KipuColors.Expense
                } else {
                    KipuColors.SurfaceVariant to KipuColors.TextMutedOnVariant
                }
                Surface(shape = RoundedCornerShape(6.dp), color = badgeColors.first) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColors.second,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Text(
                text = "El historial se conserva. Toca para ver o reactivar.",
                style = MaterialTheme.typography.bodySmall,
                color = KipuColors.TextMutedOnVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

private fun AccountType.toDashboardLabel(): String = when (this) {
    AccountType.CASH -> "Efectivo"
    AccountType.SAVINGS -> "Ahorros"
    AccountType.BANK -> "Corriente"
    AccountType.DIGITAL_WALLET -> "Billetera"
    AccountType.CREDIT_LIABILITY -> "Pasivo de tarjeta"
}

@Composable
private fun resolveAccountBadgeColors(account: Account): Triple<Color, Color, String?> {
    val base = when (account.preset) {
        AccountPreset.BCP -> Triple(Color(0xFFE0F2FE), Color(0xFF0284C7), "BCP")
        AccountPreset.BBVA -> Triple(Color(0xFFDBEAFE), Color(0xFF1D4ED8), "BBVA")
        AccountPreset.INTERBANK -> Triple(Color(0xFFDCFCE7), Color(0xFF059669), "IBK")
        AccountPreset.SCOTIABANK -> Triple(Color(0xFFFEE2E2), Color(0xFFDC2626), "Scotiabank")
        AccountPreset.BANCO_NACION -> Triple(Color(0xFFFEF3C7), Color(0xFFD97706), "BN")
        AccountPreset.YAPE -> Triple(Color(0xFFF3E8FF), Color(0xFF7E22CE), "Yape")
        AccountPreset.PLIN -> Triple(Color(0xFFCFFAFE), Color(0xFF0891B2), "Plin")
        AccountPreset.CASH -> Triple(Color(0xFFDCFCE7), Color(0xFF16A34A), null)
        else -> when (account.type) {
            AccountType.CASH -> Triple(Color(0xFFDCFCE7), Color(0xFF16A34A), null)
            AccountType.DIGITAL_WALLET -> Triple(Color(0xFFEDE9FE), Color(0xFF7C3AED), null)
            else -> Triple(Color(0xFFF1F5F9), Color(0xFF0F766E), null)
        }
    }
    val adapted = dashboardPastelColors(base.first, base.second)
    return Triple(adapted.first, adapted.second, base.third)
}

@Composable
private fun InstrumentQuotaSelectionDialog(
    accounts: List<Account>,
    cards: List<DomainCard>,
    selectedIds: Set<String>,
    maxQuota: Int,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    val allIds = remember(accounts, cards) {
        (accounts.map { it.id.value } + cards.map { it.id.value }).toSet()
    }
    val selected = remember(allIds, selectedIds) {
        mutableStateListOf<String>().apply {
            val existing = selectedIds.intersect(allIds)
            addAll(if (existing.isNotEmpty()) existing else allIds.take(maxQuota))
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Instrumentos disponibles",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Elige hasta $maxQuota. Los demás conservarán su historial y saldos, pero no podrán usarse mientras superes el límite Free.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                accounts.forEach { account ->
                    val id = account.id.value
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = id in selected,
                            onCheckedChange = { checked ->
                                if (checked && selected.size < maxQuota) selected.add(id)
                                else if (!checked) selected.remove(id)
                            },
                        )
                        Text(account.alias, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                cards.forEach { card ->
                    val id = card.id.value
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = id in selected,
                            onCheckedChange = { checked ->
                                if (checked && selected.size < maxQuota) selected.add(id)
                                else if (!checked) selected.remove(id)
                            },
                        )
                        Text(
                            card.alias ?: "${card.issuer} •••• ${card.lastFourDigits}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected.toSet()) }) {
                Text("Guardar", fontWeight = FontWeight.Bold, color = KipuColors.PrimaryText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}
