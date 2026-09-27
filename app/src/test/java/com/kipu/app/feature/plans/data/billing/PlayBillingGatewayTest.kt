package com.kipu.app.feature.plans.data.billing

import com.kipu.app.feature.plans.domain.model.BillingProduct
import com.kipu.app.feature.plans.domain.model.BillingProductKind
import com.kipu.app.feature.plans.domain.model.BillingPurchaseRequest
import com.kipu.app.feature.plans.domain.model.CommercialOption
import com.kipu.app.feature.plans.domain.model.EphemeralPurchaseToken
import com.kipu.app.feature.plans.domain.model.StorePurchaseState
import com.kipu.app.feature.plans.domain.model.StorePurchaseUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayBillingGatewayTest {
    @Test
    fun mapsOnlyTheThreeServerCatalogPlanTypesToPlayProductKinds() {
        val monthly = BillingProduct.fromCatalog("m", "kipu_pro_monthly", "monthly", "Monthly", "PRO_MONTHLY")
        val annual = BillingProduct.fromCatalog("a", "kipu_pro_annual", "annual", "Annual", "PRO_ANNUAL")
        val lifetime = BillingProduct.fromCatalog("l", "kipu_pro_lifetime", null, "Lifetime", "PRO_LIFETIME")

        assertEquals(CommercialOption.MONTHLY, monthly?.option)
        assertEquals(BillingProductKind.SUBSCRIPTION, monthly?.kind)
        assertEquals(CommercialOption.ANNUAL, annual?.option)
        assertEquals(BillingProductKind.SUBSCRIPTION, annual?.kind)
        assertEquals(CommercialOption.LIFETIME, lifetime?.option)
        assertEquals(BillingProductKind.ONE_TIME, lifetime?.kind)
        assertNull(BillingProduct.fromCatalog("free", "free", null, "Free", "FREE"))
        assertNull(BillingProduct.fromCatalog("l", "kipu_pro_lifetime", "annual", "Lifetime", "PRO_LIFETIME"))
    }

    @Test
    fun purchaseCallbacksRequireBackendVerificationAndPendingNeverDoes() {
        val ephemeral = EphemeralPurchaseToken("provider-token-secret")
        val purchased = StorePurchaseUpdate("kipu_pro_monthly", ephemeral, StorePurchaseState.PURCHASED)
        val pending = StorePurchaseUpdate("kipu_pro_monthly", ephemeral, StorePurchaseState.PENDING)

        assertTrue(purchased.requiresBackendVerification)
        assertFalse(pending.requiresBackendVerification)
        assertFalse(purchased.toString().contains("provider-token-secret"))
        assertFalse(ephemeral.toString().contains("provider-token-secret"))
        assertFalse(BillingPurchaseRequest("kipu_pro_monthly", ephemeral).toString().contains("provider-token-secret"))
        assertNotNull(purchased.purchaseToken)
    }
}
