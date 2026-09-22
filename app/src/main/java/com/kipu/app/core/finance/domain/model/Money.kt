package com.kipu.app.core.finance.domain.model

import java.util.UUID

enum class Currency {
    PEN,
    USD;

    companion object {
        fun fromCode(code: String): Currency = when (code.trim().uppercase()) {
            "PEN" -> PEN
            "USD" -> USD
            else -> throw IllegalArgumentException("Unsupported currency code: $code")
        }
    }
}

@JvmInline
value class AccountId(val value: String) {
    init {
        require(runCatching { UUID.fromString(value) }.isSuccess) { "Invalid UUID for AccountId: $value" }
    }

    companion object {
        fun generate(): AccountId = AccountId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class CardId(val value: String) {
    init {
        require(runCatching { UUID.fromString(value) }.isSuccess) { "Invalid UUID for CardId: $value" }
    }

    companion object {
        fun generate(): CardId = CardId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class MovementId(val value: String) {
    init {
        require(runCatching { UUID.fromString(value) }.isSuccess) { "Invalid UUID for MovementId: $value" }
    }

    companion object {
        fun generate(): MovementId = MovementId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class OperationId(val value: String) {
    init {
        require(runCatching { UUID.fromString(value) }.isSuccess) { "Invalid UUID for OperationId: $value" }
    }

    companion object {
        fun generate(): OperationId = OperationId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class UserId(val value: String) {
    init {
        require(runCatching { UUID.fromString(value) }.isSuccess) { "Invalid UUID for UserId: $value" }
    }

    companion object {
        fun generate(): UserId = UserId(UUID.randomUUID().toString())
    }
}

data class Money(
    val minorUnits: Long,
    val currency: Currency
) : Comparable<Money> {
    init {
        require(kotlin.math.abs(minorUnits) <= MAX_MONEY_MINOR) {
            "Money minorUnits exceeds maximum allowed range [-$MAX_MONEY_MINOR, $MAX_MONEY_MINOR]: $minorUnits"
        }
    }

    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "Currency mismatch: $currency vs ${other.currency}" }
        val result = Math.addExact(minorUnits, other.minorUnits)
        return Money(result, currency)
    }

    operator fun minus(other: Money): Money {
        require(currency == other.currency) { "Currency mismatch: $currency vs ${other.currency}" }
        val result = Math.subtractExact(minorUnits, other.minorUnits)
        return Money(result, currency)
    }

    operator fun unaryMinus(): Money {
        return Money(Math.negateExact(minorUnits), currency)
    }

    override fun compareTo(other: Money): Int {
        require(currency == other.currency) { "Cannot compare different currencies: $currency vs ${other.currency}" }
        return minorUnits.compareTo(other.minorUnits)
    }

    companion object {
        const val MAX_MONEY_MINOR: Long = 99_999_999_999_999L

        fun zero(currency: Currency): Money = Money(0L, currency)
    }
}
