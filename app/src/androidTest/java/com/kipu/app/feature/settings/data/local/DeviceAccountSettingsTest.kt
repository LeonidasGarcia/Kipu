package com.kipu.app.feature.settings.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.database.KipuDatabase
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceAccountSettingsTest {

    private lateinit var database: KipuDatabase
    private lateinit var dao: DeviceAccountSettingsDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.deviceAccountSettingsDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun putSettings_and_findSettings_persists_per_account() = runBlocking {
        val userId = UUID.randomUUID()
        val settings = DeviceAccountSettingsEntity(
            userId = userId,
            localUnlockEnabled = true,
            unlockAuthenticators = 1,
        )

        dao.putSettings(settings)
        val retrieved = dao.findSettings(userId)

        assertNotNull(retrieved)
        assertTrue(retrieved?.localUnlockEnabled == true)
        assertEquals(1, retrieved?.unlockAuthenticators)
    }

    @Test
    fun multi_user_isolation_keeps_device_settings_separate() = runBlocking {
        val userA = UUID.randomUUID()
        val userB = UUID.randomUUID()

        dao.putSettings(DeviceAccountSettingsEntity(userId = userA, localUnlockEnabled = true))
        dao.putSettings(DeviceAccountSettingsEntity(userId = userB, localUnlockEnabled = false))

        val retrievedA = dao.findSettings(userA)
        val retrievedB = dao.findSettings(userB)

        assertEquals(true, retrievedA?.localUnlockEnabled)
        assertEquals(false, retrievedB?.localUnlockEnabled)
    }

    @Test
    fun deleteSettings_removes_entry_cleanly() = runBlocking {
        val userId = UUID.randomUUID()
        dao.putSettings(DeviceAccountSettingsEntity(userId = userId, localUnlockEnabled = true))

        dao.deleteSettings(userId)
        val retrieved = dao.findSettings(userId)

        assertNull(retrieved)
    }
}
