package com.kipu.app.feature.settings.domain

import com.kipu.app.feature.settings.domain.model.ProfilePreferenceDelta
import com.kipu.app.feature.settings.domain.model.UserProfile
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface ProfilePreferencesRepository {
    fun observeProfile(userId: UUID): Flow<UserProfile?>
    suspend fun getProfile(userId: UUID): UserProfile?
    suspend fun updatePreferences(userId: UUID, delta: ProfilePreferenceDelta): Result<UserProfile>
    suspend fun setHideBalances(userId: UUID, hideBalances: Boolean): Result<UserProfile>
    suspend fun syncPendingPreferences(userId: UUID): Result<Unit>
    suspend fun refreshProfile(userId: UUID): Result<UserProfile>
}
