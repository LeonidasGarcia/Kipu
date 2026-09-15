package com.kipu.app.feature.plans.presentation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.feature.plans.domain.PlanPreferencesRepository
import com.kipu.app.feature.plans.domain.model.CommercialOption
import com.kipu.app.feature.plans.domain.model.PlanSelection
import com.kipu.app.feature.plans.domain.model.TrialEligibilitySnapshot
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanSelectionRouteTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun stateCollectionStopsBelowStartedAndRendersLatestValueOnResume() {
        val repository = RecordingRepository()
        val viewModel = PlanSelectionViewModel(repository, SavedStateHandle())
        compose.setContent { PlanSelectionRoute(viewModel = viewModel, onConfirmed = {}) }
        compose.onNodeWithTag("plan-option-FREE").assertIsSelected()

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        viewModel.selectOption(CommercialOption.LIFETIME)

        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag("plan-option-LIFETIME").assertIsSelected()
    }

    @Test
    fun systemBackAbandonsWithoutPreferenceOrOutboxWrite() {
        val repository = RecordingRepository()
        val viewModel = PlanSelectionViewModel(repository, SavedStateHandle())
        compose.setContent { PlanSelectionRoute(viewModel = viewModel, onConfirmed = {}) }
        compose.onNodeWithText("Mensual", substring = true).performClick()

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        assertEquals(0, repository.confirmCalls)
        assertEquals(0, repository.preferenceWrites)
        assertEquals(0, repository.outboxWrites)
    }

    @Test
    fun hostExitAbandonsWithoutPersistingVisualSelection() {
        val repository = RecordingRepository()
        val viewModel = PlanSelectionViewModel(repository, SavedStateHandle())
        val showRoute = mutableStateOf(true)
        compose.setContent {
            if (showRoute.value) PlanSelectionRoute(viewModel = viewModel, onConfirmed = {})
        }
        compose.onNodeWithText("Anual", substring = true).performClick()
        compose.runOnIdle { showRoute.value = false }

        compose.runOnIdle {
            assertEquals(0, repository.confirmCalls)
            assertEquals(0, repository.preferenceWrites)
            assertEquals(0, repository.outboxWrites)
        }
    }

    @Test
    fun localCommitProducesExactlyOneHostCallbackAcrossRecompositionAndLifecycleRestart() {
        val repository = RecordingRepository()
        val viewModel = PlanSelectionViewModel(repository, SavedStateHandle())
        val callbacks = AtomicInteger()
        compose.setContent { PlanSelectionRoute(viewModel = viewModel, onConfirmed = { callbacks.incrementAndGet() }) }

        compose.onNodeWithText("Confirmar Plan").performClick()
        compose.waitUntil { callbacks.get() == 1 }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()

        assertEquals(1, repository.confirmCalls)
        assertEquals(1, repository.preferenceWrites)
        assertEquals(1, repository.outboxWrites)
        assertEquals(1, callbacks.get())
    }

    @Test
    fun offlineConfirmationNavigatesImmediatelyAfterLocalCommitWithoutWaitingForSync() {
        val repository = RecordingRepository(networkAvailable = false)
        val viewModel = PlanSelectionViewModel(repository, SavedStateHandle())
        val confirmed = AtomicBoolean()
        compose.setContent { PlanSelectionRoute(viewModel = viewModel, onConfirmed = { confirmed.set(true) }) }

        compose.onNodeWithText("Lifetime", substring = true).performClick()
        compose.onNodeWithText("Confirmar Plan").performClick()
        compose.waitUntil(timeoutMillis = 2_000) { confirmed.get() }

        assertTrue(repository.localCommitCompleted)
        assertFalse(repository.syncCompleted)
        assertEquals(PlanSelection.PREMIUM_INTENT, repository.lastSelection)
        assertEquals(1, repository.outboxWrites)
    }

    private class RecordingRepository(
        private val networkAvailable: Boolean = true,
    ) : PlanPreferencesRepository {
        override val currentPreference = MutableStateFlow<PlanSelection?>(null)
        override val eligibility = MutableStateFlow(TrialEligibilitySnapshot.unknown())

        var confirmCalls = 0
        var preferenceWrites = 0
        var outboxWrites = 0
        var localCommitCompleted = false
        var syncCompleted = false
        var lastSelection: PlanSelection? = null

        override suspend fun refreshEligibility() = Unit

        override suspend fun confirmSelection(selection: PlanSelection, operationId: UUID) {
            confirmCalls++
            lastSelection = selection
            preferenceWrites++
            outboxWrites++
            localCommitCompleted = true
            currentPreference.value = lastSelection
            if (networkAvailable) syncCompleted = true
        }
    }
}
