package com.kipu.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.kipu.app.feature.auth.presentation.AuthCallbackViewModel
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
import com.kipu.app.feature.plans.data.local.PlanPreferencesDao
import com.kipu.app.feature.settings.data.local.ProfilePreferencesDao
import com.kipu.app.navigation.ACCOUNT_FORM_ROUTE
import com.kipu.app.navigation.ACCOUNTS_DASHBOARD_ROUTE
import com.kipu.app.navigation.AUTH_LOGIN_ROUTE
import com.kipu.app.navigation.AUTH_START_ROUTE
import com.kipu.app.navigation.AUTH_INTRO_ROUTE
import com.kipu.app.feature.auth.data.OnboardingPreferences
import com.kipu.app.navigation.AUTH_RESET_PASSWORD_ROUTE
import com.kipu.app.navigation.AuthDeepLinkHandler
import com.kipu.app.navigation.BIOMETRIC_ROUTE
import com.kipu.app.navigation.CARD_FORM_ROUTE
import com.kipu.app.navigation.DeepLinkResult
import com.kipu.app.navigation.DEBT_LIST_ROUTE
import com.kipu.app.navigation.KipuNavigationBar
import com.kipu.app.navigation.MOVEMENTS_HISTORY_PATTERN
import com.kipu.app.navigation.MOVEMENTS_HISTORY_ROUTE
import com.kipu.app.navigation.NOTIFICATION_CENTER_ROUTE
import com.kipu.app.navigation.PLAN_PURCHASE_ROUTE
import com.kipu.app.navigation.PLAN_SELECTION_ROUTE
import com.kipu.app.navigation.PROFILE_SETTINGS_ROUTE
import com.kipu.app.navigation.accountsDestinations
import com.kipu.app.navigation.debtDestinations
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext

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
    lateinit var onboardingPreferences: OnboardingPreferences

    private var pendingDeepLink by mutableStateOf<DeepLinkResult?>(null)
    private var pendingDebtReminderId by mutableStateOf<String?>(null)
    private var restorationJob: Job? = null
    private val authCallbackViewModel: AuthCallbackViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(localLockCoordinator)
        enableEdgeToEdge()

        pendingDeepLink = intent?.data?.let { authDeepLinkHandler.handleDeepLink(it) }
        pendingDebtReminderId = debtReminderId(intent)

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
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }
            val isMasked = profileState.value?.hideBalances ?: false

            val movementsSelected = rememberSaveable(currentUserId) { mutableStateOf(false) }

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
                restorationJob = currentCoroutineContext()[Job]
                val hasActionableDeepLink = pendingDeepLink is DeepLinkResult.ResetPassword ||
                    pendingDeepLink is DeepLinkResult.ConfirmEmail ||
                    authCallbackViewModel.state.value.handledCallback
                // Recovery installs its own temporary session; never race it with restoration.
                if (hasActionableDeepLink) return@LaunchedEffect
                val restored = authRepository.restoreSession().getOrNull() as? AuthResult.Success
                if (!hasActionableDeepLink) {
                    val destination = if (restored != null) {
                        pendingDebtReminderId?.let { "debts/detail/$it" }
                            ?: postAuthDestination(restored.userId) ?: AUTH_LOGIN_ROUTE
                    } else if (onboardingPreferences.checkpoint.first().completed) {
                        AUTH_LOGIN_ROUTE
                    } else AUTH_INTRO_ROUTE
                    navController.currentBackStackEntryFlow
                        .filter { it.destination.route == AUTH_START_ROUTE }.first()
                    navController.navigate(destination) {
                        popUpTo(AUTH_START_ROUTE) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            LaunchedEffect(currentUserId, pendingDebtReminderId) {
                val debtId = pendingDebtReminderId
                if (currentUserId != null && debtId != null) {
                    navController.navigate("debts/detail/$debtId") {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                    pendingDebtReminderId = null
                }
            }

            LaunchedEffect(pendingDeepLink) {
                authCallbackViewModel.handle(pendingDeepLink, restorationJob)
            }
            val authCallbackState by authCallbackViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(authCallbackState.targetRoute) {
                authCallbackState.targetRoute?.let { destination ->
                    navController.navigate(destination) { popUpTo(0) { inclusive = true } }
                    intent?.data = null
                    pendingDeepLink = null
                    authCallbackViewModel.consumeNavigation()
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
                                currentRoute == DEBT_LIST_ROUTE ||
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
                                            currentRoute = if (currentRoute == ACCOUNTS_DASHBOARD_ROUTE && movementsSelected.value) MOVEMENTS_HISTORY_ROUTE else currentRoute,
                                            onNavigateToDinero = {
                                                movementsSelected.value = false
                                                if (currentRoute != ACCOUNTS_DASHBOARD_ROUTE) {
                                                    navController.navigate(ACCOUNTS_DASHBOARD_ROUTE) {
                                                        popUpTo(ACCOUNTS_DASHBOARD_ROUTE)
                                                        launchSingleTop = true
                                                    }
                                                }
                                            },
                                            onNavigateToMovimientos = {
                                                if (currentRoute == ACCOUNTS_DASHBOARD_ROUTE) {
                                                    movementsSelected.value = true
                                                } else if (currentRoute != MOVEMENTS_HISTORY_ROUTE) {
                                                    navController.navigate(MOVEMENTS_HISTORY_ROUTE) { launchSingleTop = true }
                                                }
                                            },
                                            onNavigateToDeudas = {
                                                movementsSelected.value = false
                                                if (currentRoute != DEBT_LIST_ROUTE) {
                                                    navController.navigate(DEBT_LIST_ROUTE) { launchSingleTop = true }
                                                }
                                            },
                                            onRegisterClick = {
                                                if (accessState is LocalAccess.Available) {
                                                    navController.currentBackStackEntry?.savedStateHandle?.set("open_register_movement", true)
                                                }
                                            },
                                        )
                                    }
                                },
                            ) { innerPadding ->
                                val reducedMotion = rememberReducedMotionEnabled()
                                val navigationOffset = with(LocalDensity.current) { 24.dp.roundToPx() }
                                NavHost(
                                    navController = navController,
                                    startDestination = AUTH_START_ROUTE,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding)
                                        .consumeWindowInsets(innerPadding),
                                    enterTransition = {
                                        val initialRoute = initialState.destination.route
                                        val targetRoute = targetState.destination.route
                                        if (reducedMotion || (isTopLevelRoute(initialRoute) && isTopLevelRoute(targetRoute))) {
                                            EnterTransition.None
                                        } else if (isDetailRoute(targetRoute)) {
                                            slideInHorizontally(
                                                initialOffsetX = { navigationOffset },
                                                animationSpec = tween(KipuMotionTokens.NavEnterMillis, easing = KipuEasingTokens.Decelerate),
                                            ) + fadeIn(
                                                animationSpec = tween(KipuMotionTokens.NavEnterMillis, easing = KipuEasingTokens.Decelerate),
                                            )
                                        } else {
                                            fadeIn(
                                                animationSpec = tween(KipuMotionTokens.TopLevelMillis, easing = KipuEasingTokens.Decelerate),
                                            )
                                        }
                                    },
                                    exitTransition = {
                                        val initialRoute = initialState.destination.route
                                        val targetRoute = targetState.destination.route
                                        if (reducedMotion || (isTopLevelRoute(initialRoute) && isTopLevelRoute(targetRoute))) {
                                            ExitTransition.None
                                        } else {
                                            fadeOut(
                                                animationSpec = tween(KipuMotionTokens.NavExitMillis, easing = KipuEasingTokens.Accelerate),
                                            )
                                        }
                                    },
                                    popEnterTransition = {
                                        val initialRoute = initialState.destination.route
                                        val targetRoute = targetState.destination.route
                                        if (reducedMotion || (isTopLevelRoute(initialRoute) && isTopLevelRoute(targetRoute))) {
                                            EnterTransition.None
                                        } else if (isDetailRoute(targetRoute)) {
                                            fadeIn(
                                                animationSpec = tween(KipuMotionTokens.NavEnterMillis, easing = KipuEasingTokens.Decelerate),
                                            )
                                        } else {
                                            fadeIn(
                                                animationSpec = tween(KipuMotionTokens.TopLevelMillis, easing = KipuEasingTokens.Decelerate),
                                            )
                                        }
                                    },
                                    popExitTransition = {
                                        val initialRoute = initialState.destination.route
                                        val targetRoute = targetState.destination.route
                                        if (reducedMotion || (isTopLevelRoute(initialRoute) && isTopLevelRoute(targetRoute))) {
                                            ExitTransition.None
                                        } else if (isDetailRoute(initialRoute)) {
                                            slideOutHorizontally(
                                                targetOffsetX = { navigationOffset },
                                                animationSpec = tween(KipuMotionTokens.NavExitMillis, easing = KipuEasingTokens.Accelerate),
                                            ) + fadeOut(
                                                animationSpec = tween(KipuMotionTokens.NavExitMillis, easing = KipuEasingTokens.Accelerate),
                                            )
                                        } else {
                                            fadeOut(
                                                animationSpec = tween(KipuMotionTokens.NavExitMillis, easing = KipuEasingTokens.Accelerate),
                                            )
                                        }
                                    },
                                ) {
                                    authDestinations(
                                        navController = navController,
                                        onAuthenticated = { userId ->
                                            scope.launch {
                                                (pendingDebtReminderId?.let { "debts/detail/$it" } ?: postAuthDestination(userId))?.let { destination ->
                                                    navController.navigate(destination) {
                                                        popUpTo(0) { inclusive = true }
                                                        launchSingleTop = true
                                                    }
                                                    pendingDebtReminderId = null
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
                                        movementsSelected = movementsSelected,
                                        onSelectMoney = { movementsSelected.value = false },
                                    )
                                    movementDestinations(
                                        navController = navController,
                                    )
                                    movementsDestinations(
                                        navController = navController,
                                    )
                                    debtDestinations(navController = navController)
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
        pendingDebtReminderId = debtReminderId(intent)
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

private fun debtReminderId(intent: Intent?): String? {
    val uri = intent?.data ?: return null
    if (uri.scheme != "kipu" || uri.host != "debts") return null
    val segments = uri.pathSegments
    if (segments.size != 2 || segments.firstOrNull() != "detail") return null
    return runCatching { UUID.fromString(segments[1]).toString() }.getOrNull()
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

private fun isTopLevelRoute(route: String?): Boolean {
    if (route == null) return false
    return route == ACCOUNTS_DASHBOARD_ROUTE ||
        route == DEBT_LIST_ROUTE ||
        route == MOVEMENTS_HISTORY_PATTERN ||
        route == MOVEMENTS_HISTORY_ROUTE ||
        route.startsWith("movements/history")
}
