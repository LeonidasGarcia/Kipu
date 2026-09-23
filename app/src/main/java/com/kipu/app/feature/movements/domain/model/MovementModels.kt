package com.kipu.app.feature.movements.domain.model

enum class MovementType {
    EXPENSE,
    INCOME,
    TRANSFER;

    companion object {
        fun fromString(value: String): MovementType = when (value.uppercase()) {
            "EXPENSE" -> EXPENSE
            "INCOME" -> INCOME
            "TRANSFER" -> TRANSFER
            else -> throw IllegalArgumentException("Unknown movement type: $value")
        }
    }
}

enum class LedgerRole {
    SOURCE,
    DESTINATION
}

enum class MovementSyncStatus {
    PENDING,
    IN_FLIGHT,
    SYNCED,
    MIGRATED_LOCAL,
    CONFLICT,
    FAILED_PERMANENT;

    companion object {
        fun fromString(value: String): MovementSyncStatus = when (value.uppercase()) {
            "PENDING" -> PENDING
            "IN_FLIGHT" -> IN_FLIGHT
            "SYNCED" -> SYNCED
            "MIGRATED_LOCAL" -> MIGRATED_LOCAL
            "CONFLICT" -> CONFLICT
            "FAILED_PERMANENT" -> FAILED_PERMANENT
            else -> PENDING
        }
    }
}

enum class TransactionStatus {
    ACTIVE;

    companion object {
        fun fromString(value: String): TransactionStatus = when (value.uppercase()) {
            "ACTIVE" -> ACTIVE
            else -> ACTIVE
        }
    }
}

data class Transaction(
    val id: String,
    val userId: String,
    val type: MovementType,
    val amountMinor: Long,
    val currency: String,
    val sourceAccountId: String? = null,
    val destinationAccountId: String? = null,
    val categoryId: String? = null,
    val merchantId: String? = null,
    val legacyKind: String? = null,
    val occurredAt: Long,
    val note: String? = null,
    val status: TransactionStatus = TransactionStatus.ACTIVE,
    val syncStatus: MovementSyncStatus = MovementSyncStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    init {
        require(amountMinor > 0) { "Amount must be strictly positive: $amountMinor" }
        require(currency.isNotBlank()) { "Currency must not be blank" }
        when (type) {
            MovementType.EXPENSE -> {
                require(!sourceAccountId.isNullOrBlank()) { "Expense requires a source account" }
                require(legacyKind != null || !categoryId.isNullOrBlank()) { "Expense requires a category" }
            }
            MovementType.INCOME -> {
                require(!sourceAccountId.isNullOrBlank()) { "Income requires a destination/source account" }
            }
            MovementType.TRANSFER -> {
                require(!sourceAccountId.isNullOrBlank()) { "Transfer requires a source account" }
                require(!destinationAccountId.isNullOrBlank()) { "Transfer requires a destination account" }
                require(sourceAccountId != destinationAccountId) { "Source and destination accounts must be different" }
            }
        }
    }
}

data class LedgerEntry(
    val id: String,
    val transactionId: String,
    val accountId: String,
    val role: LedgerRole,
    val signedAmountMinor: Long,
    val currency: String,
    val createdAt: Long = System.currentTimeMillis(),
) {
    init {
        require(signedAmountMinor != 0L) { "Signed amount cannot be zero" }
        require(accountId.isNotBlank()) { "Account ID must not be blank" }
        require(currency.isNotBlank()) { "Currency must not be blank" }
    }
}

data class TransactionItem(
    val transaction: Transaction,
    val sourceAccountAlias: String? = null,
    val destinationAccountAlias: String? = null,
    val categoryName: String? = null,
    val categoryIcon: String? = null,
    val merchantName: String? = null,
)
