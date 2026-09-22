package com.kipu.app.feature.settings.data

import com.kipu.app.core.logging.SecureLog
import com.kipu.app.feature.settings.data.local.ProfilePreferenceOutboxEntity
import com.kipu.app.feature.settings.data.local.ProfilePreferencesDao
import com.kipu.app.feature.settings.data.local.UserProfileCacheEntity
import com.kipu.app.feature.settings.domain.ProfilePreferencesRepository
import com.kipu.app.feature.settings.domain.model.ProfilePreferenceDelta
import com.kipu.app.feature.settings.domain.model.SyncState
import com.kipu.app.feature.settings.domain.model.ThemeMode
import com.kipu.app.feature.settings.domain.model.UserProfile
import com.kipu.app.core.network.AuthenticatedSessionProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class UpdateProfileRpcParams(
    @SerialName("p_operation_id") val operationId: String,
    @SerialName("p_expected_revision") val expectedRevision: Long,
    @SerialName("p_payload") val payload: JsonObject,
)

@Serializable
data class UpdateProfileRpcResult(
    val result: String,
    @SerialName("accepted_revision") val acceptedRevision: Long? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("current_revision") val currentRevision: Long? = null,
)

@Serializable
data class RemoteProfileDto(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("currency_code") val currencyCode: String = "PEN",
    @SerialName("time_zone") val timeZone: String? = null,
    @SerialName("month_start") val monthStart: Int = 1,
    @SerialName("hide_balances") val hideBalances: Boolean = false,
    @SerialName("theme_mode") val themeMode: String = "SYSTEM",
    val revision: Long = 1L,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Singleton
class OfflineFirstProfilePreferencesRepository @Inject constructor(
    private val profileDao: ProfilePreferencesDao,
    private val supabaseClient: SupabaseClient,
    private val sessionProvider: AuthenticatedSessionProvider,
) : ProfilePreferencesRepository {

    override fun observeProfile(userId: UUID): Flow<UserProfile?> {
        return profileDao.observeProfile(userId).map { it?.toDomain() }
    }

    override suspend fun getProfile(userId: UUID): UserProfile? {
        return profileDao.findProfile(userId)?.toDomain()
    }

    override suspend fun updatePreferences(userId: UUID, delta: ProfilePreferenceDelta): Result<UserProfile> {
        return try {
            delta.monthStart?.let {
                require(it in 1..28) { "monthStart must be between 1 and 28, got $it" }
            }
            delta.currencyCode?.let {
                require(it.length == 3) { "currencyCode must be a 3-letter ISO code" }
            }

            val current = profileDao.findProfile(userId) ?: UserProfileCacheEntity(
                userId = userId,
                displayName = "",
                currencyCode = "PEN",
                timeZone = null,
                monthStart = 1,
                hideBalances = false,
                themeMode = "SYSTEM",
                remoteRevision = 1L,
                syncState = SyncState.SYNCED.name,
                updatedLocallyAt = Instant.now(),
            )

            val updated = current.copy(
                displayName = delta.displayName ?: current.displayName,
                currencyCode = delta.currencyCode ?: current.currencyCode,
                timeZone = delta.timeZone ?: current.timeZone,
                monthStart = delta.monthStart ?: current.monthStart,
                hideBalances = delta.hideBalances ?: current.hideBalances,
                themeMode = delta.themeMode?.name ?: current.themeMode,
                syncState = SyncState.PENDING.name,
                updatedLocallyAt = Instant.now(),
            )

            val payloadJson = buildJsonObject {
                delta.displayName?.let { put("display_name", it) }
                delta.currencyCode?.let { put("currency_code", it) }
                delta.timeZone?.let { put("time_zone", it) }
                delta.monthStart?.let { put("month_start", it) }
                delta.hideBalances?.let { put("hide_balances", it) }
                delta.themeMode?.let { put("theme_mode", it.name) }
            }

            val outbox = ProfilePreferenceOutboxEntity(
                operationId = UUID.randomUUID(),
                userId = userId,
                expectedRevision = current.remoteRevision,
                payload = payloadJson.toString(),
                status = "PENDING",
                attemptCount = 0,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )

            profileDao.savePreferencesWithOutbox(updated, outbox)
            Result.success(updated.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setHideBalances(userId: UUID, hideBalances: Boolean): Result<UserProfile> {
        return updatePreferences(userId, ProfilePreferenceDelta(hideBalances = hideBalances))
    }

    override suspend fun syncPendingPreferences(userId: UUID): Result<Unit> {
        val session = sessionProvider.currentSession()
        if (session != null) {
            runCatching { supabaseClient.auth.importAuthToken(session.accessToken) }
        }

        val pending = profileDao.findPendingOutbox(userId)
        for (item in pending) {
            try {
                val payloadObj = Json.parseToJsonElement(item.payload) as JsonObject
                val rpcParams = UpdateProfileRpcParams(
                    operationId = item.operationId.toString(),
                    expectedRevision = item.expectedRevision,
                    payload = payloadObj,
                )
                val result = supabaseClient.postgrest.rpc(
                    function = "update_profile_preferences",
                    parameters = rpcParams,
                ).decodeAs<UpdateProfileRpcResult>()

                when (result.result) {
                    "APPLIED", "DUPLICATE" -> {
                        val acceptedRevision = result.acceptedRevision ?: (item.expectedRevision + 1)
                        val currentProfile = profileDao.findProfile(userId)
                        if (currentProfile != null) {
                            profileDao.putProfile(
                                currentProfile.copy(
                                    remoteRevision = acceptedRevision,
                                    syncState = SyncState.SYNCED.name,
                                    remoteUpdatedAt = result.updatedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
                                        ?: Instant.now(),
                                )
                            )
                        }
                        profileDao.deleteOutbox(item.operationId)
                    }
                    "CONFLICT" -> {
                        val currentProfile = profileDao.findProfile(userId)
                        if (currentProfile != null) {
                            profileDao.putProfile(currentProfile.copy(syncState = SyncState.CONFLICT.name))
                        }
                        profileDao.updateOutbox(item.copy(status = "CONFLICT", updatedAt = Instant.now()))
                        return Result.failure(IllegalStateException("Revision conflict detected during sync"))
                    }
                    else -> {
                        profileDao.updateOutbox(item.copy(attemptCount = item.attemptCount + 1, updatedAt = Instant.now()))
                    }
                }
            } catch (e: Exception) {
                SecureLog.e("ProfilePreferencesRepo", "Error syncing preferences outbox", e)
                val msg = e.message.orEmpty()
                if (msg.contains("401") || msg.contains("Not authenticated", ignoreCase = true)) {
                    profileDao.markOutboxWaitingForAuth(userId)
                } else {
                    profileDao.updateOutbox(item.copy(attemptCount = item.attemptCount + 1, updatedAt = Instant.now()))
                }
                return Result.failure(e)
            }
        }
        return Result.success(Unit)
    }

    override suspend fun refreshProfile(userId: UUID): Result<UserProfile> {
        return try {
            val session = sessionProvider.currentSession()
            if (session != null) {
                runCatching { supabaseClient.auth.importAuthToken(session.accessToken) }
            }
            val remote = supabaseClient.postgrest.rpc("ensure_profile").decodeAs<RemoteProfileDto>()
            val entity = UserProfileCacheEntity(
                userId = UUID.fromString(remote.userId),
                displayName = remote.displayName,
                currencyCode = remote.currencyCode,
                timeZone = remote.timeZone,
                monthStart = remote.monthStart,
                hideBalances = remote.hideBalances,
                themeMode = remote.themeMode,
                remoteRevision = remote.revision,
                remoteUpdatedAt = remote.updatedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now(),
                syncState = SyncState.SYNCED.name,
                updatedLocallyAt = Instant.now(),
            )
            profileDao.putProfile(entity)
            Result.success(entity.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

private fun UserProfileCacheEntity.toDomain(): UserProfile {
    return UserProfile(
        userId = userId,
        displayName = displayName,
        currencyCode = currencyCode,
        timeZone = timeZone,
        monthStart = monthStart,
        hideBalances = hideBalances,
        themeMode = runCatching { ThemeMode.valueOf(themeMode) }.getOrDefault(ThemeMode.SYSTEM),
        remoteRevision = remoteRevision,
        remoteUpdatedAt = remoteUpdatedAt,
        syncState = runCatching { SyncState.valueOf(syncState) }.getOrDefault(SyncState.SYNCED),
    )
}
