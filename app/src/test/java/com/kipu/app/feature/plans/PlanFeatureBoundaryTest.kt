package com.kipu.app.feature.plans

import java.io.File
import org.junit.Test
import kotlin.test.assertFalse

class PlanFeatureBoundaryTest {
    @Test
    fun plansSourceDoesNotUseBillingOrServerSecrets() {
        val root = File("src/main/java/com/kipu/app/feature/plans")
        val text = root.walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        assertFalse(text.contains("com.android.billing"))
        assertFalse(text.contains("service_role", ignoreCase = true))
    }

    @Test
    fun domainDoesNotDependOnAndroidDataOrPresentationLayers() {
        val root = File("src/main/java/com/kipu/app/feature/plans/domain")
        val text = root.walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        assertFalse(text.contains("import android."))
        assertFalse(text.contains("feature.plans.data"))
        assertFalse(text.contains("feature.plans.presentation"))
    }

    @Test
    fun planPreferenceHasNoEntitlementConverter() {
        val source = File("src/main/java/com/kipu/app/feature/plans/domain/model/PlanSelection.kt").readText()
        assertFalse(source.contains("EffectiveEntitlement"))
    }
}
