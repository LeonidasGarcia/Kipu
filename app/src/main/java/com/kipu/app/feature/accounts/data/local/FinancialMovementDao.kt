package com.kipu.app.feature.accounts.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FinancialMovementDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(movement: FinancialMovementEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(movements: List<FinancialMovementEntity>)

    @Query("SELECT * FROM financial_movements WHERE user_id = :userId AND id = :id")
    suspend fun getById(userId: String, id: String): FinancialMovementEntity?

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
        SELECT COALESCE(SUM(amount_minor_units), 0) FROM financial_movements 
        WHERE user_id = :userId AND account_id = :accountId AND status = 'POSTED'
    """)
    fun observeAccountBalance(userId: String, accountId: String): Flow<Long>

    @Query("""
        SELECT COALESCE(SUM(amount_minor_units), 0) FROM financial_movements 
        WHERE user_id = :userId AND account_id = :accountId AND status = 'POSTED'
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

    @Query("SELECT COUNT(*) FROM financial_movements WHERE user_id = :userId AND account_id = :accountId")
    suspend fun countByAccount(userId: String, accountId: String): Int

    @Query("SELECT COUNT(*) FROM financial_movements WHERE user_id = :userId AND card_id = :cardId")
    suspend fun countByCard(userId: String, cardId: String): Int
}
