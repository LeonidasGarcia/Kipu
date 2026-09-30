package com.kipu.app.feature.movements.presentation

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.component.symbol
import com.kipu.app.ui.theme.rememberKipuColors
import com.kipu.app.ui.theme.KipuTheme

// Design tokens per docs/design/KIPU_UI_PASTEL_PROMPTS_AND_REFERENCE.md Reference A R3
private val AndeanTealPrimary: Color @Composable get() = rememberKipuColors().primary
private val SurfaceBaseSlate: Color @Composable get() = rememberKipuColors().surfaceSubtle
private val SurfaceSelectedMint: Color @Composable get() = rememberKipuColors().selectedSurface
private val BorderNeutralSlate: Color @Composable get() = rememberKipuColors().border
private val TextPrimarySlate: Color @Composable get() = rememberKipuColors().inkPrimary
private val TextSecondarySlate: Color @Composable get() = rememberKipuColors().inkSecondary
private val TextPillInactive: Color @Composable get() = rememberKipuColors().inkSecondary
private val DragHandleSlate: Color @Composable get() = rememberKipuColors().dragHandle
private val MintBadgeContainer: Color @Composable get() = rememberKipuColors().positiveContainer
private val MintBadgeContent: Color @Composable get() = if (rememberKipuColors().isDark) Color(0xFF86EFAC) else Color(0xFF166534)
private val CoralDebtText: Color @Composable get() = rememberKipuColors().debt
private val CompactCardBackground: Color @Composable get() = rememberKipuColors().cardVisualBackground
private val ChipSimColor = Color(0xFFE5B83A)

private enum class InstrumentFilter {
    ALL,
    ACCOUNTS,
    CARDS,
    CASH,
}

private sealed interface SelectorItem {
    data class AccountItem(val account: Account) : SelectorItem
    data class CardItem(val card: CreditCard) : SelectorItem
}

