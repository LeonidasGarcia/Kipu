package com.kipu.app.core.session

import com.kipu.app.feature.plans.data.local.PlanPreferencesDao
import com.kipu.app.feature.settings.data.local.ProfilePreferencesDao
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingChangesRepositoryImpl @Inject constructor(
    private val planPreferencesDao: PlanPreferencesDao,
    private val profilePreferencesDao: ProfilePreferencesDao,
) : PendingChangesRepository {

    override suspend fun count(userId: UUID): Int {
        val plansPending = planPreferencesDao.pendingCount(userId)
        val profilePending = profilePreferencesDao.pendingOutboxCount(userId)
        return plansPending + profilePending
    }

    override suspend fun markWaitingForAuth(userId: UUID) {
        profilePreferencesDao.markOutboxWaitingForAuth(userId)
    }
}
