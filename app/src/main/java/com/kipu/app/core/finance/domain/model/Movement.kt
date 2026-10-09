package com.kipu.app.core.finance.domain.model

import java.time.Instant

enum class MovementKind {
    OPENING,
    ADJUSTMENT,
    REVERSAL,
    CREDIT_PURCHASE,
    CARD_PAYMENT_CASH,
    CARD_PAYMENT_LIABILITY;

    val isSprint2Allowed: Boolean
        get() = this in setOf(OPENING, ADJUSTMENT, REVERSAL)
}

enum class MovementStatus {
    POSTED,
    PENDING,
}

data class FinancialMovement(
    val id: MovementId,
    val operationId: OperationId,
    val sequence: Int,
    val userId: UserId,
    val kind: MovementKind,
    val amountMinorUnits: Long,
    val currency: Currency,
    val accountId: AccountId? = null,
    val cardId: CardId? = null,
    val effectiveAt: Instant,
    val status: MovementStatus = MovementStatus.POSTED,
    val reversesMovementId: MovementId? = null,
    val adjustsMovementId: MovementId? = null,
    val createdAt: Instant = Instant.now(),
    val merchantName: String? = null,
) {
    init {
        require(sequence >= 0) { "Sequence must be non-negative: $sequence" }
        require(kotlin.math.abs(amountMinorUnits) <= Money.MAX_MONEY_MINOR) {
            "amountMinorUnits exceeds maximum: $amountMinorUnits"
        }
        require(accountId != null || cardId != null) {
            "Movement must target at least one account or card"
        }
        if (kind == MovementKind.OPENING) {
            require(accountId != null) { "OPENING movement must target an account" }
            require(reversesMovementId == null && adjustsMovementId == null) {
                "OPENING movement cannot reverse or adjust another movement"
            }
        }
        if (reversesMovementId != null) {
            require(kind == MovementKind.REVERSAL) { "Only REVERSAL movements can specify reversesMovementId" }
        }
        if (adjustsMovementId != null) {
            require(kind == MovementKind.ADJUSTMENT) { "Only ADJUSTMENT movements can specify adjustsMovementId" }
        }
    }

    val signedMoney: Money
        get() = Money(amountMinorUnits, currency)
}
