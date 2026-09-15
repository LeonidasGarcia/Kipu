package com.kipu.app.feature.plans.domain

import com.kipu.app.feature.plans.domain.model.AccessReason
import com.kipu.app.feature.plans.domain.model.Capability
import com.kipu.app.feature.plans.domain.model.FeatureAccessDecision
import com.kipu.app.feature.plans.domain.model.FeatureAccessRequest
import com.kipu.app.feature.plans.domain.model.FreePlanLimits
import org.junit.Test
import kotlin.test.assertEquals

class FeatureAccessPolicyTest {
    private val policy = FeatureAccessPolicy()
    private val limits = FreePlanLimits()

    @Test
    fun freeCoreIsAllowed() {
        assertEquals(allowed(), evaluate(Capability.FreeCore))
    }

    @Test
    fun allFiveLimitsAllowBelowThresholdAndDenyAtThreshold() {
        val cases = listOf(
            Capability.Instruments to limits.instruments,
            Capability.CustomCategories to limits.customCategories,
            Capability.Debts to limits.debts,
            Capability.Goals to limits.goals,
            Capability.Budgets to limits.budgets,
        )

        cases.forEach { (capability, limit) ->
            assertEquals(allowed(), evaluate(capability, limit - 1), "$capability below limit")
            assertEquals(deniedAtLimit(), evaluate(capability, limit), "$capability at limit")
            assertEquals(deniedAtLimit(), evaluate(capability, limit + 1), "$capability above limit")
        }
    }

    @Test
    fun denialDoesNotMutateExistingOverLimitUsage() {
        val usages = mutableListOf(1, 2, 3)

        assertEquals(deniedAtLimit(), evaluate(Capability.Debts, usages.size))
        assertEquals(listOf(1, 2, 3), usages)
    }

    @Test
    fun premiumRequiresVerifiedEntitlement() {
        assertEquals(
            FeatureAccessDecision.Denied(AccessReason.PREMIUM_ENTITLEMENT_REQUIRED),
            evaluate(Capability.PremiumOnly),
        )
    }

    @Test
    fun identicalInputsProduceIdenticalDecisions() {
        val request = FeatureAccessRequest(Capability.CustomCategories, 4, limits, null)
        assertEquals(policy.evaluate(request), policy.evaluate(request))
    }

    private fun evaluate(capability: Capability, usage: Int? = null) =
        policy.evaluate(FeatureAccessRequest(capability, usage, limits, null))

    private fun allowed() = FeatureAccessDecision.Allowed(AccessReason.FREE_CAPABILITY)

    private fun deniedAtLimit() = FeatureAccessDecision.Denied(AccessReason.FREE_LIMIT_REACHED)
}
