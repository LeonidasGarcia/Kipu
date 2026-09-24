package com.kipu.app.feature.categories.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "categories",
    primaryKeys = ["id"],
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["parent_id"]),
        Index(value = ["user_id", "is_active"]),
        Index(value = ["origin"]),
    ],
)
data class CategoryEntity(
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "user_id")
    val userId: String?, // null for system categories
    @ColumnInfo(name = "parent_id")
    val parentId: String?,
    @ColumnInfo(name = "origin")
    val origin: String, // "SYSTEM" or "CUSTOM"
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    @ColumnInfo(name = "remote_revision")
    val remoteRevision: Long = 1L,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "category_type", defaultValue = "'GENERAL'")
    val categoryType: String = "GENERAL",
)

@Serializable
@Entity(
    tableName = "category_presentations",
    primaryKeys = ["user_id", "category_id"],
    indices = [
        Index(value = ["category_id"]),
        Index(value = ["user_id"]),
    ],
)
data class CategoryPresentationEntity(
    @ColumnInfo(name = "category_id")
    val categoryId: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "icon")
    val icon: String,
    @ColumnInfo(name = "color")
    val color: String,
    @ColumnInfo(name = "remote_revision")
    val remoteRevision: Long = 1L,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

@Entity(
    tableName = "merchant_catalog_cache",
    primaryKeys = ["id"],
    indices = [
        Index(value = ["normalized_name"]),
        Index(value = ["is_active"]),
    ],
)
data class MerchantCatalogEntity(
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "normalized_name")
    val normalizedName: String,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    @ColumnInfo(name = "version")
    val version: Long = 1L,
    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long,
)

@Entity(
    tableName = "category_conflicts",
    primaryKeys = ["id"],
    indices = [
        Index(value = ["user_id", "status"]),
        Index(value = ["category_id"]),
    ],
)
data class CategoryConflictEntity(
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "category_id")
    val categoryId: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "conflict_type")
    val conflictType: String, // "PRESENTATION" or "LIFECYCLE"
    @ColumnInfo(name = "local_version")
    val localVersion: String,
    @ColumnInfo(name = "remote_version")
    val remoteVersion: String,
    @ColumnInfo(name = "status")
    val status: String = "OPEN", // "OPEN" or "RESOLVED"
    @ColumnInfo(name = "resolution_operation_id")
    val resolutionOperationId: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

@Entity(
    tableName = "category_sync_outbox",
    primaryKeys = ["user_id", "operation_id"],
    indices = [
        Index(value = ["user_id", "state", "next_attempt_at"]),
        Index(value = ["user_id", "aggregate_type", "aggregate_id"]),
    ],
)
data class CategorySyncOutboxEntity(
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
    @ColumnInfo(name = "expected_revision")
    val expectedRevision: Long? = null,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
    @ColumnInfo(name = "payload_hash")
    val payloadHash: String,
    @ColumnInfo(name = "state")
    val state: String = "PENDING", // PENDING, IN_FLIGHT, COMPLETED, FAILED
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,
    @ColumnInfo(name = "next_attempt_at")
    val nextAttemptAt: Long? = null,
    @ColumnInfo(name = "error_code")
    val errorCode: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
