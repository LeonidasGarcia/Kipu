package com.kipu.app.feature.accounts.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "accounts",
    primaryKeys = ["user_id", "id"],
    indices = [
        Index(value = ["user_id", "creation_operation_id"], unique = true),
        Index(value = ["user_id", "is_archived"]),
        Index(value = ["user_id", "type"]),
    ],
)
data class AccountEntity(
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "creation_operation_id")
    val creationOperationId: String,
    @ColumnInfo(name = "alias")
    val alias: String,
    @ColumnInfo(name = "type")
    val type: String,
    @ColumnInfo(name = "currency")
    val currency: String,
    @ColumnInfo(name = "preset_id")
    val presetId: String?,
    @ColumnInfo(name = "color")
    val color: String?,
    @ColumnInfo(name = "icon")
    val icon: String?,
    @ColumnInfo(name = "initial_balance_minor_units")
    val initialBalanceMinorUnits: Long,
    @ColumnInfo(name = "opened_at")
    val openedAt: Long,
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,
    @ColumnInfo(name = "remote_revision")
    val remoteRevision: Long = 0L,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
