package com.kipu.app.feature.movements.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MovementDao {

    @Query(
        """
        SELECT * FROM transactions 
        WHERE user_id = :userId 
        ORDER BY occurred_at DESC, created_at DESC
        """
    )
    fun observeTransactions(userId: String): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions 
        WHERE user_id = :userId 
        ORDER BY occurred_at DESC, created_at DESC 
        LIMIT :limit
        """
    )
    fun observeRecentTransactions(userId: String, limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE user_id = :userId AND id = :transactionId")
    suspend fun getTransactionById(userId: String, transactionId: String): TransactionEntity?

    @Query(
        """
        SELECT * FROM transactions 
        WHERE user_id = :userId 
          AND source_account_id = :sourceAccountId 
          AND type = :type 
          AND amount_minor = :amountMinor 
          AND currency_code = :currencyCode 
          AND occurred_at BETWEEN (:occurredAt - :windowMillis) AND (:occurredAt + :windowMillis)
          AND status = 'ACTIVE'
        """
    )
    suspend fun findSimilarTransactions(
        userId: String,
        sourceAccountId: String,
        type: String,
        amountMinor: Long,
        currencyCode: String,
        occurredAt: Long,
        windowMillis: Long,
    ): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Query("UPDATE transactions SET sync_status = :syncStatus, updated_at = :updatedAt WHERE user_id = :userId AND id = :transactionId")
    suspend fun updateTransactionSyncStatus(userId: String, transactionId: String, syncStatus: String, updatedAt: Long = System.currentTimeMillis())

    // Ledger Entries
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLedgerEntries(entries: List<LedgerEntryEntity>)

    @Query("SELECT * FROM ledger_entries WHERE user_id = :userId AND transaction_id = :transactionId")
    suspend fun getLedgerEntriesForTransaction(userId: String, transactionId: String): List<LedgerEntryEntity>

    @Query("SELECT SUM(signed_amount_minor) FROM ledger_entries WHERE user_id = :userId AND account_id = :accountId")
    suspend fun calculateLedgerSumForAccount(userId: String, accountId: String): Long?

    // Local Command Receipts
    @Query("SELECT * FROM local_command_receipts WHERE user_id = :userId AND idempotency_key = :idempotencyKey")
    suspend fun getReceipt(userId: String, idempotencyKey: String): LocalCommandReceiptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateReceipt(receipt: LocalCommandReceiptEntity)

    // Movement Outbox
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutbox(outbox: MovementOutboxEntity)

    @Query(
        """
        SELECT * FROM movement_outbox 
        WHERE user_id = :userId 
          AND state IN ('PENDING', 'RETRY') 
          AND (next_attempt_at IS NULL OR next_attempt_at <= :now) 
          AND (lease_until IS NULL OR lease_until <= :now) 
        ORDER BY created_at ASC 
        LIMIT :limit
        """
    )
    suspend fun claimPendingOutbox(userId: String, now: Long, limit: Int = 10): List<MovementOutboxEntity>

    @Query("UPDATE movement_outbox SET state = :state, lease_until = :leaseUntil, updated_at = :updatedAt WHERE user_id = :userId AND id = :id")
    suspend fun setOutboxLease(userId: String, id: String, state: String, leaseUntil: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE movement_outbox SET state = :state, attempt_count = attempt_count + 1, next_attempt_at = :nextAttemptAt, last_error_code = :errorCode, lease_until = NULL, updated_at = :updatedAt WHERE user_id = :userId AND id = :id")
    suspend fun updateOutboxResult(userId: String, id: String, state: String, nextAttemptAt: Long?, errorCode: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM movement_outbox WHERE user_id = :userId AND id = :id")
    suspend fun getOutboxById(userId: String, id: String): MovementOutboxEntity?

    // Balance Projections
    @Query("SELECT * FROM balance_projections WHERE user_id = :userId AND account_id = :accountId")
    suspend fun getBalanceProjection(userId: String, accountId: String): BalanceProjectionEntity?

    @Query("SELECT balance_minor FROM balance_projections WHERE user_id = :userId AND account_id = :accountId")
    fun observeBalanceProjection(userId: String, accountId: String): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBalanceProjection(projection: BalanceProjectionEntity)
}
