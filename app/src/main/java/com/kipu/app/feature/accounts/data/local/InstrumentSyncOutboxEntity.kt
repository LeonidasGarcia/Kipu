package com.kipu.app.feature.accounts.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "instrument_sync_outbox",
    primaryKeys = ["user_id", "operation_id"],
    indices = [
        Index(value = ["user_id", "state", "next_attempt_at"]),
        Index(value = ["user_id", "state", "lease_expires_at"]),
        Index(value = ["user_id", "aggregate_type", "aggregate_id"]),
    ],
)
data class InstrumentSyncOutboxEntity(
    @ColumnInfo(name = "operation_id")
    val operationId: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "command_type")
    val commandType: String,
    @ColumnInfo(name = "aggregate_type")
    val aggregateType: String,
    @ColumnInfo(name = "aggregate_id")
    val aggregateId: String,
    @ColumnInfo(name = "predecessor_operation_id")
    val predecessorOperationId: String? = null,
    @ColumnInfo(name = "expected_revision")
    val expectedRevision: Long? = null,
    @ColumnInfo(name = "contract_version")
    val contractVersion: Int = 1,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
    @ColumnInfo(name = "payload_hash")
    val payloadHash: String,
    @ColumnInfo(name = "state")
    val state: String = "PENDING",
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,
    @ColumnInfo(name = "next_attempt_at")
    val nextAttemptAt: Long? = null,
    @ColumnInfo(name = "lease_expires_at")
    val leaseExpiresAt: Long? = null,
    @ColumnInfo(name = "error_code")
    val errorCode: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
