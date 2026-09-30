package com.kipu.app.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MerchantCatalogRoomMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        KipuDatabase::class.java,
    )

    @Test
    fun versionFifteenCatalogRowsSurviveAndReceiveVersionSixteenDefaults() {
        val databaseName = "merchant-catalog-v15-to-v16"
        helper.createDatabase(databaseName, 15).use { database ->
            database.execSQL(
                """INSERT INTO merchant_catalog_cache
                    (id, name, normalized_name, is_active, version, last_synced_at)
                    VALUES ('merchant-1', 'Bodega', 'bodega', 1, 7, 1700000000)""".trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(databaseName, 16, true, MIGRATION_15_16).use { database ->
            database.query(
                """SELECT name, normalized_name, version, last_synced_at, default_category_id,
                    priority, logo_key, brand_color
                    FROM merchant_catalog_cache WHERE id = 'merchant-1'""".trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Bodega", cursor.getString(0))
                assertEquals("bodega", cursor.getString(1))
                assertEquals(7L, cursor.getLong(2))
                assertEquals(1700000000L, cursor.getLong(3))
                assertNull(cursor.getString(4))
                assertEquals("B", cursor.getString(5))
                assertNull(cursor.getString(6))
                assertNull(cursor.getString(7))
            }

            val indexes = database.query("PRAGMA index_list(`merchant_catalog_cache`)").use { cursor ->
                buildSet {
                    val nameColumn = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) add(cursor.getString(nameColumn))
                }
            }
            assertTrue("default_category_id index must exist after migration", indexes.contains(
                "index_merchant_catalog_cache_default_category_id",
            ))

            database.execSQL(
                """UPDATE merchant_catalog_cache
                    SET default_category_id = 'food', priority = 'A', logo_key = 'bodega',
                        brand_color = '#2563EB'
                    WHERE id = 'merchant-1'""".trimIndent(),
            )
            database.query(
                """SELECT default_category_id, priority, logo_key, brand_color
                    FROM merchant_catalog_cache WHERE id = 'merchant-1'""".trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("food", cursor.getString(0))
                assertEquals("A", cursor.getString(1))
                assertEquals("bodega", cursor.getString(2))
                assertEquals("#2563EB", cursor.getString(3))
            }
        }
    }
}
