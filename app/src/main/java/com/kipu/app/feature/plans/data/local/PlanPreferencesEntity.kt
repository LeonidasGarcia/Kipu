package com.kipu.app.feature.plans.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.kipu.app.feature.plans.domain.model.PlanSelection
import java.time.Instant
import java.util.UUID

@Entity(tableName = "plan_preferences")
data class PlanPreferencesEntity(@PrimaryKey @ColumnInfo(name = "user_id") val userId: UUID, val selection: PlanSelection, @ColumnInfo(name = "selected_at") val selectedAt: Instant, @ColumnInfo(name = "updated_at") val updatedAt: Instant)
