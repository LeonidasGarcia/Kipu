package com.kipu.app.feature.movements.domain

import com.kipu.app.feature.movements.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class ReviseTransactionTest {
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

    private fun head(p: MovementRevisionPayload = payload()) = MovementRevisionHead(
        transactionId = tx,
        userId = owner,
        payload = p,
        financialState = MovementFinancialState.CONFIRMED,
        officialRevision = 1L,
    )

    private fun refs(
        accountEligible: Boolean = true,
        categoryEligible: Boolean = true,
        merchantEligible: Boolean = true,
    ) = MovementRevisionReferences(
        accounts = mapOf(
            "acc-a" to MovementAccountReference(owner, "PEN", eligible = accountEligible),
            "acc-b" to MovementAccountReference(owner, "PEN", eligible = accountEligible),
            "acc-c" to MovementAccountReference(owner, "PEN", eligible = accountEligible),
        ),
        categories = mapOf(
            "food" to MovementCategoryReference(owner, MovementType.EXPENSE, eligible = categoryEligible),
            "transport" to MovementCategoryReference(owner, MovementType.EXPENSE, eligible = categoryEligible),
            "salary" to MovementCategoryReference(owner, MovementType.INCOME, eligible = categoryEligible),
        ),
        merchants = mapOf(
            "m-1" to MovementMerchantReference(owner, visible = true, eligible = merchantEligible),
            "m-2" to MovementMerchantReference(owner, visible = true, eligible = merchantEligible),
        ),
    )

    private fun createReviseUseCase(
        fakeRepo: MovementMaintenanceRepository = object : MovementMaintenanceRepository {
            override suspend fun getRevisionHead(userId: String, transactionId: String): MovementRevisionHead? = head()
            override suspend fun revise(userId: String, command: MovementRevisionCommand.Revise): MovementMutationResult =
                MovementMutationResult.Success(head(command.payload).copy(officialRevision = 2L), isDuplicate = false)
            override suspend fun void(userId: String, command: MovementRevisionCommand.Void): MovementMutationResult =
                MovementMutationResult.Success(head().copy(financialState = MovementFinancialState.VOIDED, officialRevision = 2L), isDuplicate = false)
            override suspend fun getConflictProposals(userId: String, transactionId: String) = emptyList<com.kipu.app.feature.movements.data.local.MovementConflictProposalEntity>()
            override suspend fun discardProposal(userId: String, proposalId: String) = true
            override suspend fun redoProposal(userId: String, proposalId: String, newIdempotencyKey: String) =
                MovementMutationResult.Success(head().copy(officialRevision = 2L), isDuplicate = false)
        }
    ) = ReviseTransaction(fakeRepo, planner)

    @Test
    fun `revise amount from 20 to 15 recovers 5 in account and reduces net expense to 15`() {
        val originalHead = head(payload(amountMinor = 2000L))
        val initialEffects = planner.financialEffects(originalHead.payload)
        assertEquals(-2000L, initialEffects.sumOf { it.signedAmountMinor })

        val reviseCmd = MovementRevisionCommand.Revise(
            idempotencyKey = key,
            transactionId = tx,
            expectedRevision = 1L,
            payload = originalHead.payload.copy(amountMinor = 1500L),
        )

        val useCase = createReviseUseCase()
        val planResult = useCase.plan(
            owner = owner,
            head = originalHead,
            command = reviseCmd,
            context = MovementMaintenanceContext(),
            references = refs(),
            appliedEffects = initialEffects,
        )

        assertTrue(planResult is MovementRevisionPlanningResult.Ready)
        val plan = (planResult as MovementRevisionPlanningResult.Ready).plan

        // Compensatory effect: +2000 (reversal of old), -1500 (new effect) -> net change is +500 (recovers 5)
        assertEquals(2, plan.effects.size)
        assertEquals(2000L, plan.effects[0].signedAmountMinor)
        assertEquals(-1500L, plan.effects[1].signedAmountMinor)
        val netCumulative = initialEffects.sumOf { it.signedAmountMinor } + plan.effects.sumOf { it.signedAmountMinor }
        assertEquals(-1500L, netCumulative)
        assertEquals(MovementFinancialState.REVISED, plan.financialState)
        assertEquals(2L, plan.proposedRevision)
    }

    @Test
    fun `revise note only does not append any cash ledger entries`() {
        val originalHead = head(payload(amountMinor = 2000L, note = "Old note"))
        val initialEffects = planner.financialEffects(originalHead.payload)

        val reviseCmd = MovementRevisionCommand.Revise(
            idempotencyKey = key,
            transactionId = tx,
            expectedRevision = 1L,
            payload = originalHead.payload.copy(note = "New note"),
        )

        val useCase = createReviseUseCase()
        val planResult = useCase.plan(
            owner = owner,
            head = originalHead,
            command = reviseCmd,
            context = MovementMaintenanceContext(),
            references = refs(),
            appliedEffects = initialEffects,
        )

        assertTrue(planResult is MovementRevisionPlanningResult.Ready)
        val plan = (planResult as MovementRevisionPlanningResult.Ready).plan
        assertTrue("Note edit should not append cash entries", plan.effects.isEmpty())
        assertEquals("New note", plan.payload.note)
    }

    @Test
    fun `revise source account reverses old account and debits new account`() {
        val originalHead = head(payload(sourceAccountId = "acc-a", amountMinor = 2000L))
        val initialEffects = planner.financialEffects(originalHead.payload)

        val reviseCmd = MovementRevisionCommand.Revise(
            idempotencyKey = key,
            transactionId = tx,
            expectedRevision = 1L,
            payload = originalHead.payload.copy(sourceAccountId = "acc-b"),
        )

        val useCase = createReviseUseCase()
        val planResult = useCase.plan(
            owner = owner,
            head = originalHead,
            command = reviseCmd,
            context = MovementMaintenanceContext(),
            references = refs(),
            appliedEffects = initialEffects,
        )

        assertTrue(planResult is MovementRevisionPlanningResult.Ready)
        val plan = (planResult as MovementRevisionPlanningResult.Ready).plan
        assertEquals(2, plan.effects.size)
        // acc-a credited back (+2000), acc-b debited (-2000)
        assertEquals("acc-a", plan.effects[0].accountId)
        assertEquals(2000L, plan.effects[0].signedAmountMinor)
        assertEquals("acc-b", plan.effects[1].accountId)
        assertEquals(-2000L, plan.effects[1].signedAmountMinor)
    }

    @Test
    fun `revise transfer adjusts both endpoints atomically`() {
        val originalHead = head(payload(
            type = MovementType.TRANSFER,
            sourceAccountId = "acc-a",
            destinationAccountId = "acc-b",
            categoryId = null,
            amountMinor = 3000L,
        ))
        val initialEffects = planner.financialEffects(originalHead.payload)
        assertEquals(0L, initialEffects.sumOf { it.signedAmountMinor })

        val reviseCmd = MovementRevisionCommand.Revise(
            idempotencyKey = key,
            transactionId = tx,
            expectedRevision = 1L,
            payload = originalHead.payload.copy(destinationAccountId = "acc-c", amountMinor = 2500L),
        )

        val useCase = createReviseUseCase()
        val planResult = useCase.plan(
            owner = owner,
            head = originalHead,
            command = reviseCmd,
            context = MovementMaintenanceContext(),
            references = refs(),
            appliedEffects = initialEffects,
        )

        assertTrue(planResult is MovementRevisionPlanningResult.Ready)
        val plan = (planResult as MovementRevisionPlanningResult.Ready).plan
        // Must reverse acc-a (+3000) & acc-b (-3000), then apply acc-a (-2500) & acc-c (+2500)
        assertEquals(4, plan.effects.size)
        assertEquals(listOf("acc-a", "acc-b", "acc-a", "acc-c"), plan.effects.map { it.accountId })
        assertEquals(listOf(3000L, -3000L, -2500L, 2500L), plan.effects.map { it.signedAmountMinor })
        assertEquals(0L, plan.effects.sumOf { it.signedAmountMinor })
    }

    @Test
    fun `revise category requires category type compatibility and eligibility`() {
        val originalHead = head(payload(categoryId = "food"))
        val initialEffects = planner.financialEffects(originalHead.payload)

        // Valid change to transport
        val validCmd = MovementRevisionCommand.Revise(
            idempotencyKey = key,
            transactionId = tx,
            expectedRevision = 1L,
            payload = originalHead.payload.copy(categoryId = "transport"),
        )
        val useCase = createReviseUseCase()
        val ready = useCase.plan(owner, originalHead, validCmd, MovementMaintenanceContext(), refs(), initialEffects)
        assertTrue(ready is MovementRevisionPlanningResult.Ready)

        // Invalid: category type INCOME for EXPENSE movement
        val incompatibleCmd = validCmd.copy(payload = originalHead.payload.copy(categoryId = "salary"))
        val rejectedType = useCase.plan(owner, originalHead, incompatibleCmd, MovementMaintenanceContext(), refs(), initialEffects)
        assertEquals("INVALID_REFERENCE", (rejectedType as MovementRevisionPlanningResult.Rejected).code)

        // Ineligible category when changed
        val ineligibleRefs = refs(categoryEligible = false)
        val rejectedIneligible = useCase.plan(owner, originalHead, validCmd, MovementMaintenanceContext(), ineligibleRefs, initialEffects)
        assertEquals("REFERENCE_NOT_ELIGIBLE", (rejectedIneligible as MovementRevisionPlanningResult.Rejected).code)
    }

    @Test
    fun `historical blocked references remain usable if unchanged but blocked if changed`() {
        // acc-a is ineligible, but originalHead already used acc-a
        val blockedRefs = refs(accountEligible = false)
        val originalHead = head(payload(sourceAccountId = "acc-a", note = "Historical"))
        val initialEffects = planner.financialEffects(originalHead.payload)

        val useCase = createReviseUseCase()

        // Edit only note keeping unchanged acc-a -> allowed
        val noteCmd = MovementRevisionCommand.Revise(
            idempotencyKey = key,
            transactionId = tx,
            expectedRevision = 1L,
            payload = originalHead.payload.copy(note = "Updated note"),
        )
        val noteResult = useCase.plan(owner, originalHead, noteCmd, MovementMaintenanceContext(), blockedRefs, initialEffects)
        assertTrue(noteResult is MovementRevisionPlanningResult.Ready)

        // Change source to acc-b which is ineligible -> rejected
        val changeCmd = MovementRevisionCommand.Revise(
            idempotencyKey = key,
            transactionId = tx,
            expectedRevision = 1L,
            payload = originalHead.payload.copy(sourceAccountId = "acc-b"),
        )
        val changeResult = useCase.plan(owner, originalHead, changeCmd, MovementMaintenanceContext(), blockedRefs, initialEffects)
        assertEquals("REFERENCE_NOT_ELIGIBLE", (changeResult as MovementRevisionPlanningResult.Rejected).code)
    }

    @Test
    fun `specialized operations strictly prohibit generic edit`() {
        val useCase = createReviseUseCase()

        // 1. Operation kind is CARD_PURCHASE
        val cardHead = head(payload(operationKind = "CARD_PURCHASE"))
        val cmd = MovementRevisionCommand.Revise(key, tx, 1L, cardHead.payload.copy(note = "Try edit"))
        val res1 = useCase.plan(owner, cardHead, cmd, MovementMaintenanceContext(), refs(), planner.financialEffects(cardHead.payload))
        assertEquals("OPERATION_SPECIALIZED", (res1 as MovementRevisionPlanningResult.Rejected).code)

        // 2. hasSpecializedRelations is true (debt, installment, etc.)
        val res2 = useCase.plan(
            owner,
            head(),
            cmd,
            MovementMaintenanceContext(hasSpecializedRelations = true),
            refs(),
            planner.financialEffects(head().payload),
        )
        assertEquals("OPERATION_SPECIALIZED", (res2 as MovementRevisionPlanningResult.Rejected).code)
    }

    @Test
    fun `revision with explicit interval and zone - S 20 moved from Period A to Period B produces A=0 and B=20 with cash balance intact and VOID produces 0 in both`() {
        val zone = ZoneId.of("America/Lima")
        val periodAStart = LocalDate.of(2026, 9, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val periodAEnd = LocalDate.of(2026, 10, 1).atStartOfDay(zone).toInstant().toEpochMilli()

        val periodBStart = LocalDate.of(2026, 10, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val periodBEnd = LocalDate.of(2026, 11, 1).atStartOfDay(zone).toInstant().toEpochMilli()

        val queryA = ExpenseConsumptionQuery("PEN", periodAStart, periodAEnd, zone)
        val queryB = ExpenseConsumptionQuery("PEN", periodBStart, periodBEnd, zone)

        // 1. Initial state: Single expense of S/20 located in Period A (Sept 15)
        val dateInA = LocalDate.of(2026, 9, 15).atStartOfDay(zone).toInstant().toEpochMilli()
        val initialPayload = payload(amountMinor = 2000L, occurredAt = dateInA)
        val headInA = head(initialPayload)
        val initialCashEffects = planner.financialEffects(initialPayload)

        assertEquals(2000L, ExpenseConsumptionCalculator.calculate(listOf(headInA), queryA).amountMinor)
        assertEquals(0L, ExpenseConsumptionCalculator.calculate(listOf(headInA), queryB).amountMinor)

        // 2. Date revised to Period B (Oct 15)
        val dateInB = LocalDate.of(2026, 10, 15).atStartOfDay(zone).toInstant().toEpochMilli()
        val reviseDateCmd = MovementRevisionCommand.Revise(
            idempotencyKey = key,
            transactionId = tx,
            expectedRevision = 1L,
            payload = initialPayload.copy(occurredAt = dateInB),
        )

        val useCase = createReviseUseCase()
        val planResult = useCase.plan(owner, headInA, reviseDateCmd, MovementMaintenanceContext(), refs(), initialCashEffects)
        assertTrue(planResult is MovementRevisionPlanningResult.Ready)
        val plan = (planResult as MovementRevisionPlanningResult.Ready).plan

        // Ledger effects for date change MUST be empty: cash balance is completely intact!
        assertTrue("Moving date does not touch cash ledger", plan.effects.isEmpty())

        // Updated head with revised payload in Period B
        val headInB = headInA.copy(
            payload = plan.payload,
            financialState = plan.financialState,
            officialRevision = plan.proposedRevision,
        )

        // Consumption check: Period A is now 0, Period B is now 20 (2000 minor)
        val consumptionAAfterMove = ExpenseConsumptionCalculator.calculate(listOf(headInB), queryA).amountMinor
        val consumptionBAfterMove = ExpenseConsumptionCalculator.calculate(listOf(headInB), queryB).amountMinor
        assertEquals(0L, consumptionAAfterMove)
        assertEquals(2000L, consumptionBAfterMove)

        // 3. Voided transaction: Both Period A and Period B must calculate to 0
        val voidedHead = headInB.copy(
            financialState = MovementFinancialState.VOIDED,
            officialRevision = 3L,
        )
        val consumptionAVoided = ExpenseConsumptionCalculator.calculate(listOf(voidedHead), queryA).amountMinor
        val consumptionBVoided = ExpenseConsumptionCalculator.calculate(listOf(voidedHead), queryB).amountMinor
        assertEquals(0L, consumptionAVoided)
        assertEquals(0L, consumptionBVoided)
    }

    @Test
    fun `use case invoke dispatches to repository and handles validation`() = runBlocking {
        var repoCalled = false
        val customRepo = object : MovementMaintenanceRepository {
            override suspend fun getRevisionHead(userId: String, transactionId: String): MovementRevisionHead? = head()
            override suspend fun revise(userId: String, command: MovementRevisionCommand.Revise): MovementMutationResult {
                repoCalled = true
                return MovementMutationResult.Success(head(command.payload).copy(officialRevision = 2L), isDuplicate = false)
            }
            override suspend fun void(userId: String, command: MovementRevisionCommand.Void): MovementMutationResult =
                throw UnsupportedOperationException()
            override suspend fun getConflictProposals(userId: String, transactionId: String) = emptyList<com.kipu.app.feature.movements.data.local.MovementConflictProposalEntity>()
            override suspend fun discardProposal(userId: String, proposalId: String) = true
            override suspend fun redoProposal(userId: String, proposalId: String, newIdempotencyKey: String) =
                MovementMutationResult.Success(head().copy(officialRevision = 2L), isDuplicate = false)
        }

        val useCase = ReviseTransaction(customRepo, planner)
        val validCmd = MovementRevisionCommand.Revise(key, tx, 1L, payload())

        val result = useCase(owner, validCmd)
        assertTrue(repoCalled)
        assertTrue(result is MovementMutationResult.Success)

        // Blank userId or command IDs rejected before calling repo
        val invalidResult = useCase("", validCmd)
        assertTrue(invalidResult is MovementMutationResult.Rejected)
        assertEquals("INVALID_COMMAND_ID", (invalidResult as MovementMutationResult.Rejected).code)
    }
}
