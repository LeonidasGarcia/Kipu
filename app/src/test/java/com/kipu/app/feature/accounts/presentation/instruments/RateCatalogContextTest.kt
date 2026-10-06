package com.kipu.app.feature.accounts.presentation.instruments

import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditProductReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RateCatalogContextTest {
    @Test
    fun `resolves only the exact product selected for the card`() {
        val result = resolveRateCatalogContext(
            card(stylePresetId = "bcp-visa-latam-gold"),
            listOf(product("Visa Oro LATAM Pass"), product("Visa Clásica LATAM Pass")),
        )

        assertTrue(result is RateCatalogContext.Resolved)
        assertEquals("Visa Oro LATAM Pass", (result as RateCatalogContext.Resolved).product.productName)
    }

    @Test
    fun `does not select a rate when only issuer and network match`() {
        val result = resolveRateCatalogContext(
            card(stylePresetId = "bcp-visa-latam-gold"),
            listOf(product("Visa Clásica LATAM Pass")),
        )

        assertTrue(result is RateCatalogContext.NoApplicableReference)
    }

    @Test
    fun `does not select an ambiguous duplicate product`() {
        val result = resolveRateCatalogContext(
            card(stylePresetId = "bcp-visa-latam-gold"),
            listOf(product("Visa Oro LATAM Pass", "one"), product("Visa Oro LATAM Pass", "two")),
        )

        assertTrue(result is RateCatalogContext.NoApplicableReference)
    }

    @Test
    fun `reports a missing product identity without choosing a rate`() {
        val result = resolveRateCatalogContext(card(stylePresetId = null), listOf(product("Visa Oro LATAM Pass")))

        assertTrue(result is RateCatalogContext.MissingProductIdentity)
    }

    @Test
    fun `loading takes precedence over an absent reference`() {
        assertEquals(
            RateCatalogReferenceState.LOADING,
            rateCatalogReferenceState(true, null, RateCatalogContext.LoadingCard),
        )
    }

    @Test
    fun `error takes precedence over an absent reference after loading`() {
        assertEquals(
            RateCatalogReferenceState.ERROR,
            rateCatalogReferenceState(false, "Network error", RateCatalogContext.LoadingCard),
        )
    }

    @Test
    fun `resolved reference is available after successful loading`() {
        val context = resolveRateCatalogContext(card("bcp-visa-latam-gold"), listOf(product("Visa Oro LATAM Pass")))

        assertEquals(RateCatalogReferenceState.AVAILABLE, rateCatalogReferenceState(false, null, context))
    }

    private fun card(stylePresetId: String?) = CreditCard(
        id = CardId.generate(),
        userId = UserId.generate(),
        issuer = "BCP",
        network = CardNetwork.VISA,
        lastFourDigits = "1234",
        currency = Currency.PEN,
        creditLimitMinorUnits = 1_000_00L,
        billingDay = 10,
        dueDay = 20,
        personalTeaBps = 4_550,
        stylePresetId = stylePresetId,
    )

    private fun product(name: String, id: String = "product") = CreditProductReference(
        id = id,
        institutionCode = "BCP",
        institutionName = "BCP",
        productName = name,
        cardNetwork = "VISA",
        penTeaMinBps = 4_550,
        penTeaMaxBps = 4_550,
        usdTeaMinBps = null,
        usdTeaMaxBps = null,
        publishedTeaSummary = null,
        publishedTceaSummary = null,
        membershipFeePenMinor = null,
        membershipFeeUsdMinor = null,
        membershipCondition = null,
        sourceUrl = null,
        verificationStatus = "VERIFICADO",
        catalogAsOf = null,
        effectiveTo = null,
    )
}
