package com.kipu.app.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MerchantRuleDatabaseTest {
    private val helper by lazy {
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            KipuDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )
    }

    @Test
    fun migration18To19PreservesHistoryAndAddsOwnerScopedRuleStorage() {
        val databaseName = "merchant-rule-migration-v18-v19"
        helper.createDatabase(databaseName, 18).apply {
            execSQL(
                """INSERT INTO financial_movements
                    (id, operation_id, operation_sequence, user_id, kind, amount_minor_units, currency,
                     effective_at, status, category_id, merchant_id, merchant_provisional_text, created_at)
                    VALUES ('movement-a', 'operation-a', 0, 'owner-a', 'ADJUSTMENT', -450, 'PEN',
                            1000, 'POSTED', 'category-a', 'merchant-a', NULL, 1000)""".trimIndent(),
            )
            execSQL(
                """INSERT INTO category_sync_outbox
                    (operation_id, user_id, command_type, aggregate_type, aggregate_id, expected_revision,
                     payload_json, payload_hash, state, attempt_count, next_attempt_at, error_code, created_at, updated_at)
                    VALUES ('operation-pending', 'owner-a', 'UPDATE_MOVEMENT_CLASSIFICATION', 'MOVEMENT', 'movement-a', 4,
                            '{"movement_id":"movement-a"}', 'payload-hash', 'PENDING', 2, 3000, NULL, 1000, 2000)""".trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 19, true, MIGRATION_18_19).use { database ->
            database.query(
                "SELECT category_id, merchant_id, merchant_provisional_text, merchant_raw_text, amount_minor_units, status " +
                    "FROM financial_movements WHERE id = 'movement-a' AND user_id = 'owner-a'",
            ).use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("category-a", cursor.getString(0))
                assertEquals("merchant-a", cursor.getString(1))
                assertNull(cursor.getString(2))
                assertNull(cursor.getString(3))
                assertEquals(-450, cursor.getLong(4))
                assertEquals("POSTED", cursor.getString(5))
            }

            database.query(
                "SELECT state, attempt_count, next_attempt_at, payload_hash FROM category_sync_outbox " +
                    "WHERE operation_id = 'operation-pending' AND user_id = 'owner-a'",
            ).use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("PENDING", cursor.getString(0))
                assertEquals(2, cursor.getInt(1))
                assertEquals(3000, cursor.getLong(2))
                assertEquals("payload-hash", cursor.getString(3))
            }

            database.execSQL(
                """INSERT INTO merchant_alias_rules
                    (id, user_id, normalized_pattern, merchant_id, remote_revision, deleted_at, updated_at, sync_state, sync_error)
                    VALUES ('rule-a', 'owner-a', 'izipay*tambo', 'merchant-a', 2, 1500, 2000, 'PENDING', 'RETRY')""".trimIndent(),
            )
            database.execSQL(
                """INSERT INTO merchant_category_preferences
                    (user_id, merchant_id, id, category_id, remote_revision, deleted_at, updated_at, sync_state, sync_error)
                    VALUES ('owner-a', 'merchant-a', 'preference-a', 'category-a', 3, 1600, 2100, 'PENDING', 'RETRY')""".trimIndent(),
            )

            database.query(
                "SELECT user_id, remote_revision, deleted_at, sync_state, sync_error FROM merchant_alias_rules WHERE id = 'rule-a'",
            ).use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("owner-a", cursor.getString(0))
                assertEquals(2, cursor.getInt(1))
                assertEquals(1500, cursor.getLong(2))
                assertEquals("PENDING", cursor.getString(3))
                assertEquals("RETRY", cursor.getString(4))
            }
            database.query(
                "SELECT category_id, remote_revision, deleted_at, sync_state, sync_error FROM merchant_category_preferences " +
                    "WHERE user_id = 'owner-a' AND merchant_id = 'merchant-a'",
            ).use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("category-a", cursor.getString(0))
                assertEquals(3, cursor.getInt(1))
                assertEquals(1600, cursor.getLong(2))
                assertEquals("PENDING", cursor.getString(3))
                assertEquals("RETRY", cursor.getString(4))
            }
        }
    }
}
