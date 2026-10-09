package com.kipu.app.feature.accounts.presentation.instruments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditProductReference
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.KipuMotionTokens
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.util.Locale

/**
 * Calm Emerald pill badge for step badges and labels.
 */
@Composable
fun CalmEmeraldPillBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(
                text = text,
                color = contentColor,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
            )
        }
    }
}

/**
 * Intro screen selection card for adding an instrument (Image 1).
 * Touch target >= 48dp, 16dp rounded card, white with subtle border.
 */
@Composable
fun CalmEmeraldIntroCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val calmColors = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = calmColors.surfaceCard,
        border = BorderStroke(1.dp, calmColors.borderSubtle),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $description"
                role = Role.Button
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(calmColors.incomeBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = calmColors.primaryDeep,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    ),
                    color = calmColors.primaryText,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    ),
                    color = calmColors.secondaryMuted,
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = calmColors.secondaryMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Neutral quota notice banner displaying active computable count without inventing Premium status or fake prices.
 */
@Composable
fun CalmEmeraldQuotaNotice(
    activeCount: Int,
    maxQuota: Int,
    onNavigateToPlans: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val calmColors = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = calmColors.pillTrack,
        border = BorderStroke(1.dp, calmColors.borderSubtle),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = calmColors.primaryDeep,
                modifier = Modifier.size(20.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Cuentas y tarjetas activas: $activeCount de $maxQuota incluidos en el plan base",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = calmColors.primaryText,
                )
                Text(
                    text = "La disponibilidad se valida al guardar",
                    style = MaterialTheme.typography.labelSmall,
                    color = calmColors.secondaryMuted,
                )
            }
            if (onNavigateToPlans != null) {
                TextButton(
                    onClick = onNavigateToPlans,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        text = "Ver planes →",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = calmColors.primaryDeep,
                    )
                }
            }
        }
    }
}

/**
 * Short, truthful privacy and security footer.
 */
@Composable
fun CalmEmeraldPrivacyFooter(
    modifier: Modifier = Modifier,
) {
    val calmColors = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = calmColors.secondaryMuted,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Kipu no se conecta a tus contraseñas ni mueve dinero. Todos los saldos son administrados de forma privada por ti.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = calmColors.secondaryMuted,
            )
        }
    }
}

/**
 * Compact issuer/bank card with "Cambiar" action for Credit Step 2 (Image 2).
 */
