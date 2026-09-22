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

