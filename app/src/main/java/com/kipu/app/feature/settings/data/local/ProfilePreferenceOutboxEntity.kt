package com.kipu.app.feature.settings.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "profile_preference_outbox",
    indices = [Index(value = ["user_id"])],
)
data class ProfilePreferenceOutboxEntity(
    @PrimaryKey
    @ColumnInfo(name = "operation_id")
    val operationId: UUID,
    @ColumnInfo(name = "user_id")
    val userId: UUID,
    @ColumnInfo(name = "expected_revision")
    val expectedRevision: Long,
    @ColumnInfo(name = "payload")
    val payload: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,
    @ColumnInfo(name = "next_attempt_at")
    val nextAttemptAt: Instant? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now(),
)
