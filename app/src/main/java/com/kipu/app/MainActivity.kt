package com.kipu.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.kipu.app.navigation.AUTH_LOGIN_ROUTE
import com.kipu.app.navigation.BIOMETRIC_ROUTE
import com.kipu.app.navigation.PLAN_SELECTION_ROUTE
import com.kipu.app.navigation.authDestinations
import com.kipu.app.navigation.planSelectionDestination
import com.kipu.app.navigation.settingsDestinations
import com.kipu.app.ui.theme.KipuTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KipuTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = AUTH_LOGIN_ROUTE,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        authDestinations(
                            navController = navController,
                            onAuthenticated = { userId ->
                                navController.navigate(PLAN_SELECTION_ROUTE)
                            },
                        )
                        planSelectionDestination(
                            onConfirmed = {
                                navController.navigate(BIOMETRIC_ROUTE)
                            },
                        )
                        settingsDestinations(
                            navController = navController,
                            onSignOut = {
                                navController.navigate(AUTH_LOGIN_ROUTE) {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}