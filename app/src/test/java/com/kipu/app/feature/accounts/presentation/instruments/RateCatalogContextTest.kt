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

    @Test
    fun `resolves the explicit Sapphire canonical catalog name`() {
        val result = resolveRateCatalogContext(
            card("bcp-visa-latam-sapphire"),
            listOf(product("Visa Infinite Sapphire LATAM Pass")),
        )

        assertTrue(result is RateCatalogContext.Resolved)
    }

    @Test
    fun `resolves the explicit American Express canonical catalog name`() {
        val result = resolveRateCatalogContext(
            card("interbank-amex-gold", issuer = "Interbank", network = CardNetwork.AMEX),
            listOf(product("American Express Gold", institutionCode = "INTERBANK", network = "AMEX")),
        )

        assertTrue(result is RateCatalogContext.Resolved)
    }

    @Test
    fun `catalog selection persists BCP Amex identity through the rate lookup`() {
        val preset = com.kipu.app.feature.accounts.presentation.components.CardStylePresets
            .forProduct("BCP", "American Express Oro LATAM Pass", "AMEX")

        val result = resolveRateCatalogContext(
            card(preset?.id, network = CardNetwork.AMEX),
            listOf(product("American Express Oro LATAM Pass", institutionCode = "BCP", network = "AMEX")),
        )

        assertTrue(result is RateCatalogContext.Resolved)
    }

    @Test
    fun `catalog selection persists Interbank Amex identity through the rate lookup`() {
        val preset = com.kipu.app.feature.accounts.presentation.components.CardStylePresets
            .forProduct("INTERBANK", "American Express Gold", "AMEX")

        val result = resolveRateCatalogContext(
            card(preset?.id, issuer = "INTERBANK", network = CardNetwork.AMEX),
            listOf(product("American Express Gold", institutionCode = "INTERBANK", network = "AMEX", penTeaMinBps = null, penTeaMaxBps = null)),
        )

        assertTrue(result is RateCatalogContext.Resolved)
    }

    @Test
    fun `existing card is recovered only from an exact official identity`() {
        val legacyCard = card(stylePresetId = null, issuer = "INTERBANK", network = CardNetwork.AMEX)
            .copy(alias = "American Express Gold")

        val result = resolveRateCatalogContext(
            legacyCard,
            listOf(product("American Express Gold", institutionCode = "INTERBANK", network = "AMEX")),
        )

        assertTrue(result is RateCatalogContext.Resolved)
    }

    @Test
    fun `existing card without an exact product identity remains unresolved`() {
        val legacyCard = card(stylePresetId = null, issuer = "INTERBANK", network = CardNetwork.AMEX)
            .copy(alias = "American Express")

        assertTrue(resolveRateCatalogContext(legacyCard, emptyList()) is RateCatalogContext.MissingProductIdentity)
    }

    @Test
    fun `does not resolve Sapphire as Iridium`() {
        val result = resolveRateCatalogContext(
            card("bcp-visa-latam-sapphire"),
            listOf(product("Visa Infinite Iridium LATAM Pass")),
        )

        assertTrue(result is RateCatalogContext.NoApplicableReference)
    }

    @Test
    fun `keeps a matching product with unpublished tea as a reference without inventing a rate`() {
        val result = resolveRateCatalogContext(
            card("bcp-visa-latam-gold"),
            listOf(product("Visa Oro LATAM Pass", penTeaMinBps = null, penTeaMaxBps = null)),
        ) as RateCatalogContext.Resolved

        assertEquals(null, result.product.penTeaMinBps)
        assertEquals(null, result.product.penTeaMaxBps)
    }

    @Test
    fun `matches the official product for a USD card without changing its published ranges`() {
        val usdCard = card("bcp-amex-latam-gold", network = CardNetwork.AMEX).copy(currency = Currency.USD)
        val usdProduct = product("American Express Oro LATAM Pass", institutionCode = "BCP", network = "AMEX")
            .copy(usdTeaMinBps = 6500, usdTeaMaxBps = 7690)

        val result = resolveRateCatalogContext(usdCard, listOf(usdProduct)) as RateCatalogContext.Resolved

        assertEquals(6500, result.product.usdTeaMinBps)
        assertEquals(7690, result.product.usdTeaMaxBps)
    }

    @Test
    fun `catalog loading hides a previous error during retry`() {
        assertEquals(
            RateCatalogReferenceState.LOADING,
            rateCatalogReferenceState(true, "Previous error", RateCatalogContext.LoadingCard),
        )
    }

    @Test
    fun `card loading and missing card are distinct states`() {
        assertEquals(RateCatalogCardState.LOADING, rateCatalogCardState(true, null))
        assertEquals(RateCatalogCardState.NOT_FOUND, rateCatalogCardState(false, null))
    }

    @Test
    fun `restored tea draft is preserved for the same card and isolated for another card`() {
        val firstCardId = "card-1"
        val secondCardId = "card-2"

        assertEquals("53.25", personalTeaDraft(firstCardId, firstCardId, "53.25", 4_550))
        assertEquals("61.00", personalTeaDraft(firstCardId, secondCardId, "53.25", 6_100))
    }

    private fun card(
        stylePresetId: String?,
        issuer: String = "BCP",
        network: CardNetwork = CardNetwork.VISA,
    ) = CreditCard(
        id = CardId.generate(),
        userId = UserId.generate(),
        issuer = issuer,
        network = network,
        lastFourDigits = "1234",
        currency = Currency.PEN,
        creditLimitMinorUnits = 1_000_00L,
        billingDay = 10,
        dueDay = 20,
        personalTeaBps = 4_550,
        stylePresetId = stylePresetId,
    )

    private fun product(
        name: String,
        id: String = "product",
        institutionCode: String = "BCP",
        network: String = "VISA",
        penTeaMinBps: Int? = 4_550,
        penTeaMaxBps: Int? = 4_550,
    ) = CreditProductReference(
        id = id,
        institutionCode = institutionCode,
        institutionName = institutionCode,
        productName = name,
        cardNetwork = network,
        penTeaMinBps = penTeaMinBps,
        penTeaMaxBps = penTeaMaxBps,
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
