package com.kipu.app.feature.accounts.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kipu.app.feature.movements.data.local.LedgerEntryEntity
import com.kipu.app.feature.movements.data.local.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FinancialMovementDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRaw(movement: FinancialMovementEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLedgerEntry(entry: LedgerEntryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Query("""INSERT OR REPLACE INTO balance_projections
        (user_id, account_id, balance_minor, currency_code, last_transaction_at, updated_at)
        SELECT a.user_id, a.id, COALESCE(SUM(le.signed_amount_minor), 0), a.currency,
            MAX(le.created_at), :now
        FROM accounts a
        LEFT JOIN ledger_entries le ON le.user_id = a.user_id AND le.account_id = a.id
        WHERE a.user_id = :userId AND a.id = :accountId
        GROUP BY a.user_id, a.id, a.currency""")
    suspend fun rebuildAccountProjection(userId: String, accountId: String, now: Long)

    @Transaction
    suspend fun insert(movement: FinancialMovementEntity) {
        insertRaw(movement)
        val accountId = movement.accountId ?: return
        if (movement.status != "POSTED") return
        require(movement.amountMinorUnits in -99_999_999_999_999L..99_999_999_999_999L) {
            "Account movement exceeds supported monetary range"
        }
        val amount = movement.amountMinorUnits
        if (amount == 0L) {
            rebuildAccountProjection(movement.userId, accountId, System.currentTimeMillis())
            return
        }
        val transactionId = "legacy:${movement.id}"
        val createdAtMillis = movement.createdAt / 1_000L
        insertTransaction(
            TransactionEntity(
                id = transactionId,
                userId = movement.userId,
                type = if (amount < 0L) "EXPENSE" else "INCOME",
                amountMinor = kotlin.math.abs(amount),
                currencyCode = movement.currency,
                sourceAccountId = accountId,
                categoryId = movement.categoryId,
                merchantId = movement.merchantId,
                legacyKind = movement.kind,
                occurredAt = movement.effectiveAt / 1_000L,
                syncStatus = "MIGRATED_LOCAL",
                createdAt = createdAtMillis,
                updatedAt = createdAtMillis,
            )
        )
        insertLedgerEntry(
            LedgerEntryEntity(
                id = transactionId,
                userId = movement.userId,
                transactionId = transactionId,
                accountId = accountId,
                role = if (amount < 0L) "SOURCE" else "DESTINATION",
                signedAmountMinor = amount,
                currencyCode = movement.currency,
                createdAt = createdAtMillis,
            )
        )
        rebuildAccountProjection(movement.userId, accountId, System.currentTimeMillis())
    }

    @Transaction
    suspend fun insertAll(movements: List<FinancialMovementEntity>) {
        movements.forEach { insert(it) }
    }

    @Query("SELECT * FROM financial_movements WHERE user_id = :userId AND id = :id")
    suspend fun getById(userId: String, id: String): FinancialMovementEntity?

    @Query("UPDATE financial_movements SET status = :status WHERE user_id = :userId AND operation_id = :operationId")
    suspend fun updateOperationStatus(userId: String, operationId: String, status: String): Int

    @Query("""
        SELECT * FROM financial_movements 
        WHERE user_id = :userId AND account_id = :accountId AND status = 'POSTED'
        ORDER BY effective_at DESC, operation_sequence DESC
    """)
    fun observeByAccount(userId: String, accountId: String): Flow<List<FinancialMovementEntity>>

    @Query("""
        SELECT * FROM financial_movements 
        WHERE user_id = :userId AND card_id = :cardId AND status = 'POSTED'
        ORDER BY effective_at DESC, operation_sequence DESC
    """)
    fun observeByCard(userId: String, cardId: String): Flow<List<FinancialMovementEntity>>

    @Query("""
        SELECT COALESCE((SELECT balance_minor FROM balance_projections
            WHERE user_id = :userId AND account_id = :accountId), 0)
    """)
    fun observeAccountBalance(userId: String, accountId: String): Flow<Long>

    @Query("""
        SELECT COALESCE((SELECT balance_minor FROM balance_projections
            WHERE user_id = :userId AND account_id = :accountId), 0)
    """)
    suspend fun getAccountBalance(userId: String, accountId: String): Long

    @Query("""
        SELECT COALESCE(SUM(amount_minor_units), 0) FROM financial_movements 
        WHERE user_id = :userId AND card_id = :cardId AND status = 'POSTED'
    """)
    fun observeCardDebt(userId: String, cardId: String): Flow<Long>

    @Query("""
        SELECT COALESCE(SUM(amount_minor_units), 0) FROM financial_movements 
        WHERE user_id = :userId AND card_id = :cardId AND status = 'POSTED'
    """)
    suspend fun getCardDebt(userId: String, cardId: String): Long

    @Query("SELECT COUNT(*) FROM ledger_entries WHERE user_id = :userId AND account_id = :accountId")
    suspend fun countByAccount(userId: String, accountId: String): Int

    @Query("SELECT COUNT(*) FROM financial_movements WHERE user_id = :userId AND card_id = :cardId")
    suspend fun countByCard(userId: String, cardId: String): Int
}
