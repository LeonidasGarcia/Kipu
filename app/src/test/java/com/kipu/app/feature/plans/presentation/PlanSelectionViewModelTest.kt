package com.kipu.app.feature.plans.presentation

import com.kipu.app.feature.plans.domain.ConfirmPlanSelection
import com.kipu.app.feature.plans.domain.OperationIdProvider
import com.kipu.app.feature.plans.domain.PlanPreferencesRepository
import com.kipu.app.feature.plans.domain.model.CommercialOption
import com.kipu.app.feature.plans.domain.model.PlanSelection
import com.kipu.app.feature.plans.domain.model.TrialEligibilitySnapshot
import com.kipu.app.feature.plans.domain.model.TrialEligibilitySource
import com.kipu.app.feature.plans.domain.model.TrialEligibilityStatus
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlanSelectionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val operationId = UUID.fromString("5d92af34-c725-4a1a-a863-2c93fa214c86")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial selection is Free and opening the screen does not persist it`() = runTest(dispatcher) {
        val repository = FakePlanPreferencesRepository()
        val viewModel = viewModel(repository)

        assertEquals(CommercialOption.FREE, viewModel.uiState.value.selectedOption)
        assertEquals(0, repository.confirmCalls)
    }

    @Test
    fun `selecting an option changes only UI state`() = runTest(dispatcher) {
        val repository = FakePlanPreferencesRepository()
        val viewModel = viewModel(repository)

        viewModel.onOptionSelected(CommercialOption.ANNUAL)

        assertEquals(CommercialOption.ANNUAL, viewModel.uiState.value.selectedOption)
        assertEquals(0, repository.confirmCalls)
    }

    @Test
    fun `eligibility is reduced to approved commercial copy states`() = runTest(dispatcher) {
        val cases = listOf(
            eligible to TrialOfferState.TRIAL_AVAILABLE,
            ineligible to TrialOfferState.TRIAL_UNAVAILABLE,
            TrialEligibilitySnapshot.UNKNOWN to TrialOfferState.SUBJECT_TO_VERIFICATION,
        )

        cases.forEach { (snapshot, expected) ->
            val viewModel = viewModel(FakePlanPreferencesRepository(eligibilityValue = snapshot))
            advanceUntilIdle()
            assertEquals(expected, viewModel.uiState.value.trialOfferState)
        }
    }

    @Test
    fun `abandoning without confirmation performs zero repository writes`() = runTest(dispatcher) {
        val repository = FakePlanPreferencesRepository()
        val viewModel = viewModel(repository)
        viewModel.onOptionSelected(CommercialOption.MONTHLY)

        viewModel.onAbandoned()
        advanceUntilIdle()

        assertEquals(0, repository.confirmCalls)
    }

    @Test
    fun `double submit while local commit is pending is coalesced`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repository = FakePlanPreferencesRepository(confirmGate = gate)
        val viewModel = viewModel(repository)
        viewModel.onOptionSelected(CommercialOption.MONTHLY)
        advanceUntilIdle()

        viewModel.confirmSelection()
        viewModel.confirmSelection()
        runCurrent()

        assertEquals(1, repository.confirmCalls)
        assertTrue(viewModel.uiState.value.isSubmitting)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `repository failures expose only a safe presentation error`() = runTest(dispatcher) {
        val repository = FakePlanPreferencesRepository(confirmFailure = IllegalStateException("token=secret"))
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.confirmSelection()
        advanceUntilIdle()

        assertEquals(PlanSelectionError.CONFIRMATION_FAILED, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.toString().contains("token=secret"))
    }

    @Test
    fun `confirmation event is emitted once and only after repository success`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repository = FakePlanPreferencesRepository(confirmGate = gate)
        val viewModel = viewModel(repository)
        val events = mutableListOf<PlanSelectionEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect(events::add)
        }
        advanceUntilIdle()

        viewModel.confirmSelection()
        runCurrent()
        assertTrue(events.isEmpty())

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(PlanSelectionEvent.Confirmed), events)
        assertEquals(1, repository.confirmCalls)
        assertNull(viewModel.uiState.value.error)
    }

    private fun viewModel(repository: FakePlanPreferencesRepository): PlanSelectionViewModel =
        PlanSelectionViewModel(
            repository = repository,
            confirmPlanSelection = ConfirmPlanSelection(repository, OperationIdProvider { operationId }),
        )

    private class FakePlanPreferencesRepository(
        private val eligibilityValue: TrialEligibilitySnapshot = TrialEligibilitySnapshot.UNKNOWN,
        private val confirmGate: CompletableDeferred<Unit>? = null,
        private val confirmFailure: Throwable? = null,
    ) : PlanPreferencesRepository {
        var confirmCalls: Int = 0
            private set

        override suspend fun getTrialEligibility(): TrialEligibilitySnapshot = eligibilityValue

        override suspend fun confirmSelection(selection: PlanSelection, operationId: UUID) {
            confirmCalls += 1
            confirmGate?.await()
            confirmFailure?.let { throw it }
        }
    }

    private companion object {
        val eligible = TrialEligibilitySnapshot(
            status = TrialEligibilityStatus.ELIGIBLE,
            source = TrialEligibilitySource.VERIFIED_ACCOUNT_HISTORY,
            verifiedAt = Instant.parse("2026-09-15T10:00:00Z"),
            validUntil = Instant.parse("2026-09-16T10:00:00Z"),
        )
        val ineligible = TrialEligibilitySnapshot(
            status = TrialEligibilityStatus.INELIGIBLE,
            source = TrialEligibilitySource.VERIFIED_ACCOUNT_HISTORY,
            verifiedAt = Instant.parse("2026-09-15T10:00:00Z"),
            validUntil = null,
        )
    }
}
