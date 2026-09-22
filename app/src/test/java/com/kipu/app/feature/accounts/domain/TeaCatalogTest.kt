package com.kipu.app.feature.accounts.domain

import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.accounts.domain.model.PersonalTea
import com.kipu.app.feature.accounts.domain.model.RateReference
import com.kipu.app.feature.accounts.domain.usecase.GetReferentialRates
import com.kipu.app.feature.accounts.domain.usecase.UpdatePersonalTea
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TeaCatalogTest {

    private lateinit var getRates: GetReferentialRates
    private lateinit var updateTea: UpdatePersonalTea

    @Before
    fun setup() {
        getRates = GetReferentialRates()
        updateTea = UpdatePersonalTea()
    }

    @Test
    fun `rate reference validates min and max basis points`() {
        val rate = RateReference(
            id = "test-rate",
            institution = "BCP",
            productName = "Visa Clásica",
            currency = Currency.PEN,
            minTeaBps = 2990,
            maxTeaBps = 8990,
            verifiedAt = LocalDate.of(2026, 1, 15),
        )

        assertEquals(29.9, rate.minTeaPercentage, 0.001)
        assertEquals(89.9, rate.maxTeaPercentage, 0.001)
        assertTrue(RateReference.DISCLAIMER.isNotBlank())
    }

    @Test
    fun `rate reference rejects invalid intervals`() {
        val negativeMin = runCatching {
            RateReference("bad", "BCP", "Card", Currency.PEN, -100, 5000, LocalDate.now())
        }.exceptionOrNull()
        assertTrue(negativeMin is IllegalArgumentException)

        val inverted = runCatching {
            RateReference("bad", "BCP", "Card", Currency.PEN, 6000, 3000, LocalDate.now())
        }.exceptionOrNull()
        assertTrue(inverted is IllegalArgumentException)
    }

    @Test
    fun `personal tea validates basis points range`() {
        val cardId = CardId.generate()
        val tea = PersonalTea(cardId = cardId, teaBps = 4550)
        assertEquals(45.5, tea.percentage, 0.001)

        val negativeTea = runCatching {
            PersonalTea(cardId = cardId, teaBps = -10)
        }.exceptionOrNull()
        assertTrue(negativeTea is IllegalArgumentException)

        val excessTea = runCatching {
            PersonalTea(cardId = cardId, teaBps = 100_001)
        }.exceptionOrNull()
        assertTrue(excessTea is IllegalArgumentException)
    }

    @Test
    fun `get referential rates filters by currency`() {
        val all = getRates()
        assertTrue(all.isNotEmpty())

        val penOnly = getRates(Currency.PEN)
        assertTrue(penOnly.all { it.currency == Currency.PEN })

        val usdOnly = getRates(Currency.USD)
        assertTrue(usdOnly.all { it.currency == Currency.USD })
    }

    @Test
    fun `update and retrieve personal tea`() {
        val cardId = CardId.generate()
        val result = updateTea(cardId, 4200)
        assertTrue(result.isSuccess)

        val retrieved = updateTea.getPersonalTea(cardId)
        assertNotNull(retrieved)
        assertEquals(4200, retrieved?.teaBps)
    }
}
