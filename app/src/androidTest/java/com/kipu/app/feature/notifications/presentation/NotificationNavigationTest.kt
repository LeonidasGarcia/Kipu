package com.kipu.app.feature.notifications.presentation

import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.navigation.ACCOUNTS_DASHBOARD_ROUTE
import com.kipu.app.navigation.NOTIFICATION_CENTER_ROUTE
import com.kipu.app.navigation.navigateToNotifications
import com.kipu.app.navigation.notificationDestinations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NotificationNavigationTest {
    @Test
    fun productionNotificationDestinationIsRegisteredAndNavigable() =
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val navController = TestNavHostController(ApplicationProvider.getApplicationContext()).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            navController.graph = navController.createGraph(startDestination = ACCOUNTS_DASHBOARD_ROUTE) {
                composable(ACCOUNTS_DASHBOARD_ROUTE) {}
                notificationDestinations(navController)
            }

            assertNotNull(navController.graph.findNode(NOTIFICATION_CENTER_ROUTE))
            navController.navigateToNotifications()

            assertEquals(NOTIFICATION_CENTER_ROUTE, navController.currentDestination?.route)
        }
}
