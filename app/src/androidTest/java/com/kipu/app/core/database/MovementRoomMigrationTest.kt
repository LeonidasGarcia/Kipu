package com.kipu.app.core.database

import android.database.Cursor
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheEntity
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
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
    fun debtEventInterestLinkMigrationPreservesHistoryAndAddsOwnerScopedForeignKey() {
        val name = "debt-interest-link-v19"
        helper.createDatabase(name, 19).use { db ->
            db.execSQL("""CREATE TABLE debts (
                id TEXT NOT NULL, user_id TEXT NOT NULL, obligation_type TEXT NOT NULL,
                counterparty_name TEXT NOT NULL, total_minor INTEGER NOT NULL, currency_code TEXT NOT NULL,
                opened_on TEXT NOT NULL, opening_mode TEXT NOT NULL DEFAULT 'HISTORICAL', due_date TEXT,
                reminder_lead_days INTEGER, notes TEXT, status TEXT NOT NULL DEFAULT 'ACTIVE',
                sync_state TEXT NOT NULL DEFAULT 'PENDING', revision INTEGER NOT NULL DEFAULT 1,
                deleted_at INTEGER, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
                PRIMARY KEY(user_id, id)
            )""")
            db.execSQL("""CREATE TABLE debt_installments (
                id TEXT NOT NULL, user_id TEXT NOT NULL, debt_id TEXT NOT NULL, installment_number INTEGER NOT NULL,
                due_date TEXT NOT NULL, amount_minor INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'PLANNED',
                revision INTEGER NOT NULL DEFAULT 1, deleted_at INTEGER, created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL, PRIMARY KEY(user_id, id),
                FOREIGN KEY(user_id, debt_id) REFERENCES debts(user_id, id) ON UPDATE NO ACTION ON DELETE NO ACTION
            )""")
            db.execSQL("CREATE UNIQUE INDEX index_debt_installments_user_id_debt_id_id ON debt_installments(user_id, debt_id, id)")
            db.execSQL("""CREATE TABLE debt_events (
                id TEXT NOT NULL, user_id TEXT NOT NULL, debt_id TEXT NOT NULL, transaction_id TEXT,
                installment_id TEXT, event_type TEXT NOT NULL, amount_minor INTEGER NOT NULL,
                principal_delta_minor INTEGER, occurred_at INTEGER NOT NULL, created_at INTEGER NOT NULL,
                PRIMARY KEY(user_id, id),
                FOREIGN KEY(user_id, debt_id) REFERENCES debts(user_id, id) ON UPDATE NO ACTION ON DELETE NO ACTION,
                FOREIGN KEY(user_id, transaction_id) REFERENCES transactions(user_id, id) ON UPDATE NO ACTION ON DELETE NO ACTION,
                FOREIGN KEY(user_id, debt_id, installment_id) REFERENCES debt_installments(user_id, debt_id, id) ON UPDATE NO ACTION ON DELETE NO ACTION
            )""")
            db.execSQL("""INSERT INTO debts (id,user_id,obligation_type,counterparty_name,total_minor,currency_code,
                opened_on,opening_mode,created_at,updated_at) VALUES ('debt','owner','PAYABLE','Proveedor',5000,'PEN','2026-10-08','HISTORICAL',1,1)""")
            db.execSQL("""INSERT INTO debt_events (id,user_id,debt_id,event_type,amount_minor,occurred_at,created_at)
                VALUES ('event','owner','debt','ADJUSTMENT',0,1,1)""")
        }

        helper.runMigrationsAndValidate(name, 20, true, MIGRATION_19_20).use { db ->
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM debt_events WHERE id='event' AND interest_transaction_id IS NULL"))
            assertEquals(4L, db.scalarLong("SELECT COUNT(DISTINCT id) FROM pragma_foreign_key_list('debt_events')"))
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='index_debt_events_user_id_interest_transaction_id'"))
        }
    }
    @Test
    fun freshRoomDatabaseInstallsMovementEvidenceGuards() {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            KipuDatabase::class.java,
        ).addCallback(MovementSchemaCallback()).build()
        try {
            val sqlite = database.openHelper.writableDatabase
            assertEquals(8L, sqlite.scalarLong(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND name LIKE 'immutable_%'",
            ))
            assertEquals(2L, sqlite.scalarLong(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND name LIKE 'movement_head_owner_%'",
            ))
        } finally {
            database.close()
        }
    }

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

    @Test
    fun upgradesSixteenPreservingPendingBytesLedgerAndUncertainFailedHistory() {
        val name = "movement-v16-revisions"
        val originalPayload = "{\"contract_version\":1,\"request_hash\":\"old-hash\",\"note\":\"Ã±;note:b\"}"
        helper.createDatabase(name,16).use { db ->
            db.execSQL("""INSERT INTO accounts (id,user_id,creation_operation_id,alias,type,currency,
                initial_balance_minor_units,opened_at,is_archived,remote_revision,created_at,updated_at)
                VALUES ('account','user','opening','Cash','CASH','PEN',0,1,0,0,1,1)""")
            db.execSQL("""INSERT INTO transactions (id,user_id,type,amount_minor,currency_code,source_account_id,
                occurred_at,status,sync_status,created_at,updated_at)
                VALUES ('income','user','INCOME',800,'PEN','account',1000,'ACTIVE','PENDING',1,1),
                       ('failed','user','INCOME',500,'PEN','account',2000,'FAILED','FAILED_PERMANENT',1,1)""")
            db.execSQL("""INSERT INTO ledger_entries(id,user_id,transaction_id,account_id,role,signed_amount_minor,currency_code,created_at)
                VALUES ('original-entry','user','income','account','DESTINATION',800,'PEN',1000)""")
            db.execSQL("""INSERT INTO balance_projections(user_id,account_id,balance_minor,currency_code,updated_at)
                VALUES ('user','account',800,'PEN',1)""")
            db.execSQL("""INSERT INTO movement_outbox(id,user_id,idempotency_key,aggregate_id,payload,state,attempt_count,created_at,updated_at)
                VALUES ('command','user','original-key','income',?,'IN_FLIGHT',3,1,1)""",arrayOf(originalPayload))
            db.execSQL("""INSERT INTO local_command_receipts(user_id,idempotency_key,request_hash,transaction_id,status,created_at,updated_at)
                VALUES ('user','original-key','old-hash','income','APPLIED',1,1)""")
        }
        helper.runMigrationsAndValidate(name,17,true,MIGRATION_16_17).use { db ->
            assertEquals(2L,db.scalarLong("SELECT COUNT(*) FROM transactions"))
            assertEquals(1L,db.scalarLong("SELECT COUNT(*) FROM ledger_entries"))
            assertEquals(800L,db.scalarLong("SELECT SUM(signed_amount_minor) FROM ledger_entries"))
            assertEquals(800L,db.scalarLong("SELECT balance_minor FROM balance_projections WHERE account_id='account'"))
            assertEquals(2L,db.scalarLong("SELECT COUNT(*) FROM transaction_revisions WHERE provenance='MIGRATION_BASELINE'"))
            assertEquals(0L,db.scalarLong("SELECT COUNT(*) FROM movement_official_revisions"))
            assertEquals(0L,db.scalarLong("SELECT COUNT(*) FROM movement_ledger_effects"))
            assertEquals(0L,db.scalarLong("SELECT COUNT(*) FROM transactions WHERE acknowledged_revision IS NOT NULL OR source IS NOT NULL"))
            assertEquals("FAILED",db.scalarString("SELECT status FROM transactions WHERE id='failed'"))
            assertEquals(originalPayload,db.scalarString("SELECT payload FROM movement_outbox WHERE id='command'"))
            assertEquals("IN_FLIGHT",db.scalarString("SELECT state FROM movement_outbox WHERE id='command'"))
            assertEquals(3L,db.scalarLong("SELECT attempt_count FROM movement_outbox WHERE id='command'"))
            assertEquals(1L,db.scalarLong("SELECT contract_version FROM movement_outbox WHERE id='command'"))
            assertEquals("old-hash",db.scalarString("SELECT request_hash FROM local_command_receipts"))
            db.query("SELECT new_payload FROM transaction_revisions WHERE transaction_id='income'").use { c ->
                c.moveToFirst()
                val payload=kotlinx.serialization.json.Json.parseToJsonElement(c.getString(0))
                assertTrue(payload.toString().contains("800"))
            }
            assertWriteRejected { db.execSQL("UPDATE transaction_revisions SET new_payload='{}'") }
            assertWriteRejected { db.execSQL("DELETE FROM transaction_revisions") }
        }
    }

    @Test
    fun upgradesSeventeenPreservingEntitlementCacheAndAddingEmptySignedGrantFields() {
        val name = "plans-v17-offline-grant"
        val userId = "10000000-0000-0000-0000-000000000001"
        helper.createDatabase(name, 17).use { db ->
            db.execSQL("""INSERT INTO accounts (id,user_id,creation_operation_id,alias,type,currency,
                initial_balance_minor_units,opened_at,is_archived,remote_revision,created_at,updated_at)
                VALUES ('account','$userId','opening','Cash','CASH','PEN',0,1,0,0,1,1)""")
            db.execSQL("""INSERT INTO transactions (id,user_id,type,amount_minor,currency_code,source_account_id,
                occurred_at,status,sync_status,created_at,updated_at)
                VALUES ('income','$userId','INCOME',800,'PEN','account',1000,'ACTIVE','PENDING',1,1)""")
            db.execSQL("""INSERT INTO ledger_entries(id,user_id,transaction_id,account_id,role,signed_amount_minor,currency_code,created_at)
                VALUES ('original-entry','$userId','income','account','DESTINATION',800,'PEN',1000)""")
            db.execSQL("""INSERT INTO balance_projections(user_id,account_id,balance_minor,currency_code,updated_at)
                VALUES ('$userId','account',800,'PEN',1)""")
            db.execSQL("""INSERT INTO movement_outbox(id,user_id,idempotency_key,aggregate_id,payload,state,attempt_count,created_at,updated_at)
                VALUES ('command','$userId','original-key','income','{"request_hash":"old-hash"}','IN_FLIGHT',3,1,1)""")
            db.execSQL(
                """INSERT INTO feature_access_cache
                   (user_id,policy_version,effective_tier,entitlement_expires_at,verified_at,source)
                   VALUES (?,?,?,?,?,?)""",
                arrayOf<Any?>(userId, 1, "PREMIUM", 1_800_000_000_000L, 1_799_000_000_000L, "VERIFIED_SERVER"),
            )
        }

        helper.runMigrationsAndValidate(name, 18, true, MIGRATION_17_18).use { db ->
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM transactions WHERE id='income'"))
            assertEquals(800L, db.scalarLong("SELECT SUM(signed_amount_minor) FROM ledger_entries WHERE transaction_id='income'"))
            assertEquals(800L, db.scalarLong("SELECT balance_minor FROM balance_projections WHERE account_id='account'"))
            assertEquals("IN_FLIGHT", db.scalarString("SELECT state FROM movement_outbox WHERE id='command'"))
            assertEquals(3L, db.scalarLong("SELECT attempt_count FROM movement_outbox WHERE id='command'"))
            assertEquals("PREMIUM", db.scalarString("SELECT effective_tier FROM feature_access_cache WHERE user_id='$userId'"))
            assertEquals("VERIFIED_SERVER", db.scalarString("SELECT source FROM feature_access_cache WHERE user_id='$userId'"))
            assertEquals(1_799_000_000_000L, db.scalarLong("SELECT verified_at FROM feature_access_cache WHERE user_id='$userId'"))
            assertEquals(0L, db.scalarLong("""SELECT COUNT(*) FROM feature_access_cache
                WHERE user_id='$userId' AND (offline_grant_payload IS NOT NULL OR offline_grant_signature IS NOT NULL
                    OR offline_grant_key_id IS NOT NULL OR offline_anchor_elapsed_ms IS NOT NULL
                    OR offline_anchor_boot_count IS NOT NULL)"""))
        }
    }

    @Test
    fun signedGrantAndMonotonicAnchorPersistTogetherInRoomCache() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java).build()
        val userId = UUID.fromString("10000000-0000-0000-0000-000000000001")
        val cache = FeatureAccessCacheEntity(
            userId = userId,
            policyVersion = 1,
            effectiveTier = "PREMIUM",
            entitlementExpiresAt = Instant.ofEpochMilli(1_800_000_000_000L),
            verifiedAt = Instant.ofEpochMilli(1_799_000_000_000L),
            source = "SIGNED_SERVER_GRANT",
            offlineGrantPayload = "signed-payload",
            offlineGrantSignature = "signed-signature",
            offlineGrantKeyId = "release-key-v1",
            offlineAnchorElapsedRealtimeMillis = 45_000L,
            offlineAnchorBootCount = 12,
        )
        try {
            database.featureAccessCacheDao().putVerified(cache)
            assertEquals(cache, database.featureAccessCacheDao().get(userId))
        } finally {
            database.close()
        }
    }

    @Test
    fun proposalsCanShareNumberButOfficialAssignmentAndAliasesAreUniqueAndOwnerScoped() {
        val name="movement-v16-branches"
        helper.createDatabase(name,16).use { db ->
            db.execSQL("""INSERT INTO transactions(id,user_id,type,amount_minor,currency_code,occurred_at,status,sync_status,created_at,updated_at)
                VALUES ('movement','owner','INCOME',100,'PEN',1000,'ACTIVE','SYNCED',1,1)""")
            db.execSQL("""INSERT INTO ledger_entries(id,user_id,transaction_id,account_id,role,signed_amount_minor,currency_code,created_at)
                VALUES ('local-entry','owner','movement','account','DESTINATION',100,'PEN',1000)""")
        }
        helper.runMigrationsAndValidate(name,17,true,MIGRATION_16_17).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            for (id in listOf("proposal-a","proposal-b")) {
                db.execSQL("""INSERT INTO transaction_revisions(user_id,revision_id,transaction_id,command_id,command_type,
                    base_revision,local_revision,new_payload,provenance,created_at)
                    VALUES ('owner',?,'movement',?,'REVISE',1,2,'{}','LOCAL_COMMAND',1)""",arrayOf(id,id))
            }
            assertEquals(2L,db.scalarLong("SELECT COUNT(*) FROM transaction_revisions WHERE local_revision=2"))
            db.execSQL("""INSERT INTO movement_official_revisions(user_id,transaction_id,official_revision,revision_id,official_revision_id,assigned_at)
                VALUES ('owner','movement',2,'proposal-a','remote-revision',1)""")
            assertWriteRejected { db.execSQL("""INSERT INTO movement_official_revisions(user_id,transaction_id,official_revision,revision_id,official_revision_id,assigned_at)
                VALUES ('owner','movement',2,'proposal-b','other-remote-revision',1)""") }
            assertWriteRejected { db.execSQL("""INSERT INTO movement_official_revisions(user_id,transaction_id,official_revision,revision_id,official_revision_id,assigned_at)
                VALUES ('foreign','movement',2,'proposal-a','foreign-remote-revision',1)""") }
            db.execSQL("""INSERT INTO movement_ledger_effects(user_id,command_id,effect_ordinal,transaction_id,revision_id,ledger_entry_id)
                VALUES ('owner','proposal-a',0,'movement','proposal-a','local-entry')""")
            db.execSQL("""INSERT INTO movement_ledger_aliases(user_id,physical_entry_id,command_id,effect_ordinal)
                VALUES ('owner','remote-entry','proposal-a',0)""")
            assertWriteRejected { db.execSQL("""INSERT INTO movement_ledger_aliases(user_id,physical_entry_id,command_id,effect_ordinal)
                VALUES ('foreign','remote-entry','proposal-a',0)""") }
            assertEquals(1L,db.scalarLong("SELECT COUNT(*) FROM ledger_entries"))
            assertEquals(100L,db.scalarLong("SELECT SUM(signed_amount_minor) FROM ledger_entries"))
        }
    }

    private fun assertWriteRejected(block: () -> Unit) {
        var rejected=false
        try { block() } catch (_: android.database.sqlite.SQLiteException) { rejected=true }
        assertTrue(rejected)
    }
    private fun androidx.sqlite.db.SupportSQLiteDatabase.scalarString(sql: String): String =
        query(sql).use { c -> c.moveToFirst(); c.getString(0) }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.scalarLong(sql: String): Long =
        query(sql).use { cursor: Cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }
}
