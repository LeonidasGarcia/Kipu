package com.kipu.app.feature.plans.data.local

import androidx.room.*
import com.kipu.app.feature.plans.domain.model.PlanSelection
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlanPreferencesDao {
    @Query("SELECT * FROM plan_preferences WHERE user_id=:userId") abstract suspend fun findPreference(userId: UUID): PlanPreferencesEntity?
    @Query("SELECT * FROM plan_preferences WHERE user_id=:userId") abstract fun observePreference(userId: UUID): Flow<PlanPreferencesEntity?>
    @Query("SELECT * FROM plan_selection_sync_state WHERE user_id=:userId") abstract suspend fun findSyncState(userId: UUID): PlanSelectionSyncStateEntity?
    @Query("SELECT * FROM sync_outbox WHERE operation_id=:operationId") abstract suspend fun findOutbox(operationId: UUID): SyncOutboxEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) protected abstract suspend fun putPreference(value: PlanPreferencesEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) protected abstract suspend fun putState(value: PlanSelectionSyncStateEntity)
    @Insert protected abstract suspend fun insertOutbox(value: SyncOutboxEntity)
    @Update protected abstract suspend fun updateOutbox(value: SyncOutboxEntity)

    @Transaction
    open suspend fun confirmSelection(userId: UUID, selection: PlanSelection, selectedAt: Instant, operationId: UUID): SyncOutboxEntity {
        findOutbox(operationId)?.let { existing ->
            if (existing.userId == userId && existing.selection == selection && existing.selectedAt == selectedAt) return existing
            throw LocalPlanSelectionConflict()
        }
        val state = findSyncState(userId) ?: PlanSelectionSyncStateEntity(userId, 0, 0, selectedAt)
        val revision = maxOf(state.lastIssuedRevision, state.acceptedRevision) + 1
        val outbox = SyncOutboxEntity(operationId = operationId, userId = userId, selectionRevision = revision, selection = selection, selectedAt = selectedAt, createdAt = selectedAt, updatedAt = selectedAt)
        putPreference(PlanPreferencesEntity(userId, selection, selectedAt, selectedAt))
        insertOutbox(outbox)
        putState(state.copy(lastIssuedRevision = revision, updatedAt = selectedAt))
        return outbox
    }

    @Query("SELECT * FROM sync_outbox WHERE user_id=:userId AND status='PENDING' AND (next_attempt_at IS NULL OR next_attempt_at<=:now) ORDER BY created_at LIMIT 1") protected abstract suspend fun nextPending(userId: UUID, now: Instant): SyncOutboxEntity?
    @Transaction open suspend fun acquireNextPending(userId: UUID, now: Instant, leaseUntil: Instant): SyncOutboxEntity? {
        val row = nextPending(userId, now) ?: return null
        val leased = row.copy(status = OutboxStatus.IN_FLIGHT, attemptCount = row.attemptCount + 1, leaseUntil = leaseUntil, updatedAt = now)
        updateOutbox(leased)
        return leased
    }
    @Query("UPDATE sync_outbox SET status='PENDING', lease_until=NULL WHERE status='IN_FLIGHT' AND lease_until<=:now") abstract suspend fun recoverExpiredLeases(now: Instant): Int
    @Query("SELECT COUNT(*) FROM sync_outbox WHERE user_id=:userId AND status IN ('PENDING','IN_FLIGHT','WAITING_FOR_AUTH')") abstract suspend fun pendingCount(userId: UUID): Int
    @Query("SELECT COUNT(*) FROM sync_outbox WHERE user_id=:userId AND status='PENDING'") abstract suspend fun retryablePendingCount(userId: UUID): Int
    @Query("UPDATE sync_outbox SET status='PENDING', next_attempt_at=:now, lease_until=NULL WHERE operation_id=:id") abstract suspend fun makePendingNow(id: UUID, now: Instant)
    @Query("UPDATE sync_outbox SET status='WAITING_FOR_AUTH', lease_until=NULL, updated_at=:now WHERE operation_id=:id") abstract suspend fun waitForAuth(id: UUID, now: Instant)
    @Query("UPDATE sync_outbox SET status='WAITING_FOR_AUTH', lease_until=NULL, updated_at=:now WHERE user_id=:userId AND status IN ('PENDING','IN_FLIGHT')") abstract suspend fun markAllWaitingForAuth(userId: UUID, now: Instant = Instant.now())
    @Query("UPDATE sync_outbox SET status='TERMINAL_ERROR', last_error_code=:code, lease_until=NULL, updated_at=:now WHERE operation_id=:id") abstract suspend fun terminal(id: UUID, code: String, now: Instant)
    @Query("UPDATE sync_outbox SET status='PENDING', next_attempt_at=:next, lease_until=NULL, last_error_code=:code, updated_at=:now WHERE operation_id=:id") abstract suspend fun retry(id: UUID, code: String?, next: Instant, now: Instant)

    @Transaction
    open suspend fun reconcile(operationId: UUID, result: RemoteSelectionResult, acceptedRevision: Long, currentSelection: PlanSelection, currentSelectedAt: Instant, serverUpdatedAt: Instant) {
        val row = requireNotNull(findOutbox(operationId))
        putPreference(PlanPreferencesEntity(row.userId, currentSelection, currentSelectedAt, serverUpdatedAt))
        val state = findSyncState(row.userId) ?: PlanSelectionSyncStateEntity(row.userId, 0, 0, serverUpdatedAt)
        putState(state.copy(acceptedRevision = maxOf(state.acceptedRevision, acceptedRevision), updatedAt = serverUpdatedAt))
        updateOutbox(row.copy(status = if (result == RemoteSelectionResult.CONFLICT) OutboxStatus.CONFLICT else OutboxStatus.COMPLETED, leaseUntil = null, updatedAt = serverUpdatedAt))
    }
}
