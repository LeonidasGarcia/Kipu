package com.kipu.app.feature.movements.domain

import com.kipu.app.feature.movements.domain.model.*
import java.util.UUID

/** Pure plan only. The repository must revalidate and persist the whole plan atomically. */
class MovementRevisionPlanner {
    fun plan(
        owner: String,
        head: MovementRevisionHead,
        command: MovementRevisionCommand,
        context: MovementMaintenanceContext,
        references: MovementRevisionReferences,
        appliedEffects: List<MovementFinancialEffect>,
    ): MovementRevisionPlanningResult {
        fun reject(code: String) = MovementRevisionPlanningResult.Rejected(code)
        if (owner.isBlank() || head.userId != owner || command.transactionId != head.transactionId) return reject("NOT_AUTHORIZED")
        if (!validUuid(command.idempotencyKey) || !validUuid(command.transactionId) ||
            (command.dependsOnCommandId != null && !validUuid(requireNotNull(command.dependsOnCommandId)))) return reject("INVALID_COMMAND_ID")
        if (command.expectedRevision != head.commandBaseRevision) return reject("REVISION_CONFLICT")
        if (context.hasSpecializedRelations ||
            !(head.payload.operationKind.equals("STANDARD",true) ||
                (head.payload.operationKind == null && context.legacyStandardVerified))) return reject("OPERATION_SPECIALIZED")
        if (head.financialState == MovementFinancialState.LEGACY_FAILED) return reject("FINANCIAL_EVIDENCE_REQUIRED")
        if (head.financialState == MovementFinancialState.VOIDED) return if (command is MovementRevisionCommand.Void)
            MovementRevisionPlanningResult.AlreadyVoided else reject("ALREADY_VOIDED_FOR_EDIT")
        val before = head.payload
        val after = (command as? MovementRevisionCommand.Revise)?.payload ?: before
        if (after.type != before.type || after.currency != before.currency || after.operationKind != before.operationKind) return reject("IMMUTABLE_FINANCIAL_KIND")
        if (after.amountMinor <= 0L) return reject("INVALID_AMOUNT")
        if (after.sourceAccountId.isNullOrBlank()) return reject("INVALID_REFERENCE")
        if (after.type == MovementType.TRANSFER && (after.destinationAccountId.isNullOrBlank() ||
                after.sourceAccountId == after.destinationAccountId)) return reject("INVALID_TRANSFER")
        if (after.type != MovementType.TRANSFER && after.destinationAccountId != null) return reject("INVALID_REFERENCE")
        if (after.type == MovementType.EXPENSE && after.categoryId.isNullOrBlank() && after.categoryId != before.categoryId) return reject("INVALID_REFERENCE")
        if (after.merchantId != null && !after.merchantProvisionalText.isNullOrBlank()) return reject("INVALID_REFERENCE")
        for ((id,oldId) in listOf(after.sourceAccountId to before.sourceAccountId, after.destinationAccountId to before.destinationAccountId)) {
            if (id == null) continue
            val account = references.accounts[id] ?: return reject("INVALID_REFERENCE")
            if (account.userId != owner || account.currency != after.currency) return reject("INVALID_REFERENCE")
            if (id != oldId && !account.eligible) return reject("REFERENCE_NOT_ELIGIBLE")
        }
        after.categoryId?.let { id ->
            val category = references.categories[id] ?: return reject("INVALID_REFERENCE")
            if (category.userId != owner || (id != before.categoryId && category.type != after.type)) return reject("INVALID_REFERENCE")
            if (id != before.categoryId && !category.eligible) return reject("REFERENCE_NOT_ELIGIBLE")
        }
        after.merchantId?.let { id ->
            val merchant = references.merchants[id] ?: return reject("INVALID_REFERENCE")
            if (!merchant.visible || (merchant.userId != null && merchant.userId != owner)) return reject("INVALID_REFERENCE")
            if (id != before.merchantId && !merchant.eligible) return reject("REFERENCE_NOT_ELIGIBLE")
        }
        return try {
            val original = financialEffects(before)
            if (netEffects(appliedEffects) != netEffects(original)) return reject("LEDGER_MISMATCH")
            val desired = if (command is MovementRevisionCommand.Void) emptyList() else financialEffects(after)
            val effects = if (original == desired) emptyList() else
                original.map { it.copy(signedAmountMinor = Math.negateExact(it.signedAmountMinor)) } + desired
            MovementRevisionPlanningResult.Ready(MovementMutationPlan(
                payload = after,
                financialState = if (command is MovementRevisionCommand.Void) MovementFinancialState.VOIDED else MovementFinancialState.REVISED,
                proposedRevision = Math.addExact(head.commandBaseRevision,1L),
                effects = effects.mapIndexed { ordinal,e -> MovementPlannedEffect(ordinal,e.accountId,e.role,e.signedAmountMinor,e.currency) },
            ))
        } catch (_: ArithmeticException) { reject("ARITHMETIC_OVERFLOW") }
    }

    fun financialEffects(payload: MovementRevisionPayload): List<MovementFinancialEffect> = when (payload.type) {
        MovementType.EXPENSE -> listOf(MovementFinancialEffect(requireNotNull(payload.sourceAccountId),LedgerRole.SOURCE,Math.negateExact(payload.amountMinor),payload.currency))
        MovementType.INCOME -> listOf(MovementFinancialEffect(requireNotNull(payload.sourceAccountId),LedgerRole.DESTINATION,payload.amountMinor,payload.currency))
        MovementType.TRANSFER -> listOf(
            MovementFinancialEffect(requireNotNull(payload.sourceAccountId),LedgerRole.SOURCE,Math.negateExact(payload.amountMinor),payload.currency),
            MovementFinancialEffect(requireNotNull(payload.destinationAccountId),LedgerRole.DESTINATION,payload.amountMinor,payload.currency),
        )
    }

    private fun netEffects(effects: List<MovementFinancialEffect>): Map<Triple<String,LedgerRole,String>,Long> {
        val sums = mutableMapOf<Triple<String,LedgerRole,String>,Long>()
        effects.forEach { e ->
            val key = Triple(e.accountId,e.role,e.currency)
            sums[key] = Math.addExact(sums[key] ?: 0L,e.signedAmountMinor)
        }
        return sums.filterValues { it != 0L }
    }
    private fun validUuid(value: String): Boolean = try {
        UUID.fromString(value).toString().equals(value,true)
    } catch (_: IllegalArgumentException) { false }
}
