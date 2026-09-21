package com.kipu.app.core.logging

import android.util.Log

/**
 * Utility for sanitizing log messages to prevent exposure of passwords,
 * tokens, recovery secrets, raw emails, and sensitive financial data
 * in compliance with FR-006 and FR-043.
 */
object LogRedactor {

    private val JWT_REGEX = Regex("""eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")
    private val EMAIL_REGEX = Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b""")
    private val PASSWORD_JSON_REGEX = Regex("""(?i)"password"\s*:\s*"[^"]*"""")
    private val PASSWORD_PARAM_REGEX = Regex("""(?i)(password|pass|secret)\s*=\s*([^\s&,;]+)""")
    private val RECOVERY_TOKEN_REGEX = Regex("""(?i)(recovery_token|token|access_token|refresh_token)\s*=\s*([^\s&,;]+)""")
    private val BEARER_REGEX = Regex("""(?i)Bearer\s+[A-Za-z0-9._~+/-]+=*""")

    /**
     * Sanitizes a log message by replacing sensitive values with redaction tokens.
     */
    fun redact(message: String?): String {
        if (message.isNullOrEmpty()) return ""
        var sanitized = message
        sanitized = JWT_REGEX.replace(sanitized, "[REDACTED_JWT]")
        sanitized = BEARER_REGEX.replace(sanitized, "Bearer [REDACTED_TOKEN]")
        sanitized = PASSWORD_JSON_REGEX.replace(sanitized, """"password":"[REDACTED_PASSWORD]"""")
        sanitized = PASSWORD_PARAM_REGEX.replace(sanitized, "$1=[REDACTED_PASSWORD]")
        sanitized = RECOVERY_TOKEN_REGEX.replace(sanitized, "$1=[REDACTED_TOKEN]")
        sanitized = EMAIL_REGEX.replace(sanitized) { matchResult ->
            maskEmail(matchResult.value)
        }
        return sanitized
    }

    private fun maskEmail(email: String): String {
        val parts = email.split("@")
        if (parts.size != 2) return "[REDACTED_EMAIL]"
        val name = parts[0]
        val domain = parts[1]
        val maskedName = if (name.length <= 2) "${name.first()}***" else "${name.first()}***${name.last()}"
        return "$maskedName@$domain"
    }
}

/**
 * Secure logging wrapper that automatically applies [LogRedactor] to all output.
 */
object SecureLog {

    fun d(tag: String, message: String) {
        Log.d(tag, LogRedactor.redact(message))
    }

    fun i(tag: String, message: String) {
        Log.i(tag, LogRedactor.redact(message))
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(tag, LogRedactor.redact(message), sanitizeThrowable(throwable))
        } else {
            Log.w(tag, LogRedactor.redact(message))
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, LogRedactor.redact(message), sanitizeThrowable(throwable))
        } else {
            Log.e(tag, LogRedactor.redact(message))
        }
    }

    private fun sanitizeThrowable(throwable: Throwable): Throwable {
        val message = throwable.message?.let { LogRedactor.redact(it) }
        return RuntimeException(message)
    }
}
