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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalDensity
import java.math.BigInteger
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PieChart
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.AccountWithBalance
import com.kipu.app.feature.accounts.domain.model.Card as DomainCard
import com.kipu.app.feature.accounts.presentation.AccountsViewModel
import com.kipu.app.feature.accounts.presentation.components.CreditCardSummaryCard
import com.kipu.app.feature.movements.presentation.QuickMovementBottomSheet
import com.kipu.app.feature.notifications.presentation.UnreadNotificationBadge
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import com.kipu.app.ui.theme.KipuMotionTokens
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: AccountsViewModel,
    onNavigateToNewAccount: () -> Unit,
    onNavigateToNewCard: () -> Unit,
    onAccountClick: (String) -> Unit = {},
    onCardClick: (String) -> Unit = onAccountClick,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    unreadNotificationCount: Int = 0,
    feedbackMessage: String? = null,
    onFeedbackConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
    onNavigateToPlans: () -> Unit = {},
    onNavigateToMovements: () -> Unit = {},
    onToggleMasked: (() -> Unit)? = null,
    openRegisterMovement: Boolean = false,
    onConsumeRegisterMovement: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val state by viewModel.dashboardUiState.collectAsStateWithLifecycle()
    val instruments by viewModel.instrumentsUiState.collectAsStateWithLifecycle()
    val creditNotifications by viewModel.creditNotifications.collectAsStateWithLifecycle()
    var showQuotaSelection by remember { mutableStateOf(false) }
    var showRegisterMovementSheet by rememberSaveable { mutableStateOf(false) }
    var isArchivedExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val vmMasked by viewModel.isMasked.collectAsStateWithLifecycle()
    val isMasked = vmMasked || LocalBalanceMasked.current
    val emeraldColors = rememberCalmEmeraldColors()

    LaunchedEffect(feedbackMessage) {
        feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            onFeedbackConsumed()
        }
    }
    LaunchedEffect(viewModel) { viewModel.refreshCreditUtilizationNotifications() }

    // Respond to root navigation register movement trigger
    LaunchedEffect(openRegisterMovement) {
        if (openRegisterMovement) {
            showRegisterMovementSheet = true
            onConsumeRegisterMovement()
        }
    }

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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Avatar circle with MC initials
                            Surface(
                                shape = CircleShape,
                                color = emeraldColors.incomeBg,
                                border = BorderStroke(1.dp, emeraldColors.incomeBorder),
                                modifier = Modifier.size(38.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "MC",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                        ),
                                        color = emeraldColors.primaryDeep,
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Bienvenido",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = emeraldColors.secondaryMuted,
                                )
                                Text(
                                    text = "Mi dinero",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                    ),
                                    color = emeraldColors.primaryText,
                                )
                            }
                        }
                    },
                    actions = {
                        // Small white circular notification button with 48dp touch target
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onNavigateToNotifications),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = emeraldColors.surfaceCard,
                                border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                                modifier = Modifier.size(38.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    UnreadNotificationBadge(
                                        unreadCount = unreadNotificationCount,
                                        onClick = onNavigateToNotifications,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Small white circular settings button with 48dp touch target
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onNavigateToSettings),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = emeraldColors.surfaceCard,
                                border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                                modifier = Modifier.size(38.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Configuración",
                                        tint = emeraldColors.secondaryMuted,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = emeraldColors.background,
                    ),
                )
            },
            containerColor = emeraldColors.background,
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
                        color = emeraldColors.primaryDeep,
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
                        onToggleMasked = {
                            if (onToggleMasked != null) {
                                onToggleMasked()
                            } else {
                                viewModel.toggleMasked()
                            }
                        },
                        onAccountClick = onAccountClick,
                        onCardClick = onCardClick,
                        onManageQuota = { showQuotaSelection = true },
                        onNavigateToPlans = onNavigateToPlans,
                        onNavigateToNewAccount = onNavigateToNewAccount,
                        onNavigateToNewCard = onNavigateToNewCard,
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

    if (showRegisterMovementSheet) {
        QuickMovementBottomSheet(
            onDismissRequest = { showRegisterMovementSheet = false },
            onNavigateToNewAccount = onNavigateToNewAccount,
            onSaved = { _ ->
                showRegisterMovementSheet = false
            },
            onMessage = { message ->
                scope.launch { snackbarHostState.showSnackbar(message) }
            },
        )
    }
}

