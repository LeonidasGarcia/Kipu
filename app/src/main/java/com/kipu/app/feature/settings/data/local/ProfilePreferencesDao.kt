package com.kipu.app.feature.settings.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ProfilePreferencesDao {

    @Query("SELECT * FROM user_profiles WHERE user_id = :userId")
    abstract suspend fun findProfile(userId: UUID): UserProfileCacheEntity?

    @Query("SELECT * FROM user_profiles WHERE user_id = :userId")
    abstract fun observeProfile(userId: UUID): Flow<UserProfileCacheEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putProfile(profile: UserProfileCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertOutbox(outbox: ProfilePreferenceOutboxEntity)

    @Update
    abstract suspend fun updateOutbox(outbox: ProfilePreferenceOutboxEntity)

    @Query("SELECT * FROM profile_preference_outbox WHERE operation_id = :operationId")
    abstract suspend fun findOutbox(operationId: UUID): ProfilePreferenceOutboxEntity?

    @Query("SELECT COUNT(*) FROM profile_preference_outbox WHERE user_id = :userId AND status IN ('PENDING', 'IN_FLIGHT', 'WAITING_FOR_AUTH')")
    abstract suspend fun pendingOutboxCount(userId: UUID): Int

    @Query("SELECT * FROM profile_preference_outbox WHERE user_id = :userId AND status IN ('PENDING', 'IN_FLIGHT', 'WAITING_FOR_AUTH') ORDER BY created_at ASC")
    abstract suspend fun findPendingOutbox(userId: UUID): List<ProfilePreferenceOutboxEntity>

    @Query("SELECT * FROM profile_preference_outbox WHERE status = 'PENDING' ORDER BY created_at ASC")
    abstract suspend fun findAllPendingOutbox(): List<ProfilePreferenceOutboxEntity>

    @Query("DELETE FROM profile_preference_outbox WHERE operation_id = :operationId")
    abstract suspend fun deleteOutbox(operationId: UUID)

    @Query("UPDATE profile_preference_outbox SET status = 'WAITING_FOR_AUTH' WHERE user_id = :userId AND status IN ('PENDING', 'IN_FLIGHT')")
    abstract suspend fun markOutboxWaitingForAuth(userId: UUID)

    @Transaction
    open suspend fun savePreferencesWithOutbox(
        profile: UserProfileCacheEntity,
        outbox: ProfilePreferenceOutboxEntity,
    ) {
        putProfile(profile)
        insertOutbox(outbox)
    }
}
