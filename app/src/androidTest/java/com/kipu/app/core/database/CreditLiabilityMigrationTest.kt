package com.kipu.app.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreditLiabilityMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        KipuDatabase::class.java,
    )

    @Test
    fun migrationLinksCardsAndRebuildsPurchaseAndPaymentLiabilityEffects() {
        val name = "credit-liability-v14-to-v15"
        helper.createDatabase(name, 14).use { db ->
            db.execSQL(
                """INSERT INTO accounts (
                    id,user_id,creation_operation_id,alias,type,currency,initial_balance_minor_units,
                    opened_at,is_archived,remote_revision,created_at,updated_at
                ) VALUES (
                    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','11111111-1111-4111-8111-111111111111',
                    '33333333-3333-4333-8333-333333333333','Bank','SAVINGS','PEN',100000,1,0,1,1,1
                )""",
            )
            db.execSQL(
                """INSERT INTO cards (
                    id,user_id,creation_operation_id,account_id,alias,type,currency,network,issuer,last_four_digits,
                    credit_limit_minor_units,billing_day,due_day,is_archived,remote_revision,created_at,updated_at
                ) VALUES (
                    '22222222-2222-4222-8222-222222222222','11111111-1111-4111-8111-111111111111',
                    '44444444-4444-4444-8444-444444444444','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','Visa','CREDIT','PEN','VISA','Bank','1234',
                    50000,10,20,0,1,1,1
                )""",
            )
            db.execSQL(
                """INSERT INTO transactions (
                    id,user_id,type,amount_minor,currency_code,category_id,occurred_at,status,sync_status,
                    created_at,updated_at,card_id,operation_kind,installment_count
                ) VALUES (
                    'purchase','11111111-1111-4111-8111-111111111111','EXPENSE',10000,'PEN','food',1000,
                    'ACTIVE','SYNCED',1000,1000,'22222222-2222-4222-8222-222222222222','CARD_PURCHASE',1
                )""",
            )
            db.execSQL(
                """INSERT INTO transactions (
                    id,user_id,type,amount_minor,currency_code,source_account_id,occurred_at,status,sync_status,
                    created_at,updated_at,card_id,operation_kind,installment_count
                ) VALUES (
                    'payment','11111111-1111-4111-8111-111111111111','TRANSFER',3000,'PEN',
                    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',2000,'ACTIVE','SYNCED',2000,2000,
                    '22222222-2222-4222-8222-222222222222','CARD_PAYMENT',1
                )""",
            )
            db.execSQL(
                """INSERT INTO credit_installments (
                    id,user_id,transaction_id,installment_number,due_date,principal_minor,interest_minor,
                    status,revision,created_at,updated_at
                ) VALUES ('installment','11111111-1111-4111-8111-111111111111','purchase',1,20650,10000,0,'PENDING',1,1000,1000)""",
            )
            db.execSQL(
                """INSERT INTO credit_payment_allocations (
                    id,user_id,payment_transaction_id,installment_id,allocated_minor,created_at
                ) VALUES ('allocation','11111111-1111-4111-8111-111111111111','payment','installment',3000,2000)""",
            )
            db.execSQL(
                """INSERT INTO ledger_entries (
                    id,user_id,transaction_id,account_id,role,signed_amount_minor,currency_code,created_at
                ) VALUES ('cash-payment','11111111-1111-4111-8111-111111111111','payment',
                    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','SOURCE',-3000,'PEN',2000)""",
            )
        }

        helper.runMigrationsAndValidate(name, 15, true, MIGRATION_14_15).use { db ->
            val liabilityAccountId = db.query(
                "SELECT account_id FROM cards WHERE id='22222222-2222-4222-8222-222222222222'",
            ).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getString(0)
            }
            assertNotEquals("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", liabilityAccountId)
            assertEquals("Pasivo tarjeta •••• 1234", db.query(
                "SELECT alias FROM accounts WHERE id=?",
                arrayOf(liabilityAccountId),
            ).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getString(0)
            })
            assertEquals("CREDIT_LIABILITY", db.query(
                "SELECT type FROM accounts WHERE id=?",
                arrayOf(liabilityAccountId),
            ).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getString(0)
            })
            assertEquals("SAVINGS", db.query(
                "SELECT type FROM accounts WHERE id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'",
            ).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getString(0)
            })
            assertEquals(-10_000L, db.query(
                "SELECT signed_amount_minor FROM ledger_entries WHERE transaction_id='purchase' AND role='LIABILITY'",
            ).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getLong(0)
            })
            assertEquals(3_000L, db.query(
                "SELECT signed_amount_minor FROM ledger_entries WHERE transaction_id='payment' AND role='LIABILITY'",
            ).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getLong(0)
            })
            assertEquals(-7_000L, db.query(
                "SELECT balance_minor FROM balance_projections WHERE account_id=?",
                arrayOf(liabilityAccountId),
            ).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getLong(0)
            })
            assertEquals(-3_000L, db.query(
                "SELECT balance_minor FROM balance_projections WHERE account_id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'",
            ).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getLong(0)
            })
        }
    }
}
