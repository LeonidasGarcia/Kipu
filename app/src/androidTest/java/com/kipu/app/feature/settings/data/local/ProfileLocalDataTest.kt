package com.kipu.app.feature.settings.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.database.KipuDatabase
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileLocalDataTest {

    private lateinit var database: KipuDatabase
    private lateinit var profileDao: ProfilePreferencesDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        profileDao = database.profilePreferencesDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun putProfile_and_findProfile_persists_and_retrieves_profile() = runBlocking {
        val userId = UUID.randomUUID()
        val profile = UserProfileCacheEntity(
            userId = userId,
            displayName = "Carlos",
            currencyCode = "USD",
            monthStart = 10,
            hideBalances = true,
            themeMode = "DARK",
            remoteRevision = 2L,
        )

        profileDao.putProfile(profile)
        val retrieved = profileDao.findProfile(userId)

        assertNotNull(retrieved)
        assertEquals("Carlos", retrieved?.displayName)
        assertEquals("USD", retrieved?.currencyCode)
        assertEquals(10, retrieved?.monthStart)
        assertEquals(true, retrieved?.hideBalances)
        assertEquals(2L, retrieved?.remoteRevision)
    }

    @Test
    fun savePreferencesWithOutbox_commits_both_atomically() = runBlocking {
        val userId = UUID.randomUUID()
        val opId = UUID.randomUUID()

        val profile = UserProfileCacheEntity(
            userId = userId,
            displayName = "Ana",
            currencyCode = "PEN",
            monthStart = 1,
            hideBalances = false,
            remoteRevision = 1L,
        )
        val outbox = ProfilePreferenceOutboxEntity(
            operationId = opId,
            userId = userId,
            expectedRevision = 1L,
            payload = """{"display_name":"Ana"}""",
            status = "PENDING",
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

        profileDao.savePreferencesWithOutbox(profile, outbox)

        val retrievedProfile = profileDao.findProfile(userId)
        val retrievedOutbox = profileDao.findOutbox(opId)

        assertNotNull(retrievedProfile)
        assertNotNull(retrievedOutbox)
        assertEquals("Ana", retrievedProfile?.displayName)
        assertEquals("PENDING", retrievedOutbox?.status)
    }

    @Test
    fun multi_user_isolation_ensures_no_data_leakage_between_users() = runBlocking {
        val userA = UUID.randomUUID()
        val userB = UUID.randomUUID()

        val profileA = UserProfileCacheEntity(userId = userA, displayName = "User A", currencyCode = "PEN")
        val profileB = UserProfileCacheEntity(userId = userB, displayName = "User B", currencyCode = "USD")

        profileDao.putProfile(profileA)
        profileDao.putProfile(profileB)

        val retrievedA = profileDao.findProfile(userA)
        val retrievedB = profileDao.findProfile(userB)

        assertEquals("User A", retrievedA?.displayName)
        assertEquals("User B", retrievedB?.displayName)

        val outboxA = ProfilePreferenceOutboxEntity(
            operationId = UUID.randomUUID(),
            userId = userA,
            expectedRevision = 1L,
            payload = "{}",
            status = "PENDING",
        )
        profileDao.insertOutbox(outboxA)

        assertEquals(1, profileDao.pendingOutboxCount(userA))
        assertEquals(0, profileDao.pendingOutboxCount(userB))
    }

    @Test
    fun markOutboxWaitingForAuth_transitions_pending_items_correctly() = runBlocking {
        val userId = UUID.randomUUID()
        val outbox = ProfilePreferenceOutboxEntity(
            operationId = UUID.randomUUID(),
            userId = userId,
            expectedRevision = 1L,
            payload = "{}",
            status = "PENDING",
        )
        profileDao.insertOutbox(outbox)

        profileDao.markOutboxWaitingForAuth(userId)

        val pendingList = profileDao.findPendingOutbox(userId)
        assertEquals(1, pendingList.size)
        assertEquals("WAITING_FOR_AUTH", pendingList.first().status)
    }
}
