package com.kipu.app.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebtRoomMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KipuDatabase::class.java)

    @Test
    fun versionEighteenFinancialHistorySurvivesAndDebtTablesStartEmpty() {
        val name = "debt-v18-to-v19"
        helper.createDatabase(name, 18).use { db ->
            db.execSQL(
                """INSERT INTO accounts (
                    id,user_id,creation_operation_id,alias,type,currency,initial_balance_minor_units,
                    opened_at,is_archived,remote_revision,created_at,updated_at
                ) VALUES ('cash-1','user-1','account-op','Ahorros','SAVINGS','PEN',100000,1000,0,1,1000,1000)""",
            )
            db.execSQL(
                """INSERT INTO transactions (
                    id,user_id,type,amount_minor,currency_code,source_account_id,operation_kind,
                    occurred_at,status,sync_status,created_at,updated_at
                ) VALUES ('legacy-movement','user-1','EXPENSE',1250,'PEN','cash-1','STANDARD',2000,
                    'ACTIVE','SYNCED',2000,2000)""",
            )
            db.execSQL(
                """INSERT INTO financial_movements (
                    id,operation_id,operation_sequence,user_id,kind,amount_minor_units,currency,
                    account_id,effective_at,status,created_at
                ) VALUES ('movement-1','movement-op',0,'user-1','ADJUSTMENT',-1250,'PEN','cash-1',2000,'POSTED',2000)""",
            )
            db.execSQL(
                """INSERT INTO ledger_entries (
                    id,user_id,transaction_id,account_id,role,signed_amount_minor,currency_code,created_at
                ) VALUES ('ledger-1','user-1','legacy-movement','cash-1','SOURCE',-1250,'PEN',2000)""",
            )
        }

        helper.runMigrationsAndValidate(name, 19, true, MIGRATION_18_19).use { db ->
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM accounts WHERE id='cash-1' AND initial_balance_minor_units=100000"))
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM transactions WHERE id='legacy-movement' AND amount_minor=1250 AND status='ACTIVE'"))
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM financial_movements WHERE id='movement-1' AND amount_minor_units=-1250"))
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM ledger_entries WHERE id='ledger-1' AND signed_amount_minor=-1250"))
            assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM debts"))
            assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM debt_installments"))
            assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM debt_events"))
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.scalarLong(sql: String): Long =
        query(sql).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }
}
