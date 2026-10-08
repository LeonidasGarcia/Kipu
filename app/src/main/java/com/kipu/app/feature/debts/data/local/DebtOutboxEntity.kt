package com.kipu.app.feature.debts.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "debt_command_outbox",
    primaryKeys = ["user_id", "operation_id"],
    indices = [
        Index(value = ["user_id", "state", "next_attempt_at", "created_at"]),
        Index(value = ["user_id", "debt_id", "created_at"]),
    ],
)
data class DebtOutboxEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "operation_id") val operationId: String,
    @ColumnInfo(name = "debt_id") val debtId: String,
    @ColumnInfo(name = "command_type") val commandType: String,
    @ColumnInfo(name = "request_hash") val requestHash: String,
    @ColumnInfo(name = "payload") val payload: String,
    @ColumnInfo(name = "state", defaultValue = "'PENDING'") val state: String = "PENDING",
    @ColumnInfo(name = "attempt_count", defaultValue = "0") val attemptCount: Int = 0,
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAt: Long? = null,
    @ColumnInfo(name = "lease_until") val leaseUntil: Long? = null,
    @ColumnInfo(name = "last_error_code") val lastErrorCode: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
