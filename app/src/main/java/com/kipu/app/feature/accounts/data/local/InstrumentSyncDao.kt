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
        SELECT * FROM instrument_sync_outbox 
        WHERE user_id = :userId 
          AND state IN ('PENDING', 'ERROR')
          AND (next_attempt_at IS NULL OR next_attempt_at <= :nowMicros)
        ORDER BY created_at ASC
    """)
    suspend fun getPendingCommands(userId: String, nowMicros: Long): List<InstrumentSyncOutboxEntity>

    @Query("""
        SELECT * FROM instrument_sync_outbox 
        WHERE user_id = :userId AND aggregate_type = :aggregateType AND aggregate_id = :aggregateId
        ORDER BY created_at DESC LIMIT 1
    """)
    suspend fun getLatestForAggregate(userId: String, aggregateType: String, aggregateId: String): InstrumentSyncOutboxEntity?

    @Query("""
        UPDATE instrument_sync_outbox 
        SET state = :newState, attempt_count = attempt_count + 1, next_attempt_at = :nextAttemptAt, error_code = :errorCode, updated_at = :nowMicros
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

    @Query("DELETE FROM instrument_sync_outbox WHERE user_id = :userId AND operation_id = :operationId")
    suspend fun delete(userId: String, operationId: String)

    @Query("SELECT COUNT(*) FROM instrument_sync_outbox WHERE user_id = :userId AND state IN ('PENDING', 'IN_FLIGHT', 'ERROR')")
    fun observePendingCount(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM instrument_sync_outbox WHERE user_id = :userId AND state IN ('PENDING', 'IN_FLIGHT', 'ERROR')")
    suspend fun getPendingCount(userId: String): Int
}
