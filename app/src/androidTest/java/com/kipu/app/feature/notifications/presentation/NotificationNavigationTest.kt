package com.kipu.app.feature.notifications.presentation

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.navigation.ACCOUNTS_DASHBOARD_ROUTE
import com.kipu.app.navigation.NOTIFICATION_CENTER_ROUTE
import com.kipu.app.navigation.navigateToNotifications
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NotificationNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `dashboard bell action opens financial PNOT route and not import inbox P19`() {
        val navController = TestNavHostController(ApplicationProvider.getApplicationContext()).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
        }
        compose.setContent {
            NavHost(navController, startDestination = ACCOUNTS_DASHBOARD_ROUTE) {
                composable(ACCOUNTS_DASHBOARD_ROUTE) {
                    TextButton(onClick = navController::navigateToNotifications) { Text("Avisos") }
                }
                composable(NOTIFICATION_CENTER_ROUTE) { Text("PNOT") }
                composable("imports/review") { Text("P19") }
            }
        }

        compose.onNodeWithText("Avisos").performClick()
        compose.onNodeWithText("PNOT").assertIsDisplayed()
        compose.onAllNodesWithText("P19").assertCountEquals(0)
        assertEquals(NOTIFICATION_CENTER_ROUTE, navController.currentDestination?.route)
    }
}
