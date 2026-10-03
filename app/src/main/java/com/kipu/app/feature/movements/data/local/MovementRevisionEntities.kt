package com.kipu.app.feature.movements.data.local

import androidx.room.*

@Entity(tableName = "transaction_revisions", primaryKeys = ["user_id", "revision_id"],
    foreignKeys = [ForeignKey(entity = TransactionEntity::class, parentColumns = ["user_id", "id"], childColumns = ["user_id", "transaction_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["user_id", "transaction_id", "local_revision"], unique = false), Index(value = ["user_id", "transaction_id", "revision_id"], unique = true)])
data class TransactionRevisionEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "revision_id") val revisionId: String,
    @ColumnInfo(name = "transaction_id") val transactionId: String,
    @ColumnInfo(name = "command_id") val commandId: String?,
    @ColumnInfo(name = "command_type") val commandType: String,
    @ColumnInfo(name = "base_revision") val baseRevision: Long,
    @ColumnInfo(name = "local_revision") val localRevision: Long,
    @ColumnInfo(name = "previous_payload") val previousPayload: String?,
    @ColumnInfo(name = "new_payload") val newPayload: String,
    @ColumnInfo(name = "change_reason") val changeReason: String?,
    @ColumnInfo(name = "provenance") val provenance: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "movement_official_revisions", primaryKeys = ["user_id", "transaction_id", "official_revision"],
    foreignKeys = [ForeignKey(entity = TransactionRevisionEntity::class, parentColumns = ["user_id", "transaction_id", "revision_id"], childColumns = ["user_id", "transaction_id", "revision_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["user_id", "official_revision_id"], unique = true), Index(value = ["user_id", "transaction_id", "revision_id"], unique = false)])
data class MovementOfficialRevisionEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "transaction_id") val transactionId: String,
    @ColumnInfo(name = "official_revision") val officialRevision: Long,
    @ColumnInfo(name = "revision_id") val revisionId: String,
    @ColumnInfo(name = "official_revision_id") val officialRevisionId: String,
    @ColumnInfo(name = "assigned_at") val assignedAt: Long,
)

@Entity(tableName = "movement_ledger_effects", primaryKeys = ["user_id", "command_id", "effect_ordinal"],
    foreignKeys = [ForeignKey(entity = TransactionRevisionEntity::class, parentColumns = ["user_id", "transaction_id", "revision_id"], childColumns = ["user_id", "transaction_id", "revision_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = LedgerEntryEntity::class, parentColumns = ["user_id", "id"], childColumns = ["user_id", "ledger_entry_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = MovementLedgerEffectEntity::class, parentColumns = ["user_id", "command_id", "effect_ordinal"], childColumns = ["user_id", "reverses_command_id", "reverses_effect_ordinal"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["user_id", "transaction_id", "revision_id"], unique = false), Index(value = ["user_id", "ledger_entry_id"], unique = true), Index(value = ["user_id", "reverses_command_id", "reverses_effect_ordinal"], unique = true)])
data class MovementLedgerEffectEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "command_id") val commandId: String,
    @ColumnInfo(name = "effect_ordinal") val effectOrdinal: Int,
    @ColumnInfo(name = "transaction_id") val transactionId: String,
    @ColumnInfo(name = "revision_id") val revisionId: String,
    @ColumnInfo(name = "ledger_entry_id") val ledgerEntryId: String,
    @ColumnInfo(name = "reverses_command_id") val reversesCommandId: String?,
    @ColumnInfo(name = "reverses_effect_ordinal") val reversesEffectOrdinal: Int?,
)

@Entity(tableName = "movement_ledger_aliases", primaryKeys = ["user_id", "physical_entry_id"],
    foreignKeys = [ForeignKey(entity = MovementLedgerEffectEntity::class, parentColumns = ["user_id", "command_id", "effect_ordinal"], childColumns = ["user_id", "command_id", "effect_ordinal"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["user_id", "command_id", "effect_ordinal"], unique = false)])
data class MovementLedgerAliasEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "physical_entry_id") val physicalEntryId: String,
    @ColumnInfo(name = "command_id") val commandId: String,
    @ColumnInfo(name = "effect_ordinal") val effectOrdinal: Int,
)

@Entity(tableName = "movement_conflict_proposals", primaryKeys = ["user_id", "proposal_id"],
    foreignKeys = [ForeignKey(entity = TransactionRevisionEntity::class, parentColumns = ["user_id", "transaction_id", "revision_id"], childColumns = ["user_id", "transaction_id", "proposed_revision_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["user_id", "command_id"], unique = true), Index(value = ["user_id", "transaction_id", "proposed_revision_id"], unique = false)])
data class MovementConflictProposalEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "proposal_id") val proposalId: String,
    @ColumnInfo(name = "transaction_id") val transactionId: String,
    @ColumnInfo(name = "command_id") val commandId: String,
    @ColumnInfo(name = "base_revision") val baseRevision: Long,
    @ColumnInfo(name = "proposed_revision_id") val proposedRevisionId: String,
    @ColumnInfo(name = "proposed_snapshot") val proposedSnapshot: String,
    @ColumnInfo(name = "local_revision_ids") val localRevisionIds: String,
    @ColumnInfo(name = "remote_revision_id") val remoteRevisionId: String?,
    @ColumnInfo(name = "resolution") val resolution: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

