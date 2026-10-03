package com.kipu.app.feature.movements.domain

import com.kipu.app.feature.movements.domain.model.MovementFinancialEffect
import com.kipu.app.feature.movements.domain.model.MovementMaintenanceContext
import com.kipu.app.feature.movements.domain.model.MovementMutationResult
import com.kipu.app.feature.movements.domain.model.MovementRevisionCommand
import com.kipu.app.feature.movements.domain.model.MovementRevisionHead
import com.kipu.app.feature.movements.domain.model.MovementRevisionPlanningResult
import com.kipu.app.feature.movements.domain.model.MovementRevisionReferences
import javax.inject.Inject

/**
 * Use case to revise a standard financial movement.
 * Validates constraints, plans compensatory ledger effects, and atomically commits the revision.
 */
class ReviseTransaction @Inject constructor(
    private val repository: MovementMaintenanceRepository,
    private val planner: MovementRevisionPlanner = MovementRevisionPlanner(),
) {
    /**
     * Pure planning step for testing or pre-commit validation.
     */
    fun plan(
        owner: String,
        head: MovementRevisionHead,
        command: MovementRevisionCommand.Revise,
        context: MovementMaintenanceContext,
        references: MovementRevisionReferences,
        appliedEffects: List<MovementFinancialEffect>,
    ): MovementRevisionPlanningResult {
        return planner.plan(owner, head, command, context, references, appliedEffects)
    }

    suspend operator fun invoke(
        userId: String,
        command: MovementRevisionCommand.Revise,
    ): MovementMutationResult {
        if (userId.isBlank() || command.transactionId.isBlank() || command.idempotencyKey.isBlank()) {
            return MovementMutationResult.Rejected("INVALID_COMMAND_ID")
        }
        return repository.revise(userId, command)
    }
}
