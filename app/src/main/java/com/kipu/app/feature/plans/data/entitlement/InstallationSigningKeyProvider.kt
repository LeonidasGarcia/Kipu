package com.kipu.app.feature.plans.data.entitlement

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

data class InstallationPublicIdentity(
    val publicKeyBase64: String,
    val thumbprint: String,
)

interface InstallationSigningKeyProvider {
    /** Returns the install-scoped public identity; private key bytes are never exposed. */
    fun getOrCreatePublicIdentity(): InstallationPublicIdentity
}

@Singleton
class AndroidKeystoreInstallationSigningKeyProvider @Inject constructor() : InstallationSigningKeyProvider {
    override fun getOrCreatePublicIdentity(): InstallationPublicIdentity {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) createKeyPair()

        val publicKey = keyStore.getCertificate(KEY_ALIAS)?.publicKey
            ?: error("Installation signing key is unavailable")
        require(publicKey.algorithm.equals("EC", ignoreCase = true))
        val encoded = publicKey.encoded ?: error("Installation public key has no encoded form")
        val thumbprint = MessageDigest.getInstance("SHA-256")
            .digest(encoded)
            .let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }

        return InstallationPublicIdentity(
            publicKeyBase64 = Base64.encodeToString(encoded, Base64.NO_WRAP),
            thumbprint = thumbprint,
        )
    }

    private fun createKeyPair() {
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEY_STORE)
        generator.initialize(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build(),
        )
        generator.generateKeyPair()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "kipu.offline-entitlement.installation.p256.v1"
    }
}
