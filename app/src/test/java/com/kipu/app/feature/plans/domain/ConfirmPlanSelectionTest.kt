package com.kipu.app.feature.plans.domain

import com.kipu.app.feature.plans.domain.model.CommercialOption
import com.kipu.app.feature.plans.domain.model.PlanSelection
import com.kipu.app.feature.plans.domain.model.TrialEligibilitySnapshot
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class ConfirmPlanSelectionTest {
    private val eligible = TrialEligibilitySnapshot.eligible(Instant.EPOCH, Instant.MAX)
    private val ineligible = TrialEligibilitySnapshot.ineligible(Instant.EPOCH)
    private val unknown = TrialEligibilitySnapshot.unknown()

    @Test
    fun mapsEveryCommercialAndEligibilityCombinationWithoutGrantingEntitlement() {
        assertEquals(PlanSelection.FREE, ConfirmPlanSelection.map(CommercialOption.FREE, eligible))
        assertEquals(PlanSelection.TRIAL_INTENT, ConfirmPlanSelection.map(CommercialOption.MONTHLY, eligible))
        assertEquals(PlanSelection.TRIAL_INTENT, ConfirmPlanSelection.map(CommercialOption.ANNUAL, eligible))
        assertEquals(PlanSelection.PREMIUM_INTENT, ConfirmPlanSelection.map(CommercialOption.MONTHLY, ineligible))
        assertEquals(PlanSelection.PREMIUM_INTENT, ConfirmPlanSelection.map(CommercialOption.ANNUAL, unknown))
        assertEquals(PlanSelection.PREMIUM_INTENT, ConfirmPlanSelection.map(CommercialOption.LIFETIME, eligible))
    }

    @Test
    fun passesTheStableOperationIdentityToTheLocalCommit() = runTest {
        val operationId = UUID.fromString("5d92af34-c725-4a1a-a863-2c93fa214c86")
        val repository = RecordingRepository()
        val confirm = ConfirmPlanSelection(repository) { operationId }

        assertEquals(PlanSelection.TRIAL_INTENT, confirm(CommercialOption.MONTHLY, eligible))
        assertEquals(PlanSelection.TRIAL_INTENT to operationId, repository.confirmed)
    }

    private class RecordingRepository : PlanPreferencesRepository {
        var confirmed: Pair<PlanSelection, UUID>? = null

        override suspend fun confirmSelection(selection: PlanSelection, operationId: UUID) {
            confirmed = selection to operationId
        }
    }
}