@Composable
fun CalmEmeraldIssuerCard(
    bank: BankChoice,
    onOpenBankPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val calmColors = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = calmColors.surfaceCard,
        border = BorderStroke(1.dp, calmColors.borderSubtle),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clickable(role = Role.Button, onClick = onOpenBankPicker)
            .semantics(mergeDescendants = true) {
                contentDescription = "Entidad emisora actual: ${bank.name}. Tocar para cambiar."
                role = Role.Button
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bank.color),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = bank.name.take(3).uppercase(),
                    color = bank.textColor,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "ENTIDAD BANCARIA",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp,
                    ),
                    color = calmColors.secondaryMuted,
                )
                Text(
                    text = "Banco de Crédito ${bank.name}".takeIf { bank.code == "BCP" } ?: bank.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = calmColors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = calmColors.pillTrack,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Cambiar",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = calmColors.primaryDeep,
                    )
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = calmColors.primaryDeep,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/**
 * Modal bottom sheet for choosing issuer bank in Step 2.
 * Groups banks into "Bancos principales en Perú" and "Tarjetas departamentales y retail".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalmEmeraldBankSelectionSheet(
    visible: Boolean,
    isCreditCard: Boolean,
    currentBankCode: String,
    bankChoices: List<BankChoice>,
    onConfirm: (BankChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val calmColors = rememberCalmEmeraldColors()
    var tempBankCode by rememberSaveable(currentBankCode) { mutableStateOf(currentBankCode) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val filteredBanks = remember(bankChoices, searchQuery) {
        if (searchQuery.isBlank()) bankChoices
        else bankChoices.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.code.contains(searchQuery, ignoreCase = true) ||
                (it.subtitle?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    val mainBanks = remember(filteredBanks) { filteredBanks.filter { !it.isRetail } }
    val retailBanks = remember(filteredBanks) { filteredBanks.filter { it.isRetail } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = calmColors.surfaceCard,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(calmColors.borderSubtle),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isCreditCard) "Seleccionar entidad emisora" else "Seleccionar banco",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                        color = calmColors.primaryText,
                    )
                    Text(
                        text = if (isCreditCard) {
                            "Elige el banco o fintech de tu tarjeta de crédito"
                        } else {
                            "Elige el banco donde tienes tu cuenta"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = calmColors.secondaryMuted,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = calmColors.secondaryMuted,
                    )
                }
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = if (isCreditCard) "Buscar banco o entidad…" else "Buscar banco…",
                        fontSize = 14.sp,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = calmColors.secondaryMuted,
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Borrar búsqueda",
                                tint = calmColors.secondaryMuted,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = calmColors.surfaceCard,
                    unfocusedContainerColor = calmColors.surfaceCard,
                    focusedIndicatorColor = calmColors.primaryDeep,
                    unfocusedIndicatorColor = calmColors.borderSubtle,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            // Bank options list (scrollable)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (mainBanks.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "BANCOS PRINCIPALES EN PERÚ",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp,
                            ),
                            color = calmColors.secondaryMuted,
                        )
                        Text(
                            text = "${mainBanks.size} disponibles",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                            color = calmColors.secondaryMuted,
                        )
                    }

                    mainBanks.chunked(2).forEach { rowBanks ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            rowBanks.forEach { bank ->
                                MainBankGridItem(
                                    bank = bank,
                                    isSelected = tempBankCode == bank.code,
                                    onSelect = { tempBankCode = bank.code },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (rowBanks.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                if (retailBanks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "TARJETAS DEPARTAMENTALES Y RETAIL",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp,
                        ),
                        color = calmColors.secondaryMuted,
                    )
                    retailBanks.forEach { bank ->
                        RetailBankRowItem(
                            bank = bank,
                            isSelected = tempBankCode == bank.code,
                            currentBankCode = currentBankCode,
                            onSelect = { tempBankCode = bank.code }
                        )
                    }
                }
            }

            // Fixed CTA at bottom
            Button(
                onClick = {
                    val confirmedBank = bankChoices.firstOrNull { it.code == tempBankCode } ?: bankChoices.first()
                    onConfirm(confirmedBank)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = calmColors.primaryDeep,
                    contentColor = calmColors.onPrimaryDeep,
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = calmColors.onPrimaryDeep,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Confirmar entidad seleccionada",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

@Composable
private fun MainBankGridItem(
    bank: BankChoice,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val calmColors = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) calmColors.pillTrack else calmColors.surfaceCard,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) calmColors.primaryDeep else calmColors.borderSubtle,
        ),
        modifier = modifier
            .heightIn(min = 88.dp)
            .clickable(role = Role.RadioButton, onClick = onSelect)
            .semantics {
                this.selected = isSelected
                this.role = Role.RadioButton
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(bank.color),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = bank.logoAcronym,
                        color = bank.textColor,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                    )
                }
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(calmColors.primaryDeep),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Seleccionado",
                            tint = calmColors.onPrimaryDeep,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, calmColors.borderSubtle, CircleShape),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = bank.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 14.sp,
                    ),
                    color = calmColors.primaryText,
                )
                Text(
                    text = bank.subtitle.orEmpty(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = calmColors.secondaryMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RetailBankRowItem(
    bank: BankChoice,
    isSelected: Boolean,
    currentBankCode: String,
    onSelect: () -> Unit,
) {
    val calmColors = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) calmColors.pillTrack else calmColors.surfaceCard,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) calmColors.primaryDeep else calmColors.borderSubtle,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable(role = Role.RadioButton, onClick = onSelect)
            .semantics {
                this.selected = isSelected
                this.role = Role.RadioButton
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                .background(bank.color),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = bank.logoAcronym,
                    color = bank.textColor,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = bank.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        ),
                        color = calmColors.primaryText,
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = calmColors.pillTrack,
                    ) {
                        Text(
                            text = "Retail",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = calmColors.secondaryMuted,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = bank.subtitle ?: "Disponible en Kipu",
                    style = MaterialTheme.typography.labelSmall,
                    color = calmColors.secondaryMuted,
                )
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(calmColors.primaryDeep),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Seleccionado",
                        tint = calmColors.onPrimaryDeep,
                        modifier = Modifier.size(13.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, calmColors.borderSubtle, CircleShape),
                )
            }
        }
    }
}

/**
 * Product item card for Step 2 list.
 * White card, subtle border (or emerald border if selected). Touch target >= 48dp.
 */
