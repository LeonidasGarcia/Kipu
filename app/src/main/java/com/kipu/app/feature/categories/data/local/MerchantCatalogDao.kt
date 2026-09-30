package com.kipu.app.feature.categories.data.local

import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class MerchantCategoryFilterRow(
    @ColumnInfo(name = "category_id")
    val categoryId: String,
    @ColumnInfo(name = "name")
    val name: String,
)

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

    @Query("SELECT * FROM merchant_catalog_cache WHERE is_active = 1 AND default_category_id = :categoryId ORDER BY name ASC")
    fun getActiveMerchantsByCategory(categoryId: String): Flow<List<MerchantCatalogEntity>>

    @Query("""
        SELECT * FROM merchant_catalog_cache
        WHERE is_active = 1
          AND default_category_id = :categoryId
          AND normalized_name LIKE '%' || :query || '%'
        ORDER BY name ASC
        LIMIT 50
    """)
    fun searchMerchantsByCategory(query: String, categoryId: String): Flow<List<MerchantCatalogEntity>>

    @Query("""
        SELECT DISTINCT presentation.category_id AS category_id, presentation.name AS name
        FROM merchant_catalog_cache AS merchant
        JOIN categories AS subcategory
          ON subcategory.id = merchant.default_category_id AND subcategory.is_active = 1
        JOIN categories AS root
          ON root.id = subcategory.parent_id AND root.is_active = 1
        JOIN category_presentations AS presentation
          ON presentation.category_id = subcategory.id AND presentation.user_id = :userId
        WHERE merchant.is_active = 1 AND merchant.default_category_id IS NOT NULL
        ORDER BY presentation.name COLLATE NOCASE ASC
    """)
    fun observeMerchantCategoryFilters(userId: String): Flow<List<MerchantCategoryFilterRow>>

    @Query("SELECT * FROM merchant_catalog_cache")
    fun observeMerchants(): Flow<List<MerchantCatalogEntity>>

    @Query("SELECT * FROM merchant_catalog_cache WHERE id = :id")
    suspend fun getMerchantById(id: String): MerchantCatalogEntity?

    @Query("SELECT COUNT(*) FROM merchant_catalog_cache WHERE is_active = 1")
    suspend fun countActiveMerchants(): Int

    @Query("SELECT MAX(version) FROM merchant_catalog_cache")
    suspend fun getLatestVersion(): Long?
}