/**
 * Accessible Android Jetpack Compose modal bottom sheet matching Reference A R3 Compact Wallet.
 *
 * Implements ~70% height container, drag handle, title/subtitle, horizontal filter pills with
 * live-derived counts, selectable account and credit card rows with 48dp minimum touch targets,
 * full TalkBack accessibility semantics, exact pastel tokens, temporary selection state until
 * "Listo" confirmation, outlined "+ Nueva cuenta" action, and masked card previews without sensitive data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSelectorBottomSheet(
    onDismissRequest: () -> Unit,
    accounts: List<Account>,
    modifier: Modifier = Modifier,
    creditCards: List<CreditCard> = emptyList(),
    selectedAccountId: String? = null,
    selectedCardId: String? = null,
    allowCreditCards: Boolean = true,
    mostUsedAccountId: String? = null,
    accountBalances: Map<String, Money> = emptyMap(),
    cardDebts: Map<String, Money> = emptyMap(),
    cardAvailableCredits: Map<String, Money> = emptyMap(),
    title: String = "Seleccionar cuenta",
    subtitle: String = "Selecciona la cuenta para este gasto",
    onAccountSelected: (Account) -> Unit = {},
    onCardSelected: (CreditCard) -> Unit = {},
    onAccountIdSelected: ((String) -> Unit)? = null,
    onCardIdSelected: ((String) -> Unit)? = null,
    onNewAccountClick: () -> Unit = {},
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    var tempSelectedAccountId by remember(selectedAccountId) { mutableStateOf(selectedAccountId) }
    var tempSelectedCardId by remember(selectedCardId) { mutableStateOf(selectedCardId) }
    var activeFilter by remember { mutableStateOf(InstrumentFilter.ALL) }

    // Derive counts directly from live data
    val validAccounts = remember(accounts) {
        accounts.filter { it.type != AccountType.CREDIT_LIABILITY }
    }
    val accountsOnly = remember(validAccounts) {
        validAccounts.filter { it.type != AccountType.CASH }
    }
    val cashAccounts = remember(validAccounts) {
        validAccounts.filter { it.type == AccountType.CASH }
    }
    val cards = remember(creditCards, allowCreditCards) {
        if (allowCreditCards) creditCards else emptyList()
    }

    val accountsCount = accountsOnly.size
    val cashCount = cashAccounts.size
    val cardsCount = cards.size
    val totalCount = accountsCount + cashCount + cardsCount

    val displayedItems: List<SelectorItem> = remember(activeFilter, accountsOnly, cashAccounts, cards) {
        when (activeFilter) {
            InstrumentFilter.ALL -> {
                accountsOnly.map { SelectorItem.AccountItem(it) } +
                    cards.map { SelectorItem.CardItem(it) } +
                    cashAccounts.map { SelectorItem.AccountItem(it) }
            }
            InstrumentFilter.ACCOUNTS -> {
                accountsOnly.map { SelectorItem.AccountItem(it) }
            }
            InstrumentFilter.CARDS -> {
                cards.map { SelectorItem.CardItem(it) }
            }
            InstrumentFilter.CASH -> {
                cashAccounts.map { SelectorItem.AccountItem(it) }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = rememberKipuColors().surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(DragHandleSlate)
                    .testTag("sheet_drag_handle"),
            )
        },
        modifier = modifier.testTag("account_selector_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.70f)
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            // Header: Title, Subtitle, and Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimarySlate,
                        ),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            color = TextSecondarySlate,
                        ),
                    )
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_close_account_selector"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = TextSecondarySlate,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Horizontal Filter Pills Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterPill(
                    label = "Todas",
                    count = totalCount,
                    selected = activeFilter == InstrumentFilter.ALL,
                    onClick = { activeFilter = InstrumentFilter.ALL },
                    testTag = "chip_filter_all",
                )

                FilterPill(
                    label = "Cuentas",
                    count = accountsCount,
                    selected = activeFilter == InstrumentFilter.ACCOUNTS,
                    onClick = { activeFilter = InstrumentFilter.ACCOUNTS },
                    testTag = "chip_filter_accounts",
                )

                if (allowCreditCards) {
                    FilterPill(
                        label = "Tarjetas",
                        count = cardsCount,
                        selected = activeFilter == InstrumentFilter.CARDS,
                        onClick = { activeFilter = InstrumentFilter.CARDS },
                        testTag = "chip_filter_cards",
                    )
                }

                FilterPill(
                    label = "Efectivo",
                    count = cashCount,
                    selected = activeFilter == InstrumentFilter.CASH,
                    onClick = { activeFilter = InstrumentFilter.CASH },
                    testTag = "chip_filter_cash",
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Financial Instruments List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("list_selectable_instruments"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (displayedItems.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No hay instrumentos disponibles en esta categoría",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextSecondarySlate,
                                ),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    items(
                        items = displayedItems,
                        key = { item ->
                            when (item) {
                                is SelectorItem.AccountItem -> "account_${item.account.id.value}"
                                is SelectorItem.CardItem -> "card_${item.card.id.value}"
                            }
                        },
                    ) { item ->
                        when (item) {
                            is SelectorItem.AccountItem -> {
                                val account = item.account
                                val balance = accountBalances[account.id.value]
                                val isSelected = tempSelectedAccountId == account.id.value
                                val isMostUsed = mostUsedAccountId != null && account.id.value == mostUsedAccountId

                                AccountItemRow(
                                    account = account,
                                    balance = balance,
                                    isSelected = isSelected,
                                    isMostUsed = isMostUsed,
                                    onSelect = {
                                        tempSelectedAccountId = account.id.value
                                        tempSelectedCardId = null
                                    },
                                )
                            }
                            is SelectorItem.CardItem -> {
                                val card = item.card
                                val debt = cardDebts[card.id.value]
                                val availableCredit = cardAvailableCredits[card.id.value]
                                val isSelected = tempSelectedCardId == card.id.value

                                CreditCardItemRow(
                                    card = card,
                                    debt = debt,
                                    availableCredit = availableCredit,
                                    isSelected = isSelected,
                                    onSelect = {
                                        tempSelectedAccountId = null
                                        tempSelectedCardId = card.id.value
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Actions Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onNewAccountClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("btn_new_account"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, AndeanTealPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AndeanTealPrimary,
                    ),
                ) {
                    Text(
                        text = "+ Nueva cuenta",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        ),
                    )
                }

                Button(
                    onClick = {
                        val confirmedAccountId = tempSelectedAccountId
                        val confirmedCardId = tempSelectedCardId

                        if (confirmedAccountId != null) {
                            val selectedAcc = accounts.find { it.id.value == confirmedAccountId }
                            if (selectedAcc != null) {
                                onAccountSelected(selectedAcc)
                            }
                            onAccountIdSelected?.invoke(confirmedAccountId)
                            onDismissRequest()
                        } else if (confirmedCardId != null) {
                            val selectedCrd = creditCards.find { it.id.value == confirmedCardId }
                            if (selectedCrd != null) {
                                onCardSelected(selectedCrd)
                            }
                            onCardIdSelected?.invoke(confirmedCardId)
                            onDismissRequest()
                        }
                    },
                    enabled = tempSelectedAccountId != null || tempSelectedCardId != null,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("btn_confirm_selection"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AndeanTealPrimary,
                        contentColor = Color.White,
                        disabledContainerColor = AndeanTealPrimary.copy(alpha = 0.38f),
                        disabledContentColor = Color.White.copy(alpha = 0.60f),
                    ),
                ) {
                    Text(
                        text = "Listo",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "",
) {
    val background = if (selected) AndeanTealPrimary else SurfaceBaseSlate
    val contentColor = if (selected) Color.White else TextPillInactive
    val border = if (selected) null else BorderStroke(1.dp, BorderNeutralSlate)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .then(if (border != null) Modifier.border(border, RoundedCornerShape(20.dp)) else Modifier)
            .background(background)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            )
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$label ($count)",
            color = contentColor,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp,
            ),
        )
    }
}

@Composable
private fun AccountItemRow(
    account: Account,
    balance: Money?,
    isSelected: Boolean,
    isMostUsed: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (isSelected) SurfaceSelectedMint else SurfaceBaseSlate
    val borderColor = if (isSelected) AndeanTealPrimary else BorderNeutralSlate
    val borderWidth = if (isSelected) 1.5.dp else 1.dp
    val shape = RoundedCornerShape(14.dp)
    val subtitle = formatAccountSubtitle(account)

    val contentDesc = buildString {
        append(account.alias)
        append(". ")
        append(subtitle)
        if (isMostUsed) append(". Más usada.")
        if (balance != null) {
            append(". Saldo disponible: ")
            append(account.currency.symbol())
            append(" ")
            append(formatMinorUnits(balance.minorUnits))
        } else {
            append(". Saldo disponible aún no cargado")
        }
        append(if (isSelected) ". Seleccionada" else ". No seleccionada")
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(BorderStroke(borderWidth, borderColor), shape)
            .selectable(
                selected = isSelected,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .semantics { contentDescription = contentDesc }
            .testTag("account_item_${account.id.value}"),
        color = background,
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccountBadge(account = account)

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = account.alias,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimarySlate,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    if (isMostUsed) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MintBadgeContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("badge_most_used_${account.id.value}"),
                        ) {
                            Text(
                                text = "Más usada",
                                color = MintBadgeContent,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                ),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondarySlate,
                        fontSize = 12.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
            ) {
                if (balance != null) {
                    MoneyText(
                        money = balance,
                        color = TextPrimarySlate,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        ),
                    )
                    Text(
                        text = "Disponible",
                        color = MintBadgeContent,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                        ),
                    )
                } else {
                    Text(
                        text = "Consultando saldo",
                        color = TextSecondarySlate,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            SelectionIndicator(selected = isSelected)
        }
    }
}

@Composable
private fun CreditCardItemRow(
    card: CreditCard,
    debt: Money?,
    availableCredit: Money?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (isSelected) SurfaceSelectedMint else SurfaceBaseSlate
    val borderColor = if (isSelected) AndeanTealPrimary else BorderNeutralSlate
    val borderWidth = if (isSelected) 1.5.dp else 1.dp
    val shape = RoundedCornerShape(14.dp)
    val cardTitle = card.alias?.takeIf { it.isNotBlank() } ?: "${card.issuer} ${card.network.name}"
    val cardSubtitle = formatCardSubtitle(card)

    val contentDesc = buildString {
        append(cardTitle)
        append(". ")
        append(cardSubtitle)
        append(". Disponible: ")
        append(availableCredit?.let { "${card.currency.symbol()} ${formatMinorUnits(it.minorUnits)}" } ?: "no cargado")
        append(". Deuda: ")
        append(debt?.let { "${card.currency.symbol()} ${formatMinorUnits(it.minorUnits)}" } ?: "no cargada")
        append(if (isSelected) ". Seleccionada" else ". No seleccionada")
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(BorderStroke(borderWidth, borderColor), shape)
            .selectable(
                selected = isSelected,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .semantics { contentDescription = contentDesc }
            .testTag("card_item_${card.id.value}"),
        color = background,
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactCardVisual(network = card.network)

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = cardTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimarySlate,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = cardSubtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondarySlate,
                        fontSize = 12.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Disp: ",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondarySlate,
                            fontSize = 11.sp,
                        ),
                    )
                    if (availableCredit != null) {
                        MoneyText(
                            money = availableCredit,
                            color = TextPrimarySlate,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            ),
                        )
                    } else {
                        Text("—", color = TextSecondarySlate, style = MaterialTheme.typography.labelSmall)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Usado: ",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = CoralDebtText,
                            fontSize = 11.sp,
                        ),
                    )
                    if (debt != null) {
                        MoneyText(
                            money = debt,
                            color = CoralDebtText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                            ),
                        )
                    } else {
                        Text("—", color = CoralDebtText, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            SelectionIndicator(selected = isSelected)
        }
    }
}

@Composable
private fun CompactCardVisual(
    network: CardNetwork,
    modifier: Modifier = Modifier,
) {
    val networkLabel = when (network) {
        CardNetwork.VISA -> "VISA"
        CardNetwork.MASTERCARD -> "MC"
        CardNetwork.AMEX -> "AMEX"
        CardNetwork.DINERS -> "DINERS"
        CardNetwork.OTHER -> "CARD"
    }

    Box(
        modifier = modifier
            .size(width = 46.dp, height = 30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(CompactCardBackground)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 7.dp, height = 6.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(ChipSimColor),
            )
            Text(
                text = networkLabel,
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

@Composable
private fun AccountBadge(
    account: Account,
    modifier: Modifier = Modifier,
) {
    val (containerColor, iconTint, icon) = when {
        account.type == AccountType.CASH -> Triple(
            Color(0xFFDCFCE7),
            Color(0xFF16A34A),
            Icons.Default.Payments,
        )
        account.preset == AccountPreset.BCP -> Triple(
            Color(0xFF002A8F),
            Color.White,
            Icons.Default.AccountBalance,
        )
        account.preset == AccountPreset.BBVA -> Triple(
            Color(0xFF004481),
            Color.White,
            Icons.Default.AccountBalance,
        )
        account.preset == AccountPreset.INTERBANK -> Triple(
            Color(0xFF009940),
            Color.White,
            Icons.Default.AccountBalance,
        )
        account.preset == AccountPreset.SCOTIABANK -> Triple(
            Color(0xFFED1C24),
            Color.White,
            Icons.Default.AccountBalance,
        )
        account.preset == AccountPreset.YAPE -> Triple(
            Color(0xFF742284),
            Color.White,
            Icons.Default.Wallet,
        )
        account.preset == AccountPreset.PLIN -> Triple(
            Color(0xFF00D1D2),
            Color.White,
            Icons.Default.Wallet,
        )
        account.type == AccountType.DIGITAL_WALLET -> Triple(
            Color(0xFFEDE9FE),
            Color(0xFF7C3AED),
            Icons.Default.Wallet,
        )
        else -> Triple(
            Color(0xFF1E3A8A),
            Color.White,
            Icons.Default.AccountBalance,
        )
    }

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun SelectionIndicator(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Box(
            modifier = modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(AndeanTealPrimary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    } else {
        Box(
            modifier = modifier
                .size(22.dp)
                .border(BorderStroke(1.5.dp, DragHandleSlate), CircleShape),
        )
    }
}

private fun accountTypeLabel(type: AccountType): String = when (type) {
    AccountType.SAVINGS -> "Ahorros"
    AccountType.BANK -> "Cuenta bancaria"
    AccountType.DIGITAL_WALLET -> "Billetera digital"
    AccountType.CASH -> "Efectivo"
    AccountType.CREDIT_LIABILITY -> "Pasivo de tarjeta"
}

private fun formatAccountSubtitle(account: Account): String {
    if (account.type == AccountType.CASH) {
        return "Billetera física · ${account.currency.name}"
    }
    val institution = account.preset?.defaultName
    val typeName = accountTypeLabel(account.type)
    val currency = account.currency.name
    return if (institution != null && institution != "Otro") {
        "$institution · $typeName $currency"
    } else {
        "$typeName · $currency"
    }
}

private fun formatCardSubtitle(card: CreditCard): String {
    return "Crédito · •••• ${card.lastFourDigits}"
}
