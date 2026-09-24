package com.kipu.app.feature.plans.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "plan_selections",
    primaryKeys = ["user_id", "feature_key"],
    indices = [Index(value = ["user_id", "updated_at"])],
)
data class PlanQuotaSelectionEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "feature_key") val featureKey: String,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "plan_selection_items",
    primaryKeys = ["user_id", "feature_key", "resource_id"],
    foreignKeys = [ForeignKey(
        entity = PlanQuotaSelectionEntity::class,
        parentColumns = ["user_id", "feature_key"],
        childColumns = ["user_id", "feature_key"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["user_id", "feature_key", "resource_type"])],
)
data class PlanQuotaSelectionItemEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "feature_key") val featureKey: String,
    @ColumnInfo(name = "resource_id") val resourceId: String,
    @ColumnInfo(name = "resource_type") val resourceType: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
