package com.kipu.app.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreditRoomMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        KipuDatabase::class.java,
    )

    @Test
    fun versionTenRowsSurviveAndCreditRowsUseOwnerCompositeReferencesAndLongAmounts() {
        val name = "credit-v10-to-v11"
        helper.createDatabase(name, 10).use { db ->
            db.execSQL(
                """INSERT INTO cards (
                    id,user_id,creation_operation_id,alias,type,currency,network,issuer,
                    last_four_digits,is_archived,remote_revision,created_at,updated_at
                ) VALUES ('card-1','user-1','card-op-1','Principal','CREDIT','PEN','VISA','BCP',
                    '1234',0,1,1000,1000)""",
            )
            db.execSQL(
                """INSERT INTO transactions (
                    id,user_id,type,amount_minor,currency_code,occurred_at,status,sync_status,created_at,updated_at
                ) VALUES ('purchase-1','user-1','EXPENSE',10000,'PEN',2000,'ACTIVE','SYNCED',2000,2000)""",
            )
            db.execSQL(
                """INSERT INTO transactions (
                    id,user_id,type,amount_minor,currency_code,occurred_at,status,sync_status,created_at,updated_at
                ) VALUES ('payment-1','user-1','TRANSFER',3334,'PEN',3000,'ACTIVE','SYNCED',3000,3000)""",
            )
            db.execSQL(
                """INSERT INTO accounts (
                    id,user_id,creation_operation_id,alias,type,currency,initial_balance_minor_units,
                    opened_at,is_archived,remote_revision,created_at,updated_at
                ) VALUES ('cash-1','user-1','cash-op','Ahorros','SAVINGS','PEN',50000,1000,0,1,1000,1000)""",
            )
            db.execSQL(
                """INSERT INTO financial_movements (
                    id,operation_id,operation_sequence,user_id,kind,amount_minor_units,currency,card_id,effective_at,status,created_at
                ) VALUES ('purchase-movement','purchase-op',0,'user-1','CREDIT_PURCHASE',10000,'PEN','card-1',2000000,'POSTED',2000000)""",
            )
            db.execSQL(
                """INSERT INTO financial_movements (
                    id,operation_id,operation_sequence,user_id,kind,amount_minor_units,currency,account_id,effective_at,status,created_at
                ) VALUES ('cash-payment-movement','payment-op',0,'user-1','CARD_PAYMENT_CASH',-3334,'PEN','cash-1',3000000,'POSTED',3000000)""",
            )
            db.execSQL(
                """INSERT INTO financial_movements (
                    id,operation_id,operation_sequence,user_id,kind,amount_minor_units,currency,card_id,effective_at,status,created_at
                ) VALUES ('liability-payment-movement','payment-op',1,'user-1','CARD_PAYMENT_LIABILITY',-3334,'PEN','card-1',3000000,'POSTED',3000000)""",
            )
            db.execSQL(
                """INSERT INTO transactions (
                    id,user_id,type,amount_minor,currency_code,source_account_id,legacy_kind,occurred_at,
                    status,sync_status,created_at,updated_at
                ) VALUES ('legacy:cash-payment-movement','user-1','EXPENSE',3334,'PEN','cash-1','CARD_PAYMENT_CASH',3000,'ACTIVE','MIGRATED_LOCAL',3000,3000)""",
            )
        }

        helper.runMigrationsAndValidate(name, 11, true, MIGRATION_10_11).use { db ->
            // MigrationTestHelper's raw connection does not enable Room's FK pragma
            // automatically; enable it to exercise the owner-composite constraints.
            db.execSQL("PRAGMA foreign_keys = ON")
            assertEquals(1L, db.scalarLong("PRAGMA foreign_keys"))
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM cards WHERE id='card-1' AND user_id='user-1'"))
            assertNull(db.scalarLongOrNull("SELECT personal_tea_bps FROM cards WHERE id='card-1' AND user_id='user-1'"))

            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM transactions WHERE user_id='user-1' AND operation_kind='LEGACY_CREDIT_BALANCE' AND amount_minor=6666"))
            assertEquals(6666L, db.scalarLong("SELECT principal_minor FROM credit_installments WHERE user_id='user-1' AND status='PENDING' AND transaction_id LIKE 'legacy-credit-balance:%'"))
            assertEquals("TRANSFER", db.scalarString("SELECT type FROM transactions WHERE id='legacy:cash-payment-movement'"))
            assertEquals("card-1", db.scalarString("SELECT card_id FROM transactions WHERE id='legacy:cash-payment-movement'"))
            assertEquals("CARD_PAYMENT", db.scalarString("SELECT operation_kind FROM transactions WHERE id='legacy:cash-payment-movement'"))

            db.execSQL(
                """INSERT INTO credit_installments (
                    id,user_id,transaction_id,installment_number,due_date,principal_minor,interest_minor,
                    status,revision,created_at,updated_at
                ) VALUES ('installment-1','user-1','purchase-1',1,20639,3334,0,'PENDING',1,4000,4000)""",
            )
            db.execSQL(
                """INSERT INTO credit_payment_allocations (
                    id,user_id,payment_transaction_id,installment_id,allocated_minor,created_at
                ) VALUES ('allocation-1','user-1','payment-1','installment-1',3334,5000)""",
            )

            assertEquals(3334L, db.scalarLong("SELECT principal_minor FROM credit_installments WHERE id='installment-1'"))
            assertEquals(3334L, db.scalarLong("SELECT allocated_minor FROM credit_payment_allocations WHERE id='allocation-1'"))

            try {
                db.execSQL(
                    """INSERT INTO credit_installments (
                        id,user_id,transaction_id,installment_number,due_date,principal_minor,interest_minor,
                        status,revision,created_at,updated_at
                    ) VALUES ('cross-owner','user-2','purchase-1',1,20639,1,0,'PENDING',1,4000,4000)""",
                )
                throw AssertionError("Cross-owner transaction reference must be rejected")
            } catch (_: SQLiteConstraintException) {
                // The transaction identity is owner-composite.
            }

            db.execSQL("UPDATE cards SET personal_tea_bps=2850 WHERE user_id='user-1' AND id='card-1'")
            assertEquals(2850L, db.scalarLong("SELECT personal_tea_bps FROM cards WHERE id='card-1'"))
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.scalarLong(sql: String): Long =
        query(sql).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.scalarLongOrNull(sql: String): Long? =
        query(sql).use { cursor ->
            check(cursor.moveToFirst())
            if (cursor.isNull(0)) null else cursor.getLong(0)
        }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.scalarString(sql: String): String =
        query(sql).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0)
        }
}
