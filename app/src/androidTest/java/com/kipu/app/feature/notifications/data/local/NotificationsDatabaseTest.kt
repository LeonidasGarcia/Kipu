package com.kipu.app.feature.notifications.data.local

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.database.MIGRATION_11_12
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationsDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        KipuDatabase::class.java,
    )

    @After
    fun cleanUp() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migration11To12CreatesAccountScopedNotificationProjectionAndOutbox() {
        helper.createDatabase(DATABASE_NAME, 11).close()

        helper.runMigrationsAndValidate(DATABASE_NAME, 12, true, MIGRATION_11_12).use { database ->
            assertEquals(
                listOf(
                    "id", "user_id", "title", "body", "notification_type",
                    "reference_entity_type", "reference_entity_id", "is_read",
                    "created_at", "deleted_at", "event_payload",
                ),
                database.columns("app_notifications"),
            )
            assertTrue(database.indexes("app_notifications").any { it.contains("user_id") })
            assertEquals(
                listOf("operation_id", "user_id", "notification_id", "is_read", "deleted_at", "attempt_count", "created_at"),
                database.columns("notification_sync_outbox"),
            )
            assertTrue(database.indexes("notification_sync_outbox").any { it.contains("notification_id") })
        }
    }

    private fun SupportSQLiteDatabase.columns(table: String): List<String> =
        query("PRAGMA table_info(`$table`)").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }

    private fun SupportSQLiteDatabase.indexes(table: String): List<String> =
        query("PRAGMA index_list(`$table`)").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }

    private companion object {
        const val DATABASE_NAME = "kipu-notifications-schema-test"
    }
}
