package com.kipu.app.feature.movements.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity): Int

    @Query("UPDATE transactions SET sync_status = :syncStatus, updated_at = :updatedAt WHERE user_id = :userId AND id = :transactionId")
    suspend fun updateTransactionSyncStatus(userId: String, transactionId: String, syncStatus: String, updatedAt: Long = System.currentTimeMillis())

    @Query("""
        UPDATE transactions SET 
            amount_minor = :amountMinor,
            currency_code = :currencyCode,
            source_account_id = :sourceAccountId,
            destination_account_id = :destinationAccountId,
            category_id = :categoryId,
            merchant_id = :merchantId,
            merchant_provisional_text = :merchantProvisionalText,
            occurred_at = :occurredAt,
            note = :note,
            status = :status,
            revision = :revision,
            sync_status = :syncStatus,
            updated_at = :updatedAt
        WHERE user_id = :userId AND id = :transactionId
    """)
    suspend fun updateTransactionFromRemote(
        userId: String,
        transactionId: String,
        amountMinor: Long,
        currencyCode: String,
        sourceAccountId: String?,
        destinationAccountId: String?,
        categoryId: String?,
        merchantId: String?,
        merchantProvisionalText: String?,
        occurredAt: Long,
        note: String?,
        status: String,
        revision: Long,
        syncStatus: String,
        updatedAt: Long = System.currentTimeMillis(),
    ): Int

    @Query("SELECT * FROM movement_outbox WHERE user_id = :userId AND aggregate_id = :transactionId AND state IN ('PENDING', 'IN_FLIGHT', 'RETRY') ORDER BY created_at ASC")
    suspend fun getPendingOutboxForTransaction(userId: String, transactionId: String): List<MovementOutboxEntity>

    @Query("UPDATE transactions SET status = 'VOIDED', sync_status = 'FAILED_PERMANENT', revision = :revision, current_revision_id = :revisionId, updated_at = :updatedAt WHERE user_id = :userId AND id = :transactionId AND status NOT IN ('VOIDED','FAILED')")
    suspend fun markTransactionVoidedAfterRejection(userId: String, transactionId: String, revision: Long, revisionId: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE local_command_receipts SET status = 'REJECTED', updated_at = :updatedAt WHERE user_id = :userId AND transaction_id = :transactionId")
    suspend fun markReceiptsRejectedForTransaction(userId: String, transactionId: String, updatedAt: Long = System.currentTimeMillis()): Int

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

    @Query("UPDATE local_command_receipts SET status = :status, updated_at = :updatedAt WHERE user_id = :userId AND idempotency_key = :idempotencyKey")
    suspend fun updateCommandReceiptStatus(userId: String, idempotencyKey: String, status: String, updatedAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateReceipt(receipt: LocalCommandReceiptEntity)

    // Movement Outbox
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutbox(outbox: MovementOutboxEntity)

    @Query(
        """
        SELECT * FROM movement_outbox 
        WHERE user_id = :userId 
          AND (state IN ('PENDING', 'RETRY') OR
               (state = 'IN_FLIGHT' AND lease_until IS NOT NULL AND lease_until <= :now))
          AND (next_attempt_at IS NULL OR next_attempt_at <= :now) 
          AND (lease_until IS NULL OR lease_until <= :now) 
        ORDER BY created_at ASC, id ASC
        LIMIT :limit
        """
    )
    suspend fun findClaimableOutbox(userId: String, now: Long, limit: Int): List<MovementOutboxEntity>

    @Transaction
    suspend fun claimPendingOutbox(userId: String, now: Long, limit: Int = 10): List<MovementOutboxEntity> {
        require(limit > 0) { "Claim limit must be positive" }
        val leaseUntil = Math.addExact(now, 60_000L)
        val candidates = findClaimableOutbox(userId, now, limit)
        candidates.forEach { command ->
            setOutboxLease(userId, command.id, "IN_FLIGHT", leaseUntil, now)
        }
        return candidates.map { it.copy(state = "IN_FLIGHT", leaseUntil = leaseUntil, updatedAt = now) }
    }

    @Query("UPDATE movement_outbox SET state = :state, lease_until = :leaseUntil, updated_at = :updatedAt WHERE user_id = :userId AND id = :id")
    suspend fun setOutboxLease(userId: String, id: String, state: String, leaseUntil: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE movement_outbox SET state = :state, attempt_count = attempt_count + 1, next_attempt_at = :nextAttemptAt, last_error_code = :errorCode, lease_until = NULL, updated_at = :updatedAt WHERE user_id = :userId AND id = :id")
    suspend fun updateOutboxResult(userId: String, id: String, state: String, nextAttemptAt: Long?, errorCode: String?, updatedAt: Long = System.currentTimeMillis())

    // A response from an expired/replaced lease must not overwrite the new worker.
    @Transaction
    suspend fun completeClaimedOutbox(
        command: MovementOutboxEntity,
        state: String,
        nextAttemptAt: Long?,
        errorCode: String?,
        transactionSyncStatus: String? = null,
    ): Boolean {
        val stored = getOutboxById(command.userId, command.id) ?: return false
        if (stored.state != "IN_FLIGHT" || stored.leaseUntil != command.leaseUntil ||
            stored.updatedAt != command.updatedAt) return false
        updateOutboxResult(command.userId, command.id, state, nextAttemptAt, errorCode)
        if (transactionSyncStatus != null) {
            updateTransactionSyncStatus(command.userId, command.aggregateId, transactionSyncStatus)
        }
        return true
    }

    @Query("SELECT * FROM movement_outbox WHERE user_id = :userId AND id = :id")
    suspend fun getOutboxById(userId: String, id: String): MovementOutboxEntity?

    // Balance Projections
    @Query("SELECT * FROM balance_projections WHERE user_id = :userId AND account_id = :accountId")
    suspend fun getBalanceProjection(userId: String, accountId: String): BalanceProjectionEntity?

    @Query("SELECT balance_minor FROM balance_projections WHERE user_id = :userId AND account_id = :accountId")
    fun observeBalanceProjection(userId: String, accountId: String): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBalanceProjection(projection: BalanceProjectionEntity)

    @Query("SELECT * FROM movement_sync_checkpoints WHERE user_id = :userId")
    suspend fun getSyncCheckpoint(userId: String): MovementSyncCheckpointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSyncCheckpoint(checkpoint: MovementSyncCheckpointEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPulledTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPulledLedgerEntries(entries: List<LedgerEntryEntity>)

    // Snapshot and identity evidence is insert-only; no replacement under an existing identity.
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRevision(revision: TransactionRevisionEntity)

    @Query("SELECT * FROM transaction_revisions WHERE user_id=:userId AND revision_id=:revisionId")
    suspend fun getRevision(userId: String, revisionId: String): TransactionRevisionEntity?

    @Query("SELECT * FROM transaction_revisions WHERE user_id=:userId AND transaction_id=:transactionId ORDER BY local_revision,revision_id")
    suspend fun getRevisions(userId: String, transactionId: String): List<TransactionRevisionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOfficialRevision(assignment: MovementOfficialRevisionEntity)

    @Query("SELECT * FROM movement_official_revisions WHERE user_id=:userId AND transaction_id=:transactionId AND official_revision=:revision")
    suspend fun getOfficialRevision(userId: String, transactionId: String, revision: Long): MovementOfficialRevisionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLedgerEffect(effect: MovementLedgerEffectEntity)

    @Query("SELECT * FROM movement_ledger_effects WHERE user_id=:userId AND transaction_id=:transactionId")
    suspend fun getLedgerEffects(userId: String, transactionId: String): List<MovementLedgerEffectEntity>

    @Query("SELECT * FROM movement_ledger_effects WHERE user_id=:userId AND command_id=:commandId AND effect_ordinal=:ordinal")
    suspend fun getLedgerEffect(userId: String, commandId: String, ordinal: Int): MovementLedgerEffectEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLedgerAlias(alias: MovementLedgerAliasEntity)

    @Query("SELECT * FROM movement_ledger_aliases WHERE user_id=:userId AND physical_entry_id=:physicalEntryId")
    suspend fun getLedgerAlias(userId: String, physicalEntryId: String): MovementLedgerAliasEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertConflictProposal(proposal: MovementConflictProposalEntity)

    @Query("SELECT * FROM movement_conflict_proposals WHERE user_id=:userId AND transaction_id=:transactionId AND resolution='UNRESOLVED'")
    suspend fun getConflictProposals(userId: String, transactionId: String): List<MovementConflictProposalEntity>

    @Query("SELECT * FROM movement_conflict_proposals WHERE user_id=:userId AND proposal_id=:proposalId")
    suspend fun getConflictProposalById(userId: String, proposalId: String): MovementConflictProposalEntity?

    @Query("UPDATE movement_conflict_proposals SET resolution=:resolution, updated_at=:updatedAt WHERE user_id=:userId AND proposal_id=:proposalId")
    suspend fun updateConflictProposalResolution(userId: String, proposalId: String, resolution: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query(
        """
        SELECT * FROM transactions 
        WHERE user_id = :userId 
          AND type = 'EXPENSE' 
          AND status IN ('ACTIVE', 'CONFIRMED', 'REVISED') 
          AND currency_code = :currencyCode 
          AND occurred_at >= :fromInclusive 
          AND occurred_at < :toExclusive
        """
    )
    suspend fun getExpensesInPeriod(
        userId: String,
        currencyCode: String,
        fromInclusive: Long,
        toExclusive: Long,
    ): List<TransactionEntity>

    @RawQuery
    suspend fun queryTransactions(query: SupportSQLiteQuery): List<TransactionEntity>

    @RawQuery
    suspend fun queryHistoryCounts(query: SupportSQLiteQuery): List<MovementHistoryCountRow>

    @RawQuery
    suspend fun queryHistoryNetFlows(query: SupportSQLiteQuery): List<MovementHistoryNetFlowRow>

    @Query(
        """
        SELECT source_account_id FROM transactions
        WHERE user_id = :userId AND status = 'ACTIVE' AND source_account_id IS NOT NULL
        GROUP BY source_account_id
        ORDER BY COUNT(*) DESC, source_account_id ASC
        LIMIT 1
        """
    )
    suspend fun getMostUsedSourceAccountId(userId: String): String?
}
