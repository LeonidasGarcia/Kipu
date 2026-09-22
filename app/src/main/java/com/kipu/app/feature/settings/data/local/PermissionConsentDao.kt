package com.kipu.app.feature.settings.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface PermissionConsentDao {

    @Query("SELECT * FROM installation_permission_state WHERE source = :source")
    suspend fun findInstallationPermission(source: String): InstallationPermissionStateEntity?

    @Query("SELECT * FROM installation_permission_state")
    fun observeInstallationPermissions(): Flow<List<InstallationPermissionStateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putInstallationPermission(entity: InstallationPermissionStateEntity)

    @Query("SELECT * FROM account_source_consent WHERE user_id = :userId AND source = :source")
    suspend fun findAccountConsent(userId: UUID, source: String): AccountSourceConsentEntity?

    @Query("SELECT * FROM account_source_consent WHERE user_id = :userId")
    fun observeAccountConsents(userId: UUID): Flow<List<AccountSourceConsentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAccountConsent(entity: AccountSourceConsentEntity)

    @Query("UPDATE account_source_consent SET consent_state = 'REVOKED' WHERE user_id = :userId")
    suspend fun revokeAllAccountConsents(userId: UUID)
}
