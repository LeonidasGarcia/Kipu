package com.kipu.app.feature.accounts.data.remote

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class RegisterCardRequestDtoTest {
    @Test
    fun `visual style and legacy preset serialize to separate nullable contract fields`() {
        val request = RegisterCardRequestDto(
            operationId = "operation-1",
            cardId = "card-1",
            type = "CREDIT",
            network = "VISA",
            issuer = "BCP",
            lastFourDigits = "1234",
            presetId = "BCP_VISA",
            stylePresetId = "bcp-visa-latam-gold",
            payloadHash = "hash",
        )

        val encoded = Json { encodeDefaults = true }.encodeToString(request)
        val fields = Json.parseToJsonElement(encoded).jsonObject

        assertEquals("BCP_VISA", fields.getValue("preset_id").jsonPrimitive.content)
        assertEquals("bcp-visa-latam-gold", fields.getValue("style_preset_id").jsonPrimitive.content)
    }
}
