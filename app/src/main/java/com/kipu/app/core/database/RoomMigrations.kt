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
