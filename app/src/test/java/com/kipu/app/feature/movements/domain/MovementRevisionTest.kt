package com.kipu.app.feature.movements.domain

import com.kipu.app.feature.movements.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class MovementRevisionTest {
    private val owner = "11111111-1111-1111-1111-111111111111"
    private val tx = "22222222-2222-2222-2222-222222222222"
    private val key = "33333333-3333-3333-3333-333333333333"
    private val planner = MovementRevisionPlanner()
    private fun payload() = MovementRevisionPayload(
        type = MovementType.EXPENSE, operationKind = "STANDARD", amountMinor = 2000L,
        currency = "PEN", sourceAccountId = "account-a", categoryId = "food", occurredAt = 1000L,
    )
    private fun head(p: MovementRevisionPayload = payload()) = MovementRevisionHead(
        transactionId = tx, userId = owner, payload = p, financialState = MovementFinancialState.CONFIRMED,
        officialRevision = 1L,
    )
    private fun refs(eligible: Boolean = true) = MovementRevisionReferences(
        accounts = mapOf("account-a" to MovementAccountReference(owner,"PEN",eligible),
            "account-b" to MovementAccountReference(owner,"PEN",eligible),
            "account-c" to MovementAccountReference(owner,"PEN",eligible)),
        categories = mapOf("food" to MovementCategoryReference(owner,MovementType.EXPENSE,eligible)),
        merchants = emptyMap(),
    )
    private fun revise(h: MovementRevisionHead, p: MovementRevisionPayload = h.payload,
        r: MovementRevisionReferences = refs(), expected: Long = h.commandBaseRevision,
        context: MovementMaintenanceContext = MovementMaintenanceContext(),
        effects: List<MovementFinancialEffect> = planner.financialEffects(h.payload)) = planner.plan(
        owner,h,MovementRevisionCommand.Revise(key,tx,expected,p),context,r,effects,
    )
    private fun ready(result: MovementRevisionPlanningResult) = (result as MovementRevisionPlanningResult.Ready).plan
    private fun rejected(result: MovementRevisionPlanningResult, code: String) {
        assertEquals(code,(result as MovementRevisionPlanningResult.Rejected).code)
    }

    @Test fun `20 to 15 preserves original and appends complete reversal plus new effect`() {
        val h=head();val originals=planner.financialEffects(h.payload)
        val plan=ready(revise(h,h.payload.copy(amountMinor=1500L),effects=originals))
        assertEquals(listOf(2000L,-1500L),plan.effects.map { it.signedAmountMinor })
        assertEquals(listOf(0,1),plan.effects.map { it.ordinal })
        assertEquals(-1500L,originals.sumOf { it.signedAmountMinor }+plan.effects.sumOf { it.signedAmountMinor })
        assertEquals(2000L,h.payload.amountMinor)
        assertEquals(1L,h.officialRevision)
        assertEquals(2L,plan.proposedRevision)
    }
    @Test fun `note date and classification changes do not append cash entries`() {
        val h=head();val plan=ready(revise(h,h.payload.copy(note="new",occurredAt=5000L)))
        assertTrue(plan.effects.isEmpty())
        assertEquals(MovementFinancialState.REVISED,plan.financialState)
    }
    @Test fun `unchanged historical blocked references remain usable`() {
        assertTrue(ready(revise(head(),payload().copy(note="history"),refs(false))).effects.isEmpty())
    }
    @Test fun `changed blocked account is rejected`() {
        rejected(revise(head(),payload().copy(sourceAccountId="account-b"),refs(false)),"REFERENCE_NOT_ELIGIBLE")
    }
    @Test fun `foreign references rejected even when unchanged`() {
        val r=refs().copy(accounts=mapOf("account-a" to MovementAccountReference("other","PEN",true)))
        rejected(revise(head(),r=r),"INVALID_REFERENCE")
    }
    @Test fun `account currency mismatch rejected`() {
        val r=refs().copy(accounts=mapOf("account-a" to MovementAccountReference(owner,"USD",true)))
        rejected(revise(head(),r=r),"INVALID_REFERENCE")
    }
    @Test fun `account change reverses old and posts new in one plan`() {
        val plan=ready(revise(head(),payload().copy(sourceAccountId="account-b")))
        assertEquals(listOf("account-a","account-b"),plan.effects.map { it.accountId })
        assertEquals(listOf(2000L,-2000L),plan.effects.map { it.signedAmountMinor })
    }
    @Test fun `transfer reversal covers both endpoints`() {
        val p=payload().copy(type=MovementType.TRANSFER,categoryId=null,destinationAccountId="account-b")
        val plan=ready(revise(head(p),p.copy(destinationAccountId="account-c",amountMinor=1500L)))
        assertEquals(listOf(2000L,-2000L,-1500L,1500L),plan.effects.map { it.signedAmountMinor })
        assertEquals(listOf("account-a","account-b","account-a","account-c"),plan.effects.map { it.accountId })
        assertEquals(0L,plan.effects.sumOf { it.signedAmountMinor })
    }
    @Test fun `specialized and debt linked movements reject generic edit`() {
        rejected(revise(head(payload().copy(operationKind="CARD_PURCHASE"))),"OPERATION_SPECIALIZED")
        rejected(revise(head(),context=MovementMaintenanceContext(hasSpecializedRelations=true)),"OPERATION_SPECIALIZED")
    }
    @Test fun `legacy missing kind requires explicit standard evidence`() {
        val h=head(payload().copy(operationKind=null))
        rejected(revise(h),"OPERATION_SPECIALIZED")
        assertTrue(ready(revise(h,context=MovementMaintenanceContext(legacyStandardVerified=true))).effects.isEmpty())
    }
    @Test fun `owner expected revision type and currency are protected`() {
        rejected(revise(head().copy(userId="other")),"NOT_AUTHORIZED")
        rejected(revise(head(),expected=0L),"REVISION_CONFLICT")
        rejected(revise(head(),payload().copy(type=MovementType.INCOME)),"IMMUTABLE_FINANCIAL_KIND")
        rejected(revise(head(),payload().copy(currency="USD")),"IMMUTABLE_FINANCIAL_KIND")
    }
    @Test fun `void appends inverse and repeated current void has no effects`() {
        val h=head();val cmd=MovementRevisionCommand.Void(key,tx,1L)
        val result=planner.plan(owner,h,cmd,MovementMaintenanceContext(),refs(),planner.financialEffects(h.payload))
        val plan=ready(result)
        assertEquals(MovementFinancialState.VOIDED,plan.financialState)
        assertEquals(listOf(2000L),plan.effects.map { it.signedAmountMinor })
        val voided=h.copy(financialState=MovementFinancialState.VOIDED,officialRevision=2L)
        assertTrue(planner.plan(owner,voided,cmd.copy(expectedRevision=2L),MovementMaintenanceContext(),refs(),emptyList()) is MovementRevisionPlanningResult.AlreadyVoided)
        rejected(planner.plan(owner,voided,cmd,MovementMaintenanceContext(),refs(),emptyList()),"REVISION_CONFLICT")
        rejected(revise(voided),"ALREADY_VOIDED_FOR_EDIT")
    }
    @Test fun `zero negative amount and incomplete transfers are rejected`() {
        rejected(revise(head(),payload().copy(amountMinor=0L)),"INVALID_AMOUNT")
        rejected(revise(head(),payload().copy(amountMinor=-1L)),"INVALID_AMOUNT")
        val p=payload().copy(type=MovementType.TRANSFER,categoryId=null,destinationAccountId="account-a")
        rejected(revise(head(p)),"INVALID_TRANSFER")
    }
    @Test fun `ledger mismatch does not invent a compensation`() {
        rejected(revise(head(),effects=emptyList()),"LEDGER_MISMATCH")
        rejected(revise(head(),effects=listOf(MovementFinancialEffect("account-a",LedgerRole.SOURCE,Long.MIN_VALUE,"PEN"))),"LEDGER_MISMATCH")
    }
    @Test fun `pending revision is distinct from official identity`() {
        val h=head().copy(localProposedRevision=3L)
        assertEquals(1L,h.officialRevision)
        assertEquals(3L,h.commandBaseRevision)
        assertEquals(4L,ready(revise(h)).proposedRevision)
        rejected(revise(h,expected=1L),"REVISION_CONFLICT")
    }
    @Test fun `revision overflow fails before a plan can be persisted`() {
        rejected(revise(head().copy(officialRevision=Long.MAX_VALUE)),"ARITHMETIC_OVERFLOW")
    }
    @Test fun `invalid UUID command identity is rejected`() {
        val h=head()
        rejected(planner.plan(owner,h,MovementRevisionCommand.Void("bad",tx,1L),MovementMaintenanceContext(),refs(),planner.financialEffects(h.payload)),"INVALID_COMMAND_ID")
    }
    @Test fun `revision hashes distinguish delimiters null and Unicode with golden vector`() {
        val hasher=MovementRevisionRequestHasher()
        val first=MovementRevisionCommand.Revise(key,tx,1L,payload().copy(merchantProvisionalText="a;note:b",note="c"))
        val second=first.copy(payload=first.payload.copy(merchantProvisionalText="a",note="b;note:c"))
        assertNotEquals(hasher.computeHash(first),hasher.computeHash(second))
        assertNotEquals(hasher.computeHash(first.copy(reason=null)),hasher.computeHash(first.copy(reason="")))
        assertEquals("44deef93fef683489a80af88b387f2e5e34bf0c43e9751793b52f5518743135d",hasher.computeHash(MovementRevisionCommand.Void(key,tx,1L,reason="anulación ñ 🦙; \"x\"\\\n")))
    }
    @Test fun `proposal and official snapshot cannot share revision identity`() {
        val proposal=MovementRevisionSnapshot("proposal",tx,owner,MovementSnapshotOrigin.LOCAL_PROPOSED,
            null,2L,key,"before",payload(),MovementFinancialState.REVISED)
        assertEquals(null,proposal.officialRevision)
        var rejected=false
        try { proposal.copy(officialRevision=2L) } catch (_: IllegalArgumentException) { rejected=true }
        assertTrue(rejected)
    }

    @Test fun `migration baseline has local revision without fabricated command or acknowledgement`() {
        val baseline = MovementRevisionSnapshot(
            snapshotId = "migration:owner:transaction",
            transactionId = tx,
            userId = owner,
            origin = MovementSnapshotOrigin.MIGRATION_BASELINE,
            officialRevision = null,
            localProposedRevision = null,
            commandId = null,
            previousSnapshotId = null,
            payload = payload(),
            financialState = MovementFinancialState.CONFIRMED,
        )
        assertNull(baseline.officialRevision)
        assertNull(baseline.commandId)

        val migratedHead = MovementRevisionHead(
            transactionId = tx,
            userId = owner,
            payload = payload(),
            financialState = MovementFinancialState.CONFIRMED,
            officialRevision = null,
            baselineRevision = 1L,
        )
        assertEquals(1L, migratedHead.commandBaseRevision)
        assertEquals(2L, ready(revise(migratedHead)).proposedRevision)
    }

}
