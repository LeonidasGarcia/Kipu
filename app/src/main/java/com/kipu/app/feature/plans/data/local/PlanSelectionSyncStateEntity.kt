package com.kipu.app.feature.plans.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import java.time.Instant
import java.util.UUID

@Entity(tableName = "plan_selection_sync_state")
data class PlanSelectionSyncStateEntity(@PrimaryKey @ColumnInfo(name = "user_id") val userId: UUID, @ColumnInfo(name = "last_issued_revision") val lastIssuedRevision: Long, @ColumnInfo(name = "accepted_revision") val acceptedRevision: Long, @ColumnInfo(name = "updated_at") val updatedAt: Instant)
