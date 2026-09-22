package com.kipu.app.core.session

import com.kipu.app.feature.accounts.data.local.InstrumentSyncDao
import com.kipu.app.feature.plans.data.local.PlanPreferencesDao
import com.kipu.app.feature.settings.data.local.ProfilePreferencesDao
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingChangesRepositoryImpl @Inject constructor(
    private val planPreferencesDao: PlanPreferencesDao,
    private val profilePreferencesDao: ProfilePreferencesDao,
    private val instrumentSyncDao: InstrumentSyncDao,
    private val customSources: Set<@JvmSuppressWildcards PendingChangesSource> = emptySet(),
) : PendingChangesRepository {

    override suspend fun count(userId: UUID): Int {
        val plansPending = planPreferencesDao.pendingCount(userId)
        val profilePending = profilePreferencesDao.pendingOutboxCount(userId)
        val instrumentPending = instrumentSyncDao.getPendingCount(userId.toString())
        val customPending = customSources.sumOf { it.count(userId) }
        return plansPending + profilePending + instrumentPending + customPending
    }

    override suspend fun markWaitingForAuth(userId: UUID) {
        planPreferencesDao.markAllWaitingForAuth(userId)
        profilePreferencesDao.markOutboxWaitingForAuth(userId)
        customSources.forEach { it.markWaitingForAuth(userId) }
    }
}
