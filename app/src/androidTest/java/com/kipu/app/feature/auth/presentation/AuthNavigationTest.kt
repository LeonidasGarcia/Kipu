package com.kipu.app.feature.auth.presentation

import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.navigation.*
import org.junit.Assert.*
import org.junit.Test

class AuthNavigationTest {
    @Test fun existingRoutesAndIntroductionAreRegistered() =
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val controller = TestNavHostController(ApplicationProvider.getApplicationContext()).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            controller.graph = controller.createGraph(startDestination = AUTH_START_ROUTE) {
                authDestinations(controller, onAuthenticated = {})
            }
            listOf(AUTH_START_ROUTE, AUTH_INTRO_ROUTE, AUTH_LOGIN_ROUTE,
                AUTH_REGISTER_ROUTE, AUTH_RECOVERY_ROUTE, AUTH_RESET_PASSWORD_ROUTE).forEach {
                assertNotNull(controller.graph.findNode(it))
            }
            controller.navigate(AUTH_LOGIN_ROUTE)
            controller.navigate(AUTH_REGISTER_ROUTE)
            assertEquals(AUTH_REGISTER_ROUTE, controller.currentDestination?.route)
            controller.popBackStack()
            assertEquals(AUTH_LOGIN_ROUTE, controller.currentDestination?.route)
        }

    @Test fun returningToLoginCannotReopenPasswordResetWithBack() =
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val controller = TestNavHostController(ApplicationProvider.getApplicationContext()).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            controller.graph = controller.createGraph(startDestination = AUTH_RESET_PASSWORD_ROUTE) {
                authDestinations(controller, onAuthenticated = {})
            }
            controller.navigateToAuthLogin()
            assertEquals(AUTH_LOGIN_ROUTE, controller.currentDestination?.route)
            assertNull(controller.previousBackStackEntry)
        }
}
