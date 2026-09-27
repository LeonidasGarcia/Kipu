package com.kipu.app.feature.plans

import java.io.File
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlanFeatureBoundaryTest {
    @Test
    fun playBillingSdkIsIsolatedToThePlatformGatewayAndNoServerSecretsShipToAndroid() {
        val root = File("src/main/java/com/kipu/app/feature/plans")
        val sources = root.walkTopDown().filter { it.extension == "kt" }.associate { it to it.readText() }
        val gateway = sources.entries.single { it.key.path.replace(File.separatorChar, '/')
            .endsWith("data/billing/PlayBillingGateway.kt") }.value
        assertTrue(gateway.contains("com.android.billing"))
        assertFalse(sources.filterKeys { !it.path.replace(File.separatorChar, '/')
            .endsWith("data/billing/PlayBillingGateway.kt") }
            .values.any { it.contains("com.android.billing") })
        val text = sources.values.joinToString("\n")
        assertFalse(text.contains("service_role", ignoreCase = true))
    }

    @Test
    fun productionDependenciesUseThePinnedBillingVersion() {
        val moduleDependencies = File("build.gradle.kts").readText()
        val dependencyCatalog = File("../gradle/libs.versions.toml").readText()
        assertTrue(dependencyCatalog.contains("billing = \"9.1.0\""))
        assertTrue(dependencyCatalog.contains("play-billing = { group = \"com.android.billingclient\", name = \"billing\", version.ref = \"billing\" }"))
        assertTrue(moduleDependencies.contains("implementation(libs.play.billing)"))
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
