package com.kipu.app.feature.accounts.domain.model

import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.UserId
import java.time.Instant

enum class CardNetwork {
    VISA,
    MASTERCARD,
    AMEX,
    OTHER,
}

enum class CardPreset(
    val defaultName: String,
    val defaultColorToken: String,
    val defaultIconToken: String,
) {
    BCP_VISA("BCP Visa", "preset_bcp", "credit_card"),
    BBVA_VISA("BBVA Visa", "preset_bbva", "credit_card"),
    INTERBANK_VISA("Interbank Visa", "preset_interbank", "credit_card"),
    SCOTIABANK_MASTERCARD("Scotiabank Mastercard", "preset_scotiabank", "credit_card"),
    GENERIC("Tarjeta", "preset_generic", "credit_card");

    companion object {
        fun fromId(id: String?): CardPreset? = entries.firstOrNull { it.name.equals(id, ignoreCase = true) }
    }
}

sealed interface Card {
    val id: CardId
    val userId: UserId
    val alias: String?
    val issuer: String
    val network: CardNetwork
    val lastFourDigits: String
    val currency: Currency
    val preset: CardPreset?
    val colorToken: String?
    val iconToken: String?
    val isArchived: Boolean
    val remoteRevision: Long
    val createdAt: Instant
    val updatedAt: Instant
    val isPlanLocked: Boolean get() = false

    val isComputableForQuota: Boolean
        get() = true

    val maskedDisplay: String
        get() = "•••• $lastFourDigits"

    fun validateCardCommon() {
        require(lastFourDigits.length == 4 && lastFourDigits.all { it.isDigit() }) {
            "Last four digits must be exactly 4 numeric digits"
        }
        require(issuer.isNotBlank() && issuer.trim().length in 1..80) {
            "Issuer must be between 1 and 80 characters"
        }
        require(!isForbiddenSensitiveCardInput(issuer)) {
            "Issuer cannot contain full PAN or CVV-like patterns"
        }
        alias?.let {
            require(it.isNotBlank() && it.trim().length in 1..80) {
                "Alias must be between 1 and 80 characters when provided"
            }
            require(!isForbiddenSensitiveCardInput(it)) {
                "Alias cannot contain full PAN or CVV-like patterns"
            }
        }
    }

    companion object {
        fun isForbiddenSensitiveCardInput(text: String): Boolean {
            val digits = text.filter { it.isDigit() }
            if (digits.length in 13..19) return true
            if (Regex("\\d{13,19}").containsMatchIn(text)) return true
            if (text.trim().matches(Regex("^\\d{3,4}$"))) return true
            return false
        }
    }
}

data class DebitCard(
    override val id: CardId,
    override val userId: UserId,
    override val alias: String? = null,
    override val issuer: String,
    override val network: CardNetwork,
    override val lastFourDigits: String,
    override val currency: Currency,
    val linkedAccountId: AccountId,
    override val preset: CardPreset? = null,
    override val colorToken: String? = null,
    override val iconToken: String? = null,
    override val isArchived: Boolean = false,
    override val isPlanLocked: Boolean = false,
    override val remoteRevision: Long = 0L,
    override val createdAt: Instant = Instant.now(),
    override val updatedAt: Instant = Instant.now(),
) : Card {
    init {
        validateCardCommon()
    }
}

data class CreditCard(
    override val id: CardId,
    override val userId: UserId,
    override val alias: String? = null,
    override val issuer: String,
    override val network: CardNetwork,
    override val lastFourDigits: String,
    override val currency: Currency,
    val creditLimitMinorUnits: Long,
    val billingDay: Int,
    val dueDay: Int,
    val personalTeaBps: Int? = null,
    override val preset: CardPreset? = null,
    override val colorToken: String? = null,
    override val iconToken: String? = null,
    override val isArchived: Boolean = false,
    override val isPlanLocked: Boolean = false,
    override val remoteRevision: Long = 0L,
    override val createdAt: Instant = Instant.now(),
    override val updatedAt: Instant = Instant.now(),
) : Card {
    init {
        validateCardCommon()
        require(creditLimitMinorUnits >= 0) {
            "Credit limit cannot be negative"
        }
        require(billingDay in 1..31) {
            "Billing day must be between 1 and 31"
        }
        require(dueDay in 1..31) {
            "Due day must be between 1 and 31"
        }
        personalTeaBps?.let {
            require(it >= 0) { "TEA basis points cannot be negative" }
        }
    }
}
