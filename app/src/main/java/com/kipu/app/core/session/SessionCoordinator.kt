package com.kipu.app.core.session

import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

/**
 * Remote session state managed by the authentication provider.
 */
sealed interface RemoteSession {
    data object Absent : RemoteSession
    data class Valid(val userId: String, val expiresAt: Instant) : RemoteSession
    data class RefreshRequired(val userId: String) : RemoteSession
    data class ReauthenticationRequired(val previousUserId: String) : RemoteSession
}

/**
 * Local access state governing whether private UI and Room data may be presented.
 */
sealed interface LocalAccess {
    data object NoOwner : LocalAccess
    data class Protected(val userId: String, val reason: String) : LocalAccess
    data class Available(val userId: String, val remoteState: RemoteSession) : LocalAccess
}

/**
 * Coordinates between remote session, local verified owner, and lock state.
 * Enforces that Room queries only run with the active verified owner and
 * that private UI is shielded when locked or when no owner is active.
 */
interface SessionCoordinator {
    val remoteSession: StateFlow<RemoteSession>
    val localAccess: StateFlow<LocalAccess>
    val currentOwner: LocalOwner?

    /**
     * Sets the active local owner after successful authentication or session restoration.
     */
    suspend fun setActiveOwner(userId: String)

    /**
     * Clears the active owner, setting state to NoOwner.
     * If [explicit] is true, marks that the user explicitly signed out.
     */
    suspend fun clearActiveOwner(explicit: Boolean)

    /**
     * Updates the remote session state. If the remote session subject does not match
     * the active local owner, an OwnerMismatch is triggered and visible state is cleared.
     */
    suspend fun updateRemoteSession(session: RemoteSession)

    /**
     * Updates the local lock state. When locked, [LocalAccess] transitions to [LocalAccess.Protected].
     */
    suspend fun updateLockState(isLocked: Boolean, reason: String = "LOCKED")
}
