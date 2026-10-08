package com.kipu.app.feature.categories.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "merchant_alias_rules",
    primaryKeys = ["user_id", "id"],
    indices = [
        Index(value = ["user_id", "normalized_pattern", "deleted_at"]),
        Index(value = ["user_id", "merchant_id"]),
    ],
)
data class MerchantAliasRuleEntity(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "normalized_pattern") val normalizedPattern: String,
    @ColumnInfo(name = "merchant_id") val merchantId: String,
    @ColumnInfo(name = "remote_revision") val remoteRevision: Long = 1L,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "sync_state") val syncState: String = "PENDING",
    @ColumnInfo(name = "sync_error") val syncError: String? = null,
)

@Entity(
    tableName = "merchant_category_preferences",
    primaryKeys = ["user_id", "merchant_id"],
    indices = [
        Index(value = ["user_id", "category_id"]),
        Index(value = ["user_id", "deleted_at"]),
    ],
)
data class MerchantCategoryPreferenceEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "merchant_id") val merchantId: String,
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "remote_revision") val remoteRevision: Long = 1L,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "sync_state") val syncState: String = "PENDING",
    @ColumnInfo(name = "sync_error") val syncError: String? = null,
)

@Dao
interface MerchantRulesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAliasRule(rule: MerchantAliasRuleEntity)

    @Update
    suspend fun updateAliasRule(rule: MerchantAliasRuleEntity)

    @Query("SELECT * FROM merchant_alias_rules WHERE user_id = :userId AND id = :ruleId")
    suspend fun getAliasRule(userId: String, ruleId: String): MerchantAliasRuleEntity?

    @Query("SELECT * FROM merchant_alias_rules WHERE user_id = :userId ORDER BY updated_at DESC")
    fun observeAliasRules(userId: String): Flow<List<MerchantAliasRuleEntity>>

    @Query("UPDATE merchant_alias_rules SET sync_state = :state, sync_error = :error, remote_revision = COALESCE(:revision, remote_revision) WHERE user_id = :userId AND id = :ruleId")
    suspend fun setAliasSyncState(userId: String, ruleId: String, state: String, revision: Long?, error: String?): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreference(preference: MerchantCategoryPreferenceEntity)

    @Query("SELECT * FROM merchant_category_preferences WHERE user_id = :userId AND merchant_id = :merchantId")
    suspend fun getPreference(userId: String, merchantId: String): MerchantCategoryPreferenceEntity?

    @Query("SELECT * FROM merchant_category_preferences WHERE user_id = :userId")
    fun observePreferences(userId: String): Flow<List<MerchantCategoryPreferenceEntity>>

    @Query("UPDATE merchant_category_preferences SET sync_state = :state, sync_error = :error, remote_revision = COALESCE(:revision, remote_revision) WHERE user_id = :userId AND merchant_id = :merchantId")
    suspend fun setPreferenceSyncState(userId: String, merchantId: String, state: String, revision: Long?, error: String?): Int
}
