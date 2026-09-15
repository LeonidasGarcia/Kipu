package com.kipu.app.feature.plans.domain

import com.kipu.app.feature.plans.domain.model.PlanSelection
import org.junit.Test
import kotlin.test.assertContentEquals

class PlanSelectionMappingTest {
    @Test
    fun planSelectionContainsPreferencesOnly() {
        assertContentEquals(
            arrayOf("FREE", "TRIAL_INTENT", "PREMIUM_INTENT"),
            PlanSelection.entries.map { it.name }.toTypedArray(),
        )
    }
}
