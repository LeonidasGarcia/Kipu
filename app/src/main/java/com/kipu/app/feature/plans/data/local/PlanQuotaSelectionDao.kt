package com.kipu.app.feature.plans.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class PlanQuotaSelectionSnapshot(
    val selection: PlanQuotaSelectionEntity,
    val items: List<PlanQuotaSelectionItemEntity>,
)

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

    @Transaction
    open suspend fun getAllSelectionSnapshots(userId: String): List<PlanQuotaSelectionSnapshot> =
        getAllSelections(userId).map { selection ->
            PlanQuotaSelectionSnapshot(
                selection = selection,
                items = getSelectedItems(userId, selection.featureKey),
            )
        }

    @Transaction
    open suspend fun getSelectionSnapshot(
        userId: String,
        featureKey: String,
    ): PlanQuotaSelectionSnapshot? = getSelection(userId, featureKey)?.let { selection ->
        PlanQuotaSelectionSnapshot(
            selection = selection,
            items = getSelectedItems(userId, featureKey),
        )
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putSelection(selection: PlanQuotaSelectionEntity)

    @Update
    protected abstract suspend fun updateSelection(selection: PlanQuotaSelectionEntity)

    @Query("DELETE FROM plan_selection_items WHERE user_id = :userId AND feature_key = :featureKey")
    protected abstract suspend fun clearItems(userId: String, featureKey: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertItems(items: List<PlanQuotaSelectionItemEntity>)

    @Transaction
    open suspend fun rebaseSelection(
        userId: String,
        featureKey: String,
        acceptedRevision: Long,
        now: Long,
    ): PlanQuotaSelectionEntity? {
        val current = getSelection(userId, featureKey) ?: return null
        val revisionBase = maxOf(current.revision, acceptedRevision)
        if (revisionBase == Long.MAX_VALUE) return null

        // Room keeps only the latest full snapshot, so a rebased snapshot need only be newer
        // than both known heads; the server accepts forward revision gaps.
        val rebased = current.copy(revision = revisionBase + 1L, updatedAt = now)
        updateSelection(rebased)
        return rebased
    }

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