@Composable
fun CalmEmeraldCreditProductCard(
    name: String,
    bankName: String,
    network: CardNetwork,
    tierLabel: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    stylePreset: com.kipu.app.feature.accounts.presentation.components.CardStylePreset? = null,
) {
    val calmColors = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = calmColors.surfaceCard,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) calmColors.primaryDeep else calmColors.borderSubtle,
        ),
        shadowElevation = if (isSelected) 1.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.selected = isSelected
                this.role = Role.RadioButton
                contentDescription = "$name, red ${network.name}. ${if (isSelected) "Seleccionada" else "Tocar para seleccionar"}"
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Mini visual representation
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .then(
                        if (stylePreset != null) Modifier.background(stylePreset.gradientBrush)
                        else Modifier.background(if (isSelected) calmColors.navSelectedPill else calmColors.pillTrack)
                    )
                    .border(1.dp, if (isSelected) calmColors.primaryDeep else calmColors.borderSubtle, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (stylePreset != null) {
                    Text(
                        text = stylePreset.tier.take(4).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = stylePreset.textColor,
                        )
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CreditCard,
                        contentDescription = null,
                        tint = if (isSelected) calmColors.primaryDeep else calmColors.secondaryMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 15.sp,
                    ),
                    color = calmColors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$bankName · Red ${network.name}${tierLabel?.let { " · $it" }.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = calmColors.secondaryMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(calmColors.primaryDeep),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Seleccionado",
                        tint = calmColors.onPrimaryDeep,
                        modifier = Modifier.size(14.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, calmColors.borderSubtle, CircleShape),
                )
            }
        }
    }
}

/**
 * Empty search state for Credit Step 2.
 */
@Composable
fun CalmEmeraldCreditSearchEmptyState(
    searchQuery: String,
    bankName: String,
    onRegisterCustomCard: () -> Unit,
    onChangeBank: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val calmColors = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = calmColors.surfaceCard,
        border = BorderStroke(1.dp, calmColors.borderSubtle),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(calmColors.pillTrack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = calmColors.secondaryMuted,
                    modifier = Modifier.size(26.dp),
                )
            }

            Text(
                text = "No encontramos «$searchQuery» en $bankName",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = calmColors.primaryText,
                textAlign = TextAlign.Center,
            )

            Text(
                text = "Verifica que el nombre o categoría sea correcto, o regístrala manualmente ingresando su línea y fechas en el siguiente paso.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = calmColors.secondaryMuted,
                textAlign = TextAlign.Center,
            )

            OutlinedButton(
                onClick = onRegisterCustomCard,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, calmColors.primaryDeep),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = calmColors.primaryDeep),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(
                    text = "Agregar manualmente",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }

            TextButton(
                onClick = onChangeBank,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    text = "¿Es de otro banco? Cambiar entidad bancaria",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = calmColors.primaryDeep,
                )
            }
        }
    }
}

/**
 * Bottom card for registering manual credit card in Step 2.
 */
@Composable
fun CalmEmeraldCustomCardActionCard(
    onRegisterCustomCard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val calmColors = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = calmColors.surfaceCard,
        border = BorderStroke(1.dp, calmColors.borderSubtle),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(role = Role.Button, onClick = onRegisterCustomCard),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(calmColors.pillTrack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.CreditCard,
                    contentDescription = null,
                    tint = calmColors.primaryDeep,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "¿No encuentras tu tarjeta?",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = calmColors.primaryText,
                )
                Text(
                    text = "Agrégala manualmente en un minuto",
                    style = MaterialTheme.typography.bodySmall,
                    color = calmColors.secondaryMuted,
                )
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = calmColors.pillTrack,
                border = BorderStroke(1.dp, calmColors.borderSubtle),
            ) {
                Text(
                    text = "Agregar manualmente",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = calmColors.primaryDeep,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * Summary card for Step 3 showing the chosen or custom card product.
 */
@Composable
fun CalmEmeraldStep3SummaryCard(
    productName: String?,
    alias: String,
    bank: BankChoice,
    network: CardNetwork,
    modifier: Modifier = Modifier,
) {
    val calmColors = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = calmColors.surfaceCard,
        border = BorderStroke(1.dp, calmColors.borderSubtle),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(bank.color),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = bank.name.take(3).uppercase(),
                    color = bank.textColor,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = if (productName != null) "TARJETA SELECCIONADA" else "TARJETA PERSONALIZADA",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp,
                    ),
                    color = calmColors.secondaryMuted,
                )
                Text(
                    text = productName ?: alias.ifBlank { "Tarjeta ${bank.name}" },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                    color = calmColors.primaryText,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${bank.name} · ${network.name}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = calmColors.secondaryMuted,
                )
            }
            CalmEmeraldPillBadge(
                text = "Listo",
                containerColor = calmColors.incomeBg,
                contentColor = calmColors.incomeEmerald,
                leadingIcon = Icons.Default.Check,
            )
        }
    }
}

/**
 * Informational card on credit utilization alert thresholds (50%, 80%, 100%).
 * Strictly informational, without non-persisting toggle chips.
 */
