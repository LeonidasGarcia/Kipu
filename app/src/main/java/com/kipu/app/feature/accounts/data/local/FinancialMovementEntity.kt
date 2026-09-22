package com.kipu.app.feature.accounts.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "financial_movements",
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
        Index(value = ["user_id", "operation_id", "operation_sequence"], unique = true),
        Index(value = ["user_id", "opening_account_id"], unique = true),
        Index(value = ["user_id", "account_id", "status", "effective_at"]),
        Index(value = ["user_id", "card_id", "status", "effective_at"]),
        Index(value = ["user_id", "category_id"]),
        Index(value = ["merchant_id"]),
    ],
)
data class FinancialMovementEntity(
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "operation_id")
    val operationId: String,
    @ColumnInfo(name = "operation_sequence")
    val operationSequence: Int,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "kind")
    val kind: String,
    @ColumnInfo(name = "amount_minor_units")
    val amountMinorUnits: Long,
    @ColumnInfo(name = "currency")
    val currency: String,
    @ColumnInfo(name = "account_id")
    val accountId: String? = null,
    @ColumnInfo(name = "card_id")
    val cardId: String? = null,
    @ColumnInfo(name = "opening_account_id")
    val openingAccountId: String? = null,
    @ColumnInfo(name = "effective_at")
    val effectiveAt: Long,
    @ColumnInfo(name = "status")
    val status: String = "POSTED",
    @ColumnInfo(name = "reverses_movement_id")
    val reversesMovementId: String? = null,
    @ColumnInfo(name = "adjusts_movement_id")
    val adjustsMovementId: String? = null,
    @ColumnInfo(name = "category_id")
    val categoryId: String? = null,
    @ColumnInfo(name = "merchant_id")
    val merchantId: String? = null,
    @ColumnInfo(name = "merchant_provisional_text")
    val merchantProvisionalText: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
