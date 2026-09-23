package com.kipu.app.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 1 -> 2: Adds EP-APS tables for profile caching, preference outbox,
 * device unlock settings, installation permission state, and account source consent,
 * preserving all existing EP-PLA tables without data loss.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `user_profiles` (
                `user_id` TEXT NOT NULL,
                `display_name` TEXT NOT NULL,
                `currency_code` TEXT NOT NULL,
                `time_zone` TEXT,
                `month_start` INTEGER NOT NULL,
                `hide_balances` INTEGER NOT NULL,
                `theme_mode` TEXT NOT NULL,
                `remote_revision` INTEGER NOT NULL,
                `remote_updated_at` INTEGER,
                `sync_state` TEXT NOT NULL,
                `updated_locally_at` INTEGER NOT NULL,
                PRIMARY KEY(`user_id`)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `profile_preference_outbox` (
                `operation_id` TEXT NOT NULL,
                `user_id` TEXT NOT NULL,
                `expected_revision` INTEGER NOT NULL,
                `payload` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `attempt_count` INTEGER NOT NULL,
                `next_attempt_at` INTEGER,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`operation_id`)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_profile_preference_outbox_user_id` ON `profile_preference_outbox` (`user_id`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `device_account_settings` (
                `user_id` TEXT NOT NULL,
                `local_unlock_enabled` INTEGER NOT NULL,
                `unlock_authenticators` INTEGER NOT NULL,
                PRIMARY KEY(`user_id`)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `installation_permission_state` (
                `source` TEXT NOT NULL,
                `device_authorization` TEXT NOT NULL,
                `last_checked_at` INTEGER NOT NULL,
                PRIMARY KEY(`source`)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `account_source_consent` (
                `user_id` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `consent_state` TEXT NOT NULL,
                `capability_state` TEXT NOT NULL,
                `explanation_version` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`user_id`, `source`)
            )
        """.trimIndent())
    }
}

/**
 * Migration 2 -> 3: Adds EP-CTA tables for accounts, cards, financial_movements,
 * and instrument_sync_outbox, preserving all existing EP-PLA and EP-APS tables.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. accounts
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `accounts` (
                `id` TEXT NOT NULL,
                `user_id` TEXT NOT NULL,
                `creation_operation_id` TEXT NOT NULL,
                `alias` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `currency` TEXT NOT NULL,
                `preset_id` TEXT,
                `color` TEXT,
                `icon` TEXT,
                `initial_balance_minor_units` INTEGER NOT NULL,
                `opened_at` INTEGER NOT NULL,
                `is_archived` INTEGER NOT NULL,
                `remote_revision` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`user_id`, `id`)
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_accounts_user_id_creation_operation_id` ON `accounts` (`user_id`, `creation_operation_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_user_id_is_archived` ON `accounts` (`user_id`, `is_archived`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_user_id_type` ON `accounts` (`user_id`, `type`)")

        // 2. cards
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `cards` (
                `id` TEXT NOT NULL,
                `user_id` TEXT NOT NULL,
                `creation_operation_id` TEXT NOT NULL,
                `account_id` TEXT,
                `alias` TEXT,
                `type` TEXT NOT NULL,
                `currency` TEXT NOT NULL,
                `network` TEXT NOT NULL,
                `issuer` TEXT NOT NULL,
                `last_four_digits` TEXT NOT NULL,
                `credit_limit_minor_units` INTEGER,
                `billing_day` INTEGER,
                `due_day` INTEGER,
                `preset_id` TEXT,
                `color` TEXT,
                `icon` TEXT,
                `is_archived` INTEGER NOT NULL,
                `remote_revision` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`user_id`, `id`),
                FOREIGN KEY(`user_id`, `account_id`) REFERENCES `accounts`(`user_id`, `id`) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_cards_user_id_creation_operation_id` ON `cards` (`user_id`, `creation_operation_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_user_id_is_archived_type` ON `cards` (`user_id`, `is_archived`, `type`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_user_id_issuer_network_last_four_digits` ON `cards` (`user_id`, `issuer`, `network`, `last_four_digits`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_user_id_account_id` ON `cards` (`user_id`, `account_id`)")

        // 3. financial_movements
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `financial_movements` (
                `id` TEXT NOT NULL,
                `operation_id` TEXT NOT NULL,
                `operation_sequence` INTEGER NOT NULL,
                `user_id` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `amount_minor_units` INTEGER NOT NULL,
                `currency` TEXT NOT NULL,
                `account_id` TEXT,
                `card_id` TEXT,
                `opening_account_id` TEXT,
                `effective_at` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `reverses_movement_id` TEXT,
                `adjusts_movement_id` TEXT,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`user_id`, `id`),
                FOREIGN KEY(`user_id`, `account_id`) REFERENCES `accounts`(`user_id`, `id`) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_financial_movements_user_id_operation_id_operation_sequence` ON `financial_movements` (`user_id`, `operation_id`, `operation_sequence`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_financial_movements_user_id_opening_account_id` ON `financial_movements` (`user_id`, `opening_account_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_movements_user_id_account_id_status_effective_at` ON `financial_movements` (`user_id`, `account_id`, `status`, `effective_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_movements_user_id_card_id_status_effective_at` ON `financial_movements` (`user_id`, `card_id`, `status`, `effective_at`)")

        // 4. instrument_sync_outbox
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `instrument_sync_outbox` (
                `operation_id` TEXT NOT NULL,
                `user_id` TEXT NOT NULL,
                `command_type` TEXT NOT NULL,
                `aggregate_type` TEXT NOT NULL,
                `aggregate_id` TEXT NOT NULL,
                `predecessor_operation_id` TEXT,
                `expected_revision` INTEGER,
                `contract_version` INTEGER NOT NULL,
                `payload_json` TEXT NOT NULL,
                `payload_hash` TEXT NOT NULL,
                `state` TEXT NOT NULL,
                `attempt_count` INTEGER NOT NULL,
                `next_attempt_at` INTEGER,
                `error_code` TEXT,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`user_id`, `operation_id`)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_instrument_sync_outbox_user_id_state_next_attempt_at` ON `instrument_sync_outbox` (`user_id`, `state`, `next_attempt_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_instrument_sync_outbox_user_id_aggregate_type_aggregate_id` ON `instrument_sync_outbox` (`user_id`, `aggregate_type`, `aggregate_id`)")
    }
}

/**
 * Migration 3 -> 4: Adds EP-CCO tables for categories, category_presentations,
 * merchant_catalog_cache, category_conflicts, category_sync_outbox, and extends
 * financial_movements with category and merchant classification columns.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. categories
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `categories` (
                `id` TEXT NOT NULL,
                `user_id` TEXT,
                `parent_id` TEXT,
                `origin` TEXT NOT NULL,
                `is_active` INTEGER NOT NULL,
                `remote_revision` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_user_id` ON `categories` (`user_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_parent_id` ON `categories` (`parent_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_user_id_is_active` ON `categories` (`user_id`, `is_active`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_origin` ON `categories` (`origin`)")

        // 2. category_presentations
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `category_presentations` (
                `category_id` TEXT NOT NULL,
                `user_id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `icon` TEXT NOT NULL,
                `color` TEXT NOT NULL,
                `remote_revision` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`user_id`, `category_id`)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_category_presentations_category_id` ON `category_presentations` (`category_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_category_presentations_user_id` ON `category_presentations` (`user_id`)")

        // 3. merchant_catalog_cache
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `merchant_catalog_cache` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `normalized_name` TEXT NOT NULL,
                `is_active` INTEGER NOT NULL,
                `version` INTEGER NOT NULL,
                `last_synced_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_merchant_catalog_cache_normalized_name` ON `merchant_catalog_cache` (`normalized_name`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_merchant_catalog_cache_is_active` ON `merchant_catalog_cache` (`is_active`)")

        // 4. category_conflicts
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `category_conflicts` (
                `id` TEXT NOT NULL,
                `category_id` TEXT NOT NULL,
                `user_id` TEXT NOT NULL,
                `conflict_type` TEXT NOT NULL,
                `local_version` TEXT NOT NULL,
                `remote_version` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `resolution_operation_id` TEXT,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_category_conflicts_user_id_status` ON `category_conflicts` (`user_id`, `status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_category_conflicts_category_id` ON `category_conflicts` (`category_id`)")

        // 5. category_sync_outbox
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `category_sync_outbox` (
                `operation_id` TEXT NOT NULL,
                `user_id` TEXT NOT NULL,
                `command_type` TEXT NOT NULL,
                `aggregate_type` TEXT NOT NULL,
                `aggregate_id` TEXT NOT NULL,
                `expected_revision` INTEGER,
                `payload_json` TEXT NOT NULL,
                `payload_hash` TEXT NOT NULL,
                `state` TEXT NOT NULL,
                `attempt_count` INTEGER NOT NULL,
                `next_attempt_at` INTEGER,
                `error_code` TEXT,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`user_id`, `operation_id`)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_category_sync_outbox_user_id_state_next_attempt_at` ON `category_sync_outbox` (`user_id`, `state`, `next_attempt_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_category_sync_outbox_user_id_aggregate_type_aggregate_id` ON `category_sync_outbox` (`user_id`, `aggregate_type`, `aggregate_id`)")

        // 6. Alter financial_movements with classification columns
        db.execSQL("ALTER TABLE `financial_movements` ADD COLUMN `category_id` TEXT")
        db.execSQL("ALTER TABLE `financial_movements` ADD COLUMN `merchant_id` TEXT")
        db.execSQL("ALTER TABLE `financial_movements` ADD COLUMN `merchant_provisional_text` TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_movements_user_id_category_id` ON `financial_movements` (`user_id`, `category_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_movements_merchant_id` ON `financial_movements` (`merchant_id`)")
    }
}

/**
 * Migration 4 -> 5: Adds EP-MOV tables for transactions, ledger_entries,
 * local_command_receipts, movement_outbox, and balance_projections.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. transactions
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `transactions` (
                `id` TEXT NOT NULL,
                `user_id` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `amount_minor` INTEGER NOT NULL,
                `currency_code` TEXT NOT NULL,
                `source_account_id` TEXT,
                `destination_account_id` TEXT,
                `category_id` TEXT,
                `merchant_id` TEXT,
                `occurred_at` INTEGER NOT NULL,
                `note` TEXT,
                `status` TEXT NOT NULL,
                `sync_status` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`user_id`, `id`),
                FOREIGN KEY(`user_id`, `source_account_id`) REFERENCES `accounts`(`user_id`, `id`) ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_user_id_occurred_at` ON `transactions` (`user_id`, `occurred_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_user_id_source_account_id_occurred_at` ON `transactions` (`user_id`, `source_account_id`, `occurred_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_user_id_sync_status` ON `transactions` (`user_id`, `sync_status`)")

        // 2. ledger_entries
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `ledger_entries` (
                `id` TEXT NOT NULL,
                `user_id` TEXT NOT NULL,
                `transaction_id` TEXT NOT NULL,
                `account_id` TEXT NOT NULL,
                `role` TEXT NOT NULL,
                `signed_amount_minor` INTEGER NOT NULL,
                `currency_code` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`user_id`, `id`)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_entries_user_id_transaction_id` ON `ledger_entries` (`user_id`, `transaction_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_entries_user_id_account_id` ON `ledger_entries` (`user_id`, `account_id`)")

        // 3. local_command_receipts
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `local_command_receipts` (
                `user_id` TEXT NOT NULL,
                `idempotency_key` TEXT NOT NULL,
                `request_hash` TEXT NOT NULL,
                `transaction_id` TEXT,
                `status` TEXT NOT NULL,
                `response_payload` TEXT,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`user_id`, `idempotency_key`)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_command_receipts_user_id_transaction_id` ON `local_command_receipts` (`user_id`, `transaction_id`)")

        // 4. movement_outbox
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `movement_outbox` (
                `id` TEXT NOT NULL,
                `user_id` TEXT NOT NULL,
                `idempotency_key` TEXT NOT NULL,
                `aggregate_id` TEXT NOT NULL,
                `payload` TEXT NOT NULL,
                `state` TEXT NOT NULL,
                `attempt_count` INTEGER NOT NULL,
                `next_attempt_at` INTEGER,
                `lease_until` INTEGER,
                `last_error_code` TEXT,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`user_id`, `id`)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_movement_outbox_user_id_state_next_attempt_at` ON `movement_outbox` (`user_id`, `state`, `next_attempt_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_movement_outbox_user_id_aggregate_id` ON `movement_outbox` (`user_id`, `aggregate_id`)")

        // 5. balance_projections
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `balance_projections` (
                `user_id` TEXT NOT NULL,
                `account_id` TEXT NOT NULL,
                `balance_minor` INTEGER NOT NULL,
                `currency_code` TEXT NOT NULL,
                `last_transaction_at` INTEGER,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`user_id`, `account_id`)
            )
        """.trimIndent())
    }
}

/**
 * Migration 5 -> 6: preserve posted account movements in the local ledger.
 * Card-only liability rows remain in financial_movements until the card model is migrated.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.query(
            """SELECT COUNT(*) FROM financial_movements
               WHERE account_id IS NOT NULL AND status = 'POSTED'
                 AND (amount_minor_units < -99999999999999 OR amount_minor_units > 99999999999999)"""
        ).use { cursor ->
            check(cursor.moveToFirst() && cursor.getLong(0) == 0L) {
                "Cannot migrate account movements outside the supported monetary range"
            }
        }

        db.execSQL("ALTER TABLE transactions ADD COLUMN legacy_kind TEXT")
        db.execSQL(
            """INSERT INTO transactions (
                   id, user_id, type, amount_minor, currency_code, source_account_id,
                   destination_account_id, category_id, merchant_id, legacy_kind,
                   occurred_at, note, status, sync_status, created_at, updated_at
               )
               SELECT 'legacy:' || id, user_id,
                   CASE WHEN amount_minor_units < 0 THEN 'EXPENSE' ELSE 'INCOME' END,
                   ABS(amount_minor_units), currency, account_id, NULL,
                   category_id, merchant_id, kind,
                   effective_at / 1000, NULL, 'ACTIVE', 'MIGRATED_LOCAL',
                   created_at / 1000, created_at / 1000
               FROM financial_movements
               WHERE account_id IS NOT NULL AND status = 'POSTED' AND amount_minor_units <> 0"""
        )
        db.execSQL(
            """INSERT INTO ledger_entries (
                   id, user_id, transaction_id, account_id, role,
                   signed_amount_minor, currency_code, created_at
               )
               SELECT 'legacy:' || id, user_id, 'legacy:' || id, account_id,
                   CASE WHEN amount_minor_units < 0 THEN 'SOURCE' ELSE 'DESTINATION' END,
                   amount_minor_units, currency, created_at / 1000
               FROM financial_movements
               WHERE account_id IS NOT NULL AND status = 'POSTED' AND amount_minor_units <> 0"""
        )
        db.execSQL(
            """INSERT OR REPLACE INTO balance_projections (
                   user_id, account_id, balance_minor, currency_code, last_transaction_at, updated_at
               )
               SELECT a.user_id, a.id, COALESCE(SUM(le.signed_amount_minor), 0),
                   a.currency, MAX(le.created_at), CAST(strftime('%s', 'now') AS INTEGER) * 1000
               FROM accounts a
               LEFT JOIN ledger_entries le ON le.user_id = a.user_id AND le.account_id = a.id
               GROUP BY a.user_id, a.id, a.currency"""
        )
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN merchant_provisional_text TEXT")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS movement_sync_checkpoints (
                user_id TEXT NOT NULL PRIMARY KEY,
                sequence INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

