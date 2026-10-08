package com.kipu.app.feature.debts.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface DebtOutboxDao {
    @Query("SELECT * FROM debt_command_outbox WHERE user_id = :userId AND operation_id = :operationId LIMIT 1")
    suspend fun get(userId: String, operationId: String): DebtOutboxEntity?

    @Query("SELECT * FROM debt_command_outbox WHERE user_id = :userId AND (state IN ('PENDING','RETRY') OR (state='IN_FLIGHT' AND lease_until <= :now)) AND (next_attempt_at IS NULL OR next_attempt_at <= :now) AND (lease_until IS NULL OR lease_until <= :now) ORDER BY created_at ASC, operation_id ASC LIMIT :limit")
    suspend fun getClaimable(userId: String, now: Long, limit: Int): List<DebtOutboxEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(command: DebtOutboxEntity)

    @Query("UPDATE debt_command_outbox SET state = :state, lease_until = :leaseUntil, updated_at = :now WHERE user_id = :userId AND operation_id = :operationId AND state IN ('PENDING','RETRY','IN_FLIGHT')")
    suspend fun setLease(userId: String, operationId: String, state: String, leaseUntil: Long?, now: Long): Int

    @Query("UPDATE debt_command_outbox SET state = :state, attempt_count = attempt_count + :incrementAttempt, next_attempt_at = :nextAttemptAt, lease_until = NULL, last_error_code = :errorCode, updated_at = :now WHERE user_id = :userId AND operation_id = :operationId")
    suspend fun updateResult(userId: String, operationId: String, state: String, incrementAttempt: Int, nextAttemptAt: Long?, errorCode: String?, now: Long): Int

    @Transaction
    suspend fun claimPending(userId: String, now: Long, limit: Int = 10): List<DebtOutboxEntity> {
        require(limit > 0) { "Debt command claim limit must be positive" }
        val leaseUntil = Math.addExact(now, 60_000L)
        return getClaimable(userId, now, limit).mapNotNull { candidate ->
            if (setLease(userId, candidate.operationId, "IN_FLIGHT", leaseUntil, now) != 1) {
                null
            } else {
                get(userId, candidate.operationId)?.copy(
                    state = "IN_FLIGHT",
                    leaseUntil = leaseUntil,
                    updatedAt = now,
                )
            }
        }
    }

    @Transaction
    suspend fun completeClaimed(
        command: DebtOutboxEntity,
        state: String,
        nextAttemptAt: Long?,
        errorCode: String?,
        now: Long,
    ): Boolean {
        val stored = get(command.userId, command.operationId) ?: return false
        if (stored.state != "IN_FLIGHT" || stored.leaseUntil != command.leaseUntil || stored.updatedAt != command.updatedAt) {
            return false
        }
        return updateResult(
            userId = command.userId,
            operationId = command.operationId,
            state = state,
            incrementAttempt = if (state == "RETRY" || state == "FAILED_PERMANENT") 1 else 0,
            nextAttemptAt = nextAttemptAt,
            errorCode = errorCode,
            now = now,
        ) == 1
    }

    @Query("SELECT COUNT(*) FROM debt_command_outbox WHERE user_id = :userId AND state IN ('PENDING','IN_FLIGHT','RETRY')")
    suspend fun countPending(userId: String): Int
}
