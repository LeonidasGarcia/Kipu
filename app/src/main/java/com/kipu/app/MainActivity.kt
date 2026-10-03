package com.kipu.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kipu.app.core.security.LocalAuthenticatorGateway
import com.kipu.app.core.security.LocalLockCoordinator
import com.kipu.app.core.security.LockScreenOverlay
import com.kipu.app.core.security.model.LocalLockState
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.auth.domain.AuthRepository
import com.kipu.app.feature.auth.domain.model.AuthResult
import com.kipu.app.feature.auth.data.RecoverySessionInstaller
import com.kipu.app.feature.plans.data.local.PlanPreferencesDao
import com.kipu.app.feature.settings.data.local.ProfilePreferencesDao
import com.kipu.app.navigation.ACCOUNT_FORM_ROUTE
import com.kipu.app.navigation.ACCOUNTS_DASHBOARD_ROUTE
import com.kipu.app.navigation.AUTH_LOGIN_ROUTE
import com.kipu.app.navigation.AUTH_RESET_PASSWORD_ROUTE
import com.kipu.app.navigation.AuthDeepLinkHandler
import com.kipu.app.navigation.BIOMETRIC_ROUTE
import com.kipu.app.navigation.CARD_FORM_ROUTE
import com.kipu.app.navigation.DeepLinkResult
import com.kipu.app.navigation.KipuNavigationBar
import com.kipu.app.navigation.MOVEMENTS_HISTORY_PATTERN
import com.kipu.app.navigation.MOVEMENTS_HISTORY_ROUTE
import com.kipu.app.navigation.NOTIFICATION_CENTER_ROUTE
import com.kipu.app.navigation.PLAN_PURCHASE_ROUTE
import com.kipu.app.navigation.PLAN_SELECTION_ROUTE
import com.kipu.app.navigation.PROFILE_SETTINGS_ROUTE
import com.kipu.app.navigation.accountsDestinations
import com.kipu.app.navigation.authDestinations
import com.kipu.app.navigation.movementDestinations
import com.kipu.app.navigation.movementsDestinations
import com.kipu.app.navigation.notificationDestinations
import com.kipu.app.navigation.planSelectionDestination
import com.kipu.app.navigation.settingsDestinations
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.KipuEasingTokens
import com.kipu.app.ui.theme.KipuMotionTokens
import com.kipu.app.ui.theme.KipuTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var sessionCoordinator: SessionCoordinator

    @Inject
    lateinit var localLockCoordinator: LocalLockCoordinator

    @Inject
    lateinit var localAuthenticatorGateway: LocalAuthenticatorGateway

    @Inject
    lateinit var authDeepLinkHandler: AuthDeepLinkHandler

    @Inject
    lateinit var profileDao: ProfilePreferencesDao

    @Inject
    lateinit var planPreferencesDao: PlanPreferencesDao

    @Inject
    lateinit var recoverySessionInstaller: RecoverySessionInstaller

    private var pendingDeepLink by mutableStateOf<DeepLinkResult?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(localLockCoordinator)
        enableEdgeToEdge()

        pendingDeepLink = intent?.data?.let { authDeepLinkHandler.handleDeepLink(it) }

        setContent {
            val scope = rememberCoroutineScope()
            val navController = rememberNavController()

            val accessState by sessionCoordinator.localAccess.collectAsStateWithLifecycle()
            val lockState by localLockCoordinator.lockState.collectAsStateWithLifecycle()

            val currentUserId = when (val access = accessState) {
                is LocalAccess.Available -> access.userId
                is LocalAccess.Protected -> access.userId
                is LocalAccess.NoOwner -> null
            }?.let { runCatching { UUID.fromString(it) }.getOrNull() }

            val profileState = remember(currentUserId) {
                if (currentUserId != null) {
                    profileDao.observeProfile(currentUserId)
                } else {
                    flowOf(null)
                }
            }.collectAsState(initial = null)

            val isDark = when (profileState.value?.themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }
            val isMasked = profileState.value?.hideBalances ?: false

            var lastKnownUserId by remember { mutableStateOf(currentUserId) }
            LaunchedEffect(currentUserId) {
                if (lastKnownUserId != null && lastKnownUserId != currentUserId) {
                    navController.clearBackStack(ACCOUNTS_DASHBOARD_ROUTE)
                    navController.clearBackStack(MOVEMENTS_HISTORY_PATTERN)
                    navController.clearBackStack(MOVEMENTS_HISTORY_ROUTE)
                }
                lastKnownUserId = currentUserId
            }

            LaunchedEffect(Unit) {
                val restored = authRepository.restoreSession().getOrNull() as? AuthResult.Success
                val hasActionableDeepLink = pendingDeepLink is DeepLinkResult.ResetPassword ||
                    pendingDeepLink is DeepLinkResult.ConfirmEmail
                if (restored != null && !hasActionableDeepLink) {
                    postAuthDestination(restored.userId)?.let { destination ->
                        navController.currentBackStackEntryFlow
                            .filter { it.destination.route == AUTH_LOGIN_ROUTE }
                            .first()
                        navController.navigate(destination) {
                            popUpTo(AUTH_LOGIN_ROUTE) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            }

            LaunchedEffect(pendingDeepLink) {
                when (val link = pendingDeepLink) {
                    is DeepLinkResult.ResetPassword -> {
                        recoverySessionInstaller.install(link.callbackUrl)
                        navController.navigate(AUTH_RESET_PASSWORD_ROUTE)
                        pendingDeepLink = null
                    }
                    is DeepLinkResult.ConfirmEmail -> {
                        navController.navigate(AUTH_LOGIN_ROUTE)
                        pendingDeepLink = null
                    }
                    else -> Unit
                }
            }

            KipuTheme(darkTheme = isDark) {
                val background = MaterialTheme.colorScheme.background
                SideEffect {
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    val isLightBackground = background.luminance() > 0.5f
                    insetsController.isAppearanceLightStatusBars = isLightBackground
                    insetsController.isAppearanceLightNavigationBars = isLightBackground
                }

                CompositionLocalProvider(LocalBalanceMasked provides isMasked) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentRoute = navBackStackEntry?.destination?.route

                            val isRootDestination = currentRoute == ACCOUNTS_DASHBOARD_ROUTE ||
                                currentRoute == MOVEMENTS_HISTORY_PATTERN ||
                                currentRoute == MOVEMENTS_HISTORY_ROUTE ||
                                currentRoute?.startsWith("movements/history") == true

                            val isUserAuthenticated = accessState is LocalAccess.Available || accessState is LocalAccess.Protected
                            val shouldShowBottomBar = isRootDestination && isUserAuthenticated

                            Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                                bottomBar = {
                                    if (shouldShowBottomBar) {
                                        KipuNavigationBar(
                                            currentRoute = currentRoute,
                                            onNavigateToDinero = {
                                                if (currentRoute != ACCOUNTS_DASHBOARD_ROUTE) {
                                                    val hasDashboardInStack = runCatching {
                                                        navController.getBackStackEntry(ACCOUNTS_DASHBOARD_ROUTE)
                                                    }.isSuccess

                                                    navController.navigate(ACCOUNTS_DASHBOARD_ROUTE) {
                                                        if (hasDashboardInStack) {
                                                            popUpTo(ACCOUNTS_DASHBOARD_ROUTE) {
                                                                inclusive = false
                                                                saveState = true
                                                            }
                                                        } else {
                                                            val currentEntry = navController.currentBackStackEntry
                                                            if (currentEntry != null) {
                                                                popUpTo(currentEntry.destination.id) {
                                                                    inclusive = true
                                                                    saveState = true
                                                                }
                                                            }
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            },
                                            onNavigateToMovimientos = {
                                                if (currentRoute != MOVEMENTS_HISTORY_PATTERN &&
                                                    currentRoute != MOVEMENTS_HISTORY_ROUTE &&
                                                    currentRoute?.startsWith("movements/history") != true
                                                ) {
                                                    val hasDashboardInStack = runCatching {
                                                        navController.getBackStackEntry(ACCOUNTS_DASHBOARD_ROUTE)
                                                    }.isSuccess

                                                    navController.navigate(MOVEMENTS_HISTORY_ROUTE) {
                                                        if (hasDashboardInStack) {
                                                            popUpTo(ACCOUNTS_DASHBOARD_ROUTE) {
                                                                saveState = true
                                                            }
                                                        } else {
                                                            val currentEntry = navController.currentBackStackEntry
                                                            if (currentEntry != null) {
                                                                popUpTo(currentEntry.destination.id) {
                                                                    inclusive = false
                                                                    saveState = true
                                                                }
                                                            }
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            },
                                        )
                                    }
                                },
                            ) { innerPadding ->
                                val reducedMotion = rememberReducedMotionEnabled()
                                NavHost(
                                    navController = navController,
                                    startDestination = AUTH_LOGIN_ROUTE,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding)
                                        .consumeWindowInsets(innerPadding),
                                    enterTransition = {
                                        if (reducedMotion) {
                                            EnterTransition.None
                                        } else {
                                            val targetRoute = targetState.destination.route
                                            if (isDetailRoute(targetRoute)) {
                                                slideIntoContainer(
                                                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                                    animationSpec = tween(KipuMotionTokens.NavEnterMillis, easing = KipuEasingTokens.Decelerate),
                                                ) + fadeIn(
                                                    animationSpec = tween(KipuMotionTokens.NavEnterMillis, easing = KipuEasingTokens.Decelerate),
                                                )
                                            } else {
                                                fadeIn(
                                                    animationSpec = tween(KipuMotionTokens.NavEnterMillis, easing = KipuEasingTokens.Decelerate),
                                                )
                                            }
                                        }
                                    },
                                    exitTransition = {
                                        if (reducedMotion) {
                                            ExitTransition.None
                                        } else {
                                            fadeOut(
                                                animationSpec = tween(KipuMotionTokens.NavExitMillis, easing = KipuEasingTokens.Accelerate),
                                            )
                                        }
                                    },
                                    popEnterTransition = {
                                        if (reducedMotion) {
                                            EnterTransition.None
                                        } else {
                                            fadeIn(
                                                animationSpec = tween(KipuMotionTokens.NavEnterMillis, easing = KipuEasingTokens.Decelerate),
                                            )
                                        }
                                    },
                                    popExitTransition = {
                                        if (reducedMotion) {
                                            ExitTransition.None
                                        } else {
                                            val initialRoute = initialState.destination.route
                                            if (isDetailRoute(initialRoute)) {
                                                slideOutOfContainer(
                                                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                                                    animationSpec = tween(KipuMotionTokens.NavExitMillis, easing = KipuEasingTokens.Accelerate),
                                                ) + fadeOut(
                                                    animationSpec = tween(KipuMotionTokens.NavExitMillis, easing = KipuEasingTokens.Accelerate),
                                                )
                                            } else {
                                                fadeOut(
                                                    animationSpec = tween(KipuMotionTokens.NavExitMillis, easing = KipuEasingTokens.Accelerate),
                                                )
                                            }
                                        }
                                    },
                                ) {
                                    authDestinations(
                                        navController = navController,
                                        onAuthenticated = { userId ->
                                            scope.launch {
                                                postAuthDestination(userId)?.let { destination ->
                                                    navController.navigate(destination) {
                                                        popUpTo(AUTH_LOGIN_ROUTE) { inclusive = true }
                                                        launchSingleTop = true
                                                    }
                                                }
                                            }
                                        },
                                    )
                                    planSelectionDestination(
                                        navController = navController,
                                        onConfirmed = {
                                            navController.navigate(BIOMETRIC_ROUTE)
                                        },
                                    )
                                    settingsDestinations(
                                        navController = navController,
                                        onSignOut = {
                                            navController.clearBackStack(ACCOUNTS_DASHBOARD_ROUTE)
                                            navController.clearBackStack(MOVEMENTS_HISTORY_PATTERN)
                                            navController.clearBackStack(MOVEMENTS_HISTORY_ROUTE)
                                            navController.navigate(AUTH_LOGIN_ROUTE) {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        },
                                    )
                                    accountsDestinations(
                                        navController = navController,
                                    )
                                    movementDestinations(
                                        navController = navController,
                                    )
                                    movementsDestinations(
                                        navController = navController,
                                    )
                                    notificationDestinations(
                                        navController = navController,
                                    )
                                }
                            }

                            if (lockState == LocalLockState.LOCKED) {
                                LockScreenOverlay(
                                    gateway = localAuthenticatorGateway,
                                    onUnlockSuccess = { localLockCoordinator.unlock() },
                                    onSignOutRequested = {
                                        scope.launch {
                                            authRepository.signOut(explicit = true)
                                            navController.clearBackStack(ACCOUNTS_DASHBOARD_ROUTE)
                                            navController.clearBackStack(MOVEMENTS_HISTORY_PATTERN)
                                            navController.clearBackStack(MOVEMENTS_HISTORY_ROUTE)
                                            navController.navigate(AUTH_LOGIN_ROUTE) {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink = intent.data?.let { authDeepLinkHandler.handleDeepLink(it) }
    }

    private suspend fun postAuthDestination(rawUserId: String): String? {
        val userId = runCatching { UUID.fromString(rawUserId) }.getOrNull() ?: return null
        return if (planPreferencesDao.findPreference(userId) == null) {
            PLAN_SELECTION_ROUTE
        } else {
            ACCOUNTS_DASHBOARD_ROUTE
        }
    }
}

private fun isDetailRoute(route: String?): Boolean {
    if (route == null) return false
    return route.contains("/{") ||
        route == ACCOUNT_FORM_ROUTE ||
        route == CARD_FORM_ROUTE ||
        route == NOTIFICATION_CENTER_ROUTE ||
        route.startsWith("settings/") ||
        route == PLAN_PURCHASE_ROUTE
}
