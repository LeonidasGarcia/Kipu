package com.kipu.app.feature.movements.domain.model

// Financial state is independent of sync transport state and historical FAILED rows.
enum class MovementFinancialState { CONFIRMED, REVISED, VOIDED, LEGACY_FAILED }

data class MovementRevisionPayload(
    val type: MovementType,
    val operationKind: String?,
    val amountMinor: Long,
    val currency: String,
    val sourceAccountId: String?,
    val destinationAccountId: String? = null,
    val categoryId: String? = null,
    val merchantId: String? = null,
    val merchantProvisionalText: String? = null,
    val occurredAt: Long,
    val note: String? = null,
)

data class MovementRevisionHead(
    val transactionId: String,
    val userId: String,
    val payload: MovementRevisionPayload,
    val financialState: MovementFinancialState,
    val officialRevision: Long?,
    val localProposedRevision: Long? = null,
    /** Local snapshot synthesized from pre-S4 data; it is not evidence of server acknowledgement. */
    val baselineRevision: Long? = null,
) {
    init {
        require(officialRevision == null || officialRevision > 0L)
        require(localProposedRevision == null || localProposedRevision > (officialRevision ?: 0L))
        require(baselineRevision == null || baselineRevision > 0L)
        require(officialRevision != null || localProposedRevision != null || baselineRevision != null)
        require(baselineRevision == null || (officialRevision == null && localProposedRevision == null))
    }
    // A pending command's proposed number does not become an official snapshot identity.
    val commandBaseRevision: Long get() = localProposedRevision ?: officialRevision ?: requireNotNull(baselineRevision)
}

sealed interface MovementRevisionCommand {
    val idempotencyKey: String
    val transactionId: String
    val expectedRevision: Long
    val dependsOnCommandId: String?
    val reason: String?
    data class Revise(
        override val idempotencyKey: String,
        override val transactionId: String,
        override val expectedRevision: Long,
        val payload: MovementRevisionPayload,
        override val dependsOnCommandId: String? = null,
        override val reason: String? = null,
    ) : MovementRevisionCommand
    data class Void(
        override val idempotencyKey: String,
        override val transactionId: String,
        override val expectedRevision: Long,
        override val dependsOnCommandId: String? = null,
        override val reason: String? = null,
    ) : MovementRevisionCommand
}

data class MovementMaintenanceContext(
    val hasSpecializedRelations: Boolean = false,
    val legacyStandardVerified: Boolean = false,
)
data class MovementAccountReference(val userId: String, val currency: String, val eligible: Boolean)
data class MovementCategoryReference(val userId: String, val type: MovementType, val eligible: Boolean)
data class MovementMerchantReference(val userId: String?, val visible: Boolean, val eligible: Boolean)
data class MovementRevisionReferences(
    val accounts: Map<String, MovementAccountReference>,
    val categories: Map<String, MovementCategoryReference>,
    val merchants: Map<String, MovementMerchantReference>,
)
data class MovementFinancialEffect(val accountId: String, val role: LedgerRole, val signedAmountMinor: Long, val currency: String)
data class MovementPlannedEffect(val ordinal: Int, val accountId: String, val role: LedgerRole, val signedAmountMinor: Long, val currency: String)
data class MovementMutationPlan(
    val payload: MovementRevisionPayload,
    val financialState: MovementFinancialState,
    val proposedRevision: Long,
    val effects: List<MovementPlannedEffect>,
)
sealed interface MovementRevisionPlanningResult {
    data class Ready(val plan: MovementMutationPlan) : MovementRevisionPlanningResult
    data class Rejected(val code: String) : MovementRevisionPlanningResult
    data object AlreadyVoided : MovementRevisionPlanningResult
}
sealed interface MovementMutationResult {
    data class Success(val head: MovementRevisionHead, val isDuplicate: Boolean) : MovementMutationResult
    data class Conflict(val current: MovementRevisionHead?, val code: String) : MovementMutationResult
    data class Rejected(val code: String) : MovementMutationResult
}

data class ExpenseConsumptionQuery(
    val currency: String,
    val fromInclusive: Long,
    val toExclusive: Long,
    val zoneId: java.time.ZoneId,
    val categoryIds: Set<String> = emptySet(),
) {
    init { require(currency in setOf("PEN", "USD")); require(fromInclusive <= toExclusive) }
}
data class ExpenseConsumptionResult(val amountMinor: Long, val datasetVersion: Long)


enum class MovementSnapshotOrigin { MIGRATION_BASELINE, OFFICIAL, LOCAL_PROPOSED }
data class MovementRevisionSnapshot(
    val snapshotId: String,
    val transactionId: String,
    val userId: String,
    val origin: MovementSnapshotOrigin,
    val officialRevision: Long?,
    val localProposedRevision: Long?,
    val commandId: String?,
    val previousSnapshotId: String?,
    val payload: MovementRevisionPayload,
    val financialState: MovementFinancialState,
) {
    init {
        require(snapshotId.isNotBlank() && transactionId.isNotBlank() && userId.isNotBlank())
        when (origin) {
            MovementSnapshotOrigin.MIGRATION_BASELINE -> require(
                officialRevision == null && localProposedRevision == null && commandId == null,
            )
            MovementSnapshotOrigin.OFFICIAL -> require(officialRevision != null && officialRevision > 0L && localProposedRevision == null)
            MovementSnapshotOrigin.LOCAL_PROPOSED -> require(
                officialRevision == null && localProposedRevision != null && localProposedRevision > 0L && !commandId.isNullOrBlank(),
            )
        }
    }
}
data class MovementLedgerEffectIdentity(val userId: String, val commandId: String, val ordinal: Int) {
    init { require(userId.isNotBlank() && commandId.isNotBlank() && ordinal >= 0) }
}
data class MovementLedgerAlias(val physicalEntryId: String, val logicalIdentity: MovementLedgerEffectIdentity)