@Composable
fun CalmEmeraldUtilizationNoticeCard(
    modifier: Modifier = Modifier,
) {
    val calmColors = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = calmColors.incomeBg,
        border = BorderStroke(1.dp, calmColors.incomeBorder),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = calmColors.primaryDeep,
                modifier = Modifier.size(18.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Alertas de uso responsable",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = calmColors.primaryText,
                )
                Text(
                    text = "Umbrales de alerta: 50%, 80% y 100% de tu línea. Cada aviso se activa al alcanzar su umbral.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = calmColors.primaryText,
                )
            }
        }
    }
}

/**
 * Collapsible SBS catalog referential TEA card (readonly, no fake Apply, no fake custom input).
 */
@Composable
fun CalmEmeraldReferencialTeaCollapsible(
    reference: CreditProductReference?,
    currency: Currency,
    modifier: Modifier = Modifier,
) {
    val calmColors = rememberCalmEmeraldColors()
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val reducedMotion = rememberReducedMotionEnabled()

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = calmColors.surfaceCard,
        border = BorderStroke(1.dp, calmColors.borderSubtle),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "TEA de tu tarjeta",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = calmColors.primaryText,
                    )
                    CalmEmeraldPillBadge(
                        text = "Referencial",
                        containerColor = calmColors.pillTrack,
                        contentColor = calmColors.secondaryMuted,
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Plegar TEA" else "Desplegar TEA",
                    tint = calmColors.secondaryMuted,
                    modifier = Modifier.size(20.dp),
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = if (reducedMotion) fadeIn(tween(0)) + expandVertically(animationSpec = tween(0))
                else fadeIn(tween(KipuMotionTokens.SubtreeEnterMillis)) + expandVertically(animationSpec = tween(KipuMotionTokens.SubtreeEnterMillis)),
                exit = if (reducedMotion) fadeOut(tween(0)) + shrinkVertically(animationSpec = tween(0))
                else fadeOut(tween(KipuMotionTokens.SubtreeExitMillis)) + shrinkVertically(animationSpec = tween(KipuMotionTokens.SubtreeExitMillis)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (reference != null) {
                        val low = if (currency == Currency.PEN) reference.penTeaMinBps else reference.usdTeaMinBps
                        val high = if (currency == Currency.PEN) reference.penTeaMaxBps else reference.usdTeaMaxBps
                        val rateStr = when {
                            low != null && high != null && low != high ->
                                "${String.format(java.util.Locale.forLanguageTag(androidx.compose.ui.text.intl.Locale.current.toLanguageTag()), "%.2f", low / 100.0)}% – ${String.format(java.util.Locale.forLanguageTag(androidx.compose.ui.text.intl.Locale.current.toLanguageTag()), "%.2f", high / 100.0)}%"
                            low != null -> "${String.format(java.util.Locale.forLanguageTag(androidx.compose.ui.text.intl.Locale.current.toLanguageTag()), "%.2f", low / 100.0)}%"
                            high != null -> "${String.format(java.util.Locale.forLanguageTag(androidx.compose.ui.text.intl.Locale.current.toLanguageTag()), "%.2f", high / 100.0)}%"
                            else -> "Tasa no desglosada para ${currency.name} en la referencia del catálogo."
                        }
                        Text(
                            text = "Tasa referencial del catálogo (${currency.name}): $rateStr",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = calmColors.primaryText,
                        )
                        reference.catalogAsOf?.let { asOf ->
                            Text(
                                text = "Fecha de referencia: $asOf",
                                style = MaterialTheme.typography.labelSmall,
                                color = calmColors.secondaryMuted,
                            )
                        }
                    } else {
                        Text(
                            text = "Este producto no tiene una tasa referencial disponible en el catálogo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = calmColors.secondaryMuted,
                        )
                    }
                    Text(
                        text = "Referencia orientativa; no sustituye la tasa indicada en el contrato con la entidad emisora.",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                        color = calmColors.secondaryMuted,
                    )
                }
            }
        }
    }
}

/**
 * Visual introduction screen for "Agregar a Mi dinero" (Image 1).
 */
