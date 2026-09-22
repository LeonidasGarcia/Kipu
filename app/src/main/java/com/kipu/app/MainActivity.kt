package com.kipu.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.kipu.app.core.security.LocalAuthenticatorGateway
import com.kipu.app.core.security.LocalLockCoordinator
import com.kipu.app.core.security.LockScreenOverlay
import com.kipu.app.core.security.model.LocalLockState
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.auth.domain.AuthRepository
import com.kipu.app.feature.auth.domain.model.AuthResult
import com.kipu.app.feature.settings.data.local.ProfilePreferencesDao
import com.kipu.app.navigation.AUTH_LOGIN_ROUTE
import com.kipu.app.navigation.AUTH_RESET_PASSWORD_ROUTE
import com.kipu.app.navigation.AuthDeepLinkHandler
import com.kipu.app.navigation.BIOMETRIC_ROUTE
import com.kipu.app.navigation.DeepLinkResult
import com.kipu.app.navigation.PLAN_SELECTION_ROUTE
import com.kipu.app.navigation.PROFILE_SETTINGS_ROUTE
import com.kipu.app.navigation.authDestinations
import com.kipu.app.navigation.planSelectionDestination
import com.kipu.app.navigation.settingsDestinations
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.theme.KipuTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
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

    private var pendingDeepLink by mutableStateOf<DeepLinkResult?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(localLockCoordinator)
        enableEdgeToEdge()

        pendingDeepLink = authDeepLinkHandler.handleDeepLink(intent?.data)

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

            LaunchedEffect(Unit) {
                val restoreResult = authRepository.restoreSession()
                if (restoreResult.getOrNull() is AuthResult.Success && pendingDeepLink == null) {
                    navController.navigate(PROFILE_SETTINGS_ROUTE) {
                        popUpTo(AUTH_LOGIN_ROUTE) { inclusive = true }
                    }
                }
            }

            LaunchedEffect(pendingDeepLink) {
                when (val link = pendingDeepLink) {
                    is DeepLinkResult.ResetPassword -> {
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
                CompositionLocalProvider(LocalBalanceMasked provides isMasked) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
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

                            if (lockState == LocalLockState.LOCKED) {
                                LockScreenOverlay(
                                    gateway = localAuthenticatorGateway,
                                    onUnlockSuccess = { localLockCoordinator.unlock() },
                                    onSignOutRequested = {
                                        scope.launch {
                                            authRepository.signOut(explicit = true)
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
        pendingDeepLink = authDeepLinkHandler.handleDeepLink(intent.data)
    }
}