package com.kipu.app.feature.categories.data

import com.kipu.app.feature.plans.data.local.FeatureAccessCacheDao
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheEntity
import com.kipu.app.feature.plans.data.local.PlanQuotaSelectionDao
import com.kipu.app.feature.plans.data.local.PlanQuotaSelectionEntity
import com.kipu.app.feature.plans.data.local.PlanQuotaSelectionItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

class FakeQuotaSelectionDao : PlanQuotaSelectionDao() {
    private val heads = mutableMapOf<Pair<String, String>, PlanQuotaSelectionEntity>()
    private val items = mutableMapOf<Pair<String, String>, Set<String>>()

    override suspend fun getSelection(userId: String, featureKey: String) = heads[userId to featureKey]

    override suspend fun getSelectedResourceIds(userId: String, featureKey: String) =
        items[userId to featureKey].orEmpty().sorted()

    override fun observeSelectedResourceIds(userId: String, featureKey: String): Flow<List<String>> =
        flowOf(getSelectedResourceIdsSync(userId, featureKey))

    private fun getSelectedResourceIdsSync(userId: String, featureKey: String) =
        items[userId to featureKey].orEmpty().sorted()

    override suspend fun putSelection(selection: PlanQuotaSelectionEntity) {
        heads[selection.userId to selection.featureKey] = selection
    }

    override suspend fun clearItems(userId: String, featureKey: String) {
        items.remove(userId to featureKey)
    }

    override suspend fun insertItems(items: List<PlanQuotaSelectionItemEntity>) {
        if (items.isEmpty()) return
        val key = items.first().userId to items.first().featureKey
        this.items[key] = items.mapTo(linkedSetOf()) { it.resourceId }
    }

    override suspend fun replaceSelection(
        userId: String,
        featureKey: String,
        resourceType: String,
        resourceIds: Collection<String>,
        now: Long,
        resourceTypesById: Map<String, String>,
    ): PlanQuotaSelectionEntity {
        val ids = resourceIds.toSortedSet()
        val old = heads[userId to featureKey]
        if (old != null && items[userId to featureKey].orEmpty() == ids) return old
        val next = PlanQuotaSelectionEntity(userId, featureKey, (old?.revision ?: 0L) + 1L, now)
        heads[userId to featureKey] = next
        items[userId to featureKey] = ids
        return next
    }
}

class FakeFeatureAccessCacheDao : FeatureAccessCacheDao {
    override suspend fun get(userId: UUID): FeatureAccessCacheEntity? = null
    override fun observe(userId: UUID): Flow<FeatureAccessCacheEntity?> = flowOf(null)
}
