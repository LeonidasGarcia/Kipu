package com.kipu.app.feature.plans.data

import com.kipu.app.feature.plans.data.entitlement.OfflineEntitlementGrantPublicKeyResolver
import com.kipu.app.feature.plans.data.entitlement.OfflineEntitlementGrantBase64UrlDecoder
import com.kipu.app.feature.plans.data.entitlement.OfflineEntitlementGrantVerifier
import com.kipu.app.feature.plans.data.entitlement.InstallationSigningKeyProvider
import com.kipu.app.feature.plans.data.entitlement.InstallationPublicIdentity
import com.kipu.app.feature.plans.domain.model.SignedOfflineEntitlementGrant
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class OfflineEntitlementGrantVerifierTest {
    private val owner = "7d100000-0000-4000-8000-000000000001"
    private val thumbprint = "install-thumbprint"
    private val keyPair = ecKeyPair()
    private val resolver = OfflineEntitlementGrantPublicKeyResolver { keyId ->
        if (keyId == "test-key-1") keyPair.public else null
    }
    private val installIdentity = object : InstallationSigningKeyProvider {
        override fun getOrCreatePublicIdentity() = InstallationPublicIdentity("public", thumbprint)
    }
    private val base64Decoder = OfflineEntitlementGrantBase64UrlDecoder { Base64.getUrlDecoder().decode(it) }
    private val verifier = OfflineEntitlementGrantVerifier(resolver, installIdentity, base64Decoder)

    @Test
    fun acceptsValidSignatureAndReturnsSignedClaims() {
        val grant = signedGrant()

        val claims = verifier.verify(grant, owner)

        assertNotNull(claims)
        assertEquals(owner, claims?.userId)
        assertEquals(thumbprint, claims?.installationKeyThumbprint)
        assertEquals(1, claims?.policyVersion)
    }

    @Test
    fun rejectsPayloadAlterationAndWrongOwner() {
        val valid = signedGrant()
        val decoded = Base64.getUrlDecoder().decode(valid.payload)
        decoded[decoded.lastIndex] = (decoded.last().toInt() xor 1).toByte()
        val tampered = valid.copy(payload = Base64.getUrlEncoder().withoutPadding().encodeToString(decoded))

        assertNull(verifier.verify(tampered, owner))
        assertNull(verifier.verify(valid, "7d100000-0000-4000-8000-000000000002"))
    }

    @Test
    fun rejectsUnknownKeyAndInstallationMismatch() {
        val valid = signedGrant()
        assertNull(verifier.verify(valid.copy(keyId = "unknown-key"), owner))

        val otherInstall = object : InstallationSigningKeyProvider {
            override fun getOrCreatePublicIdentity() = InstallationPublicIdentity("public", "other-thumbprint")
        }
        assertNull(OfflineEntitlementGrantVerifier(resolver, otherInstall, base64Decoder).verify(valid, owner))
    }

    @Test
    fun rejectsMalformedAndNonEs256Signatures() {
        val valid = signedGrant()
        assertNull(verifier.verify(valid.copy(signature = "not-base64"), owner))
        assertNull(verifier.verify(valid.copy(signature = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(20))), owner))
    }

    private fun signedGrant(): SignedOfflineEntitlementGrant {
        val payload = """{"version":1,"keyId":"test-key-1","grantId":"d45373b2-9b32-4e19-a847-f585e59c50ba","userId":"$owner","installationKeyThumbprint":"$thumbprint","policyVersion":1,"tier":"PREMIUM","serverVerifiedAt":"2026-10-02T12:00:00.000Z","entitlementEndsAt":"2026-10-10T12:00:00.000Z","notAfter":"2026-10-05T12:00:00.000Z"}"""
        val bytes = payload.toByteArray(Charsets.UTF_8)
        val derSignature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(bytes)
            sign()
        }
        return SignedOfflineEntitlementGrant(
            payload = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
            signature = Base64.getUrlEncoder().withoutPadding().encodeToString(derToP1363(derSignature)),
            keyId = "test-key-1",
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
        val r = signature.copyOfRange(offset, offset + rLength).stripIntegerPadding()
        offset += rLength
        require(signature[offset++].toInt() == 0x02)
        val sLength = signature[offset++].toInt() and 0xff
        val s = signature.copyOfRange(offset, offset + sLength).stripIntegerPadding()
        return ByteArray(64).apply {
            r.copyInto(this, 32 - r.size)
            s.copyInto(this, 64 - s.size)
        }
    }

    private fun ByteArray.stripIntegerPadding(): ByteArray {
        var start = 0
        while (start < lastIndex && this[start] == 0.toByte()) start++
        return copyOfRange(start, size)
    }
}
