package com.kipu.app.feature.settings.domain.model

import java.time.Instant
import java.util.UUID

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class SyncState {
    SYNCED,
    PENDING,
    CONFLICT,
    WAITING_FOR_AUTH,
    ERROR,
}

data class UserProfile(
    val userId: UUID,
    val displayName: String,
    val currencyCode: String = "PEN",
    val timeZone: String? = null,
    val monthStart: Int = 1,
    val hideBalances: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val remoteRevision: Long = 1L,
    val remoteUpdatedAt: Instant? = null,
    val syncState: SyncState = SyncState.SYNCED,
) {
    init {
        require(monthStart in 1..28) { "monthStart must be between 1 and 28, got $monthStart" }
        require(currencyCode.length == 3) { "currencyCode must be a 3-letter ISO code" }
    }
}

data class ProfilePreferenceDelta(
    val displayName: String? = null,
    val currencyCode: String? = null,
    val timeZone: String? = null,
    val monthStart: Int? = null,
    val hideBalances: Boolean? = null,
    val themeMode: ThemeMode? = null,
) {
    val isEmpty: Boolean
        get() = displayName == null && currencyCode == null && timeZone == null &&
            monthStart == null && hideBalances == null && themeMode == null
}
