package com.kipu.app.feature.accounts.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InstrumentSyncDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: InstrumentSyncOutboxEntity)

    @Update
    suspend fun update(entity: InstrumentSyncOutboxEntity)

    @Query("SELECT * FROM instrument_sync_outbox WHERE user_id = :userId AND operation_id = :operationId")
    suspend fun getByOperationId(userId: String, operationId: String): InstrumentSyncOutboxEntity?

    @Query("""
        SELECT command.* FROM instrument_sync_outbox AS command
        WHERE command.user_id = :userId
          AND (
            (command.state IN ('PENDING', 'ERROR') AND (command.next_attempt_at IS NULL OR command.next_attempt_at <= :nowMicros))
            OR (command.state = 'IN_FLIGHT' AND command.lease_expires_at IS NOT NULL AND command.lease_expires_at <= :nowMicros)
          )
          AND (
            command.predecessor_operation_id IS NULL OR EXISTS (
              SELECT 1 FROM instrument_sync_outbox AS predecessor
              WHERE predecessor.user_id = command.user_id
                AND predecessor.operation_id = command.predecessor_operation_id
                AND predecessor.state = 'SYNCED'
            )
          )
        ORDER BY command.created_at ASC, command.operation_id ASC
    """)
    suspend fun getPendingCommands(userId: String, nowMicros: Long): List<InstrumentSyncOutboxEntity>

    @Query("""
        UPDATE instrument_sync_outbox
        SET state = 'IN_FLIGHT', lease_expires_at = :leaseExpiresAt,
            attempt_count = attempt_count + 1, next_attempt_at = NULL, error_code = NULL, updated_at = :nowMicros
        WHERE user_id = :userId AND operation_id = :operationId
          AND (
            (state IN ('PENDING', 'ERROR') AND (next_attempt_at IS NULL OR next_attempt_at <= :nowMicros))
            OR (state = 'IN_FLIGHT' AND lease_expires_at IS NOT NULL AND lease_expires_at <= :nowMicros)
          )
          AND (
            predecessor_operation_id IS NULL OR EXISTS (
              SELECT 1 FROM instrument_sync_outbox AS predecessor
              WHERE predecessor.user_id = instrument_sync_outbox.user_id
                AND predecessor.operation_id = instrument_sync_outbox.predecessor_operation_id
                AND predecessor.state = 'SYNCED'
            )
          )
    """)
    suspend fun claimCommand(userId: String, operationId: String, nowMicros: Long, leaseExpiresAt: Long): Int

    @Query("""
        SELECT * FROM instrument_sync_outbox 
        WHERE user_id = :userId AND aggregate_type = :aggregateType AND aggregate_id = :aggregateId
        ORDER BY created_at DESC LIMIT 1
    """)
    suspend fun getLatestForAggregate(userId: String, aggregateType: String, aggregateId: String): InstrumentSyncOutboxEntity?

    @Query("""
        UPDATE instrument_sync_outbox 
        SET state = :newState, next_attempt_at = :nextAttemptAt, lease_expires_at = NULL,
            error_code = :errorCode, updated_at = :nowMicros
        WHERE user_id = :userId AND operation_id = :operationId
    """)
    suspend fun updateState(
        userId: String,
        operationId: String,
        newState: String,
        nextAttemptAt: Long?,
        errorCode: String?,
        nowMicros: Long,
    )

    @Query("""
        UPDATE instrument_sync_outbox
        SET state = 'SYNCED', next_attempt_at = NULL, lease_expires_at = NULL,
            error_code = NULL, updated_at = :nowMicros
        WHERE user_id = :userId AND operation_id = :operationId AND state = 'IN_FLIGHT'
    """)
    suspend fun markSynced(userId: String, operationId: String, nowMicros: Long): Int

    @Query("DELETE FROM instrument_sync_outbox WHERE user_id = :userId AND operation_id = :operationId")
    suspend fun delete(userId: String, operationId: String)

    @Query("SELECT COUNT(*) FROM instrument_sync_outbox WHERE user_id = :userId AND state IN ('PENDING', 'IN_FLIGHT', 'ERROR')")
    fun observePendingCount(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM instrument_sync_outbox WHERE user_id = :userId AND state IN ('PENDING', 'IN_FLIGHT', 'ERROR')")
    suspend fun getPendingCount(userId: String): Int
}