@Composable
fun CalmEmeraldIntroView(
    activeCount: Int,
    maxQuota: Int,
    onNavigateToPlans: (() -> Unit)?,
    onSelectSavingsDebit: () -> Unit,
    onSelectCreditCard: () -> Unit,
    onSelectWallet: () -> Unit,
    onSelectCash: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val calmColors = rememberCalmEmeraldColors()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "¿Qué quieres agregar?",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                ),
                color = calmColors.primaryText,
            )
            Text(
                text = "Elige dónde guardas tu dinero o la tarjeta que quieres registrar.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                ),
                color = calmColors.secondaryMuted,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CalmEmeraldIntroCard(
                icon = Icons.Default.AccountBalance,
                title = "Cuenta bancaria",
                description = "Ahorros, sueldo o cuenta corriente en una entidad financiera",
                onClick = onSelectSavingsDebit,
            )
            CalmEmeraldIntroCard(
                icon = Icons.Default.CreditCard,
                title = "Tarjeta de crédito",
                description = "Línea autorizada, ciclos de corte, pago y control de consumos",
                onClick = onSelectCreditCard,
            )
            CalmEmeraldIntroCard(
                icon = Icons.Default.Wallet,
                title = "Billetera digital",
                description = "Yape o Plin registradas como saldos independientes",
                onClick = onSelectWallet,
            )
            CalmEmeraldIntroCard(
                icon = Icons.Default.Payments,
                title = "Efectivo",
                description = "Dinero en efectivo disponible para gastos cotidianos",
                onClick = onSelectCash,
            )
        }

        CalmEmeraldQuotaNotice(
            activeCount = activeCount,
            maxQuota = maxQuota,
            onNavigateToPlans = onNavigateToPlans,
        )

        CalmEmeraldPrivacyFooter()

        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Step 2: Credit Card Selector (Image 2).
 * Shows compact issuer card with change action, local search, family filter chips,
 * and verified credit product options or empty state.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalmEmeraldCreditSelectView(
    bank: BankChoice,
    productsForBank: List<CatalogProductChoice>,
    selectedProductName: String?,
    creditSearchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedFamilyFilter: String,
    onFamilyFilterChange: (String) -> Unit,
    onSelectProduct: (CatalogProductChoice) -> Unit,
    onRegisterCustomCard: () -> Unit,
    onOpenBankPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val calmColors = rememberCalmEmeraldColors()

    val filterCategories = remember(productsForBank) {
        listOf("Todas") + productsForBank.map { it.network.name }.distinct()
    }
    val filteredProducts = remember(productsForBank, creditSearchQuery, selectedFamilyFilter) {
        productsForBank.filter { product ->
            (creditSearchQuery.isBlank() || product.name.contains(creditSearchQuery.trim(), ignoreCase = true)) &&
                (selectedFamilyFilter == "Todas" || product.network.name == selectedFamilyFilter)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "¿Qué tarjeta tienes?",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                ),
                color = calmColors.primaryText,
            )
            Text(
                text = "Selecciona tu tarjeta para completar automáticamente sus datos.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                ),
                color = calmColors.secondaryMuted,
            )
        }

        CalmEmeraldIssuerCard(
            bank = bank,
            onOpenBankPicker = onOpenBankPicker,
        )

        OutlinedTextField(
            value = creditSearchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Buscar tarjeta", fontSize = 14.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = calmColors.secondaryMuted,
                )
            },
            trailingIcon = {
                if (creditSearchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Limpiar búsqueda",
                            tint = calmColors.secondaryMuted,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = calmColors.surfaceCard,
                unfocusedContainerColor = calmColors.surfaceCard,
                focusedIndicatorColor = calmColors.primaryDeep,
                unfocusedIndicatorColor = calmColors.borderSubtle,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filterCategories.forEach { family ->
                val isSelected = selectedFamilyFilter == family
                FilterChip(
                    selected = isSelected,
                    onClick = { onFamilyFilterChange(family) },
                    label = {
                        Text(
                            text = family,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 13.sp,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = calmColors.primaryDeep,
                        selectedLabelColor = calmColors.onPrimaryDeep,
                        containerColor = calmColors.surfaceCard,
                        labelColor = calmColors.primaryText,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) calmColors.primaryDeep else calmColors.borderSubtle,
                        selectedBorderColor = calmColors.primaryDeep,
                    ),
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }

        if (filteredProducts.isEmpty()) {
            CalmEmeraldCreditSearchEmptyState(
                searchQuery = creditSearchQuery,
                bankName = bank.name,
                onRegisterCustomCard = onRegisterCustomCard,
                onChangeBank = onOpenBankPicker,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filteredProducts.forEach { product ->
                    CalmEmeraldCreditProductCard(
                        name = product.name,
                        bankName = bank.name,
                        network = product.network,
                        tierLabel = product.stylePreset?.tierLabel,
                        isSelected = selectedProductName == product.name,
                        onClick = { onSelectProduct(product) },
                        stylePreset = product.stylePreset,
                    )
                }
            }

            CalmEmeraldCustomCardActionCard(
                onRegisterCustomCard = onRegisterCustomCard,
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}


/**
 * TEA section for Step 3: editable annual TEA with referential recommendation card and "Aplicar" button.
 */
@Composable
fun CalmEmeraldTeaConfigCard(
    teaInput: String,
    onTeaInputChange: (String) -> Unit,
    bankName: String,
    productName: String?,
    modifier: Modifier = Modifier,
) {
    val calmColors = rememberCalmEmeraldColors()
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = calmColors.surfaceCard,
        border = BorderStroke(1.dp, calmColors.borderSubtle),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "TEA de tu tarjeta",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                        color = calmColors.primaryText,
                    )
                    CalmEmeraldPillBadge(
                        text = "Opcional",
                        containerColor = calmColors.pillTrack,
                        contentColor = calmColors.secondaryMuted,
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Plegar TEA" else "Desplegar TEA",
                    tint = calmColors.secondaryMuted,
                    modifier = Modifier.size(20.dp),
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = teaInput,
                        onValueChange = onTeaInputChange,
                        label = { Text("TEA Anual (%)") },
                        placeholder = { Text("84.50") },
                        suffix = { Text("%", fontWeight = FontWeight.Bold) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text("Ingresa la tasa de tu contrato. La TEA del catálogo no reemplaza tu tasa personal.",
                        style = MaterialTheme.typography.bodySmall, color = calmColors.secondaryMuted)

                }
            }
        }
    }
}

/**
 * Usage alerts section with 50%, 80%, 100% toggle pills.
 */
@Composable
fun CalmEmeraldUsageAlertsCard(
    alert50: Boolean,
    onToggle50: () -> Unit,
    alert80: Boolean,
    onToggle80: () -> Unit,
    alert100: Boolean,
    onToggle100: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val calmColors = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = calmColors.surfaceCard,
        border = BorderStroke(1.dp, calmColors.borderSubtle),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Alertas de uso",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                    color = calmColors.primaryText,
                )
                Text(
                    text = "Avísame cuando use gran parte de mi línea.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = calmColors.secondaryMuted,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AlertTogglePill(
                    label = "50%",
                    selected = alert50,
                    onClick = onToggle50,
                    modifier = Modifier.weight(1f),
                )
                AlertTogglePill(
                    label = "80%",
                    selected = alert80,
                    onClick = onToggle80,
                    modifier = Modifier.weight(1f),
                )
                AlertTogglePill(
                    label = "100%",
                    selected = alert100,
                    onClick = onToggle100,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AlertTogglePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val calmColors = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) calmColors.incomeBg else calmColors.surfaceCard,
        border = BorderStroke(
            1.dp,
            if (selected) calmColors.incomeEmerald else calmColors.borderSubtle
        ),
        modifier = modifier
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .semantics { this.selected = selected },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = calmColors.incomeEmerald,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                ),
                color = if (selected) calmColors.incomeEmerald else calmColors.primaryText,
            )
        }
    }
}

