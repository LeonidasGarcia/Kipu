package com.kipu.app.feature.accounts.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(account: AccountEntity)

    @Update
    suspend fun update(account: AccountEntity)

    @Query("SELECT * FROM accounts WHERE user_id = :userId AND id = :id")
    suspend fun getById(userId: String, id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE user_id = :userId AND id = :id")
    fun observeById(userId: String, id: String): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE user_id = :userId ORDER BY is_archived ASC, created_at DESC")
    fun observeAll(userId: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE user_id = :userId AND is_archived = 0 ORDER BY created_at DESC")
    fun observeActive(userId: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE user_id = :userId AND is_archived = 0 AND type NOT IN ('CASH', 'GOALS_VIRTUAL', 'CREDIT_LIABILITY')")
    suspend fun getActiveComputable(userId: String): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE user_id = :userId AND is_archived = 1 ORDER BY created_at DESC")
    fun observeArchived(userId: String): Flow<List<AccountEntity>>

    @Query("SELECT COUNT(*) FROM accounts WHERE user_id = :userId AND is_archived = 0 AND type NOT IN ('CASH', 'GOALS_VIRTUAL', 'CREDIT_LIABILITY')")
    suspend fun countActiveComputableAccounts(userId: String): Int

    @Query("SELECT COUNT(*) FROM accounts WHERE user_id = :userId AND creation_operation_id = :creationOperationId")
    suspend fun countByCreationOperationId(userId: String, creationOperationId: String): Int

    @Query("UPDATE accounts SET alias = :alias, preset_id = :presetId, color = :color, icon = :icon, updated_at = :nowMicros WHERE user_id = :userId AND id = :id")
    suspend fun updateAppearance(userId: String, id: String, alias: String, presetId: String?, color: String?, icon: String?, nowMicros: Long)

    @Query("UPDATE accounts SET is_archived = :isArchived, updated_at = :nowMicros WHERE user_id = :userId AND id = :id")
    suspend fun setArchived(userId: String, id: String, isArchived: Boolean, nowMicros: Long)

    @Query("UPDATE accounts SET remote_revision = :revision, updated_at = :nowMicros WHERE user_id = :userId AND id = :id")
    suspend fun updateRemoteRevision(userId: String, id: String, revision: Long, nowMicros: Long): Int

    @Query("DELETE FROM accounts WHERE user_id = :userId AND id = :id")
    suspend fun delete(userId: String, id: String)
}
