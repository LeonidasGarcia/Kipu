package com.kipu.app.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.kipu.app.core.logging.SecureLog
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.auth.SessionManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Encrypted session storage backed by Android Keystore using AES-256-GCM.
 * Implements Supabase-kt [SessionManager] to persist session envelopes securely.
 * Complies with FR-006, FR-012, and excluded from backups per FR-043.
 */
@Singleton
class KeystoreEncryptedSessionStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) : SessionManager {

    companion object {
        private const val TAG = "KeystoreSessionStorage"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "kipu_session_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val SESSION_FILE_NAME = "kipu_session.enc"
    }

    private val sessionFile by lazy { File(context.filesDir, SESSION_FILE_NAME) }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    override suspend fun saveSession(session: io.github.jan.supabase.auth.user.UserSession) {
        val json = kotlinx.serialization.json.Json.encodeToString(io.github.jan.supabase.auth.user.UserSession.serializer(), session)
        save(json)
    }

    override suspend fun loadSession(): io.github.jan.supabase.auth.user.UserSession {
        val json = load() ?: throw io.github.jan.supabase.auth.exception.NoSessionFoundException()
        return kotlinx.serialization.json.Json.decodeFromString(io.github.jan.supabase.auth.user.UserSession.serializer(), json)
    }

    override suspend fun deleteSession() {
        delete()
    }

    suspend fun save(session: String) {
        withContext(Dispatchers.IO) {
            try {
                val secretKey = getOrCreateSecretKey()
                val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                    init(Cipher.ENCRYPT_MODE, secretKey)
                }
                val iv = cipher.iv
                val encryptedBytes = cipher.doFinal(session.toByteArray(Charsets.UTF_8))

                val tempFile = File(context.filesDir, "$SESSION_FILE_NAME.tmp")
                FileOutputStream(tempFile).use { fos ->
                    fos.write(iv.size)
                    fos.write(iv)
                    fos.write(encryptedBytes)
                    fos.flush()
                }
                if (sessionFile.exists()) sessionFile.delete()
                tempFile.renameTo(sessionFile)
            } catch (e: Exception) {
                SecureLog.e(TAG, "Failed to save encrypted session", e)
            }
        }
    }

    suspend fun load(): String? {
        return withContext(Dispatchers.IO) {
            try {
                if (!sessionFile.exists() || sessionFile.length() == 0L) return@withContext null
                val bytes = FileInputStream(sessionFile).use { it.readBytes() }
                if (bytes.isEmpty()) return@withContext null

                val ivSize = bytes[0].toInt()
                if (bytes.size <= 1 + ivSize) return@withContext null
                val iv = bytes.copyOfRange(1, 1 + ivSize)
                val cipherText = bytes.copyOfRange(1 + ivSize, bytes.size)

                val secretKey = getOrCreateSecretKey()
                val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                    init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
                }
                val decrypted = cipher.doFinal(cipherText)
                String(decrypted, Charsets.UTF_8)
            } catch (e: Exception) {
                SecureLog.w(TAG, "Failed to decrypt session; clearing corrupted file", e)
                sessionFile.delete()
                null
            }
        }
    }

    suspend fun delete() {
        withContext(Dispatchers.IO) {
            try {
                if (sessionFile.exists()) {
                    sessionFile.delete()
                }
            } catch (e: Exception) {
                SecureLog.e(TAG, "Failed to delete encrypted session file", e)
            }
        }
    }
}
