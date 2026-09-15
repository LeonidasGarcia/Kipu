package com.kipu.app.feature.plans.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.kipu.app.feature.plans.domain.model.PlanSelection
import java.time.Instant
import java.util.UUID

enum class OutboxStatus { PENDING, IN_FLIGHT, WAITING_FOR_AUTH, COMPLETED, CONFLICT, TERMINAL_ERROR }
enum class RemoteSelectionResult { APPLIED, DUPLICATE, STALE, CONFLICT }

@Entity(tableName = "sync_outbox", indices = [Index("user_id"), Index(value = ["user_id", "selection_revision"], unique = true)])
data class SyncOutboxEntity(
    @PrimaryKey @ColumnInfo(name = "operation_id") val operationId: UUID,
    @ColumnInfo(name = "user_id") val userId: UUID,
    @ColumnInfo(name = "aggregate_type") val aggregateType: String = "PLAN_SELECTION",
    @ColumnInfo(name = "contract_version") val contractVersion: Int = 1,
    @ColumnInfo(name = "selection_revision") val selectionRevision: Long,
    val selection: PlanSelection,
    @ColumnInfo(name = "selected_at") val selectedAt: Instant,
    val status: OutboxStatus = OutboxStatus.PENDING,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAt: Instant? = null,
    @ColumnInfo(name = "lease_until") val leaseUntil: Instant? = null,
    @ColumnInfo(name = "last_error_code") val lastErrorCode: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

class LocalPlanSelectionConflict : IllegalStateException("Operation ID already belongs to another payload")
