package com.kipu.app.feature.debts.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class DebtSettlementActivityRow(
    val eventId: String,
    val principalMinor: Long,
    val interestMinor: Long,
    val occurredAt: Long,
    val isVoided: Boolean,
    val eventType: String,
    val principalDeltaMinor: Long?,
)

data class DebtInstallmentHighlightRow(
    val debtId: String,
    val id: String,
    val installmentNumber: Int,
    val dueDate: String,
    val amountMinor: Long,
    val status: String,
    val revision: Long,
)

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts WHERE user_id = :userId AND deleted_at IS NULL ORDER BY updated_at DESC, id ASC")
    fun observeDebts(userId: String): Flow<List<DebtEntity>>

    @Query("""
        SELECT d.id AS debtId, d.user_id AS userId, d.obligation_type AS obligationType,
               d.counterparty_name AS counterpartyName, d.total_minor AS principalMinor,
               MAX(0, d.total_minor + COALESCE(SUM(
                   CASE
                       WHEN e.id IS NULL OR t.status = 'VOIDED' THEN 0
                       WHEN e.principal_delta_minor IS NOT NULL THEN e.principal_delta_minor
                       WHEN e.event_type = 'PAYMENT' THEN -e.amount_minor
                       ELSE 0
                   END
               ), 0)) AS remainingPrincipalMinor,
               d.currency_code AS currencyCode, d.opened_on AS openedOn, d.opening_mode AS openingMode,
               d.due_date AS dueDate, d.reminder_lead_days AS reminderLeadDays,
               d.notes AS notes, d.status AS status, d.sync_state AS syncState, d.revision AS revision
        FROM debts d
        LEFT JOIN debt_events e ON e.user_id = d.user_id AND e.debt_id = d.id
        LEFT JOIN transactions t ON t.user_id = e.user_id AND t.id = e.transaction_id
        WHERE d.user_id = :userId AND d.deleted_at IS NULL
        GROUP BY d.user_id, d.id
        ORDER BY d.updated_at DESC, d.id ASC
    """)
    fun observeSummaries(userId: String): Flow<List<DebtSummaryRow>>

    @Query("""
        SELECT d.id AS debtId, d.user_id AS userId, d.obligation_type AS obligationType,
               d.counterparty_name AS counterpartyName, d.total_minor AS principalMinor,
               MAX(0, d.total_minor + COALESCE(SUM(
                   CASE
                       WHEN e.id IS NULL OR t.status = 'VOIDED' THEN 0
                       WHEN e.principal_delta_minor IS NOT NULL THEN e.principal_delta_minor
                       WHEN e.event_type = 'PAYMENT' THEN -e.amount_minor
                       ELSE 0
                   END
               ), 0)) AS remainingPrincipalMinor,
               d.currency_code AS currencyCode, d.opened_on AS openedOn, d.opening_mode AS openingMode,
               d.due_date AS dueDate, d.reminder_lead_days AS reminderLeadDays,
               d.notes AS notes, d.status AS status, d.sync_state AS syncState, d.revision AS revision
        FROM debts d
        LEFT JOIN debt_events e ON e.user_id = d.user_id AND e.debt_id = d.id
        LEFT JOIN transactions t ON t.user_id = e.user_id AND t.id = e.transaction_id
        WHERE d.user_id = :userId AND d.id = :debtId AND d.deleted_at IS NULL
        GROUP BY d.user_id, d.id
        LIMIT 1
    """)
    fun observeSummary(userId: String, debtId: String): Flow<DebtSummaryRow?>

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM debt_events
            WHERE user_id = :userId AND debt_id = :debtId
        )
    """)
    fun observeHasFinancialHistory(userId: String, debtId: String): Flow<Boolean>

    @Query("SELECT * FROM debts WHERE user_id = :userId AND id = :debtId AND deleted_at IS NULL LIMIT 1")
    suspend fun getDebt(userId: String, debtId: String): DebtEntity?

    @Query("SELECT * FROM debts WHERE user_id = :userId AND id = :debtId LIMIT 1")
    suspend fun getDebtIncludingDeleted(userId: String, debtId: String): DebtEntity?

    @Query("SELECT * FROM debt_sync_checkpoints WHERE user_id = :userId LIMIT 1")
    suspend fun getSyncCheckpoint(userId: String): DebtSyncCheckpointEntity?

    @Query("SELECT * FROM debt_installments WHERE user_id = :userId AND debt_id = :debtId AND deleted_at IS NULL ORDER BY installment_number ASC")
    fun observeInstallments(userId: String, debtId: String): Flow<List<DebtInstallmentEntity>>

    @Query("""
        SELECT chosen.debt_id AS debtId, chosen.id AS id,
               chosen.installment_number AS installmentNumber, chosen.due_date AS dueDate,
               chosen.amount_minor AS amountMinor, chosen.status AS status, chosen.revision AS revision
        FROM debt_installments AS chosen
        INNER JOIN (
            SELECT base.user_id AS userId, base.debt_id AS debtId,
                   (
                       SELECT candidate.id
                       FROM debt_installments AS candidate
                       WHERE candidate.user_id = base.user_id
                         AND candidate.debt_id = base.debt_id
                         AND candidate.deleted_at IS NULL
                         AND candidate.status IN ('PENDING', 'PARTIAL', 'PAID')
                       ORDER BY
                           CASE WHEN candidate.status IN ('PENDING', 'PARTIAL') THEN 0 ELSE 1 END ASC,
                           CASE WHEN candidate.status IN ('PENDING', 'PARTIAL') THEN candidate.due_date END ASC,
                           CASE WHEN candidate.status = 'PAID' THEN candidate.due_date END DESC,
                           CASE WHEN candidate.status IN ('PENDING', 'PARTIAL') THEN candidate.installment_number END ASC,
                           CASE WHEN candidate.status = 'PAID' THEN candidate.installment_number END DESC,
                           candidate.id ASC
                       LIMIT 1
                   ) AS highlightId
            FROM debt_installments AS base
            WHERE base.user_id = :userId
              AND base.deleted_at IS NULL
              AND base.status IN ('PENDING', 'PARTIAL', 'PAID')
            GROUP BY base.user_id, base.debt_id
        ) AS highlight
          ON chosen.user_id = highlight.userId AND chosen.id = highlight.highlightId
        ORDER BY chosen.due_date ASC, chosen.installment_number ASC
    """)
    fun observeInstallmentHighlights(userId: String): Flow<List<DebtInstallmentHighlightRow>>

    @Query("SELECT * FROM debt_installments WHERE user_id = :userId AND debt_id = :debtId AND deleted_at IS NULL ORDER BY installment_number ASC")
    suspend fun getInstallments(userId: String, debtId: String): List<DebtInstallmentEntity>

    @Query("SELECT * FROM debt_installments WHERE user_id = :userId AND debt_id = :debtId AND id = :installmentId LIMIT 1")
    suspend fun getInstallment(userId: String, debtId: String, installmentId: String): DebtInstallmentEntity?

    @Query("SELECT * FROM debt_installments WHERE user_id = :userId AND id = :installmentId LIMIT 1")
    suspend fun getInstallmentById(userId: String, installmentId: String): DebtInstallmentEntity?

    @Query("""
        SELECT MAX(0, i.amount_minor - COALESCE(SUM(
            CASE WHEN e.id IS NULL OR t.status = 'VOIDED' THEN 0
                 WHEN e.principal_delta_minor IS NOT NULL THEN MAX(0, -e.principal_delta_minor)
                 ELSE e.amount_minor END
        ), 0))
        FROM debt_installments i
        LEFT JOIN debt_events e ON e.user_id = i.user_id AND e.debt_id = i.debt_id
            AND e.installment_id = i.id AND e.event_type = 'PAYMENT'
        LEFT JOIN transactions t ON t.user_id = e.user_id AND t.id = e.transaction_id
        WHERE i.user_id = :userId AND i.debt_id = :debtId AND i.id = :installmentId
    """)
    suspend fun getInstallmentRemaining(userId: String, debtId: String, installmentId: String): Long

    @Query("SELECT * FROM debt_events WHERE user_id = :userId AND debt_id = :debtId ORDER BY occurred_at ASC, id ASC")
    fun observeEvents(userId: String, debtId: String): Flow<List<DebtEventEntity>>

    @Query("""
        SELECT e.id AS eventId, e.amount_minor AS principalMinor,
               COALESCE(i.amount_minor, 0) AS interestMinor, e.occurred_at AS occurredAt,
               CASE WHEN p.status = 'VOIDED' THEN 1 ELSE 0 END AS isVoided,
               e.event_type AS eventType, e.principal_delta_minor AS principalDeltaMinor
        FROM debt_events e
        LEFT JOIN transactions p ON p.user_id = e.user_id AND p.id = e.transaction_id
        LEFT JOIN transactions i ON i.user_id = e.user_id AND i.id = e.interest_transaction_id
        WHERE e.user_id = :userId AND e.debt_id = :debtId
          AND e.event_type IN ('PAYMENT', 'ADJUSTMENT', 'FORGIVENESS')
        ORDER BY e.occurred_at DESC, e.id DESC
    """)
    fun observeSettlementActivities(userId: String, debtId: String): Flow<List<DebtSettlementActivityRow>>

    @Query("SELECT * FROM debt_events WHERE user_id = :userId AND debt_id = :debtId ORDER BY occurred_at ASC, id ASC")
    suspend fun getEvents(userId: String, debtId: String): List<DebtEventEntity>

    @Query("SELECT * FROM debt_events WHERE user_id = :userId AND id = :eventId LIMIT 1")
    suspend fun getEvent(userId: String, eventId: String): DebtEventEntity?

    @Query("""
        SELECT * FROM debt_events
        WHERE user_id = :userId AND (transaction_id = :transactionId OR interest_transaction_id = :transactionId)
        ORDER BY occurred_at DESC, id DESC LIMIT 1
    """)
    suspend fun getSettlementEventForTransaction(userId: String, transactionId: String): DebtEventEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDebt(debt: DebtEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDebt(debt: DebtEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInstallments(installments: List<DebtInstallmentEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvent(event: DebtEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(event: DebtEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInstallment(installment: DebtInstallmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncCheckpoint(checkpoint: DebtSyncCheckpointEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvents(events: List<DebtEventEntity>)

    @Update
    suspend fun updateDebt(debt: DebtEntity): Int

    @Query("UPDATE debts SET sync_state = :syncState WHERE user_id = :userId AND id = :debtId")
    suspend fun updateSyncState(userId: String, debtId: String, syncState: String): Int

    @Query("UPDATE debts SET sync_state = :syncState, revision = MAX(revision, :revision) WHERE user_id = :userId AND id = :debtId")
    suspend fun updateRemoteRevision(userId: String, debtId: String, revision: Long, syncState: String): Int

    @Update
    suspend fun updateInstallments(installments: List<DebtInstallmentEntity>): Int

    @Query("DELETE FROM debt_installments WHERE user_id = :userId AND debt_id = :debtId AND NOT EXISTS (SELECT 1 FROM debt_events e WHERE e.user_id = :userId AND e.debt_id = :debtId AND e.installment_id = debt_installments.id)")
    suspend fun deleteUnreferencedInstallments(userId: String, debtId: String): Int

    @Query("DELETE FROM debts WHERE user_id = :userId AND id = :debtId AND NOT EXISTS (SELECT 1 FROM debt_events e WHERE e.user_id = :userId AND e.debt_id = :debtId)")
    suspend fun deleteDebtIfUnreferenced(userId: String, debtId: String): Int

    @Query("SELECT COUNT(*) FROM debts WHERE user_id = :userId AND status = 'ACTIVE' AND deleted_at IS NULL")
    suspend fun countActiveDebts(userId: String): Int
}