/**
 * Step 3: Credit Limit Input Card (Images 1 & 2 in media_1791086926370.png)
 */
@Composable
fun CalmEmeraldCreditLimitInputCard(
    creditLimitInput: String,
    onCreditLimitInputChange: (String) -> Unit,
    currency: Currency,
    onCurrencyChange: (Currency) -> Unit,
    isError: Boolean,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    val calmColors = rememberCalmEmeraldColors()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "LÍNEA DE CRÉDITO",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                ),
                color = if (isError) calmColors.expenseCoral else calmColors.secondaryMuted,
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = calmColors.pillTrack,
                border = BorderStroke(1.dp, calmColors.borderSubtle),
            ) {
                Row(modifier = Modifier.padding(2.dp)) {
                    val penSelected = currency == Currency.PEN
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (penSelected) calmColors.primaryDeep else Color.Transparent,
                        modifier = Modifier.heightIn(min = 48.dp).clickable { onCurrencyChange(Currency.PEN) }
                    ) {
                        Text(
                            text = "PEN (S/)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (penSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp,
                            ),
                            color = if (penSelected) calmColors.onPrimaryDeep else calmColors.secondaryMuted,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
                        )
                    }
                    val usdSelected = currency == Currency.USD
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (usdSelected) calmColors.primaryDeep else Color.Transparent,
                        modifier = Modifier.heightIn(min = 48.dp).clickable { onCurrencyChange(Currency.USD) }
                    ) {
                        Text(
                            text = "USD ($)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (usdSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp,
                            ),
                            color = if (usdSelected) calmColors.onPrimaryDeep else calmColors.secondaryMuted,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
                        )
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = calmColors.surfaceCard,
            border = BorderStroke(
                width = if (isError) 1.5.dp else 1.dp,
                color = if (isError) calmColors.expenseCoral else calmColors.borderSubtle,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (currency == Currency.PEN) "S/ " else "$ ",
                        style = TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isError) calmColors.expenseCoral else calmColors.primaryText,
                        ),
                    )
                    BasicTextField(
                        value = creditLimitInput,
                        onValueChange = onCreditLimitInputChange,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isError) calmColors.expenseCoral else calmColors.primaryText,
                        ),
                        decorationBox = { innerTextField ->
                            if (creditLimitInput.isEmpty()) {
                                Text(
                                    text = "0.00",
                                    style = TextStyle(
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isError) calmColors.expenseCoral else calmColors.secondaryMuted.copy(alpha = 0.4f),
                                    ),
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (isError) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = calmColors.expenseCoral,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar monto",
                        tint = calmColors.secondaryMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        Text(
            text = if (isError) "ⓘ ${errorMessage ?: "Ingresa una línea de crédito válida mayor a S/ 0.00"}"
            else "El monto total que tu banco te permite utilizar.",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
            color = if (isError) calmColors.expenseCoral else calmColors.secondaryMuted,
        )
    }
}

