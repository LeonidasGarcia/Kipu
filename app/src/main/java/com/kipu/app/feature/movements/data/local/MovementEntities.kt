package com.kipu.app.feature.movements.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.kipu.app.feature.accounts.data.local.AccountEntity

@Entity(
    tableName = "transactions",
    primaryKeys = ["user_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["user_id", "id"],
            childColumns = ["user_id", "source_account_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["user_id", "occurred_at"]),
        Index(value = ["user_id", "card_id", "occurred_at"]),
        Index(value = ["user_id", "source_account_id", "occurred_at"]),
        Index(value = ["user_id", "sync_status"]),
    ],
)
data class TransactionEntity(
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "type")
    val type: String,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "source_account_id")
    val sourceAccountId: String? = null,
    @ColumnInfo(name = "destination_account_id")
    val destinationAccountId: String? = null,
    @ColumnInfo(name = "category_id")
    val categoryId: String? = null,
    @ColumnInfo(name = "merchant_id")
    val merchantId: String? = null,
    @ColumnInfo(name = "merchant_provisional_text")
    val merchantProvisionalText: String? = null,
    @ColumnInfo(name = "legacy_kind")
    val legacyKind: String? = null,
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,
    @ColumnInfo(name = "note")
    val note: String? = null,
    @ColumnInfo(name = "status")
    val status: String = "ACTIVE",
    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "PENDING",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "card_id")
    val cardId: String? = null,
    @ColumnInfo(name = "operation_kind")
    val operationKind: String? = null,
    @ColumnInfo(name = "installment_count")
    val installmentCount: Int? = null,
    @ColumnInfo(name = "revision", defaultValue = "1") val revision: Long = 1L,
    @ColumnInfo(name = "acknowledged_revision") val acknowledgedRevision: Long? = null,
    @ColumnInfo(name = "current_revision_id") val currentRevisionId: String? = null,
    @ColumnInfo(name = "source") val source: String? = null,
)

@Entity(
    tableName = "ledger_entries",
    primaryKeys = ["user_id", "id"],
    indices = [
        Index(value = ["user_id", "transaction_id"]),
        Index(value = ["user_id", "account_id"]),
    ],
)
data class LedgerEntryEntity(
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "transaction_id")
    val transactionId: String,
    @ColumnInfo(name = "account_id")
    val accountId: String,
    @ColumnInfo(name = "role")
    val role: String,
    @ColumnInfo(name = "signed_amount_minor")
    val signedAmountMinor: Long,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "local_command_receipts",
    primaryKeys = ["user_id", "idempotency_key"],
    indices = [
        Index(value = ["user_id", "transaction_id"]),
    ],
)
data class LocalCommandReceiptEntity(
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "idempotency_key")
    val idempotencyKey: String,
    @ColumnInfo(name = "request_hash")
    val requestHash: String,
    @ColumnInfo(name = "transaction_id")
    val transactionId: String? = null,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "response_payload")
    val responsePayload: String? = null,
    @ColumnInfo(name = "contract_version", defaultValue = "1") val contractVersion: Int = 1,
    @ColumnInfo(name = "command_type", defaultValue = "'REGISTER'") val commandType: String = "REGISTER",
    @ColumnInfo(name = "expected_revision") val expectedRevision: Long? = null,
    @ColumnInfo(name = "resulting_revision") val resultingRevision: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "movement_outbox",
    primaryKeys = ["user_id", "id"],
    indices = [
        Index(value = ["user_id", "state", "next_attempt_at"]),
        Index(value = ["user_id", "aggregate_id"]),
    ],
)
data class MovementOutboxEntity(
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "idempotency_key")
    val idempotencyKey: String,
    @ColumnInfo(name = "aggregate_id")
    val aggregateId: String,
    @ColumnInfo(name = "payload")
    val payload: String,
    @ColumnInfo(name = "contract_version", defaultValue = "1") val contractVersion: Int = 1,
    @ColumnInfo(name = "command_type", defaultValue = "'REGISTER'") val commandType: String = "REGISTER",
    @ColumnInfo(name = "expected_revision") val expectedRevision: Long? = null,
    @ColumnInfo(name = "depends_on_command_id") val dependsOnCommandId: String? = null,
    @ColumnInfo(name = "revision_id") val revisionId: String? = null,
    @ColumnInfo(name = "state")
    val state: String = "PENDING",
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,
    @ColumnInfo(name = "next_attempt_at")
    val nextAttemptAt: Long? = null,
    @ColumnInfo(name = "lease_until")
    val leaseUntil: Long? = null,
    @ColumnInfo(name = "last_error_code")
    val lastErrorCode: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "balance_projections",
    primaryKeys = ["user_id", "account_id"],
)
data class BalanceProjectionEntity(
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "account_id")
    val accountId: String,
    @ColumnInfo(name = "balance_minor")
    val balanceMinor: Long,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "last_transaction_at")
    val lastTransactionAt: Long? = null,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "movement_sync_checkpoints", primaryKeys = ["user_id"])
data class MovementSyncCheckpointEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "sequence") val sequence: Long = 0L,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)
