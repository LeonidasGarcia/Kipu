package com.kipu.app.feature.plans.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlanQuotaSelectionDao {
    @Query("SELECT * FROM plan_selections WHERE user_id = :userId AND feature_key = :featureKey")
    abstract suspend fun getSelection(userId: String, featureKey: String): PlanQuotaSelectionEntity?

    @Query("SELECT resource_id FROM plan_selection_items WHERE user_id = :userId AND feature_key = :featureKey ORDER BY resource_id")
    abstract suspend fun getSelectedResourceIds(userId: String, featureKey: String): List<String>

    @Query("SELECT resource_id FROM plan_selection_items WHERE user_id = :userId AND feature_key = :featureKey ORDER BY resource_id")
    abstract fun observeSelectedResourceIds(userId: String, featureKey: String): Flow<List<String>>

    @Query("SELECT * FROM plan_selection_items WHERE user_id = :userId AND feature_key = :featureKey ORDER BY resource_id")
    abstract suspend fun getSelectedItems(userId: String, featureKey: String): List<PlanQuotaSelectionItemEntity>

    @Query("SELECT * FROM plan_selections WHERE user_id = :userId")
    abstract suspend fun getAllSelections(userId: String): List<PlanQuotaSelectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putSelection(selection: PlanQuotaSelectionEntity)

    @Query("DELETE FROM plan_selection_items WHERE user_id = :userId AND feature_key = :featureKey")
    protected abstract suspend fun clearItems(userId: String, featureKey: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertItems(items: List<PlanQuotaSelectionItemEntity>)

    @Transaction
    open suspend fun replaceSelection(
        userId: String,
        featureKey: String,
        resourceType: String,
        resourceIds: Collection<String>,
        now: Long,
        resourceTypesById: Map<String, String> = emptyMap(),
    ): PlanQuotaSelectionEntity {
        val normalizedIds = resourceIds.toSortedSet()
        require(normalizedIds.none(String::isBlank)) { "Resource IDs must not be blank" }
        val currentIds = getSelectedResourceIds(userId, featureKey).toSet()
        val current = getSelection(userId, featureKey)
        if (current != null && currentIds == normalizedIds) return current

        val next = PlanQuotaSelectionEntity(
            userId = userId,
            featureKey = featureKey,
            revision = (current?.revision ?: 0L) + 1L,
            updatedAt = now,
        )
        putSelection(next)
        clearItems(userId, featureKey)
        if (normalizedIds.isNotEmpty()) {
            insertItems(normalizedIds.map { id ->
                PlanQuotaSelectionItemEntity(userId, featureKey, id, resourceTypesById[id] ?: resourceType, now)
            })
        }
        return next
    }
}
