package com.kipu.app.feature.accounts.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "cards",
    primaryKeys = ["user_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["user_id", "id"],
            childColumns = ["user_id", "account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["user_id", "creation_operation_id"], unique = true),
        Index(value = ["user_id", "is_archived", "type"]),
        Index(value = ["user_id", "issuer", "network", "last_four_digits"]),
        Index(value = ["user_id", "account_id"]),
    ],
)
data class CardEntity(
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "creation_operation_id")
    val creationOperationId: String,
    @ColumnInfo(name = "account_id")
    val accountId: String? = null,
    @ColumnInfo(name = "alias")
    val alias: String? = null,
    @ColumnInfo(name = "type")
    val type: String, // DEBIT, CREDIT
    @ColumnInfo(name = "currency")
    val currency: String,
    @ColumnInfo(name = "network")
    val network: String, // VISA, MASTERCARD, AMEX, OTHER
    @ColumnInfo(name = "issuer")
    val issuer: String,
    @ColumnInfo(name = "last_four_digits")
    val lastFourDigits: String,
    @ColumnInfo(name = "credit_limit_minor_units")
    val creditLimitMinorUnits: Long? = null,
    @ColumnInfo(name = "billing_day")
    val billingDay: Int? = null,
    @ColumnInfo(name = "due_day")
    val dueDay: Int? = null,
    @ColumnInfo(name = "preset_id")
    val presetId: String? = null,
    @ColumnInfo(name = "style_preset_id")
    val stylePresetId: String? = null,
    @ColumnInfo(name = "color")
    val color: String? = null,
    @ColumnInfo(name = "icon")
    val icon: String? = null,
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,
    @ColumnInfo(name = "remote_revision")
    val remoteRevision: Long = 0L,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "personal_tea_bps")
    val personalTeaBps: Int? = null,
)
