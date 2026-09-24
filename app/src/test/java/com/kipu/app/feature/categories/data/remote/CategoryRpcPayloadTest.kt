package com.kipu.app.feature.categories.data.remote

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CategoryRpcPayloadTest {

    @Test
    fun `category type is nested under the current p_payload RPC argument`() {
        val request = CreateCategoryRequestDto(
            operationId = "operation",
            categoryId = "category",
            categoryType = "INCOME",
            name = "Salario",
            icon = "work",
            color = "#112233",
            payloadHash = "hash",
        )

        val body = Json.encodeToString(CategoryRpcPayload(request))
        val root = Json.parseToJsonElement(body).jsonObject
        val payload = root.getValue("p_payload").jsonObject

        assertEquals("INCOME", payload.getValue("category_type").jsonPrimitive.content)
        assertEquals("category", payload.getValue("category_id").jsonPrimitive.content)
        assertFalse(root.containsKey("category_type"))
    }
}
