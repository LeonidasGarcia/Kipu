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
 * Migration 3 -> 4: Adds EP-MOV tables for transactions, ledger_entries,
 * local_command_receipts, movement_outbox, and balance_projections.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
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

