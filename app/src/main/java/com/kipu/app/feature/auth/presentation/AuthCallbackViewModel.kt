package com.kipu.app.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.feature.auth.data.RecoverySessionInstaller
import com.kipu.app.navigation.AUTH_LOGIN_ROUTE
import com.kipu.app.navigation.AUTH_RESET_PASSWORD_ROUTE
import com.kipu.app.navigation.DeepLinkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthCallbackState(val handledCallback: Boolean = false, val targetRoute: String? = null)

/** Provider code exchange survives Activity recreation; raw callback URLs are never saved. */
@HiltViewModel
class AuthCallbackViewModel @Inject constructor(private val installer: RecoverySessionInstaller) : ViewModel() {
    private val _state = MutableStateFlow(AuthCallbackState())
    val state = _state.asStateFlow()
    private var installation: Job? = null
    private var activeLink: DeepLinkResult? = null
    private val pendingLinks = ArrayDeque<DeepLinkResult>()

    fun handle(link: DeepLinkResult?, restoration: Job?) {
        if (link !is DeepLinkResult.ResetPassword && link !is DeepLinkResult.ConfirmEmail) return
        if (link == activeLink || link in pendingLinks) return
        pendingLinks.addLast(link)
        if (installation?.isActive == true) return
        _state.value = AuthCallbackState(handledCallback = true)
        installation = viewModelScope.launch {
            restoration?.cancelAndJoin()
            while (pendingLinks.isNotEmpty()) {
                val next = pendingLinks.removeFirst()
                activeLink = next
                val route = if (next is DeepLinkResult.ResetPassword) {
                    installer.install(next.callbackUrl)
                    AUTH_RESET_PASSWORD_ROUTE
                } else AUTH_LOGIN_ROUTE
                _state.value = AuthCallbackState(handledCallback = true, targetRoute = route)
                activeLink = null
            }
        }
    }

    fun consumeNavigation() {
        _state.value = _state.value.copy(targetRoute = null)
    }
}