/**
 * State 1: Empty Dashboard Content
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
    val emeraldColors = rememberCalmEmeraldColors()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Hero zero balance
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = emeraldColors.heroGradientStart),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .drawBehind {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.06f),
                            radius = size.width * 0.45f,
                            center = Offset(size.width * 0.95f, size.height * 0.1f),
                            style = Stroke(width = 30f),
                        )
                    },
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Surface(shape = CircleShape, color = Color(0xFF6EE7B7), modifier = Modifier.size(7.dp)) {}
                            Text(
                                text = "TOTAL DISPONIBLE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
                                color = Color(0xFFA7F3D0),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "S/ 0.00",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            fontFeatureSettings = "tnum",
                        ),
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Empieza agregando tu primera cuenta o efectivo",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = emeraldColors.heroSubcardBg,
                            modifier = Modifier.weight(1f),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("En cuentas", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Color.White.copy(alpha = 0.8f))
                                Text("S/ 0.00", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp), color = Color.White)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = emeraldColors.heroSubcardBg,
                            modifier = Modifier.weight(1f),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("En efectivo", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Color.White.copy(alpha = 0.8f))
                                Text("S/ 0.00", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp), color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Plan strip
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = emeraldColors.incomeBg,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PieChart,
                                    contentDescription = null,
                                    tint = emeraldColors.incomeEmerald,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Cupo Free · $quotaCount de $maxQuota cuentas y tarjetas",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = emeraldColors.primaryText,
                            )
                            Text(
                                text = "Te quedan ${(maxQuota - quotaCount).coerceAtLeast(0)} cuentas y tarjetas disponibles",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = emeraldColors.secondaryMuted,
                            )
                        }
                    }
                    Text(
                        text = "Ver planes >",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = emeraldColors.primaryDeep,
                        modifier = Modifier
                            .clickable(onClick = onNavigateToPlans)
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
            }
        }

        // Section Title: Cuentas y efectivo
        item {
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
                        text = "Cuentas y efectivo",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = emeraldColors.primaryText,
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = emeraldColors.pillTrack,
                    ) {
                        Text(
                            text = "0 registradas",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = emeraldColors.secondaryMuted,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }

        // Central Empty Card: Aún no tienes cuentas registradas
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = emeraldColors.surfaceCard),
                border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = emeraldColors.incomeBg,
                        border = BorderStroke(1.dp, emeraldColors.incomeBorder),
                        modifier = Modifier.size(52.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = emeraldColors.incomeEmerald,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Aún no tienes cuentas registradas",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = emeraldColors.primaryText,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Registra tus cuentas bancarias, billeteras digitales o efectivo físico para tener control de tus finanzas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = emeraldColors.secondaryMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onNavigateToNewAccount,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = emeraldColors.primaryDeep,
                            contentColor = emeraldColors.onPrimaryDeep,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(
                            text = "+ Agregar primera cuenta",
                            fontWeight = FontWeight.Bold,
                            color = emeraldColors.onPrimaryDeep,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }

        // Tarjetas de crédito empty
        item {
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
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = emeraldColors.primaryText,
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = emeraldColors.pillTrack,
                    ) {
                        Text(
                            text = "0 activas",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = emeraldColors.secondaryMuted,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = emeraldColors.surfaceCard),
                border = BorderStroke(1.dp, emeraldColors.borderSubtle),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (emeraldColors.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CreditCard,
                                    contentDescription = null,
                                    tint = emeraldColors.secondaryMuted,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Sin tarjetas de crédito vinculadas",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = emeraldColors.primaryText,
                            )
                            Text(
                                text = "Controla límites de crédito y fechas de pago",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = emeraldColors.secondaryMuted,
                            )
                        }
                    }
                    TextButton(onClick = onNavigateToNewCard, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("+ Vincular", fontWeight = FontWeight.Bold, color = emeraldColors.primaryDeep)
                    }
                }
            }
        }

        // Privacy Guarantee Card
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = emeraldColors.incomeBg,
                border = BorderStroke(1.dp, emeraldColors.incomeBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = emeraldColors.incomeEmerald,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Tus datos permanecen privados y protegidos. Puedes comenzar agregando una cuenta de ahorros o el efectivo que llevas contigo.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = emeraldColors.primaryDeep,
                        lineHeight = 16.sp,
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * State 2, 3, 4: Active Dashboard Content
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
    onNavigateToNewAccount: () -> Unit,
    onNavigateToNewCard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val emeraldColors = rememberCalmEmeraldColors()
    val computableCount = data?.activeComputableCount ?: instruments.activeComputableCount
    val maxQuota = data?.maxFreeQuota ?: instruments.maxFreeQuota
    val isQuotaReached = computableCount >= maxQuota

    val liquidAccounts = data?.liquidAccounts.orEmpty()
    val activeCreditCards = data?.creditCards.orEmpty().filter { !it.card.isArchived }

    val bankAccountsByCurrency = remember(liquidAccounts) {
        val map = mutableMapOf<Currency, BigInteger>()
        liquidAccounts.filter { it.account.type != AccountType.CASH }.forEach { item ->
            val curr = item.balance.currency
            map[curr] = map.getOrDefault(curr, BigInteger.ZERO).add(BigInteger.valueOf(item.balance.minorUnits))
        }
        if (map.isEmpty()) map[Currency.PEN] = BigInteger.ZERO
        map
    }

    val cashAccountsByCurrency = remember(liquidAccounts) {
        val map = mutableMapOf<Currency, BigInteger>()
        liquidAccounts.filter { it.account.type == AccountType.CASH }.forEach { item ->
            val curr = item.balance.currency
            map[curr] = map.getOrDefault(curr, BigInteger.ZERO).add(BigInteger.valueOf(item.balance.minorUnits))
        }
        if (map.isEmpty()) map[Currency.PEN] = BigInteger.ZERO
        map
    }

    val hasUsd = (data?.totalUsd != null && data.totalUsd.minorUnits != 0L) ||
        liquidAccounts.any { it.balance.currency == Currency.USD } ||
        instruments.activeAccounts.any { it.currency.name == "USD" }

    val archivedAccounts = instruments.archivedAccounts
    val archivedCreditCardsWithDebt = data?.creditCards.orEmpty().filter {
        it.card.isArchived && it.debt.minorUnits > 0L
    }
    val archivedCardsWithoutDebt = instruments.archivedCards.filter { card ->
        archivedCreditCardsWithDebt.none { it.card.id == card.id }
    }
    val totalArchivedCount = archivedAccounts.size + archivedCreditCardsWithDebt.size + archivedCardsWithoutDebt.size

    val reducedMotion = rememberReducedMotionEnabled()
    val chevronRotation = animateFloatAsState(
        targetValue = if (isArchivedExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else KipuMotionTokens.SegmentMillis),
        label = "archivedChevronRotation",
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Privacy Banner when active
        if (isMasked) {
            item(key = "privacy_banner") {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF093E35),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = Color(0xFFA7F3D0),
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "Modo privacidad activado · Toca el ojo para revelar",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp),
                                color = Color.White,
                            )
                        }
                        IconButton(
                            onClick = onToggleMasked,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Desactivar modo privacidad",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }

        // 1. HERO FIRST in Active Dashboard (deep emerald 22dp rounded)
        item(key = "dashboard_hero") {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = emeraldColors.heroGradientStart),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .drawBehind {
                        // Subtle decorative arcs
                        drawCircle(
                            color = Color.White.copy(alpha = 0.05f),
                            radius = size.width * 0.45f,
                            center = Offset(size.width * 0.95f, size.height * 0.15f),
                            style = Stroke(width = 30f),
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.03f),
                            radius = size.width * 0.7f,
                            center = Offset(size.width * 0.95f, size.height * 0.15f),
                            style = Stroke(width = 20f),
                        )
                    },
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Header row: TOTAL DISPONIBLE + Eye toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF6EE7B7),
                                modifier = Modifier.size(7.dp),
                            ) {}
                            Text(
                                text = "TOTAL DISPONIBLE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp,
                                ),
                                color = Color(0xFFA7F3D0),
                            )
                            if (isMasked) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.White.copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        text = "Oculto",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }

                        if (isMasked) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.18f),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable(onClick = onToggleMasked)
                                    .semantics {
                                        contentDescription = "Mostrar saldos"
                                    },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        } else {
                            IconButton(
                                onClick = onToggleMasked,
                                modifier = Modifier
                                    .size(48.dp)
                                    .semantics {
                                        contentDescription = "Ocultar saldos"
                                    },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    // Main Total Balance (PEN and USD separated, never combined)
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val fontScale = LocalDensity.current.fontScale
                        val shouldStack = maxWidth < 340.dp || fontScale >= 1.25f

                        if (shouldStack || hasUsd) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Column {
                                    if (hasUsd) {
                                        Text(
                                            text = "Soles (PEN)",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            color = Color.White.copy(alpha = 0.75f),
                                        )
                                    }
                                    MoneyText(
                                        money = data?.totalPen ?: Money(0L, Currency.PEN),
                                        isMasked = isMasked,
                                        style = MaterialTheme.typography.headlineLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = if (hasUsd) 26.sp else 32.sp,
                                            fontFeatureSettings = "tnum",
                                        ),
                                        color = Color.White,
                                    )
                                }
                                if (hasUsd) {
                                    Column {
                                        Text(
                                            text = "Dólares (USD)",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            color = Color.White.copy(alpha = 0.75f),
                                        )
                                        MoneyText(
                                            money = data?.totalUsd ?: Money(0L, Currency.USD),
                                            isMasked = isMasked,
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 20.sp,
                                                fontFeatureSettings = "tnum",
                                            ),
                                            color = Color.White,
                                        )
                                    }
                                }
                            }
                        } else {
                            MoneyText(
                                money = data?.totalPen ?: Money(0L, Currency.PEN),
                                isMasked = isMasked,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 32.sp,
                                    fontFeatureSettings = "tnum",
                                ),
                                color = Color.White,
                            )
                        }
                    }

                    // Clear Truthful Subtitle
                    Text(
                        text = "Activos líquidos reales · Excluye líneas de crédito",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Color.White.copy(alpha = 0.8f),
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Two Tinted Compact Panels: En cuentas bancarias & En efectivo side-by-side
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = emeraldColors.heroSubcardBg,
                            modifier = Modifier.weight(1f),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "En cuentas bancarias",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = Color.White.copy(alpha = 0.8f),
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                bankAccountsByCurrency.forEach { (curr, totalBigInt) ->
                                    val safeMinor = totalBigInt.coerceIn(
                                        BigInteger.valueOf(Long.MIN_VALUE),
                                        BigInteger.valueOf(Long.MAX_VALUE),
                                    ).toLong()
                                    MoneyText(
                                        minorUnits = safeMinor,
                                        currency = curr,
                                        isMasked = isMasked,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                        ),
                                        color = Color.White,
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = emeraldColors.heroSubcardBg,
                            modifier = Modifier.weight(1f),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "En efectivo",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = Color.White.copy(alpha = 0.8f),
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                cashAccountsByCurrency.forEach { (curr, totalBigInt) ->
                                    val safeMinor = totalBigInt.coerceIn(
                                        BigInteger.valueOf(Long.MIN_VALUE),
                                        BigInteger.valueOf(Long.MAX_VALUE),
                                    ).toLong()
                                    MoneyText(
                                        minorUnits = safeMinor,
                                        currency = curr,
                                        isMasked = isMasked,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                        ),
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quota reference is secondary to money; effective entitlement is validated on save.
        item(key = "quota_strip") {
            if (isQuotaReached && maxQuota > 0) {
                BasicPlanLimitCard(
                    usedQuota = computableCount,
                    maxQuota = maxQuota,
                    onNavigateToPlans = onNavigateToPlans,
                )
            } else {
            Surface(shape = RoundedCornerShape(16.dp), color = emeraldColors.pillTrack,
                modifier = Modifier.fillMaxWidth()) {
                FlowRow(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(if (LocalDensity.current.fontScale > 1.3f) Modifier.fillMaxWidth()
                        else Modifier.widthIn(min = 180.dp).weight(1f)) {
                        Text("Cupo Free · $computableCount de $maxQuota",
                            style = MaterialTheme.typography.labelLarge, color = emeraldColors.primaryText)
                        Text(if (isQuotaReached) "Cupo de cuentas y tarjetas completo"
                            else "${(maxQuota - computableCount).coerceAtLeast(0)} espacios disponibles",
                            style = MaterialTheme.typography.bodySmall, color = emeraldColors.secondaryMuted)
                    }
                    TextButton(onClick = onNavigateToPlans, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Ver planes")
                    }
                }
            }
            }
        }

        // Credit notifications
        if (creditNotifications.isNotEmpty()) {
            items(creditNotifications, key = { it.id }) { notification ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCardClick(notification.cardId) },
                    colors = CardDefaults.cardColors(containerColor = emeraldColors.warningBg),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, emeraldColors.warningBorder),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            notification.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = emeraldColors.warningAmber,
                        )
                        Text(notification.body, style = MaterialTheme.typography.bodySmall, color = emeraldColors.primaryText)
                    }
                }
            }
        }

        // 3. Section: Cuentas y efectivo
        item(key = "accounts_section_header") {
            MoneySectionHeader("Cuentas y efectivo", "${liquidAccounts.size} ${if (liquidAccounts.size == 1) "activa" else "activas"}",
                "+ Cuenta", onNavigateToNewAccount, "Gestionar", onManageQuota)
        }

        // Account cards list
        items(liquidAccounts, key = { it.account.id.value }) { item ->
            CalmEmeraldAccountCard(
                accountWithBalance = item,
                isMasked = isMasked,
                onClick = { onAccountClick(item.account.id.value) },
            )
        }

        // 4. Section: Tarjetas de crédito
        item(key = "cards_section_header") {
            Column(modifier = Modifier.padding(top = 6.dp)) {
                MoneySectionHeader("Tarjetas de crédito", "${activeCreditCards.size} ${if (activeCreditCards.size == 1) "activa" else "activas"}",
                    "+ Tarjeta", onNavigateToNewCard, "Ver detalle",
                    { activeCreditCards.firstOrNull()?.let { onCardClick(it.card.id.value) } }, activeCreditCards.isNotEmpty())
                Text(
                    text = "Líneas de crédito (no suman al saldo disponible)",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = emeraldColors.secondaryMuted,
                )
            }
        }

        if (activeCreditCards.isNotEmpty()) {
            items(activeCreditCards, key = { it.card.id.value }) { creditItem ->
                CreditCardSummaryCard(
                    creditCardWithSummary = creditItem,
                    onClick = { onCardClick(creditItem.card.id.value) },
                )
            }
        }

        // Archived items accordion
        if (totalArchivedCount > 0) {
            item(key = "archived_section") {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = emeraldColors.surfaceCard),
                    border = BorderStroke(1.dp, emeraldColors.borderSubtle),
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
                                    tint = emeraldColors.secondaryMuted,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = "Cuentas y tarjetas archivadas",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = emeraldColors.primaryText,
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = emeraldColors.pillTrack,
                                ) {
                                    Text(
                                        text = "$totalArchivedCount",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = emeraldColors.secondaryMuted,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ExpandMore,
                                    contentDescription = if (isArchivedExpanded) "Colapsar" else "Expandir",
                                    tint = emeraldColors.secondaryMuted,
                                    modifier = Modifier.graphicsLayer { rotationZ = chevronRotation.value },
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = isArchivedExpanded,
                            enter = expandVertically(tween(250)) + fadeIn(tween(200)),
                            exit = shrinkVertically(tween(200)) + fadeOut(tween(150)),
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                archivedAccounts.forEach { account ->
                                    ArchivedInstrumentCard(
                                        title = account.alias,
                                        subtitle = "${account.type.toDashboardLabel()} · ${account.currency.name}",
                                        onClick = { onAccountClick(account.id.value) },
                                    )
                                }
                                archivedCreditCardsWithDebt.forEach { item ->
                                    ArchivedInstrumentCard(
                                        title = item.card.alias ?: "${item.card.issuer} ${item.card.network}",
                                        subtitle = "${item.card.issuer} •••• ${item.card.lastFourDigits}",
                                        debtMoney = item.debt,
                                        badge = "Archivada · Deuda pendiente",
                                        onClick = { onCardClick(item.card.id.value) },
                                    )
                                }
                                archivedCardsWithoutDebt.forEach { card ->
                                    ArchivedInstrumentCard(
                                        title = card.alias ?: "${card.issuer} ${card.network}",
                                        subtitle = "${card.issuer} · ${card.network} •••• ${card.lastFourDigits}",
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

        // Financial Health Banner (Images 2, 3, 4, 5)
        item(key = "financial_health_banner") {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = emeraldColors.incomeBg,
                border = BorderStroke(1.dp, emeraldColors.incomeBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = emeraldColors.incomeEmerald,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Tus cuentas están sincronizadas. El nivel de deuda en tarjeta se mantiene en rango saludable (inferior al 30%).",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        ),
                        color = emeraldColors.incomeEmerald,
                    )
                }
            }
        }

        // Bottom space above floating nav bar
        item(key = "bottom_space") {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

/**
 * Calm Emerald Compact Account Card
 */
