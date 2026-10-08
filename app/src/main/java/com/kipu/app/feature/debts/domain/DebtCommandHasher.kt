package com.kipu.app.feature.debts.domain

import java.security.MessageDigest

/** Hashes canonical debt command JSON before the command leaves the device. */
object DebtCommandHasher {
    fun sha256(canonicalPayload: String): String {
        require(canonicalPayload.isNotBlank()) { "A canonical command payload is required" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonicalPayload.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
