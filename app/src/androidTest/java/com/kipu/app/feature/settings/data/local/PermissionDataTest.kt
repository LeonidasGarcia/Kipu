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
class PermissionDataTest {

    private lateinit var database: KipuDatabase
    private lateinit var dao: PermissionConsentDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.permissionConsentDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun installationPermission_persists_and_retrieves_device_wide() = runBlocking {
        val entity = InstallationPermissionStateEntity(
            source = "OWN_NOTIFICATIONS",
            deviceAuthorization = "GRANTED",
            lastCheckedAt = Instant.now(),
        )

        dao.putInstallationPermission(entity)
        val retrieved = dao.findInstallationPermission("OWN_NOTIFICATIONS")

        assertNotNull(retrieved)
        assertEquals("GRANTED", retrieved?.deviceAuthorization)
    }

    @Test
    fun accountSourceConsent_isolates_between_users() = runBlocking {
        val userA = UUID.randomUUID()
        val userB = UUID.randomUUID()

        val consentA = AccountSourceConsentEntity(
            userId = userA,
            source = "OWN_NOTIFICATIONS",
            consentState = "GRANTED",
            capabilityState = "ALLOWED",
        )
        val consentB = AccountSourceConsentEntity(
            userId = userB,
            source = "OWN_NOTIFICATIONS",
            consentState = "DENIED",
            capabilityState = "DENIED",
        )

        dao.putAccountConsent(consentA)
        dao.putAccountConsent(consentB)

        val retrievedA = dao.findAccountConsent(userA, "OWN_NOTIFICATIONS")
        val retrievedB = dao.findAccountConsent(userB, "OWN_NOTIFICATIONS")

        assertEquals("GRANTED", retrievedA?.consentState)
        assertEquals("DENIED", retrievedB?.consentState)
    }

    @Test
    fun revokeAllAccountConsents_only_affects_targeted_user() = runBlocking {
        val userA = UUID.randomUUID()
        val userB = UUID.randomUUID()

        dao.putAccountConsent(
            AccountSourceConsentEntity(
                userId = userA,
                source = "OWN_NOTIFICATIONS",
                consentState = "GRANTED",
                capabilityState = "ALLOWED",
            )
        )
        dao.putAccountConsent(
            AccountSourceConsentEntity(
                userId = userB,
                source = "OWN_NOTIFICATIONS",
                consentState = "GRANTED",
                capabilityState = "ALLOWED",
            )
        )

        dao.revokeAllAccountConsents(userA)

        val retrievedA = dao.findAccountConsent(userA, "OWN_NOTIFICATIONS")
        val retrievedB = dao.findAccountConsent(userB, "OWN_NOTIFICATIONS")

        assertEquals("REVOKED", retrievedA?.consentState)
        assertEquals("GRANTED", retrievedB?.consentState)
    }
}
