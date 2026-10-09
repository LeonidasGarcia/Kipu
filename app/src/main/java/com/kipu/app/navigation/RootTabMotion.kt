package com.kipu.app.navigation

/** Resolves the visible root tab for routes whose content is hosted inside another destination. */
internal object RootTabMotion {
    fun indexOf(route: String?, selectedDashboardTab: Int): Int? = when {
        route == ACCOUNTS_DASHBOARD_ROUTE -> selectedDashboardTab.takeIf { it in 0..2 }
        route == MOVEMENTS_HISTORY_PATTERN || route == MOVEMENTS_HISTORY_ROUTE ||
            route?.startsWith("movements/history") == true -> 1
        route == DEBT_LIST_ROUTE -> 2
        else -> null
    }

    fun isRootTransition(
        initialRoute: String?,
        targetRoute: String?,
        selectedDashboardTab: Int,
    ): Boolean {
        val initialIndex = indexOf(initialRoute, selectedDashboardTab) ?: return false
        val targetIndex = indexOf(targetRoute, selectedDashboardTab) ?: return false
        return initialIndex != targetIndex
    }

    fun isForward(
        initialRoute: String?,
        targetRoute: String?,
        selectedDashboardTab: Int,
    ): Boolean {
        val initialIndex = indexOf(initialRoute, selectedDashboardTab) ?: return true
        val targetIndex = indexOf(targetRoute, selectedDashboardTab) ?: return true
        return targetIndex > initialIndex
    }
}
