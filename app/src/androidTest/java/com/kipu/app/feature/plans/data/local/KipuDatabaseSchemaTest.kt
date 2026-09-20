package com.kipu.app.feature.plans.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.core.database.KipuDatabase
import java.io.IOException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KipuDatabaseSchemaTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        KipuDatabase::class.java,
    )

    @After
    fun deleteDatabase() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    @Throws(IOException::class)
    fun cleanVersionOneSchema_hasAllTablesConstraintsAndIndexes() {
        helper.createDatabase(DATABASE_NAME, 1).use { database ->
            assertEquals(
                setOf("feature_access_cache", "plan_preferences", "plan_selection_sync_state", "sync_outbox"),
                database.userTables(),
            )
            assertEquals(
                listOf("user_id", "selection", "selected_at", "updated_at"),
                database.columns("plan_preferences"),
            )
            assertEquals(
                listOf(
                    "operation_id", "user_id", "aggregate_type", "contract_version",
                    "selection_revision", "selection", "selected_at", "status", "attempt_count",
                    "next_attempt_at", "lease_until", "last_error_code", "created_at", "updated_at",
                ),
                database.columns("sync_outbox"),
            )
            assertTrue(database.indexes("sync_outbox").any { it.contains("user_id") })
            assertTrue(database.indexes("sync_outbox").any { it.contains("selection_revision") })
            assertTrue(database.tableSql("sync_outbox").contains("contract_version"))
        }
    }

    @Test
    fun representativeVersionOneRowsSurviveValidationAndNormalReopen() {
        helper.createDatabase(DATABASE_NAME, 1).use { database ->
            database.execSQL(
                "INSERT INTO plan_preferences VALUES (?, ?, ?, ?)",
                arrayOf<Any?>(USER_ID, "TRIAL_INTENT", 1_789_401_792_123_456L, 1_789_401_792_123_456L),
            )
            database.execSQL(
                "INSERT INTO plan_selection_sync_state VALUES (?, ?, ?, ?)",
                arrayOf<Any?>(USER_ID, 3L, 2L, 1_789_401_793_000_000L),
            )
            database.execSQL(
                """INSERT INTO sync_outbox
                   (operation_id,user_id,aggregate_type,contract_version,selection_revision,selection,
                    selected_at,status,attempt_count,next_attempt_at,lease_until,last_error_code,created_at,updated_at)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                arrayOf<Any?>(
                    OPERATION_ID, USER_ID, "PLAN_SELECTION", 1, 3L, "TRIAL_INTENT",
                    1_789_401_792_123_456L, "PENDING", 0, null, null, null,
                    1_789_401_792_123_456L, 1_789_401_792_123_456L,
                ),
            )
        }

        helper.runMigrationsAndValidate(DATABASE_NAME, 1, true).close()
        val database = Room.databaseBuilder(context, KipuDatabase::class.java, DATABASE_NAME).build()
        try {
            database.openHelper.readableDatabase.query(
                "SELECT p.selection, s.last_issued_revision, o.operation_id " +
                    "FROM plan_preferences p JOIN plan_selection_sync_state s USING(user_id) " +
                    "JOIN sync_outbox o USING(user_id)",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("TRIAL_INTENT", cursor.getString(0))
                assertEquals(3L, cursor.getLong(1))
                assertEquals(OPERATION_ID, cursor.getString(2))
            }
        } finally { database.close() }
    }

    @Test
    fun newerUnknownSchemaFailsSafelyWithoutDeletingRepresentativeData() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DATABASE_NAME)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE migration_sentinel (value TEXT NOT NULL)")
                    db.execSQL("INSERT INTO migration_sentinel VALUES ('preserve-me')")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { it.writableDatabase }

        val room = Room.databaseBuilder(context, KipuDatabase::class.java, DATABASE_NAME).build()
        assertThrows(IllegalStateException::class.java) { room.openHelper.writableDatabase }
        room.close()

        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            helper.readableDatabase.query("SELECT value FROM migration_sentinel").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("preserve-me", cursor.getString(0))
            }
        }
    }

    private fun SupportSQLiteDatabase.userTables(): Set<String> = query(
        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_%' AND name NOT LIKE 'room_%'",
    ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    private fun SupportSQLiteDatabase.columns(table: String): List<String> =
        query("PRAGMA table_info(`$table`)").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }

    private fun SupportSQLiteDatabase.indexes(table: String): List<String> =
        query("PRAGMA index_list(`$table`)").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }

    private fun SupportSQLiteDatabase.tableSql(table: String): String =
        query("SELECT sql FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0)
        }

    private companion object {
        const val DATABASE_NAME = "kipu-schema-test"
        const val USER_ID = "10000000-0000-0000-0000-000000000001"
        const val OPERATION_ID = "30000000-0000-0000-0000-000000000003"
    }
}
