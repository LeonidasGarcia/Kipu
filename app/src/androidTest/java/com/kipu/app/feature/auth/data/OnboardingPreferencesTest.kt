package com.kipu.app.feature.auth.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OnboardingPreferencesTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun pageAndCompletionSurviveReopeningTheStore() = runTest {
        val file = File(folder.root, "intro.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val first = OnboardingPreferenceStore(PreferenceDataStoreFactory.create(scope = firstScope) { file })
        assertEquals(OnboardingCheckpoint(), first.checkpoint.first())
        first.savePage(1)
        first.complete()
        assertEquals(OnboardingCheckpoint(true, 1), first.checkpoint.first())
        firstScope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
        firstScope.coroutineContext[kotlinx.coroutines.Job]!!.join()

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val second = OnboardingPreferenceStore(PreferenceDataStoreFactory.create(scope = secondScope) { file })
            assertEquals(OnboardingCheckpoint(true, 1), second.checkpoint.first())
        } finally { secondScope.cancel() }
    }

    @Test fun outOfRangeCheckpointIsBoundedToThreePages() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = OnboardingPreferenceStore(PreferenceDataStoreFactory.create(scope = scope) {
                File(folder.root, "bounds.preferences_pb")
            })
            store.savePage(-1)
            assertEquals(0, store.checkpoint.first().page)
            store.savePage(7)
            assertEquals(2, store.checkpoint.first().page)
            assertFalse(store.checkpoint.first().completed)
        } finally { scope.cancel() }
    }
}