@Composable
private fun BasicPlanLimitCard(
    usedQuota: Int,
    maxQuota: Int,
    onNavigateToPlans: () -> Unit,
) {
    val colors = rememberCalmEmeraldColors()
    val progress = (usedQuota.toFloat() / maxQuota).coerceIn(0f, 1f)
    val percentage = (usedQuota.toLong() * 100 / maxQuota).toInt()
    val stacked = LocalDensity.current.fontScale > 1.3f

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceCard,
        border = BorderStroke(1.dp, colors.warningBorder),
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = if (stacked) Modifier.fillMaxWidth() else Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(shape = CircleShape, color = colors.warningBg) {
                        Icon(
                            imageVector = Icons.Default.PieChart,
                            contentDescription = null,
                            tint = colors.warningAmber,
                            modifier = Modifier.padding(8.dp).size(20.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Plan Básico", style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold, color = colors.primaryText)
                        Text("$usedQuota de $maxQuota cuentas y tarjetas usadas ($percentage %)",
                            style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
                        Surface(shape = RoundedCornerShape(50), color = colors.warningBg) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(5.dp).clip(CircleShape).background(colors.warningAmber))
                                Text("Límite alcanzado", style = MaterialTheme.typography.labelSmall,
                                    color = colors.warningText)
                            }
                        }
                    }
                }
                Button(
                    onClick = onNavigateToPlans,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.warningAmber,
                        contentColor = if (colors.isDark) colors.onPrimaryDeep else colors.primaryText,
                    ),
                ) {
                    Text("Mejorar a PRO", style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold)
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = colors.warningAmber,
                trackColor = colors.warningBg,
            )
        }
    }
}

