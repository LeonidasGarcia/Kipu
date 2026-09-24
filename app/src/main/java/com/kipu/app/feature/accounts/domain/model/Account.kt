package com.kipu.app.feature.accounts.domain.model

import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import java.time.Instant

enum class AccountType {
    CASH,
    SAVINGS,
    BANK,
    DIGITAL_WALLET,
}

enum class AccountPreset(
    val defaultName: String,
    val defaultColorToken: String,
    val defaultIconToken: String,
) {
    BCP("BCP", "preset_bcp", "account_balance"),
    BBVA("BBVA", "preset_bbva", "account_balance"),
    INTERBANK("Interbank", "preset_interbank", "account_balance"),
    SCOTIABANK("Scotiabank", "preset_scotiabank", "account_balance"),
    BANCO_NACION("Banco de la Nación", "preset_bn", "account_balance"),
    YAPE("Yape", "preset_yape", "wallet"),
    PLIN("Plin", "preset_plin", "wallet"),
    CASH("Efectivo", "preset_cash", "payments"),
    GENERIC("Otro", "preset_generic", "account_balance");

    companion object {
        fun fromId(id: String?): AccountPreset? = entries.firstOrNull { it.name.equals(id, ignoreCase = true) }
    }
}

data class Account(
    val id: AccountId,
    val userId: UserId,
    val alias: String,
    val type: AccountType,
    val currency: Currency,
    val preset: AccountPreset? = null,
    val colorToken: String? = null,
    val iconToken: String? = null,
    val initialBalance: Money,
    val openedAt: Instant,
    val isArchived: Boolean = false,
    val remoteRevision: Long = 0L,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val isPlanLocked: Boolean = false,
) {
    init {
        require(alias.isNotBlank() && alias.trim().length in 1..80) {
            "Account alias must be between 1 and 80 characters"
        }
        require(initialBalance.currency == currency) {
            "Initial balance currency (${initialBalance.currency}) must match account currency ($currency)"
        }
        require(initialBalance.minorUnits >= 0) {
            "Initial balance cannot be negative"
        }
    }

    val isComputableForQuota: Boolean
        get() = type != AccountType.CASH
}
