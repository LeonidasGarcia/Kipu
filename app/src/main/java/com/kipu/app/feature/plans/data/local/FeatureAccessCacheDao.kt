package com.kipu.app.feature.plans.data.local

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface FeatureAccessCacheDao {
    @Query("SELECT * FROM feature_access_cache WHERE user_id = :userId")
    suspend fun get(userId: UUID): FeatureAccessCacheEntity?

    @Query("SELECT * FROM feature_access_cache WHERE user_id = :userId")
    fun observe(userId: UUID): Flow<FeatureAccessCacheEntity?>
}
