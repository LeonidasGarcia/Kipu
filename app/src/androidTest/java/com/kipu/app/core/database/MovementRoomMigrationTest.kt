package com.kipu.app.core.database

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MovementRoomMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        KipuDatabase::class.java,
    )

    @Test
    fun migratesPostedAccountHistoryAndKeepsCardLiabilitySeparate() {
        val name = "movement-v5-history"
        helper.createDatabase(name, 5).use { db ->
            db.execSQL(
                """INSERT INTO accounts (id, user_id, creation_operation_id, alias, type, currency,
                    initial_balance_minor_units, opened_at, is_archived, remote_revision, created_at, updated_at)
                    VALUES ('account', 'user', 'open-op', 'Cash', 'CASH', 'PEN', 10000, 1000000, 0, 0, 1000000, 1000000)"""
            )
            db.execSQL(
                """INSERT INTO ledger_entries (id, user_id, transaction_id, account_id, role,
                    signed_amount_minor, currency_code, created_at)
                    VALUES ('manual', 'user', 'manual', 'account', 'SOURCE', -2000, 'PEN', 1000)"""
            )
            listOf(
                Triple("opening", "OPENING", 10000),
                Triple("adjustment", "ADJUSTMENT", 500),
                Triple("reversal", "REVERSAL", -1000),
                Triple("payment", "CARD_PAYMENT_CASH", -1500),
            ).forEachIndexed { index, (id, kind, amount) ->
                db.execSQL(
                    """INSERT INTO financial_movements (id, operation_id, operation_sequence, user_id,
                        kind, amount_minor_units, currency, account_id, effective_at, status, created_at)
                        VALUES ('$id', '$id', $index, 'user', '$kind', $amount, 'PEN', 'account',
                        2000000, 'POSTED', 2000000)"""
                )
            }
            db.execSQL(
                """INSERT INTO financial_movements (id, operation_id, operation_sequence, user_id,
                    kind, amount_minor_units, currency, card_id, effective_at, status, created_at)
                    VALUES ('liability', 'liability', 0, 'user', 'CARD_PAYMENT_LIABILITY', -1500,
                    'PEN', 'card', 2000000, 'POSTED', 2000000)"""
            )
        }

        helper.runMigrationsAndValidate(name, 9, true, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9).use { db ->
            assertEquals(4L, db.scalarLong("SELECT COUNT(*) FROM transactions WHERE legacy_kind IS NOT NULL"))
            assertEquals(5L, db.scalarLong("SELECT COUNT(*) FROM ledger_entries"))
            assertEquals(6000L, db.scalarLong("SELECT balance_minor FROM balance_projections WHERE account_id = 'account'"))
            assertEquals(-1500L, db.scalarLong("SELECT signed_amount_minor FROM ledger_entries WHERE id = 'legacy:payment'"))
            assertEquals(2000L, db.scalarLong("SELECT occurred_at FROM transactions WHERE id = 'legacy:opening'"))
            db.query("SELECT legacy_kind FROM transactions WHERE id = 'legacy:payment'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("CARD_PAYMENT_CASH", cursor.getString(0))
            }
            db.execSQL("UPDATE transactions SET merchant_provisional_text = 'Bodega' WHERE id = 'legacy:payment'")
            db.query("SELECT merchant_provisional_text FROM transactions WHERE id = 'legacy:payment'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Bodega", cursor.getString(0))
            }
            db.execSQL("INSERT INTO movement_sync_checkpoints (user_id, sequence, updated_at) VALUES ('user', 4, 1)")
            assertEquals(4L, db.scalarLong("SELECT sequence FROM movement_sync_checkpoints WHERE user_id = 'user'"))
            assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM plan_selections"))
        }
    }

    @Test
    fun upgradesVersionFourWithoutLosingOpeningBalance() {
        val name = "movement-v4-history"
        helper.createDatabase(name, 4).use { db ->
            db.execSQL(
                """INSERT INTO accounts (id, user_id, creation_operation_id, alias, type, currency,
                    initial_balance_minor_units, opened_at, is_archived, remote_revision, created_at, updated_at)
                    VALUES ('account', 'user', 'open-op', 'Cash', 'CASH', 'PEN', 2500, 1000000, 0, 0, 1000000, 1000000)"""
            )
            db.execSQL(
                """INSERT INTO financial_movements (id, operation_id, operation_sequence, user_id,
                    kind, amount_minor_units, currency, account_id, opening_account_id,
                    effective_at, status, created_at)
                    VALUES ('opening', 'open-op', 0, 'user', 'OPENING', 2500, 'PEN', 'account',
                    'account', 1000000, 'POSTED', 1000000)"""
            )
        }

        helper.runMigrationsAndValidate(name, 9, true, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9).use { db ->
            assertEquals(2500L, db.scalarLong("SELECT balance_minor FROM balance_projections WHERE account_id = 'account'"))
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM transactions WHERE legacy_kind = 'OPENING'"))
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.scalarLong(sql: String): Long =
        query(sql).use { cursor: Cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }
}
