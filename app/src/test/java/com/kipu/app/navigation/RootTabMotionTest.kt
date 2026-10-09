package com.kipu.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootTabMotionTest {
    @Test
    fun debtToMovementsUsesTheMovementsIndexAndDirection() {
        assertEquals(1, RootTabMotion.indexOf(ACCOUNTS_DASHBOARD_ROUTE, selectedDashboardTab = 1))
        assertTrue(RootTabMotion.isRootTransition(DEBT_LIST_ROUTE, ACCOUNTS_DASHBOARD_ROUTE, selectedDashboardTab = 1))
        assertFalse(RootTabMotion.isForward(DEBT_LIST_ROUTE, ACCOUNTS_DASHBOARD_ROUTE, selectedDashboardTab = 1))
    }

    @Test
    fun movementsToDebtUsesAdjacentRootTabDirection() {
        assertTrue(RootTabMotion.isRootTransition(ACCOUNTS_DASHBOARD_ROUTE, DEBT_LIST_ROUTE, selectedDashboardTab = 1))
        assertTrue(RootTabMotion.isForward(ACCOUNTS_DASHBOARD_ROUTE, DEBT_LIST_ROUTE, selectedDashboardTab = 1))
    }

    @Test
    fun debtToMoneyUsesMoneyIndexAndDirection() {
        assertEquals(0, RootTabMotion.indexOf(ACCOUNTS_DASHBOARD_ROUTE, selectedDashboardTab = 0))
        assertFalse(RootTabMotion.isForward(DEBT_LIST_ROUTE, ACCOUNTS_DASHBOARD_ROUTE, selectedDashboardTab = 0))
    }

    @Test
    fun retainedDebtTabTakesPrecedenceOverTheAccountsRoute() {
        assertEquals(
            2,
            RootTabMotion.indexOf(ACCOUNTS_DASHBOARD_ROUTE, selectedDashboardTab = 2),
        )
    }

    @Test
    fun returningFromDebtRouteUsesTheNewDashboardSelection() {
        listOf(0, 1).forEach { destinationTab ->
            assertTrue(
                "Debt route should animate to dashboard tab $destinationTab",
                RootTabMotion.isRootTransition(
                    initialRoute = DEBT_LIST_ROUTE,
                    targetRoute = ACCOUNTS_DASHBOARD_ROUTE,
                    selectedDashboardTab = destinationTab,
                ),
            )
            assertFalse(
                "Moving from debt to tab $destinationTab is backward",
                RootTabMotion.isForward(
                    initialRoute = DEBT_LIST_ROUTE,
                    targetRoute = ACCOUNTS_DASHBOARD_ROUTE,
                    selectedDashboardTab = destinationTab,
                ),
            )
        }
    }
}
