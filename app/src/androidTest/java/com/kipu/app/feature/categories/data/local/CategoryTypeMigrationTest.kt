package com.kipu.app.feature.categories.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.database.MIGRATION_9_10
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryTypeMigrationTest {

    @Test
    fun migrationKeepsLegacyCategoryGeneralAndPreservesMovementClassification() {
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            KipuDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )
        val databaseName = "category-type-migration-test"
        helper.createDatabase(databaseName, 9).apply {
            execSQL(
                """INSERT INTO categories
                    (id, user_id, parent_id, origin, is_active, remote_revision, created_at, updated_at)
                    VALUES ('legacy-category', NULL, NULL, 'SYSTEM', 1, 7, 1000, 1000)""".trimIndent(),
            )
            execSQL(
                """INSERT INTO financial_movements
                    (id, operation_id, operation_sequence, user_id, kind, amount_minor_units, currency,
                     effective_at, status, category_id, created_at)
                    VALUES ('legacy-movement', 'legacy-operation', 0, 'legacy-user', 'ADJUSTMENT', -250,
                            'PEN', 1000, 'POSTED', 'legacy-category', 1000)""".trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 10, true, MIGRATION_9_10).use { migrated ->
            migrated.query("SELECT category_type FROM categories WHERE id = 'legacy-category'").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("GENERAL", cursor.getString(0))
            }
            migrated.query("SELECT category_id FROM financial_movements WHERE id = 'legacy-movement'").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("legacy-category", cursor.getString(0))
            }
        }
    }
}
