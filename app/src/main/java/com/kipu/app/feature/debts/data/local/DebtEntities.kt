package com.kipu.app.feature.debts.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.kipu.app.feature.movements.data.local.TransactionEntity

@Entity(
    tableName = "debts",
    primaryKeys = ["user_id", "id"],
    indices = [
        Index(value = ["user_id", "status", "due_date"]),
        Index(value = ["user_id", "updated_at"]),
    ],
)
data class DebtEntity(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "obligation_type") val obligationType: String,
    @ColumnInfo(name = "counterparty_name") val counterpartyName: String,
    @ColumnInfo(name = "total_minor") val totalMinor: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "opened_on") val openedOn: String,
    @ColumnInfo(name = "opening_mode", defaultValue = "'HISTORICAL'") val openingMode: String = "HISTORICAL",
    @ColumnInfo(name = "due_date") val dueDate: String? = null,
    @ColumnInfo(name = "reminder_lead_days") val reminderLeadDays: Int? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "status", defaultValue = "'ACTIVE'") val status: String = "ACTIVE",
    @ColumnInfo(name = "sync_state", defaultValue = "'PENDING'") val syncState: String = "PENDING",
    @ColumnInfo(name = "revision", defaultValue = "1") val revision: Long = 1L,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "debt_installments",
    primaryKeys = ["user_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = DebtEntity::class,
            parentColumns = ["user_id", "id"],
            childColumns = ["user_id", "debt_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["debt_id", "installment_number"], unique = true),
        Index(value = ["user_id", "debt_id", "due_date"]),
        Index(value = ["user_id", "debt_id", "id"], unique = true),
    ],
)
data class DebtInstallmentEntity(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "debt_id") val debtId: String,
    @ColumnInfo(name = "installment_number") val installmentNumber: Int,
    @ColumnInfo(name = "due_date") val dueDate: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "status", defaultValue = "'PLANNED'") val status: String = "PLANNED",
    @ColumnInfo(name = "revision", defaultValue = "1") val revision: Long = 1L,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "debt_events",
    primaryKeys = ["user_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = DebtEntity::class,
            parentColumns = ["user_id", "id"],
            childColumns = ["user_id", "debt_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["user_id", "id"],
            childColumns = ["user_id", "transaction_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["user_id", "id"],
            childColumns = ["user_id", "interest_transaction_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = DebtInstallmentEntity::class,
            parentColumns = ["user_id", "debt_id", "id"],
            childColumns = ["user_id", "debt_id", "installment_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["user_id", "debt_id", "occurred_at", "id"]),
        Index(value = ["user_id", "transaction_id"]),
        Index(value = ["user_id", "interest_transaction_id"]),
        Index(value = ["user_id", "debt_id", "installment_id"]),
    ],
)
data class DebtEventEntity(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "debt_id") val debtId: String,
    @ColumnInfo(name = "transaction_id") val transactionId: String? = null,
    @ColumnInfo(name = "interest_transaction_id") val interestTransactionId: String? = null,
    @ColumnInfo(name = "installment_id") val installmentId: String? = null,
    @ColumnInfo(name = "event_type") val eventType: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "principal_delta_minor") val principalDeltaMinor: Long? = null,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)
