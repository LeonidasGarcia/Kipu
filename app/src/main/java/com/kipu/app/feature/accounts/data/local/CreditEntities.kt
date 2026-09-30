package com.kipu.app.feature.accounts.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Embedded
import com.kipu.app.feature.movements.data.local.TransactionEntity

@Entity(
    tableName = "credit_installments",
    primaryKeys = ["user_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["user_id", "id"],
            childColumns = ["user_id", "transaction_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["user_id", "transaction_id", "installment_number"], unique = true),
        Index(value = ["user_id", "due_date", "status"]),
    ],
)
data class CreditInstallmentEntity(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "transaction_id") val transactionId: String,
    @ColumnInfo(name = "installment_number") val installmentNumber: Int,
    /** Epoch day; domain and API boundaries convert this to ISO `date`. */
    @ColumnInfo(name = "due_date") val dueDate: Long,
    @ColumnInfo(name = "principal_minor") val principalMinor: Long,
    @ColumnInfo(name = "interest_minor") val interestMinor: Long,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

@Entity(
    tableName = "credit_payment_allocations",
    primaryKeys = ["user_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["user_id", "id"],
            childColumns = ["user_id", "payment_transaction_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CreditInstallmentEntity::class,
            parentColumns = ["user_id", "id"],
            childColumns = ["user_id", "installment_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["user_id", "payment_transaction_id"]),
        Index(value = ["user_id", "installment_id"]),
    ],
)
data class CreditPaymentAllocationEntity(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "payment_transaction_id") val paymentTransactionId: String,
    @ColumnInfo(name = "installment_id") val installmentId: String,
    @ColumnInfo(name = "allocated_minor") val allocatedMinor: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

data class CreditInstallmentOutstanding(
    @Embedded val installment: CreditInstallmentEntity,
    @ColumnInfo(name = "outstanding_minor") val outstandingMinor: Long,
    @ColumnInfo(name = "purchase_occurred_at") val purchaseOccurredAt: Long,
)

@androidx.room.Dao
interface CreditDao {
    @androidx.room.Query(
        """SELECT i.* FROM credit_installments i
           JOIN transactions t ON t.user_id = i.user_id AND t.id = i.transaction_id
           WHERE i.user_id = :userId AND t.card_id = :cardId AND i.deleted_at IS NULL
             AND t.status = 'ACTIVE' AND t.sync_status != 'FAILED_PERMANENT'
           ORDER BY i.due_date, t.occurred_at, i.installment_number, i.id""",
    )
    fun observeInstallmentsForCard(userId: String, cardId: String): kotlinx.coroutines.flow.Flow<List<CreditInstallmentEntity>>

    @androidx.room.Query(
        """SELECT COALESCE(SUM(MAX(i.principal_minor - COALESCE(a.allocated_minor, 0), 0)), 0)
           FROM credit_installments i
           JOIN transactions t ON t.user_id = i.user_id AND t.id = i.transaction_id
           LEFT JOIN (
             SELECT user_id, installment_id, SUM(allocated_minor) AS allocated_minor
             FROM credit_payment_allocations GROUP BY user_id, installment_id
           ) a ON a.user_id = i.user_id AND a.installment_id = i.id
           WHERE i.user_id = :userId AND t.card_id = :cardId
             AND i.deleted_at IS NULL AND i.status != 'VOIDED'
             AND t.status = 'ACTIVE' AND t.sync_status != 'FAILED_PERMANENT'""",
    )
    fun observeOutstandingPrincipalForCard(userId: String, cardId: String): kotlinx.coroutines.flow.Flow<Long>

    @androidx.room.Query(
        """SELECT COALESCE(SUM(MAX(i.principal_minor - COALESCE(a.allocated_minor, 0), 0)), 0)
           FROM credit_installments i
           JOIN transactions t ON t.user_id = i.user_id AND t.id = i.transaction_id
           LEFT JOIN (
             SELECT user_id, installment_id, SUM(allocated_minor) AS allocated_minor
             FROM credit_payment_allocations GROUP BY user_id, installment_id
           ) a ON a.user_id = i.user_id AND a.installment_id = i.id
           WHERE i.user_id = :userId AND t.card_id = :cardId
             AND i.deleted_at IS NULL AND i.status != 'VOIDED'
             AND t.status = 'ACTIVE' AND t.sync_status != 'FAILED_PERMANENT'""",
    )
    suspend fun getOutstandingPrincipalForCard(userId: String, cardId: String): Long

    @androidx.room.Query(
        """SELECT CASE WHEN COALESCE(SUM(CASE
                WHEN t.status = 'ACTIVE' AND t.sync_status != 'FAILED_PERMANENT'
                THEN le.signed_amount_minor ELSE 0 END), 0) < 0
             THEN -COALESCE(SUM(CASE
                WHEN t.status = 'ACTIVE' AND t.sync_status != 'FAILED_PERMANENT'
                THEN le.signed_amount_minor ELSE 0 END), 0)
             ELSE 0 END
           FROM cards c
           LEFT JOIN ledger_entries le ON le.user_id = c.user_id AND le.account_id = c.account_id
                AND le.role = 'LIABILITY'
           LEFT JOIN transactions t ON t.user_id = le.user_id AND t.id = le.transaction_id
           WHERE c.user_id = :userId AND c.id = :cardId AND c.type = 'CREDIT'""",
    )
    fun observeLedgerOutstandingPrincipalForCard(userId: String, cardId: String): kotlinx.coroutines.flow.Flow<Long>

    @androidx.room.Query(
        """SELECT CASE WHEN COALESCE(SUM(CASE
                WHEN t.status = 'ACTIVE' AND t.sync_status != 'FAILED_PERMANENT'
                THEN le.signed_amount_minor ELSE 0 END), 0) < 0
             THEN -COALESCE(SUM(CASE
                WHEN t.status = 'ACTIVE' AND t.sync_status != 'FAILED_PERMANENT'
                THEN le.signed_amount_minor ELSE 0 END), 0)
             ELSE 0 END
           FROM cards c
           LEFT JOIN ledger_entries le ON le.user_id = c.user_id AND le.account_id = c.account_id
                AND le.role = 'LIABILITY'
           LEFT JOIN transactions t ON t.user_id = le.user_id AND t.id = le.transaction_id
           WHERE c.user_id = :userId AND c.id = :cardId AND c.type = 'CREDIT'""",
    )
    suspend fun getLedgerOutstandingPrincipalForCard(userId: String, cardId: String): Long

    @androidx.room.Query(
        """SELECT i.*, (i.principal_minor - COALESCE(a.allocated_minor, 0)) AS outstanding_minor,
                  t.occurred_at AS purchase_occurred_at
           FROM credit_installments i
           JOIN transactions t ON t.user_id = i.user_id AND t.id = i.transaction_id
           LEFT JOIN (
             SELECT user_id, installment_id, SUM(allocated_minor) AS allocated_minor
             FROM credit_payment_allocations GROUP BY user_id, installment_id
           ) a ON a.user_id = i.user_id AND a.installment_id = i.id
           WHERE i.user_id = :userId AND t.card_id = :cardId
             AND i.deleted_at IS NULL AND i.status != 'VOIDED'
             AND t.status = 'ACTIVE' AND t.sync_status != 'FAILED_PERMANENT'
             AND i.principal_minor > COALESCE(a.allocated_minor, 0)
           ORDER BY i.due_date, t.occurred_at, i.installment_number, i.id""",
    )
    suspend fun getOutstandingInstallmentsForCard(userId: String, cardId: String): List<CreditInstallmentOutstanding>

    @androidx.room.Query(
        """SELECT * FROM credit_installments
           WHERE user_id = :userId AND transaction_id = :transactionId AND deleted_at IS NULL
           ORDER BY installment_number""",
    )
    suspend fun getInstallmentsForTransaction(userId: String, transactionId: String): List<CreditInstallmentEntity>

    @androidx.room.Query("SELECT * FROM credit_installments WHERE user_id = :userId AND id = :installmentId AND deleted_at IS NULL")
    suspend fun getInstallmentById(userId: String, installmentId: String): CreditInstallmentEntity?

    @androidx.room.Query(
        """SELECT * FROM credit_payment_allocations
           WHERE user_id = :userId AND payment_transaction_id = :paymentTransactionId
           ORDER BY created_at, installment_id""",
    )
    suspend fun getAllocationsForPayment(userId: String, paymentTransactionId: String): List<CreditPaymentAllocationEntity>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.ABORT)
    suspend fun insertInstallments(installments: List<CreditInstallmentEntity>)

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.ABORT)
    suspend fun insertAllocations(allocations: List<CreditPaymentAllocationEntity>)

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertPulledInstallments(installments: List<CreditInstallmentEntity>)

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertPulledAllocations(allocations: List<CreditPaymentAllocationEntity>)

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.ABORT)
    suspend fun insertInstallment(installment: CreditInstallmentEntity)

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.ABORT)
    suspend fun insertAllocation(allocation: CreditPaymentAllocationEntity)

    @androidx.room.Query(
        """UPDATE credit_installments SET status = :status, revision = :revision, updated_at = :updatedAt
           WHERE user_id = :userId AND id = :installmentId""",
    )
    suspend fun updateInstallmentStatus(
        userId: String,
        installmentId: String,
        status: String,
        revision: Long,
        updatedAt: Long,
    ): Int

    @androidx.room.Query(
        """UPDATE credit_installments SET id = :remoteId, due_date = :dueDate,
           principal_minor = :principalMinor, interest_minor = :interestMinor, status = :status,
           revision = :revision, updated_at = :updatedAt
           WHERE user_id = :userId AND transaction_id = :transactionId AND installment_number = :installmentNumber""",
    )
    suspend fun adoptRemoteInstallmentId(
        userId: String,
        transactionId: String,
        installmentNumber: Int,
        remoteId: String,
        dueDate: Long,
        principalMinor: Long,
        interestMinor: Long,
        status: String,
        revision: Long,
        updatedAt: Long,
    ): Int

    @androidx.room.Query(
        """SELECT COALESCE(SUM(allocated_minor), 0) FROM credit_payment_allocations
           WHERE user_id = :userId AND installment_id = :installmentId""",
    )
    suspend fun getAllocatedMinorForInstallment(userId: String, installmentId: String): Long

    @androidx.room.Query("DELETE FROM credit_installments WHERE user_id = :userId AND transaction_id = :transactionId")
    suspend fun deleteInstallmentsForTransaction(userId: String, transactionId: String): Int

    @androidx.room.Query("DELETE FROM credit_payment_allocations WHERE user_id = :userId AND payment_transaction_id = :transactionId")
    suspend fun deleteAllocationsForPayment(userId: String, transactionId: String): Int
}