@Composable
private fun CalmEmeraldAccountCard(
    accountWithBalance: AccountWithBalance,
    isMasked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val account = accountWithBalance.account
    val isCash = account.type == AccountType.CASH
    val emeraldColors = rememberCalmEmeraldColors()

    val (badgeBg, badgeTint, badgeLabel) = resolveAccountBadgeColors(account)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = emeraldColors.surfaceCard),
        border = BorderStroke(1.dp, emeraldColors.borderSubtle),
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
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (badgeLabel != null && LocalDensity.current.fontScale <= 1.3f) {
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
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
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // Account Name & Type
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = account.alias,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        ),
                        color = emeraldColors.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (account.preset == AccountPreset.BCP && account.alias.contains("Sueldo", ignoreCase = true)) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = emeraldColors.incomeBg,
                        ) {
                            Text(
                                text = "Principal",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                ),
                                color = emeraldColors.incomeEmerald,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                }

                val issuerLabel = account.preset?.defaultName ?: account.type.toDashboardLabel()
                val typeLabel = account.type.toDashboardLabel()
                Text(
                    text = "$issuerLabel · $typeLabel · ${account.currency.name}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = emeraldColors.secondaryMuted,
                    maxLines = 1,
                )
                if (account.isPlanLocked) {
                    Text(
                        text = "Bloqueado por el plan Free",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = emeraldColors.expenseCoral,
                    )
                }
            }

            // Balance and Status
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(
                    money = accountWithBalance.balance,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    ),
                    color = emeraldColors.primaryText,
                    isMasked = isMasked,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (!isCash) {
                        Surface(
                            shape = CircleShape,
                            color = emeraldColors.incomeEmerald,
                            modifier = Modifier.size(5.dp),
                        ) {}
                    }
                    Text(
                        text = if (isCash) "En bolsillo" else "Disponible",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = if (isCash) emeraldColors.secondaryMuted else emeraldColors.incomeEmerald,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchivedInstrumentCard(
    title: String,
    subtitle: String,
    badge: String = "Archivado",
    debtMoney: Money? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val emeraldColors = rememberCalmEmeraldColors()

    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = emeraldColors.pillTrack),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = emeraldColors.primaryText,
                    )
                    if (debtMoney != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "$subtitle · Deuda:",
                                style = MaterialTheme.typography.bodySmall,
                                color = emeraldColors.secondaryMuted,
                            )
                            MoneyText(
                                money = debtMoney,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = emeraldColors.expenseCoral,
                            )
                        }
                    } else {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = emeraldColors.secondaryMuted,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (badge.contains("Deuda pendiente")) emeraldColors.expenseBg else emeraldColors.surfaceCard,
                ) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                        color = if (badge.contains("Deuda pendiente")) emeraldColors.expenseCoral else emeraldColors.secondaryMuted,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
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
    val isDark = rememberCalmEmeraldColors().isDark
    return when (account.preset) {
        AccountPreset.BCP -> Triple(
            if (isDark) Color(0xFF0C4A6E) else Color(0xFFE0F2FE),
            if (isDark) Color(0xFF7DD3FC) else Color(0xFF0284C7),
            "BCP"
        )
        AccountPreset.BBVA -> Triple(
            if (isDark) Color(0xFF1E3A8A) else Color(0xFFDBEAFE),
            if (isDark) Color(0xFF93C5FD) else Color(0xFF1D4ED8),
            "BBVA"
        )
        AccountPreset.INTERBANK -> Triple(
            if (isDark) Color(0xFF14532D) else Color(0xFFDCFCE7),
            if (isDark) Color(0xFF86EFAC) else Color(0xFF059669),
            "IBK"
        )
        AccountPreset.SCOTIABANK -> Triple(
            if (isDark) Color(0xFF7F1D1D) else Color(0xFFFEE2E2),
            if (isDark) Color(0xFFFCA5A5) else Color(0xFFDC2626),
            "Scotiabank"
        )
        AccountPreset.BANCO_NACION -> Triple(
            if (isDark) Color(0xFF78350F) else Color(0xFFFEF3C7),
            if (isDark) Color(0xFFFCD34D) else Color(0xFFD97706),
            "BN"
        )
        AccountPreset.YAPE -> Triple(
            if (isDark) Color(0xFF581C87) else Color(0xFFF3E8FF),
            if (isDark) Color(0xFFD8B4FE) else Color(0xFF7E22CE),
            "Yape"
        )
        AccountPreset.PLIN -> Triple(
            if (isDark) Color(0xFF164E63) else Color(0xFFCFFAFE),
            if (isDark) Color(0xFF67E8F9) else Color(0xFF0891B2),
            "Plin"
        )
        AccountPreset.CASH -> Triple(
            if (isDark) Color(0xFF14532D) else Color(0xFFE2F4EE),
            if (isDark) Color(0xFF86EFAC) else Color(0xFF075E52),
            null
        )
        else -> when (account.type) {
            AccountType.CASH -> Triple(
                if (isDark) Color(0xFF14532D) else Color(0xFFE2F4EE),
                if (isDark) Color(0xFF86EFAC) else Color(0xFF075E52),
                null
            )
            AccountType.DIGITAL_WALLET -> Triple(
                if (isDark) Color(0xFF4C1D95) else Color(0xFFEDE9FE),
                if (isDark) Color(0xFFC4B5FD) else Color(0xFF7C3AED),
                null
            )
            else -> Triple(
                if (isDark) Color(0xFF134E4A) else Color(0xFFE6F7F3),
                if (isDark) Color(0xFF5EEAD4) else Color(0xFF075E52),
                null
            )
        }
    }
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
    val emeraldColors = rememberCalmEmeraldColors()
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
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = emeraldColors.primaryText,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Elige hasta $maxQuota. Los demás conservarán su historial y saldos, pero no podrán usarse mientras superes el límite Free.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = emeraldColors.secondaryMuted,
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
                        Text(account.alias, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = emeraldColors.primaryText)
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
                            color = emeraldColors.primaryText,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected.toSet()) }) {
                Text("Guardar", fontWeight = FontWeight.Bold, color = emeraldColors.primaryDeep)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = emeraldColors.secondaryMuted)
            }
        },
    )
}

@Composable
private fun MoneySectionHeader(
    title: String, count: String, addLabel: String, onAdd: () -> Unit,
    manageLabel: String, onManage: () -> Unit, showManage: Boolean = true,
) {
    val colors = rememberCalmEmeraldColors()
    val stacked = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.2f
    val heading: @Composable (Modifier) -> Unit = { modifier ->
        FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp), fontWeight = FontWeight.Bold)
            Surface(shape = RoundedCornerShape(6.dp), color = colors.pillTrack) {
                Text(count, style = MaterialTheme.typography.labelSmall, color = colors.secondaryMuted,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
    }
    val actions: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onAdd, contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                Text(addLabel, style = MaterialTheme.typography.labelMedium)
            }
            if (showManage) TextButton(onClick = onManage, contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                Text(manageLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    if (stacked) {
        Column(Modifier.fillMaxWidth()) { heading(Modifier.fillMaxWidth()); actions() }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            heading(Modifier.weight(1f)); actions()
        }
    }
}
