package com.kipu.app.feature.categories.domain

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.model.CaptureCandidate
import com.kipu.app.feature.categories.domain.model.CaptureProvenance
import com.kipu.app.feature.categories.domain.model.MerchantAliasRule
import com.kipu.app.feature.categories.domain.model.MerchantAliasRuleId
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.domain.model.SourceMerchantText
import com.kipu.app.feature.categories.domain.usecase.EvaluateMerchantAlias
import com.kipu.app.feature.plans.domain.model.EffectiveEntitlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureCandidateAuthorizationTest {
    private val owner = UserId.generate()
    private val merchant = MerchantId.generate()
    private val provenance = CaptureProvenance(
        sourcePackage = "pe.example.bank",
        sourceEventId = "notification-42",
        capturedAtEpochMillis = 900,
    )

    @Test
    fun `candidate creation requires current verified Premium and consent`() {
        val noEntitlement = CaptureCandidate.authorize(
            owner, SourceMerchantText("TAMBO"), provenance,
            EffectiveEntitlement(verified = false, expiresAtEpochMillis = 2_000),
            consentGranted = true,
            nowEpochMillis = 1_000,
        )
        val noConsent = CaptureCandidate.authorize(
            owner, SourceMerchantText("TAMBO"), provenance,
            EffectiveEntitlement(verified = true, expiresAtEpochMillis = 2_000),
            consentGranted = false,
            nowEpochMillis = 1_000,
        )
        val expired = CaptureCandidate.authorize(
            owner, SourceMerchantText("TAMBO"), provenance,
            EffectiveEntitlement(verified = true, expiresAtEpochMillis = 1_000),
            consentGranted = true,
            nowEpochMillis = 1_000,
        )

        assertTrue(noEntitlement.isFailure)
        assertTrue(noConsent.isFailure)
        assertTrue(expired.isFailure)
    }

    @Test
    fun `authorized provenance-bearing candidate can be evaluated by exact alias`() {
        val candidate = CaptureCandidate.authorize(
            owner, SourceMerchantText(" IZIPAY*TÁMBO "), provenance,
            EffectiveEntitlement(verified = true, expiresAtEpochMillis = 2_000),
            consentGranted = true,
            nowEpochMillis = 1_000,
        ).getOrThrow()
        val rule = MerchantAliasRule(
            id = MerchantAliasRuleId.generate(), ownerId = owner,
            normalizedPattern = "izipay*tambo", merchantId = merchant,
        )

        val result = EvaluateMerchantAlias()(candidate, listOf(rule), setOf(merchant))

        assertEquals(MerchantAliasEvaluation.Match(merchant), result)
    }
}
