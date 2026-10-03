package com.kipu.app.feature.movements.domain

import com.kipu.app.feature.movements.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VoidTransactionTest {
    private val owner = "11111111-1111-1111-1111-111111111111"
    private val tx = "22222222-2222-2222-2222-222222222222"
    private val key = "33333333-3333-3333-3333-333333333333"

    private val planner = MovementRevisionPlanner()

    private fun payload(
        type: MovementType = MovementType.EXPENSE,
        amountMinor: Long = 2000L,
        sourceAccountId: String? = "acc-a",
        destinationAccountId: String? = null,
        categoryId: String? = "food",
        merchantId: String? = null,
        merchantProvisionalText: String? = null,
        occurredAt: Long = 1000L,
        note: String? = null,
        operationKind: String? = "STANDARD",
    ) = MovementRevisionPayload(
        type = type,
        operationKind = operationKind,
        amountMinor = amountMinor,
        currency = "PEN",
        sourceAccountId = sourceAccountId,
        destinationAccountId = destinationAccountId,
        categoryId = categoryId,
        merchantId = merchantId,
        merchantProvisionalText = merchantProvisionalText,
        occurredAt = occurredAt,
        note = note,
    )

    private fun head(
        p: MovementRevisionPayload = payload(),
        state: MovementFinancialState = MovementFinancialState.CONFIRMED,
        revision: Long = 1L,
    ) = MovementRevisionHead(
        transactionId = tx,
        userId = owner,
        payload = p,
        financialState = state,
        officialRevision = revision,
    )

    private fun refs() = MovementRevisionReferences(
        accounts = mapOf(
            "acc-a" to MovementAccountReference(owner, "PEN", eligible = true),
            "acc-b" to MovementAccountReference(owner, "PEN", eligible = true),
        ),
        categories = mapOf("food" to MovementCategoryReference(owner, MovementType.EXPENSE, eligible = true)),
        merchants = emptyMap(),
    )

    @Test
    fun voidExpenseReversesFullAmountToSourceAccount() {
        val currentHead = head()
        val command = MovementRevisionCommand.Void(
            idempotencyKey = key,
            transactionId = tx,
            expectedRevision = 1L,
            reason = "Error de registro",
        )
        val applied = planner.financialEffects(currentHead.payload)

        val result = planner.plan(
            owner = owner,
            head = currentHead,
            command = command,
            context = MovementMaintenanceContext(legacyStandardVerified = true),
            references = refs(),
            appliedEffects = applied,
        )

        assertTrue(result is MovementRevisionPlanningResult.Ready)
        val plan = (result as MovementRevisionPlanningResult.Ready).plan
        assertEquals(MovementFinancialState.VOIDED, plan.financialState)
        assertEquals(2L, plan.proposedRevision)
        // Original was -2000L, reversal should be +2000L
        assertEquals(1, plan.effects.size)
        val effect = plan.effects.first()
        assertEquals("acc-a", effect.accountId)
        assertEquals(LedgerRole.SOURCE, effect.role)
        assertEquals(2000L, effect.signedAmountMinor)
        assertEquals("PEN", effect.currency)
    }

    @Test
    fun voidTransferReversesBothAccounts() {
        val transferPayload = payload(
            type = MovementType.TRANSFER,
            amountMinor = 4000L,
            sourceAccountId = "acc-a",
            destinationAccountId = "acc-b",
            categoryId = null,
        )
        val currentHead = head(p = transferPayload)
        val command = MovementRevisionCommand.Void(
            idempotencyKey = key,
            transactionId = tx,
            expectedRevision = 1L,
            reason = "Transferencia equivocada",
        )
        val applied = planner.financialEffects(transferPayload)

        val result = planner.plan(
            owner = owner,
            head = currentHead,
            command = command,
            context = MovementMaintenanceContext(legacyStandardVerified = true),
            references = refs(),
            appliedEffects = applied,
        )

        assertTrue(result is MovementRevisionPlanningResult.Ready)
        val plan = (result as MovementRevisionPlanningResult.Ready).plan
        assertEquals(MovementFinancialState.VOIDED, plan.financialState)
        assertEquals(2L, plan.proposedRevision)
        assertEquals(2, plan.effects.size)

        // Source account acc-a was -4000L, now +4000L
        val sourceReversal = plan.effects.first { it.accountId == "acc-a" }
        assertEquals(4000L, sourceReversal.signedAmountMinor)
        assertEquals(LedgerRole.SOURCE, sourceReversal.role)

        // Destination account acc-b was +4000L, now -4000L
        val destReversal = plan.effects.first { it.accountId == "acc-b" }
        assertEquals(-4000L, destReversal.signedAmountMinor)
        assertEquals(LedgerRole.DESTINATION, destReversal.role)
    }

    @Test
    fun voidAlreadyVoidedTransactionReturnsAlreadyVoided() {
        val voidedHead = head(state = MovementFinancialState.VOIDED, revision = 2L)
        val command = MovementRevisionCommand.Void(
            idempotencyKey = key,
            transactionId = tx,
            expectedRevision = 2L,
        )

        val result = planner.plan(
            owner = owner,
            head = voidedHead,
            command = command,
            context = MovementMaintenanceContext(legacyStandardVerified = true),
            references = refs(),
            appliedEffects = emptyList(),
        )

        assertEquals(MovementRevisionPlanningResult.AlreadyVoided, result)
    }

    @Test
    fun reviseVoidedTransactionRejectsWithAlreadyVoidedForEdit() {
        val voidedHead = head(state = MovementFinancialState.VOIDED, revision = 2L)
        val command = MovementRevisionCommand.Revise(
            idempotencyKey = key,
            transactionId = tx,
            expectedRevision = 2L,
            payload = payload(amountMinor = 1500L),
        )

        val result = planner.plan(
            owner = owner,
            head = voidedHead,
            command = command,
            context = MovementMaintenanceContext(legacyStandardVerified = true),
            references = refs(),
            appliedEffects = emptyList(),
        )

        assertTrue(result is MovementRevisionPlanningResult.Rejected)
        assertEquals("ALREADY_VOIDED_FOR_EDIT", (result as MovementRevisionPlanningResult.Rejected).code)
    }

    @Test
    fun specializedMovementRejectsVoid() {
        val specializedHead = head(p = payload(operationKind = "CARD_PURCHASE"))
        val command = MovementRevisionCommand.Void(
            idempotencyKey = key,
            transactionId = tx,
            expectedRevision = 1L,
        )

        val result = planner.plan(
            owner = owner,
            head = specializedHead,
            command = command,
            context = MovementMaintenanceContext(hasSpecializedRelations = true),
            references = refs(),
            appliedEffects = planner.financialEffects(specializedHead.payload),
        )

        assertTrue(result is MovementRevisionPlanningResult.Rejected)
        assertEquals("OPERATION_SPECIALIZED", (result as MovementRevisionPlanningResult.Rejected).code)
    }

    @Test
    fun revisionConflictRejectsVoid() {
        val currentHead = head()
        val command = MovementRevisionCommand.Void(
            idempotencyKey = key,
            transactionId = tx,
            expectedRevision = 99L, // wrong base revision
        )

        val result = planner.plan(
            owner = owner,
            head = currentHead,
            command = command,
            context = MovementMaintenanceContext(legacyStandardVerified = true),
            references = refs(),
            appliedEffects = planner.financialEffects(currentHead.payload),
        )

        assertTrue(result is MovementRevisionPlanningResult.Rejected)
        assertEquals("REVISION_CONFLICT", (result as MovementRevisionPlanningResult.Rejected).code)
    }

    @Test
    fun voidTransactionUseCaseDelegatesToRepository() = runBlocking {
        var called = false
        val repo = object : MovementMaintenanceRepository {
            override suspend fun getRevisionHead(userId: String, transactionId: String): MovementRevisionHead? = null
            override suspend fun revise(userId: String, command: MovementRevisionCommand.Revise): MovementMutationResult =
                throw UnsupportedOperationException()
            override suspend fun void(userId: String, command: MovementRevisionCommand.Void): MovementMutationResult {
                called = true
                assertEquals(owner, userId)
                assertEquals(tx, command.transactionId)
                return MovementMutationResult.Success(head = head(state = MovementFinancialState.VOIDED, revision = 2L), isDuplicate = false)
            }
            override suspend fun getConflictProposals(userId: String, transactionId: String) = emptyList<com.kipu.app.feature.movements.data.local.MovementConflictProposalEntity>()
            override suspend fun discardProposal(userId: String, proposalId: String): Boolean = true
            override suspend fun redoProposal(userId: String, proposalId: String, newIdempotencyKey: String): MovementMutationResult =
                throw UnsupportedOperationException()
        }

        val useCase = VoidTransaction(repo, planner)
        val command = MovementRevisionCommand.Void(
            idempotencyKey = key,
            transactionId = tx,
            expectedRevision = 1L,
        )

        val result = useCase(owner, command)
        assertTrue(called)
        assertTrue(result is MovementMutationResult.Success)
        assertEquals(MovementFinancialState.VOIDED, (result as MovementMutationResult.Success).head.financialState)
    }
}
