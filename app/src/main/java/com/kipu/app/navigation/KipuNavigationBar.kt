package com.kipu.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
fun KipuNavigationBar(
    currentRoute: String?,
    onNavigateToDinero: () -> Unit,
    onNavigateToMovimientos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDineroSelected = currentRoute == ACCOUNTS_DASHBOARD_ROUTE
    val isMovimientosSelected = currentRoute == MOVEMENTS_HISTORY_PATTERN ||
        currentRoute == MOVEMENTS_HISTORY_ROUTE ||
        currentRoute?.startsWith("movements/history") == true

    NavigationBar(
        modifier = modifier.testTag("kipu_bottom_navigation_bar"),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        NavigationBarItem(
            selected = isDineroSelected,
            onClick = onNavigateToDinero,
            icon = {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                )
            },
            label = { Text("Dinero") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
            modifier = Modifier
                .testTag("nav_item_dinero")
                .semantics { contentDescription = "Dinero" },
        )

        NavigationBarItem(
            selected = isMovimientosSelected,
            onClick = onNavigateToMovimientos,
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                    contentDescription = null,
                )
            },
            label = { Text("Movimientos") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
            modifier = Modifier
                .testTag("nav_item_movimientos")
                .semantics { contentDescription = "Movimientos" },
        )
    }
}
