package com.kipu.app.core.session

import com.kipu.app.core.logging.SecureLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class SessionCoordinatorImpl @Inject constructor() : SessionCoordinator {

    private val _remoteSession = MutableStateFlow<RemoteSession>(RemoteSession.Absent)
    override val remoteSession: StateFlow<RemoteSession> = _remoteSession.asStateFlow()

    private val _localAccess = MutableStateFlow<LocalAccess>(LocalAccess.NoOwner)
    override val localAccess: StateFlow<LocalAccess> = _localAccess.asStateFlow()

    private var _currentOwner: LocalOwner? = null
    override val currentOwner: LocalOwner? get() = _currentOwner

    private var isLocked: Boolean = false

    override suspend fun setActiveOwner(userId: String) {
        _currentOwner = LocalOwner(userId, explicitlySignedOut = false)
        if (isLocked) {
            _localAccess.value = LocalAccess.Protected(userId, "APP_LOCKED")
        } else {
            _localAccess.value = LocalAccess.Available(userId, _remoteSession.value)
        }
        SecureLog.i("SessionCoordinator", "Active owner set to verified user")
    }

    override suspend fun clearActiveOwner(explicit: Boolean) {
        _currentOwner = _currentOwner?.copy(explicitlySignedOut = explicit)
        _localAccess.value = LocalAccess.NoOwner
        SecureLog.i("SessionCoordinator", "Active owner cleared (explicit=$explicit)")
    }

    override suspend fun updateRemoteSession(session: RemoteSession) {
        _remoteSession.value = session
        val owner = _currentOwner
        when (session) {
            is RemoteSession.Valid -> {
                if (owner != null && owner.verifiedUserId != session.userId) {
                    SecureLog.w("SessionCoordinator", "Owner mismatch between remote session and local owner! Clearing state.")
                    clearActiveOwner(false)
                    return
                }
            }
            is RemoteSession.Absent, is RemoteSession.RefreshRequired, is RemoteSession.ReauthenticationRequired -> {
                // Keep owner for offline manual operations, but update local access state
            }
        }
        if (owner != null && !owner.explicitlySignedOut) {
            if (!isLocked) {
                _localAccess.value = LocalAccess.Available(owner.verifiedUserId, session)
            }
        }
    }

    override suspend fun updateLockState(isLocked: Boolean, reason: String) {
        this.isLocked = isLocked
        val owner = _currentOwner
        if (owner != null && !owner.explicitlySignedOut) {
            _localAccess.value = if (isLocked) {
                LocalAccess.Protected(owner.verifiedUserId, reason)
            } else {
                LocalAccess.Available(owner.verifiedUserId, _remoteSession.value)
            }
        }
    }
}
