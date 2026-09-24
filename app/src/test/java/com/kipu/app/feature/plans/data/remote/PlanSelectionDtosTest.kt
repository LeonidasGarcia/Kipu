package com.kipu.app.feature.plans.data.remote

import com.kipu.app.feature.plans.domain.model.FreePlanLimits
import com.kipu.app.feature.plans.domain.model.TrialEligibilityStatus
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlanSelectionDtosTest {
    private val json = Json

    @Test
    fun requestUsesTypedSelectionStringRevisionAndMicrosecondTimestamp() {
        val dto = PlanSelectionRequestDto(
            1,
            "5d92af34-c725-4a1a-a863-2c93fa214c86",
            "9223372036854775807",
            PlanSelectionDto.PREMIUM_INTENT,
            "2026-09-14T15:03:12.123456Z",
        )

        val encoded = json.encodeToString(dto)
        assertTrue(encoded.contains("\"selection_revision\":\"9223372036854775807\""))
        assertTrue(encoded.contains("\"selection\":\"PREMIUM_INTENT\""))
        assertTrue(encoded.contains("2026-09-14T15:03:12.123456Z"))
    }

    @Test
    fun responseAcceptsEveryDocumentedResultAndRejectsUnknownEnums() {
        SelectionResultDto.entries.forEach { expected ->
            assertEquals(expected, json.decodeFromString<PlanSelectionResponseDto>(response(expected.name)).result)
        }
        assertFailsWith<SerializationException> {
            json.decodeFromString<PlanSelectionResponseDto>(response("RETRY"))
        }
    }

    @Test
    fun eligibilityVariantsMapOnlyValidStatusSourceShapes() {
        val eligible = json.decodeFromString<TrialEligibilityResponseDto>(
            """{"status":"ELIGIBLE","source":"VERIFIED_ACCOUNT_HISTORY","verified_at":"2026-09-14T15:03:12.123456Z","valid_until":"2026-09-21T15:03:12.123456Z"}""",
        ).toDomain()
        val ineligible = json.decodeFromString<TrialEligibilityResponseDto>(
            """{"status":"INELIGIBLE","source":"VERIFIED_ACCOUNT_HISTORY","verified_at":"2026-09-14T15:03:12.123456Z","valid_until":null}""",
        ).toDomain()
        val unknown = json.decodeFromString<TrialEligibilityResponseDto>(
            """{"status":"UNKNOWN","source":"UNAVAILABLE","verified_at":null,"valid_until":null}""",
        ).toDomain()

        assertEquals(TrialEligibilityStatus.ELIGIBLE, eligible.status)
        assertEquals(TrialEligibilityStatus.INELIGIBLE, ineligible.status)
        assertEquals(TrialEligibilityStatus.UNKNOWN, unknown.status)
        assertFailsWith<IllegalArgumentException> {
            TrialEligibilityResponseDto(
                TrialEligibilityStatusDto.ELIGIBLE,
                TrialEligibilitySourceDto.UNAVAILABLE,
                "2026-09-14T15:03:12Z",
                "2026-09-21T15:03:12Z",
            ).toDomain()
        }
    }

    @Test
    fun fixedFreeLimitsAndErrorRetryabilityRoundTrip() {
        val response = json.decodeFromString<PlanSelectionResponseDto>(response("APPLIED"))
        assertEquals(FreePlanLimits(), response.freeLimits.toDomain())

        val retryable = json.decodeFromString<ErrorResponseDto>(
            """{"code":"UNAVAILABLE","retryable":true,"retry_after_seconds":17}""",
        )
        val terminal = json.decodeFromString<ErrorResponseDto>(
            """{"code":"INVALID_REQUEST","retryable":false}""",
        )
        assertTrue(retryable.retryable)
        assertEquals(17, retryable.retryAfterSeconds)
        assertFalse(terminal.retryable)
    }

    @Test
    fun quotaSelectionDtosRoundTrip() {
        val request = QuotaSelectionRequestDto(
            contractVersion = 1,
            operationId = "5d92af34-c725-4a1a-a863-2c93fa214c86",
            featureKey = "CUSTOM_CATEGORIES",
            selectionRevision = "1",
            items = listOf(
                QuotaSelectionItemDto(
                    resourceId = "6a92af34-c725-4a1a-a863-2c93fa214c87",
                    resourceType = "CATEGORY_ROOT",
                ),
            ),
        )
        val encoded = json.encodeToString(request)
        assertTrue(encoded.contains("\"feature_key\":\"CUSTOM_CATEGORIES\""))
        assertTrue(encoded.contains("\"resource_type\":\"CATEGORY_ROOT\""))

        val responsePayload = """{"contract_version":1,"operation_id":"5d92af34-c725-4a1a-a863-2c93fa214c86","result":"APPLIED","feature_key":"CUSTOM_CATEGORIES","accepted_revision":"1","current_items":[{"resource_id":"6a92af34-c725-4a1a-a863-2c93fa214c87","resource_type":"CATEGORY_ROOT"}],"server_time":"2026-09-15T12:00:00.000000Z"}"""
        val response = json.decodeFromString<QuotaSelectionResponseDto>(responsePayload)
        assertEquals("APPLIED", response.result)
        assertEquals("1", response.acceptedRevision)
        assertEquals(1, response.currentItems?.size)
    }

    private fun response(result: String) =
        """{"contract_version":1,"operation_id":"5d92af34-c725-4a1a-a863-2c93fa214c86","result":"$result","accepted_revision":"3","current_preference":{"selection":"PREMIUM_INTENT","selected_at":"2026-09-14T15:03:12.123456Z","updated_at":"2026-09-15T12:00:00.000000Z"},"free_limits":{"policy_version":1,"instruments":4,"custom_categories":5,"debts":2,"goals":2,"budgets":2},"server_time":"2026-09-15T12:00:00.000000Z"}"""
}
