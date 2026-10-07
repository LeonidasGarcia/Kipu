package com.kipu.app.feature.accounts.presentation.components

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardStylePresetsTest {

    @Test
    fun `canonical IDs are unique and only selectable bank products are included`() {
        val ids = CardStylePresets.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(68, ids.size)
        assertFalse(CardStylePresets.all.any { '?' in it.familyLabel })
        assertEquals(19, CardStylePresets.forInstitution("BCP", CardStyleType.DEBIT).size +
            CardStylePresets.forInstitution("BCP", CardStyleType.CREDIT).size)
        assertEquals(4, CardStylePresets.forInstitution("SCOTIABANK", CardStyleType.DEBIT).size +
            CardStylePresets.forInstitution("SCOTIABANK", CardStyleType.CREDIT).size)
        assertFalse(CardStylePresets.all.any { it.familyId == "scotiabank-regular-hist" })
    }

    @Test
    fun `declared foreground passes AA at both ends of every gradient`() {
        CardStylePresets.all.forEach { preset ->
            val text = preset.textColor
            val startRatio = contrastRatio(text, colorFromHex(preset.gradientStartHex))
            val endRatio = contrastRatio(text, colorFromHex(preset.gradientEndHex))
            assertTrue("${preset.id} start=$startRatio", startRatio >= 4.5)
            assertTrue("${preset.id} end=$endRatio", endRatio >= 4.5)
        }
    }

    @Test
    fun `product matching rejects excluded and ambiguous products`() {
        assertNotNull(CardStylePresets.forProduct("SCOTIABANK", "Visa Singular Signature"))
        assertNull(CardStylePresets.forProduct("SCOTIABANK", "Visa Clásica"))
        assertEquals("bcp-visa-latam-gold", CardStylePresets.forProduct("BCP", "Visa Oro LATAM Pass")?.id)
    }

    @Test
    fun `official catalog names select the explicit Amex presets`() {
        assertEquals(
            "bcp-amex-latam-gold",
            CardStylePresets.forProduct("BCP", "American Express Oro LATAM Pass", "AMEX")?.id,
        )
        assertEquals(
            "interbank-amex-gold",
            CardStylePresets.forProduct("INTERBANK", "American Express Gold", "AMEX")?.id,
        )
    }

    @Test
    fun `official mappings keep Sapphire and Iridium distinct and reject partial names`() {
        assertEquals(
            "bcp-visa-latam-sapphire",
            CardStylePresets.forProduct("BCP", "Visa Infinite Sapphire LATAM Pass", "VISA")?.id,
        )
        assertEquals(
            "bcp-visa-latam-iridium",
            CardStylePresets.forProduct("BCP", "Visa Infinite Iridium LATAM Pass", "VISA")?.id,
        )
        assertNull(CardStylePresets.forProduct("BCP", "Visa Infinite", "VISA"))
    }

    private fun contrastRatio(foreground: androidx.compose.ui.graphics.Color, background: androidx.compose.ui.graphics.Color): Double {
        val foregroundLuminance = luminance(foreground.red, foreground.green, foreground.blue)
        val backgroundLuminance = luminance(background.red, background.green, background.blue)
        return (max(foregroundLuminance, backgroundLuminance) + 0.05) /
            (min(foregroundLuminance, backgroundLuminance) + 0.05)
    }

    private fun luminance(red: Float, green: Float, blue: Float): Double =
        0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)

    private fun channel(value: Float): Double {
        val normalized = value.toDouble()
        return if (normalized <= 0.04045) normalized / 12.92 else ((normalized + 0.055) / 1.055).pow(2.4)
    }
}