/**
 * Step 3: Billing and Due Dates (Images 1 & 2 in media_1791086926370.png)
 */
@Composable
fun CalmEmeraldDateInputsCard(
    billingDayInput: String,
    onBillingDayChange: (String) -> Unit,
    dueDayInput: String,
    onDueDayChange: (String) -> Unit,
    billingError: String? = null,
    dueError: String? = null,
    modifier: Modifier = Modifier,
) {
    val calmColors = rememberCalmEmeraldColors()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "FECHAS DE TU TARJETA",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
            ),
            color = if (billingError != null || dueError != null) calmColors.expenseCoral else calmColors.secondaryMuted,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Día de cierre
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = calmColors.surfaceCard,
                    border = BorderStroke(
                        width = if (billingError != null) 1.5.dp else 1.dp,
                        color = if (billingError != null) calmColors.expenseCoral else calmColors.borderSubtle,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Día de cierre",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                            ),
                            color = if (billingError != null) calmColors.expenseCoral else calmColors.primaryText,
                        )
                        BasicTextField(
                            value = billingDayInput,
                            onValueChange = { onBillingDayChange(it.filter(Char::isDigit).take(2)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (billingError != null) calmColors.expenseCoral else calmColors.primaryText,
                                textAlign = TextAlign.Center,
                            ),
                            modifier = Modifier.width(48.dp),
                        )
                    }
                }
                Text(
                    text = if (billingError != null) "ⓘ $billingError" else "Cuando termina tu período de compras.",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                    color = if (billingError != null) calmColors.expenseCoral else calmColors.secondaryMuted,
                )
            }

            // Día de pago
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = calmColors.surfaceCard,
                    border = BorderStroke(
                        width = if (dueError != null) 1.5.dp else 1.dp,
                        color = if (dueError != null) calmColors.expenseCoral else calmColors.borderSubtle,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Día de pago",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                            ),
                            color = if (dueError != null) calmColors.expenseCoral else calmColors.primaryText,
                        )
                        BasicTextField(
                            value = dueDayInput,
                            onValueChange = { onDueDayChange(it.filter(Char::isDigit).take(2)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (dueError != null) calmColors.expenseCoral else calmColors.primaryText,
                                textAlign = TextAlign.Center,
                            ),
                            modifier = Modifier.width(48.dp),
                        )
                    }
                }
                Text(
                    text = if (dueError != null) "ⓘ $dueError" else "Fecha límite para pagar.",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                    color = if (dueError != null) calmColors.expenseCoral else calmColors.secondaryMuted,
                )
            }
        }
    }
}

/**
 * Step 3: Last 4 digits card (Images 1 & 2 in media_1791086926370.png)
 */
