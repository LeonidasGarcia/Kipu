package com.kipu.app.feature.debts.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "debt_sync_checkpoints", primaryKeys = ["user_id"])
data class DebtSyncCheckpointEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "sequence") val sequence: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
