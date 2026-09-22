package com.kipu.app.core.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.kipu.app.core.security.model.LocalLockState
import com.kipu.app.core.security.model.LockReason
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.settings.data.local.DeviceAccountSettingsDao
import com.kipu.app.feature.settings.data.local.DeviceAccountSettingsEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class LocalLockCoordinator(
    private val dao: DeviceAccountSettingsDao,
    private val sessionCoordinator: SessionCoordinator,
    private val monotonicClock: () -> Long,
    private val scope: CoroutineScope,
) : DefaultLifecycleObserver {

    @Inject
    constructor(
        dao: DeviceAccountSettingsDao,
        sessionCoordinator: SessionCoordinator,
    ) : this(
        dao = dao,
        sessionCoordinator = sessionCoordinator,
        monotonicClock = { android.os.SystemClock.elapsedRealtime() },
        scope = CoroutineScope(Dispatchers.Main.immediate),
    )

    companion object {
        const val BACKGROUND_TIMEOUT_MS = 60_000L // 60 seconds monotonic
    }

    private val _lockState = MutableStateFlow(LocalLockState.DISABLED)
    val lockState: StateFlow<LocalLockState> = _lockState.asStateFlow()

    private val _lastLockReason = MutableStateFlow<LockReason?>(null)
    val lastLockReason: StateFlow<LockReason?> = _lastLockReason.asStateFlow()

    private var backgroundStartedMs: Long? = null
    private var isEnabledForCurrentOwner: Boolean = false

    init {
        scope.launch {
            sessionCoordinator.localAccess.collect { access ->
                val userIdStr = when (access) {
                    is LocalAccess.Available -> access.userId
                    is LocalAccess.Protected -> access.userId
                    is LocalAccess.NoOwner -> null
                }
                val userId = userIdStr?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (userId != null) {
                    val settings = dao.findSettings(userId)
                    isEnabledForCurrentOwner = settings?.localUnlockEnabled == true
                    if (isEnabledForCurrentOwner) {
                        if (_lockState.value != LocalLockState.UNLOCKED) {
                            _lockState.value = LocalLockState.LOCKED
                            if (_lastLockReason.value == null) {
                                _lastLockReason.value = LockReason.APP_START
                            }
                        }
                    } else {
                        _lockState.value = LocalLockState.DISABLED
                    }
                } else {
                    isEnabledForCurrentOwner = false
                    _lockState.value = LocalLockState.DISABLED
                }
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        onAppBackgrounded()
    }

    override fun onStart(owner: LifecycleOwner) {
        onAppForegrounded()
    }

    suspend fun setLocalUnlockEnabled(userId: UUID, enabled: Boolean, authenticators: Int = 0) {
        isEnabledForCurrentOwner = enabled
        dao.putSettings(
            DeviceAccountSettingsEntity(
                userId = userId,
                localUnlockEnabled = enabled,
                unlockAuthenticators = authenticators,
            )
        )
        if (enabled) {
            _lockState.value = LocalLockState.UNLOCKED
        } else {
            _lockState.value = LocalLockState.DISABLED
            _lastLockReason.value = null
        }
    }

    fun onAppBackgrounded() {
        if (isEnabledForCurrentOwner && _lockState.value == LocalLockState.UNLOCKED) {
            backgroundStartedMs = monotonicClock()
        }
    }

    fun onAppForegrounded() {
        val startedMs = backgroundStartedMs
        backgroundStartedMs = null

        if (isEnabledForCurrentOwner && _lockState.value == LocalLockState.UNLOCKED && startedMs != null) {
            val elapsed = monotonicClock() - startedMs
            if (elapsed >= BACKGROUND_TIMEOUT_MS) {
                _lockState.value = LocalLockState.LOCKED
                _lastLockReason.value = LockReason.BACKGROUND_TIMEOUT
            }
        }
    }

    fun unlock() {
        _lockState.value = LocalLockState.UNLOCKED
    }

    fun lock(reason: LockReason) {
        if (isEnabledForCurrentOwner) {
            _lockState.value = LocalLockState.LOCKED
            _lastLockReason.value = reason
        }
    }
}
