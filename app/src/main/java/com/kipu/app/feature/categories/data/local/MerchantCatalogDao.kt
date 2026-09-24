package com.kipu.app.feature.categories.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantCatalogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMerchants(merchants: List<MerchantCatalogEntity>)

    @Query("""
        SELECT * FROM merchant_catalog_cache 
        WHERE is_active = 1 AND normalized_name LIKE '%' || :query || '%' 
        ORDER BY name ASC 
        LIMIT 50
    """)
    fun searchMerchants(query: String): Flow<List<MerchantCatalogEntity>>

    @Query("SELECT * FROM merchant_catalog_cache WHERE is_active = 1 ORDER BY name ASC")
    fun getAllActiveMerchants(): Flow<List<MerchantCatalogEntity>>

    @Query("SELECT * FROM merchant_catalog_cache")
    fun observeMerchants(): Flow<List<MerchantCatalogEntity>>

    @Query("SELECT * FROM merchant_catalog_cache WHERE id = :id")
    suspend fun getMerchantById(id: String): MerchantCatalogEntity?

    @Query("SELECT COUNT(*) FROM merchant_catalog_cache WHERE is_active = 1")
    suspend fun countActiveMerchants(): Int

    @Query("SELECT MAX(version) FROM merchant_catalog_cache")
    suspend fun getLatestVersion(): Long?
}
