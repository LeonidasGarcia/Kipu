package com.kipu.app.feature.accounts.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(card: CardEntity)

    @Update
    suspend fun update(card: CardEntity)

    @Query("SELECT * FROM cards WHERE user_id = :userId AND id = :id")
    suspend fun getById(userId: String, id: String): CardEntity?

    @Query("SELECT * FROM cards WHERE user_id = :userId AND id = :id")
    fun observeById(userId: String, id: String): Flow<CardEntity?>

    @Query("SELECT * FROM cards WHERE user_id = :userId ORDER BY is_archived ASC, created_at DESC")
    fun observeAll(userId: String): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE user_id = :userId AND is_archived = 0 ORDER BY created_at DESC")
    fun observeActive(userId: String): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE user_id = :userId AND is_archived = 0")
    suspend fun getActive(userId: String): List<CardEntity>

    @Query("SELECT * FROM cards WHERE user_id = :userId AND account_id = :accountId AND is_archived = 0")
    fun observeActiveDebitCardsForAccount(userId: String, accountId: String): Flow<List<CardEntity>>

    @Query("SELECT COUNT(*) FROM cards WHERE user_id = :userId AND is_archived = 0")
    suspend fun countActiveCards(userId: String): Int

    @Query("SELECT * FROM cards WHERE user_id = :userId AND issuer = :issuer AND network = :network AND last_four_digits = :lastFourDigits")
    suspend fun findDuplicates(userId: String, issuer: String, network: String, lastFourDigits: String): List<CardEntity>

    @Query("SELECT COUNT(*) FROM cards WHERE user_id = :userId AND creation_operation_id = :creationOperationId")
    suspend fun countByCreationOperationId(userId: String, creationOperationId: String): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE user_id = :userId AND card_id = :cardId")
    suspend fun countTransactionsForCard(userId: String, cardId: String): Int

    @Query("UPDATE cards SET alias = :alias, preset_id = :presetId, color = :color, icon = :icon, updated_at = :nowMicros WHERE user_id = :userId AND id = :id")
    suspend fun updateAppearance(userId: String, id: String, alias: String?, presetId: String?, color: String?, icon: String?, nowMicros: Long)

    @Query("UPDATE cards SET personal_tea_bps = :teaBps, updated_at = :nowMicros WHERE user_id = :userId AND id = :id AND type = 'CREDIT'")
    suspend fun updatePersonalTea(userId: String, id: String, teaBps: Int?, nowMicros: Long): Int

    @Query("UPDATE cards SET credit_limit_minor_units = :creditLimitMinorUnits, billing_day = :billingDay, due_day = :dueDay, last_four_digits = :lastFourDigits, alias = :alias, updated_at = :nowMicros WHERE user_id = :userId AND id = :id AND type = 'CREDIT'")
    suspend fun updateCreditTerms(
        userId: String,
        id: String,
        creditLimitMinorUnits: Long,
        billingDay: Int,
        dueDay: Int,
        lastFourDigits: String,
        alias: String?,
        nowMicros: Long,
    ): Int

    @Query("UPDATE cards SET remote_revision = :revision, updated_at = :nowMicros WHERE user_id = :userId AND id = :id")
    suspend fun updateRemoteRevision(userId: String, id: String, revision: Long, nowMicros: Long): Int

    @Query("UPDATE cards SET is_archived = :isArchived, updated_at = :nowMicros WHERE user_id = :userId AND id = :id")
    suspend fun setArchived(userId: String, id: String, isArchived: Boolean, nowMicros: Long)

    @Query("DELETE FROM cards WHERE user_id = :userId AND id = :id")
    suspend fun delete(userId: String, id: String)
}
