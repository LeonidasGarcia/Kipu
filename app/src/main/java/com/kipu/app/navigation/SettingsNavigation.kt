package com.kipu.app.navigation

import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.kipu.app.feature.settings.domain.model.PermissionSource
import com.kipu.app.feature.settings.presentation.BiometricSettingsScreen
import com.kipu.app.feature.settings.presentation.BiometricSettingsViewModel
import com.kipu.app.feature.settings.presentation.PermissionsScreen
import com.kipu.app.feature.settings.presentation.PermissionsViewModel
import com.kipu.app.feature.settings.presentation.ProfileSettingsScreen
import com.kipu.app.feature.settings.presentation.SettingsViewModel
import com.kipu.app.feature.categories.presentation.merchantrules.MerchantAliasRulesScreen
import com.kipu.app.feature.categories.presentation.merchantrules.MerchantCategoryPreferenceScreen

const val BIOMETRIC_ROUTE = "onboarding/biometrics"
const val PROFILE_SETTINGS_ROUTE = "settings/profile"
const val PERMISSIONS_ROUTE = "settings/permissions"
const val CATEGORIES_ROUTE = "settings/categories"
const val MERCHANT_ALIAS_RULES_ROUTE = "settings/merchant-alias-rules"
const val MERCHANT_CATEGORY_PREFERENCES_ROUTE = "settings/merchant-category-preferences"

fun NavGraphBuilder.settingsDestinations(
    navController: NavController,
    onSignOut: () -> Unit,
) {
    composable(BIOMETRIC_ROUTE) {
        val viewModel: BiometricSettingsViewModel = hiltViewModel()
        val isOnboarding = navController.previousBackStackEntry?.destination?.route == PLAN_SELECTION_ROUTE
        BiometricSettingsScreen(
            viewModel = viewModel,
            onNavigateBack = { navController.popBackStack() },
            onContinue = if (isOnboarding) {
                {
                    navController.navigate(MOVEMENTS_HISTORY_ROUTE) {
                        popUpTo(PLAN_SELECTION_ROUTE) { inclusive = true }
                    }
                }
            } else {
                null
            },
        )
    }

    composable(PROFILE_SETTINGS_ROUTE) {
        val viewModel: SettingsViewModel = hiltViewModel()
        ProfileSettingsScreen(
            viewModel = viewModel,
            onNavigateBack = { navController.popBackStack() },
            onNavigateToPermissions = { navController.navigate(PERMISSIONS_ROUTE) },
            onNavigateToBiometrics = { navController.navigate(BIOMETRIC_ROUTE) },
            onNavigateToCategories = { navController.navigate(CATEGORIES_ROUTE) },
            onNavigateToMerchantAliases = { navController.navigate(MERCHANT_ALIAS_RULES_ROUTE) },
            onNavigateToMerchantCategoryPreferences = { navController.navigate(MERCHANT_CATEGORY_PREFERENCES_ROUTE) },
            onNavigateToMovements = { navController.navigate(MOVEMENTS_HISTORY_ROUTE) },
            onNavigateToAccounts = { navController.navigate(ACCOUNTS_DASHBOARD_ROUTE) },
            onNavigateToPurchase = { navController.navigate(PLAN_PURCHASE_ROUTE) },
            onSignOut = onSignOut,
        )
    }

    composable(CATEGORIES_ROUTE) {
        val viewModel: com.kipu.app.feature.categories.presentation.categories.CategoriesViewModel = hiltViewModel()
        com.kipu.app.feature.categories.presentation.categories.CategoriesScreen(
            viewModel = viewModel,
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable(MERCHANT_ALIAS_RULES_ROUTE) {
        MerchantAliasRulesScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable(MERCHANT_CATEGORY_PREFERENCES_ROUTE) {
        MerchantCategoryPreferenceScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable(PERMISSIONS_ROUTE) {
        val viewModel: PermissionsViewModel = hiltViewModel()
        val context = LocalContext.current
        PermissionsScreen(
            viewModel = viewModel,
            onNavigateBack = { navController.popBackStack() },
            onOpenSettingsIntent = { source ->
                val intent = when (source) {
                    PermissionSource.OWN_NOTIFICATIONS -> {
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    }
                    PermissionSource.OTHER_APP_NOTIFICATION_CONTENT -> {
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    }
                }
                runCatching { context.startActivity(intent) }
            },
        )
    }
}
