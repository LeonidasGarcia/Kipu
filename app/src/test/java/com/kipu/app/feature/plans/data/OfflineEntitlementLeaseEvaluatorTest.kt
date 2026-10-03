package com.kipu.app.feature.plans.data

import com.kipu.app.feature.plans.data.entitlement.InstallationPublicIdentity
import com.kipu.app.feature.plans.data.entitlement.InstallationSigningKeyProvider
import com.kipu.app.feature.plans.data.entitlement.OfflineEntitlementClock
import com.kipu.app.feature.plans.data.entitlement.OfflineEntitlementClockReading
import com.kipu.app.feature.plans.data.entitlement.OfflineEntitlementGrantBase64UrlDecoder
import com.kipu.app.feature.plans.data.entitlement.OfflineEntitlementGrantPublicKeyResolver
import com.kipu.app.feature.plans.data.entitlement.OfflineEntitlementGrantVerifier
import com.kipu.app.feature.plans.data.entitlement.OfflineEntitlementLeaseEvaluator
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheEntity
import com.kipu.app.feature.plans.domain.OfflineEntitlementLeasePolicy
import com.kipu.app.feature.plans.domain.model.OfflineEntitlementLeaseDecision
import com.kipu.app.feature.plans.domain.model.SignedOfflineEntitlementGrant
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.Base64
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineEntitlementLeaseEvaluatorTest {
    private val owner = UUID.fromString("7d100000-0000-4000-8000-000000000001")
    private val thumbprint = "install-thumbprint"
    private val keyPair = ecKeyPair()
    private var reading = OfflineEntitlementClockReading(elapsedRealtimeMillis = 10_000L, bootCount = 7)

    private val installKeys = object : InstallationSigningKeyProvider {
        override fun getOrCreatePublicIdentity() = InstallationPublicIdentity("install-public-key", thumbprint)
    }
    private val verifier = OfflineEntitlementGrantVerifier(
        publicKeys = OfflineEntitlementGrantPublicKeyResolver { if (it == KEY_ID) keyPair.public else null },
        installationKeys = installKeys,
        base64UrlDecoder = OfflineEntitlementGrantBase64UrlDecoder { Base64.getUrlDecoder().decode(it) },
    )
    private val evaluator = OfflineEntitlementLeaseEvaluator(
        verifier = verifier,
        installationKeys = installKeys,
        clock = OfflineEntitlementClock { reading },
        policy = OfflineEntitlementLeasePolicy(),
    )

    @Test
    fun signedGrantStoredInCacheAllowsUntilExactLeaseBoundaryThenRevalidates() {
        val grant = signedGrant()
        val claims = requireNotNull(verifier.verify(grant, owner.toString()))
        val cache = FeatureAccessCacheEntity.fromVerifiedGrant(
            userId = owner,
            claims = claims,
            grant = grant,
            anchorElapsedRealtimeMillis = 10_000L,
            anchorBootCount = 7,
        )

        reading = reading.copy(elapsedRealtimeMillis = 10_000L + HOURS_72 - 1L)
        assertEquals(
            OfflineEntitlementLeaseDecision.Allowed(SERVER_VERIFIED_AT + HOURS_72 - 1L),
            evaluator.evaluate(owner.toString(), cache),
        )

        reading = reading.copy(elapsedRealtimeMillis = 10_000L + HOURS_72)
        assertTrue(evaluator.evaluate(owner.toString(), cache) is OfflineEntitlementLeaseDecision.RevalidationRequired)
    }

    @Test
    fun bootChangeOwnerChangeUnsignedRestoreAndFreeTierFailClosed() {
        val grant = signedGrant()
        val claims = requireNotNull(verifier.verify(grant, owner.toString()))
        val cache = FeatureAccessCacheEntity.fromVerifiedGrant(owner, claims, grant, 10_000L, 7)

        reading = reading.copy(bootCount = 8)
        assertTrue(evaluator.evaluate(owner.toString(), cache) is OfflineEntitlementLeaseDecision.RevalidationRequired)
        reading = reading.copy(bootCount = 7)
        assertTrue(evaluator.evaluate(UUID.randomUUID().toString(), cache) is OfflineEntitlementLeaseDecision.RevalidationRequired)

        val restoredUnsigned = FeatureAccessCacheEntity(
            userId = owner,
            policyVersion = 1,
            effectiveTier = "PREMIUM",
            verifiedAt = Instant.ofEpochMilli(SERVER_VERIFIED_AT),
            source = "VERIFIED_SERVER",
        )
        assertTrue(evaluator.evaluate(owner.toString(), restoredUnsigned) is OfflineEntitlementLeaseDecision.RevalidationRequired)
        assertEquals(
            OfflineEntitlementLeaseDecision.PremiumRequired,
            evaluator.evaluate(owner.toString(), FeatureAccessCacheEntity.verifiedFree(owner)),
        )
    }

    private fun signedGrant(): SignedOfflineEntitlementGrant {
        val serverVerifiedAt = Instant.ofEpochMilli(SERVER_VERIFIED_AT).toString()
        val entitlementEnd = Instant.ofEpochMilli(SERVER_VERIFIED_AT + HOURS_72 * 2L).toString()
        val notAfter = Instant.ofEpochMilli(SERVER_VERIFIED_AT + HOURS_72).toString()
        val payload = """{"version":1,"keyId":"$KEY_ID","grantId":"$GRANT_ID","userId":"$owner","installationKeyThumbprint":"$thumbprint","policyVersion":1,"tier":"PREMIUM","serverVerifiedAt":"$serverVerifiedAt","entitlementEndsAt":"$entitlementEnd","notAfter":"$notAfter"}"""
        val bytes = payload.toByteArray(Charsets.UTF_8)
        val derSignature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(bytes)
            sign()
        }
        return SignedOfflineEntitlementGrant(
            payload = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
            signature = Base64.getUrlEncoder().withoutPadding().encodeToString(derToP1363(derSignature)),
            keyId = KEY_ID,
        )
    }

    private fun ecKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }

    private fun derToP1363(signature: ByteArray): ByteArray {
        var offset = 2
        if (signature[1].toInt() and 0x80 != 0) offset = 2 + (signature[1].toInt() and 0x7f)
        require(signature[offset++].toInt() == 0x02)
        val rLength = signature[offset++].toInt() and 0xff
        val r = signature.copyOfRange(offset, offset + rLength).stripPadding()
        offset += rLength
        require(signature[offset++].toInt() == 0x02)
        val sLength = signature[offset++].toInt() and 0xff
        val s = signature.copyOfRange(offset, offset + sLength).stripPadding()
        return ByteArray(64).apply {
            r.copyInto(this, 32 - r.size)
            s.copyInto(this, 64 - s.size)
        }
    }

    private fun ByteArray.stripPadding(): ByteArray {
        var start = 0
        while (start < lastIndex && this[start] == 0.toByte()) start++
        return copyOfRange(start, size)
    }

    private companion object {
        const val KEY_ID = "test-key-1"
        const val GRANT_ID = "d45373b2-9b32-4e19-a847-f585e59c50ba"
        const val SERVER_VERIFIED_AT = 1_791_000_000_000L
        const val HOURS_72 = 72L * 60L * 60L * 1_000L
    }
}
