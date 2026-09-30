package com.kipu.app.feature.categories.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class MovementClassificationTuple(
    val id: String,
    val category_id: String?,
    val merchant_id: String?,
    val merchant_provisional_text: String?
)

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategoriesIfAbsent(categories: List<CategoryEntity>)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE user_id = :userId OR user_id IS NULL ORDER BY created_at ASC")
    fun observeCategoriesForUser(userId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE user_id = :userId OR user_id IS NULL")
    suspend fun getCategoriesForUser(userId: String): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories WHERE user_id = :userId AND origin = 'CUSTOM' AND parent_id IS NULL AND is_active = 1")
    suspend fun countActiveCustomRoots(userId: String): Int

    @Query("SELECT COUNT(*) FROM categories WHERE user_id = :userId AND origin = 'CUSTOM' AND parent_id IS NULL AND is_active = 1 AND category_type = :categoryType")
    suspend fun countActiveCustomRootsByType(userId: String, categoryType: String): Int

    // Presentation
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPresentation(presentation: CategoryPresentationEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPresentationsIfAbsent(presentations: List<CategoryPresentationEntity>)

    @Update
    suspend fun updatePresentation(presentation: CategoryPresentationEntity)

    @Query("SELECT * FROM category_presentations WHERE user_id = :userId AND category_id = :categoryId")
    suspend fun getPresentation(userId: String, categoryId: String): CategoryPresentationEntity?

    @Query("SELECT * FROM category_presentations WHERE user_id = :userId")
    fun observePresentationsForUser(userId: String): Flow<List<CategoryPresentationEntity>>

    // Conflicts
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConflict(conflict: CategoryConflictEntity)

    @Update
    suspend fun updateConflict(conflict: CategoryConflictEntity)

    @Query("SELECT * FROM category_conflicts WHERE user_id = :userId AND status = 'OPEN' ORDER BY created_at DESC")
    fun observeOpenConflicts(userId: String): Flow<List<CategoryConflictEntity>>

    @Query("SELECT * FROM category_conflicts WHERE id = :id")
    suspend fun getConflict(id: String): CategoryConflictEntity?

    // Movement Classification
    @Query("SELECT id, category_id, merchant_id, merchant_provisional_text FROM financial_movements WHERE id = :movementId AND user_id = :userId")
    fun observeMovementClassification(movementId: String, userId: String): Flow<MovementClassificationTuple?>

    @Query("""
        UPDATE financial_movements 
        SET category_id = :categoryId, 
            merchant_id = :merchantId, 
            merchant_provisional_text = :provisionalText 
        WHERE id = :movementId AND user_id = :userId
    """)
    suspend fun updateMovementClassification(
        movementId: String,
        userId: String,
        categoryId: String?,
        merchantId: String?,
        provisionalText: String?
    ): Int

    @Query("UPDATE financial_movements SET category_id = NULL WHERE id = :movementId AND user_id = :userId")
    suspend fun clearCategoryClassification(movementId: String, userId: String): Int

    @Query("UPDATE financial_movements SET merchant_id = NULL, merchant_provisional_text = NULL WHERE id = :movementId AND user_id = :userId")
    suspend fun clearMerchantClassification(movementId: String, userId: String): Int

    // Outbox
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutboxCommand(command: CategorySyncOutboxEntity)

    @Query("SELECT * FROM category_sync_outbox WHERE user_id = :userId AND state = 'PENDING' ORDER BY created_at ASC")
    suspend fun getPendingOutboxCommands(userId: String): List<CategorySyncOutboxEntity>

    @Update
    suspend fun updateOutboxCommand(command: CategorySyncOutboxEntity)
}