@Composable
fun CalmEmeraldLastFourDigitsCard(
    lastFourDigits: String,
    onLastFourDigitsChange: (String) -> Unit,
    isError: Boolean,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    val calmColors = rememberCalmEmeraldColors()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Últimos 4 dígitos",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                color = if (isError) calmColors.expenseCoral else calmColors.primaryText,
            )
            CalmEmeraldPillBadge(
                text = "Obligatorio",
                containerColor = calmColors.pillTrack,
                contentColor = calmColors.secondaryMuted,
            )
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = calmColors.surfaceCard,
            border = BorderStroke(
                width = if (isError) 1.5.dp else 1.dp,
                color = if (isError) calmColors.expenseCoral else calmColors.borderSubtle,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "••••  ",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = calmColors.secondaryMuted,
                )
                BasicTextField(
                    value = lastFourDigits,
                    onValueChange = { onLastFourDigitsChange(it.filter(Char::isDigit).take(4)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = if (isError) calmColors.expenseCoral else calmColors.primaryText,
                    ),
                    decorationBox = { innerTextField ->
                        if (lastFourDigits.isEmpty()) {
                            Text(
                                text = "0000",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = calmColors.secondaryMuted.copy(alpha = 0.4f),
                            )
                        }
                        innerTextField()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (isError) {
            Text(
                text = "ⓘ ${errorMessage ?: "Ingresa exactamente 4 números."}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = calmColors.expenseCoral,
            )
        }
        Text(
            text = "Solo para identificar tu tarjeta en Kipu. Nunca pedimos tu número completo ni CVV.",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
            color = calmColors.secondaryMuted,
        )
    }
}

/**
 * Error alert banner shown above save button in Step 3 when validation fails.
 */
@Composable
fun CalmEmeraldErrorBanner(
    message: String = "Revisa los campos con error para continuar",
    modifier: Modifier = Modifier,
) {
    val calmColors = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = calmColors.expenseBg,
        border = BorderStroke(1.dp, calmColors.expenseBorder),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = calmColors.expenseCoral,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                ),
                color = calmColors.expenseCoral,
            )
        }
    }
}

/**
 * Step 3: Success Bottom Sheet (Image 4 in media_1791086926370.png)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalmEmeraldSuccessBottomSheet(
    card: CreditCard,
    bankChoice: BankChoice,
    onDismiss: () -> Unit,
    onViewDetail: (CreditCard) -> Unit,
    onBackToMoney: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val calmColors = rememberCalmEmeraldColors()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = calmColors.surfaceCard,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(calmColors.borderSubtle),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Success checkmark circle
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(calmColors.incomeBg),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(calmColors.incomeEmerald.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Éxito",
                        tint = calmColors.incomeEmerald,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // Title and subtitle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "¡Tarjeta configurada con éxito!",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    ),
                    color = calmColors.primaryText,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Tu ${card.alias ?: "Tarjeta"} de ${bankChoice.name} está lista para registrar compras y controlar tu línea.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = calmColors.secondaryMuted,
                    textAlign = TextAlign.Center,
                )
            }

            // Card snippet
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = calmColors.surfaceCard,
                border = BorderStroke(1.dp, calmColors.borderSubtle),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bankChoice.color),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = bankChoice.logoAcronym,
                            color = bankChoice.textColor,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = card.alias ?: "Tarjeta ${bankChoice.name}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = calmColors.primaryText,
                        )
                        Text(
                            text = "•••• ${card.lastFourDigits} · ${bankChoice.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = calmColors.secondaryMuted,
                        )
                    }
                }
            }

            // 2x2 Grid summary
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = calmColors.pillTrack.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, calmColors.borderSubtle),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        // Cell 1: Línea total
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "Línea total",
                                style = MaterialTheme.typography.labelSmall,
                                color = calmColors.secondaryMuted,
                            )
                            Text(
                                text = "${if (card.currency == Currency.PEN) "S/" else "$"} ${com.kipu.app.ui.component.formatMinorUnits(card.creditLimitMinorUnits)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = calmColors.primaryText,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(50.dp)
                                .background(calmColors.borderSubtle)
                                .align(Alignment.CenterVertically)
                        )
                        // Cell 2: Alertas activadas
                        val alertsText = "50%, 80% y 100%"

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "Alertas activadas",
                                style = MaterialTheme.typography.labelSmall,
                                color = calmColors.secondaryMuted,
                            )
                            Text(
                                text = alertsText,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = calmColors.primaryText,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(calmColors.borderSubtle)
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        // Cell 3: Próximo corte
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "Próximo corte",
                                style = MaterialTheme.typography.labelSmall,
                                color = calmColors.secondaryMuted,
                            )
                            Text(
                                text = "Día ${card.billingDay}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = calmColors.primaryText,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(50.dp)
                                .background(calmColors.borderSubtle)
                                .align(Alignment.CenterVertically)
                        )
                        // Cell 4: Próximo pago
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "Próximo pago",
                                style = MaterialTheme.typography.labelSmall,
                                color = calmColors.secondaryMuted,
                            )
                            Text(
                                text = "Día ${card.dueDay}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = calmColors.primaryText,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Primary action button
            Button(
                onClick = { onViewDetail(card) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = calmColors.primaryDeep,
                    contentColor = calmColors.onPrimaryDeep,
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp),
            ) {
                Text(
                    text = "Ir al detalle de la tarjeta →",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }

            // Secondary action button
            TextButton(
                onClick = onBackToMoney,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(
                    text = "Volver a Mi dinero",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = calmColors.primaryText,
                )
            }
        }
    }
}
