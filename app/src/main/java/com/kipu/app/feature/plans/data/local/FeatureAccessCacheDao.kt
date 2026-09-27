package com.kipu.app.feature.plans.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface FeatureAccessCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putVerified(entity: FeatureAccessCacheEntity)

    @Query("SELECT * FROM feature_access_cache WHERE user_id = :userId")
    suspend fun get(userId: UUID): FeatureAccessCacheEntity?

    @Query("SELECT * FROM feature_access_cache WHERE user_id = :userId")
    fun observe(userId: UUID): Flow<FeatureAccessCacheEntity?>
}
