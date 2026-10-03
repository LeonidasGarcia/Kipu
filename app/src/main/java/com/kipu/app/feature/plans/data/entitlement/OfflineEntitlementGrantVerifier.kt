package com.kipu.app.feature.plans.data.entitlement

import android.util.Base64
import com.kipu.app.BuildConfig
import com.kipu.app.feature.plans.domain.model.OfflineEntitlementGrantClaims
import com.kipu.app.feature.plans.domain.model.SignedOfflineEntitlementGrant
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun interface OfflineEntitlementGrantPublicKeyResolver {
    fun resolve(keyId: String): PublicKey?
}

fun interface OfflineEntitlementGrantBase64UrlDecoder {
    fun decode(value: String): ByteArray
}

@Singleton
class AndroidOfflineEntitlementGrantBase64UrlDecoder @Inject constructor() :
    OfflineEntitlementGrantBase64UrlDecoder {
    override fun decode(value: String): ByteArray =
        Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}

@Singleton
class BuildConfiguredOfflineEntitlementGrantPublicKeyResolver @Inject constructor() :
    OfflineEntitlementGrantPublicKeyResolver {
    override fun resolve(keyId: String): PublicKey? = runCatching {
        if (BuildConfig.OFFLINE_GRANT_KEY_ID.isBlank() || keyId != BuildConfig.OFFLINE_GRANT_KEY_ID) {
            return null
        }
        val encoded = Base64.decode(
            BuildConfig.OFFLINE_GRANT_PUBLIC_KEY_X509_BASE64,
            Base64.DEFAULT,
        )
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))
    }.getOrNull()
}

@Serializable
data class OfflineEntitlementGrantPayload(
    val version: Int,
    val keyId: String,
    val grantId: String,
    val userId: String,
    val installationKeyThumbprint: String,
    val policyVersion: Int,
    val tier: String,
    val serverVerifiedAt: String,
    val entitlementEndsAt: String? = null,
    val notAfter: String,
)

/** Verifies the server signature before decoding or trusting any grant claim. */
@Singleton
class OfflineEntitlementGrantVerifier @Inject constructor(
    private val publicKeys: OfflineEntitlementGrantPublicKeyResolver,
    private val installationKeys: InstallationSigningKeyProvider,
    private val base64UrlDecoder: OfflineEntitlementGrantBase64UrlDecoder,
) {
    fun verify(
        grant: SignedOfflineEntitlementGrant?,
        expectedUserId: String,
    ): OfflineEntitlementGrantClaims? = runCatching {
        if (grant == null || grant.keyId.isBlank() || grant.keyId.length > 64) return null
        if (grant.payload.length > MAX_ENCODED_PAYLOAD_LENGTH || grant.signature.length > MAX_ENCODED_SIGNATURE_LENGTH) return null

        val payloadBytes = decodeBase64Url(grant.payload) ?: return null
        val signatureBytes = decodeBase64Url(grant.signature) ?: return null
        if (payloadBytes.isEmpty() || payloadBytes.size > MAX_PAYLOAD_BYTES || signatureBytes.size != ES256_SIGNATURE_BYTES) {
            return null
        }

        val publicKey = publicKeys.resolve(grant.keyId) ?: return null
        if (!publicKey.algorithm.equals("EC", ignoreCase = true)) return null
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(payloadBytes)
        if (!verifier.verify(rawEcdsaToDer(signatureBytes))) return null

        val payload = Json.decodeFromString<OfflineEntitlementGrantPayload>(
            payloadBytes.toString(Charsets.UTF_8),
        )
        if (payload.keyId != grant.keyId || payload.userId != expectedUserId) return null
        if (payload.version != GRANT_VERSION || payload.policyVersion != POLICY_VERSION || payload.tier != PREMIUM_TIER) return null
        UUID.fromString(payload.userId)
        UUID.fromString(payload.grantId)

        val identity = installationKeys.getOrCreatePublicIdentity()
        if (payload.installationKeyThumbprint != identity.thumbprint) return null
        val serverVerifiedAt = Instant.parse(payload.serverVerifiedAt).toEpochMilli()
        val entitlementEndsAt = payload.entitlementEndsAt?.let { Instant.parse(it).toEpochMilli() }
        val notAfter = Instant.parse(payload.notAfter).toEpochMilli()

        OfflineEntitlementGrantClaims(
            version = payload.version,
            keyId = payload.keyId,
            grantId = payload.grantId,
            userId = payload.userId,
            installationKeyThumbprint = payload.installationKeyThumbprint,
            policyVersion = payload.policyVersion,
            tier = payload.tier,
            serverVerifiedAtMillis = serverVerifiedAt,
            entitlementEndsAtMillis = entitlementEndsAt,
            notAfterMillis = notAfter,
        )
    }.getOrNull()

    private fun decodeBase64Url(value: String): ByteArray? = runCatching {
        require(value.matches(BASE64_URL_PATTERN))
        base64UrlDecoder.decode(value)
    }.getOrNull()

    private fun rawEcdsaToDer(raw: ByteArray): ByteArray {
        require(raw.size == ES256_SIGNATURE_BYTES)
        val r = raw.copyOfRange(0, 32).toPositiveDerInteger()
        val s = raw.copyOfRange(32, 64).toPositiveDerInteger()
        val sequenceLength = 2 + r.size + 2 + s.size
        return byteArrayOf(0x30, sequenceLength.toByte(), 0x02, r.size.toByte()) + r +
            byteArrayOf(0x02, s.size.toByte()) + s
    }

    private fun ByteArray.toPositiveDerInteger(): ByteArray {
        var first = 0
        while (first < lastIndex && this[first] == 0.toByte()) first++
        val magnitude = copyOfRange(first, size)
        return if (magnitude[0].toInt() and 0x80 != 0) byteArrayOf(0) + magnitude else magnitude
    }

    private companion object {
        const val GRANT_VERSION = 1
        const val POLICY_VERSION = 1
        const val PREMIUM_TIER = "PREMIUM"
        const val ES256_SIGNATURE_BYTES = 64
        const val MAX_PAYLOAD_BYTES = 4_096
        const val MAX_ENCODED_PAYLOAD_LENGTH = 5_500
        const val MAX_ENCODED_SIGNATURE_LENGTH = 128
        val BASE64_URL_PATTERN = Regex("^[A-Za-z0-9_-]+$")
    }
}
